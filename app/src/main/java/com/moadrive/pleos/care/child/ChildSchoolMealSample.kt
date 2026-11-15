package com.moadrive.pleos.care.child

import com.moadrive.pleos.care.school.SchoolInfo
import com.moadrive.pleos.care.school.SchoolMeal
import com.moadrive.pleos.care.school.SchoolMealResult
import java.time.LocalDate

/**
 * Published NEIS meal, retrieved 2026-11-15. The fixed source date is intentionally
 * retained when previewing on another day; this never enters the child's stored data.
 * Source: https://open.neis.go.kr/hub/mealServiceDietInfo?Type=xml&ATPT_OFCDC_SC_CODE=B10&SD_SCHUL_CODE=7091375&MLSV_YMD=20261113
 * The original response is preserved in test resources/neis/daedo-meal-20261113.xml.
 */
internal object ChildSchoolMealSample {
    val school = SchoolInfo("B10", "7091375", "서울대도초등학교", "서울특별시 강남구 선릉로 209", "서울특별시")
    val meals = SchoolMealResult(LocalDate.of(2026, 11, 13), listOf(SchoolMeal(
        mealType = "중식",
        menu = """
            현미찹쌀밥
            낙지수제비국 (5.6)
            돼지갈비찜 (5.6.10)
            명엽채볶음 (5.6)
            배추김치 (9)
            망고
            급식우유 (2)
        """.trimIndent(),
        calories = "599.0 Kcal",
        allergyText = "원문 알레르기 번호 · 2, 5, 6, 9, 10\n해당 식품은 학교 안내에서 확인해 주세요.",
        nutrition = """
            탄수화물(g) : 81.5
            단백질(g) : 29.7
            지방(g) : 15.5
            비타민A(R.E) : 77.9
            티아민(mg) : 0.6
            리보플라빈(mg) : 0.4
            비타민C(mg) : 10.9
            칼슘(mg) : 176.2
            철분(mg) : 2.5
        """.trimIndent(),
        origin = """
            쇠고기(종류) : 국내산(한우)
            쇠고기 식육가공품 : 국내산
            돼지고기 : 국내산
            돼지고기 식육가공품 : 국내산
            닭고기 : 국내산
            닭고기 식육가공품 : 국내산
            오리고기 : 국내산
            오리고기 가공품 : 국내산
            쌀 : 국내산
            배추 : 국내산
            고춧가루 : 국내산
            콩 : 국내산
            낙지 : 국내산
            고등어 : 국내산
            갈치 : 국내산
            오징어 : 국내산
            꽃게 : 국내산
            참조기 : 국내산
            비고 :
        """.trimIndent(),
    )))
    val day = ChildSchoolDay(meals.date, "미술 · 나만의 동물 그리기", "돼지갈비찜과 망고", isSample = true)
}
