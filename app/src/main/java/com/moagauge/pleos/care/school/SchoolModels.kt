package com.moagauge.pleos.care.school

import java.time.LocalDate

const val SCHOOL_SOURCE_NAME = "나이스 교육정보 개방 포털"
const val SCHOOL_SOURCE_URL = "https://open.neis.go.kr/"

data class SchoolInfo(
    val officeCode: String,
    val schoolCode: String,
    val name: String,
    val address: String,
    val region: String = "",
)

fun SchoolInfo.isValidSchool(): Boolean = officeCode.matches(Regex("[A-Z][0-9]{2}")) &&
    schoolCode.matches(Regex("[0-9]{7}")) && name.isNotBlank() && name.length <= 100 &&
    address.length <= 240 && region.length <= 60

data class SchoolSearchResult(
    val schools: List<SchoolInfo>,
    val hasMore: Boolean,
    val page: Int,
    val limitedWithoutKey: Boolean,
    val source: String = SCHOOL_SOURCE_NAME,
)

data class SchoolMeal(
    val mealType: String,
    val menu: String,
    val calories: String,
    val allergyText: String,
    val nutrition: String = "",
    val origin: String = "",
)

data class SchoolMealResult(
    val date: LocalDate,
    val meals: List<SchoolMeal>,
    val source: String = SCHOOL_SOURCE_NAME,
)

/** Public messages never include request URLs, keys, child names or raw response bodies. */
class SchoolApiException(val userMessage: String = "학교 정보를 불러오지 못했어요. 잠시 후 다시 확인해 주세요.") : Exception(userMessage)
