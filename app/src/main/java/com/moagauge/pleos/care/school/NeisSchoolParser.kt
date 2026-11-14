package com.moagauge.pleos.care.school

import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

internal const val MAX_SCHOOL_RESPONSE_BYTES = 2_000_000

/** Adapters for NEIS schoolInfo and mealServiceDietInfo XML, verified on 2026-11-13. */
internal object NeisSchoolParser {
    private val htmlBreak = Regex("<br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
    private val tags = Regex("<[^>]*>")
    private val unsafeBlocks = Regex("<(script|style|iframe|object)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val entities = Regex("&(#x[0-9a-fA-F]{1,6}|#[0-9]{1,7}|[A-Za-z]{2,8});")
    private val allergens = Regex("\\(([0-9]{1,2}(?:[.,]\\s*[0-9]{1,2})*)\\)")

    fun schools(xml: String, page: Int, pageSize: Int, hasApiKey: Boolean): SchoolSearchResult {
        val root = document(xml, "schoolInfo")
            ?: return SchoolSearchResult(emptyList(), false, page, false)
        val (total, rows) = root.responseRows(if (hasApiKey) pageSize else 5)
        val schools = rows.mapNotNull { row ->
            SchoolInfo(row.value("ATPT_OFCDC_SC_CODE"), row.value("SD_SCHUL_CODE"),
                plainText(row.value("SCHUL_NM")), plainText(row.value("ORG_RDNMA")), plainText(row.value("LCTN_SC_NM")))
                .takeIf { it.isValidSchool() }
        }.distinctBy { it.officeCode to it.schoolCode }
        if (rows.isNotEmpty() && schools.isEmpty()) throw SchoolApiException("검색된 학교의 식별 정보를 확인하지 못했어요. 학교 이름을 더 정확히 입력해 주세요.")
        return SchoolSearchResult(schools,
            hasMore = hasApiKey && rows.isNotEmpty() && page < 100 && page.toLong() * pageSize < total,
            page = page, limitedWithoutKey = !hasApiKey && total > rows.size)
    }

    fun meals(xml: String, school: SchoolInfo, date: LocalDate, maxRows: Int = 10): SchoolMealResult {
        if (!school.isValidSchool()) throw SchoolApiException("학교를 다시 검색해 선택해 주세요.")
        val root = document(xml, "mealServiceDietInfo") ?: return SchoolMealResult(date, emptyList())
        val expectedDate = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        val (_, rows) = root.responseRows(maxRows)
        val meals = rows.mapNotNull { row ->
            if (row.value("ATPT_OFCDC_SC_CODE") != school.officeCode || row.value("SD_SCHUL_CODE") != school.schoolCode ||
                row.value("MLSV_YMD") != expectedDate) return@mapNotNull null
            val type = plainText(row.value("MMEAL_SC_NM")).takeIf { it.length in 1..30 } ?: return@mapNotNull null
            val menu = plainText(row.value("DDISH_NM")).takeIf { it.length in 1..8_000 } ?: return@mapNotNull null
            val allergyNumbers = allergens.findAll(menu).flatMap { it.groupValues[1].split(Regex("[.,]\\s*")).asSequence() }
                .mapNotNull { it.toIntOrNull() }.filter { it in 1..19 }.distinct().sorted().toList()
            SchoolMeal(type, menu, plainText(row.value("CAL_INFO")).take(120),
                if (allergyNumbers.isEmpty()) "" else "원문 알레르기 번호 · ${allergyNumbers.joinToString(", ")}\n해당 식품은 학교 안내에서 확인해 주세요.",
                nutrition = plainText(row.value("NTR_INFO")).take(4_000),
                origin = plainText(row.value("ORPLC_INFO")).take(8_000))
        }.distinctBy { it.mealType to it.menu }
        // A response for a different date/school is a failure, not evidence of an empty menu.
        if (rows.isNotEmpty() && meals.isEmpty()) throw SchoolApiException("선택한 학교와 날짜의 급식 정보를 확인하지 못했어요.")
        return SchoolMealResult(date, meals)
    }

    private fun document(xml: String, expectedRoot: String): Element? {
        if (xml.length > MAX_SCHOOL_RESPONSE_BYTES || Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml)) throw SchoolApiException()
        val root = try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            }
            factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> InputSource(StringReader("")) }
                setErrorHandler(object : DefaultHandler() {
                    override fun error(error: SAXParseException) { throw error }
                    override fun fatalError(error: SAXParseException) { throw error }
                })
            }.parse(InputSource(StringReader(xml))).documentElement
        } catch (_: Exception) { throw SchoolApiException() }
        val code = root.value("CODE")
        when (code) {
            "INFO-200" -> return null
            "INFO-000" -> if (root.tagName != expectedRoot) throw SchoolApiException()
            "ERROR-290", "INFO-300" -> throw SchoolApiException("학교 정보 인증키 설정을 확인해 주세요.")
            "ERROR-337" -> throw SchoolApiException("학교 정보의 하루 조회 한도에 도달했어요. 나중에 다시 확인해 주세요.")
            "ERROR-300", "ERROR-333", "ERROR-336" -> throw SchoolApiException("학교 정보 조회 조건을 확인하지 못했어요. 학교를 다시 선택해 주세요.")
            else -> throw SchoolApiException()
        }
        return root
    }

    private fun Element.value(name: String): String = getElementsByTagName(name).item(0)?.textContent?.trim().orEmpty()
    private fun Element.responseRows(maxRows: Int): Pair<Int, List<Element>> {
        val headers = getElementsByTagName("head")
        val header = headers.item(0) as? Element ?: throw SchoolApiException()
        if (headers.length != 1 || header.value("CODE") != "INFO-000") throw SchoolApiException()
        val total = header.value("list_total_count").toIntOrNull()?.takeIf { it >= 0 } ?: throw SchoolApiException()
        val nodes = getElementsByTagName("row")
        if (maxRows !in 1..100 || nodes.length > maxRows || nodes.length > total || (total > 0 && nodes.length == 0))
            throw SchoolApiException()
        return total to (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun plainText(value: String): String {
        val cleaned = tags.replace(htmlBreak.replace(unsafeBlocks.replace(value, ""), "\n"), " ")
        return entities.replace(cleaned) { match ->
            when (val entity = match.groupValues[1]) {
                "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                "middot" -> "·"; "ndash" -> "–"; "mdash" -> "—"
                else -> {
                    val code = if (entity.startsWith("#x")) entity.drop(2).toIntOrNull(16) else entity.removePrefix("#").toIntOrNull()
                    if (code != null && code in 32..0x10FFFF && code !in 0xD800..0xDFFF) String(Character.toChars(code)) else match.value
                }
            }
        }.lineSequence().map { it.replace(Regex("[\\t \\u00a0]+"), " ").filterNot(Char::isISOControl).trim() }
            .filter { it.isNotEmpty() }.joinToString("\n")
    }
}
