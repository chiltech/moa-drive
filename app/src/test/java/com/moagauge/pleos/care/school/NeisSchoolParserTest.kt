package com.moagauge.pleos.care.school

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NeisSchoolParserTest {
    private val school = SchoolInfo("B10", "7132131", "남서울중학교", "서울특별시 관악구 남부순환로172길 97", "서울특별시")
    private val day = LocalDate.of(2026, 11, 13)
    // Reduced from the live NEIS response retrieved on 2026-11-13. No student data is sent or stored.
    private val schoolXml = """
        <schoolInfo><head><list_total_count>7</list_total_count><RESULT><CODE>INFO-000</CODE><MESSAGE>정상 처리되었습니다.</MESSAGE></RESULT></head>
        <row><ATPT_OFCDC_SC_CODE>B10</ATPT_OFCDC_SC_CODE><SD_SCHUL_CODE>7132131</SD_SCHUL_CODE><SCHUL_NM>남서울중학교</SCHUL_NM><LCTN_SC_NM>서울특별시</LCTN_SC_NM><ORG_RDNMA>서울특별시 관악구 남부순환로172길 97</ORG_RDNMA></row>
        <row><ATPT_OFCDC_SC_CODE>C10</ATPT_OFCDC_SC_CODE><SD_SCHUL_CODE>7181088</SD_SCHUL_CODE><SCHUL_NM>부산중학교</SCHUL_NM><LCTN_SC_NM>부산광역시</LCTN_SC_NM><ORG_RDNMA>부산광역시 동구</ORG_RDNMA></row></schoolInfo>
    """.trimIndent()
    private val mealXml = """
        <mealServiceDietInfo><head><list_total_count>1</list_total_count><RESULT><CODE>INFO-000</CODE><MESSAGE>정상 처리되었습니다.</MESSAGE></RESULT></head>
        <row><ATPT_OFCDC_SC_CODE>B10</ATPT_OFCDC_SC_CODE><SD_SCHUL_CODE>7132131</SD_SCHUL_CODE><SCHUL_NM>남서울중학교</SCHUL_NM><MMEAL_SC_CODE>2</MMEAL_SC_CODE><MMEAL_SC_NM>중식</MMEAL_SC_NM><MLSV_YMD>20261113</MLSV_YMD><DDISH_NM><![CDATA[소불고기주먹밥 (1.5.6.13.16.18)<br/>배추겉절이 (9)<BR />미니윌 (2) &amp; 과일]]></DDISH_NM><CAL_INFO>763.6 Kcal</CAL_INFO></row></mealServiceDietInfo>
    """.trimIndent()

    @Test fun schoolResultsKeepCodesAndDisambiguatingAddressAndRegion() {
        val result = NeisSchoolParser.schools(schoolXml, page = 1, pageSize = 5, hasApiKey = false)
        assertEquals(school, result.schools.first())
        assertEquals("부산광역시", result.schools.last().region)
        assertEquals(2, result.schools.size)
    }

    @Test fun keylessResultsExplainTheCapWithoutOfferingNonfunctionalPagination() {
        val result = NeisSchoolParser.schools(schoolXml, 1, 5, false)
        assertTrue(result.limitedWithoutKey)
        assertFalse(result.hasMore)
        assertTrue(NeisSchoolParser.schools(schoolXml, 1, 5, true).hasMore)
        assertFalse(NeisSchoolParser.schools(schoolXml, 2, 5, true).hasMore)
    }

    @Test fun schoolsWithoutUsableOfficialCodesCannotBeSelected() {
        val xml = schoolXml.replace("7132131", "       ")
        assertEquals(listOf("7181088"), NeisSchoolParser.schools(xml, 1, 5, false).schools.map { it.schoolCode })
        assertFalse(school.copy(officeCode = "B10&KEY=x").isValidSchool())
    }

    @Test fun mealsPreserveOriginalAllergenMarkersAndCleanBreaksAndEntities() {
        val result = NeisSchoolParser.meals(mealXml, school, day)
        val meal = result.meals.single()
        assertEquals(day, result.date)
        assertEquals("중식", meal.mealType)
        assertEquals("소불고기주먹밥 (1.5.6.13.16.18)\n배추겉절이 (9)\n미니윌 (2) & 과일", meal.menu)
        assertEquals("763.6 Kcal", meal.calories)
        assertTrue(meal.allergyText.contains("1"))
        assertTrue(meal.allergyText.contains("18"))
    }

    @Test fun noDataIsEmptyButServiceFailuresAndMalformedResponsesAreErrors() {
        val empty = "<RESULT><CODE>INFO-200</CODE><MESSAGE>해당하는 데이터가 없습니다.</MESSAGE></RESULT>"
        assertTrue(NeisSchoolParser.schools(empty, 1, 5, false).schools.isEmpty())
        assertTrue(NeisSchoolParser.meals(empty, school, day).meals.isEmpty())
        listOf("<RESULT><CODE>ERROR-290</CODE></RESULT>", "<html>maintenance</html>", "<schoolInfo>").forEach { xml ->
            assertThrows(SchoolApiException::class.java) { NeisSchoolParser.schools(xml, 1, 5, false) }
        }
    }

    @Test fun publishedMealRetainsNutritionAndOriginWithPlainTextLineBreaks() {
        val xml = checkNotNull(javaClass.getResourceAsStream("/neis/daedo-meal-20261113.xml"))
            .bufferedReader().use { it.readText() }
        val daedo = SchoolInfo("B10", "7091375", "서울대도초등학교", "서울특별시 강남구 선릉로 209", "서울특별시")
        val result = NeisSchoolParser.meals(xml, daedo, day)
        assertEquals(com.moagauge.pleos.care.child.ChildSchoolMealSample.meals, result)
        val meal = result.meals.single()
        assertEquals(7, meal.menu.lines().size)
        assertEquals("599.0 Kcal", meal.calories)
        assertEquals("탄수화물(g) : 81.5", meal.nutrition.lines().first())
        assertTrue(meal.nutrition.contains("단백질(g) : 29.7\n지방(g) : 15.5"))
        assertTrue(meal.origin.contains("돼지고기 : 국내산\n"))
        assertFalse(meal.nutrition.contains("<br"))
        assertFalse(meal.origin.contains("<br"))
        assertEquals("", NeisSchoolParser.meals(mealXml, school, day).meals.single().nutrition)
    }

    @Test fun mealsFromAnotherSchoolOrDayCannotBecomeTodaysMeal() {
        assertThrows(SchoolApiException::class.java) { NeisSchoolParser.meals(mealXml.replace("20261113", "20261112"), school, day) }
        assertThrows(SchoolApiException::class.java) { NeisSchoolParser.meals(mealXml.replace("7132131", "7010057"), school, day) }
        assertThrows(SchoolApiException::class.java) { NeisSchoolParser.meals(mealXml.replace("B10", "C10"), school, day) }
    }

    @Test fun repeatedMealRowsDoNotDuplicateTheSameMeal() {
        val row = mealXml.substringAfter("</head>").substringBefore("</mealServiceDietInfo>")
        val duplicated = mealXml.replace("</mealServiceDietInfo>", "$row</mealServiceDietInfo>")
            .replace("<list_total_count>1</list_total_count>", "<list_total_count>2</list_total_count>")
        assertEquals(1, NeisSchoolParser.meals(duplicated, school, day).meals.size)
    }

    @Test fun successfulPositiveCountsWithoutRowsAreFailuresNotEmptyResults() {
        val noSchools = schoolXml.substringBefore("</head>") + "</head></schoolInfo>"
        val noMeals = mealXml.substringBefore("</head>") + "</head></mealServiceDietInfo>"
        assertThrows(SchoolApiException::class.java) { NeisSchoolParser.schools(noSchools, 1, 5, false) }
        assertThrows(SchoolApiException::class.java) { NeisSchoolParser.meals(noMeals, school, day) }
    }

    @Test fun aSuccessfulResponseWithOnlyInvalidSchoolRowsIsAFailure() {
        val invalid = schoolXml.replace("7132131", "       ").replace("7181088", "       ")
        assertThrows(SchoolApiException::class.java) { NeisSchoolParser.schools(invalid, 1, 5, false) }
    }

    @Test fun successEnvelopesRequireNonnegativeCountsConsistentWithTheirRows() {
        for (replacement in listOf("", "<list_total_count>-1</list_total_count>", "<list_total_count>invalid</list_total_count>", "<list_total_count>0</list_total_count>")) {
            assertThrows(SchoolApiException::class.java) {
                NeisSchoolParser.schools(schoolXml.replace("<list_total_count>7</list_total_count>", replacement), 1, 5, false)
            }
            assertThrows(SchoolApiException::class.java) {
                NeisSchoolParser.meals(mealXml.replace("<list_total_count>1</list_total_count>", replacement), school, day)
            }
        }
    }

    @Test fun returnedRowsCannotExceedTheActualRequestOrKeylessSampleCap() {
        val schoolRow = schoolXml.substringAfter("</head>").substringBefore("</schoolInfo>")
        val sixSchools = schoolXml.substringBefore("</head>") + "</head>" + schoolRow.repeat(3) + "</schoolInfo>"
        assertThrows(SchoolApiException::class.java) { NeisSchoolParser.schools(sixSchools, 1, 20, false) }
        assertThrows(SchoolApiException::class.java) { NeisSchoolParser.schools(sixSchools, 1, 5, true) }
        val mealRow = mealXml.substringAfter("</head>").substringBefore("</mealServiceDietInfo>")
        val sixMeals = (mealXml.substringBefore("</head>") + "</head>" + mealRow.repeat(6) + "</mealServiceDietInfo>")
            .replace("<list_total_count>1</list_total_count>", "<list_total_count>6</list_total_count>")
        assertThrows(SchoolApiException::class.java) { NeisSchoolParser.meals(sixMeals, school, day, maxRows = 5) }
    }

    @Test fun keyAndDailyLimitErrorsProvideDifferentActionableMessages() {
        val keyError = assertThrows(SchoolApiException::class.java) {
            NeisSchoolParser.schools("<RESULT><CODE>ERROR-290</CODE></RESULT>", 1, 5, false)
        }
        val limitError = assertThrows(SchoolApiException::class.java) {
            NeisSchoolParser.schools("<RESULT><CODE>ERROR-337</CODE></RESULT>", 1, 5, false)
        }
        assertTrue(keyError.userMessage.contains("인증키"))
        assertTrue(limitError.userMessage.contains("하루 조회 한도"))
    }

    @Test fun externalEntitiesAndOversizedDocumentsAreRejected() {
        assertThrows(SchoolApiException::class.java) {
            NeisSchoolParser.schools("<!DOCTYPE schoolInfo [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>$schoolXml", 1, 5, false)
        }
        assertThrows(SchoolApiException::class.java) { NeisSchoolParser.schools("x".repeat(2_000_001), 1, 5, false) }
    }
}
