package com.moadrive.pleos.care.news

import org.junit.Assert.*
import org.junit.Test

class CareNewsParserTest {
    // Reduced from the official lists fetched on 2026-11-13; publication dates are list values.
    private val educationRow = """
        <tr><td class="no">70</td><td class="title left">
          <a href="#" onclick="javascript:goView('294', '107612', '0', null, 'W', '1', 'N', '');"
             title="「유아교육법 시행령」 일부개정령안 국무회의 통과">
            「유아교육법 시행령」 일부개정령안 국무회의 통과
          </a></td><td>홍보담당관</td><td>2026-11-10</td><td>990</td></tr>
    """.trimIndent()
    private val petRow = """
        <tr><td>134</td><td><p>
          <a href="/bbs/home/792/582084/artclView.do" onclick="jf_viewArtcl('home', '792', '582084')">
            사진 한 장으로 반려동물 건강 체크, 인공지능(AI)로 여는 펫헬스케어 시대
            <span class="new">새글</span>
          </a></p><dl><dt>작성일/작성자</dt><dd class="date">2026.10.29</dd>
          <dd class="file">첨부파일</dd></dl></td></tr>
    """.trimIndent()

    @Test fun parsesEducationOfficialTitleDateAndNumericArticleIdentity() {
        val article = CareNewsParser.parse(CareNewsKind.CHILD, educationRow).single()
        assertEquals("「유아교육법 시행령」 일부개정령안 국무회의 통과", article.title)
        assertEquals("2026-11-10", article.publishedDate)
        assertEquals("교육부", article.source)
        assertEquals("https://www.moe.go.kr/boardCnts/viewRenew.do?boardID=294&boardSeq=107612&lev=0&searchType=null&statusYN=W&page=1&s=moe&m=020402&opType=N", article.url)
    }

    @Test fun parsesPetDatesAndRemovesListBadgesFromHeadline() {
        val article = CareNewsParser.parse(CareNewsKind.PET, petRow).single()
        assertEquals("사진 한 장으로 반려동물 건강 체크, 인공지능(AI)로 여는 펫헬스케어 시대", article.title)
        assertEquals("2026-10-29", article.publishedDate)
        assertEquals("농림축산식품부", article.source)
        assertEquals("https://www.mafra.go.kr/bbs/home/792/582084/artclView.do", article.url)
    }

    @Test fun decodesHeadlineEntitiesWithoutExecutingMarkup() {
        val article = CareNewsParser.parse(CareNewsKind.CHILD, educationRow.replace(
            "「유아교육법 시행령」 일부개정령안 국무회의 통과", "유아&nbsp;교육 &amp; 돌봄 &#39;안내&#39; &#xB7; 소식")).single()
        assertEquals("유아 교육 & 돌봄 '안내' · 소식", article.title)
        assertTrue(CareNewsParser.parse(CareNewsKind.CHILD,
            educationRow.replace("「유아교육법 시행령」", "<script>alert(1)</script>유아")).isEmpty())
    }

    @Test fun ignoresUnrelatedItemsInvalidDatesAndUnsafeArticleLinks() {
        assertTrue(CareNewsParser.parse(CareNewsKind.PET, petRow.replace("반려동물", "농식품")).isEmpty())
        assertTrue(CareNewsParser.parse(CareNewsKind.PET, petRow.replace("2026.10.29", "2026.02.30")).isEmpty())
        listOf("https://evil.example/artclView.do", "javascript:alert(1)", "//evil.example/bbs/home/792/582084/artclView.do",
            "https://www.mafra.go.kr@evil.example/bbs/home/792/582084/artclView.do").forEach { unsafe ->
            assertTrue(CareNewsParser.parse(CareNewsKind.PET,
                petRow.replace("/bbs/home/792/582084/artclView.do", unsafe)).isEmpty())
        }
    }

    @Test fun deduplicatesAndOrdersNewestFirstWithoutUsingNavigationLinks() {
        val older = petRow.replace("582084", "579409").replace("2026.10.29", "2026.10.13")
        val articles = CareNewsParser.parse(CareNewsKind.PET,
            "<nav><a href='/bbs/home/792/1/artclView.do'>반려 메뉴</a></nav>$older$petRow$petRow")
        assertEquals(listOf("2026-10-29", "2026-10-13"), articles.map { it.publishedDate })
    }

    @Test fun rejectsOversizedDocumentsAndDoesNotAcceptChangedBoardMarkup() {
        assertThrows(CareNewsException::class.java) {
            CareNewsParser.parse(CareNewsKind.CHILD, "x".repeat(2_000_001))
        }
        assertTrue(CareNewsParser.parse(CareNewsKind.CHILD, "<html>서비스 점검 중입니다</html>").isEmpty())
        assertTrue(CareNewsParser.parse(CareNewsKind.CHILD, educationRow.replace("'294'", "'999'")).isEmpty())
    }
}
