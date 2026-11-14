package com.moagauge.pleos.care.child

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.moagauge.pleos.care.school.SchoolInfo
import com.moagauge.pleos.care.school.isValidSchool

/** Family data is stored only in this app's dedicated private preferences. */
class ChildCareStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("moa_child_care_v1", Context.MODE_PRIVATE)

    fun load(): ChildCareData = runCatching {
        val root = JSONObject(preferences.getString("data", null) ?: return ChildCareData())
        val profile = root.optJSONObject("profile")?.let { entry ->
            val name = entry.optString("name").trim()
            val age = entry.optInt("age", -1)
            val school = entry.optString("school").trim()
            val schoolInfo = entry.optJSONObject("schoolInfo")?.let { item ->
                val officeCode = item.optString("officeCode").trim()
                val schoolCode = item.optString("schoolCode").trim()
                val schoolName = item.optString("name").trim()
                if (officeCode.isBlank() || schoolCode.isBlank() || schoolName.isBlank()) null
                else SchoolInfo(officeCode, schoolCode, schoolName, item.optString("address"), item.optString("region"))
                    .takeIf { it.isValidSchool() }
            }
            if (validateChildProfile(name, age.toString(), school) != null) null else ChildProfile(
                name, age, runCatching { ChildGender.valueOf(entry.optString("gender")) }.getOrDefault(ChildGender.UNSPECIFIED),
                schoolInfo?.name ?: school, schoolInfo)
        }
        val day = root.optJSONObject("schoolDay")?.let { entry ->
            parseChildReminderDate(entry.optString("date"))?.let { date ->
                ChildSchoolDay(date, entry.optString("classes").take(100), entry.optString("meal").take(120))
            }
        }
        val items = root.optJSONArray("reminders") ?: JSONArray()
        val reminders = (0 until items.length()).mapNotNull { index ->
            val entry = items.optJSONObject(index) ?: return@mapNotNull null
            val id = entry.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val title = entry.optString("title").trim().takeIf { it.isNotEmpty() && it.length <= 40 } ?: return@mapNotNull null
            val date = parseChildReminderDate(entry.optString("date")) ?: return@mapNotNull null
            ChildVaccinationReminder(id, title, date, entry.optString("note").take(120),
                parseChildReminderDate(entry.optString("completedOn")), entry.optBoolean("notificationsEnabled", false))
        }.distinctBy { it.id }.take(50)
        ChildCareData(profile, day, reminders)
    }.getOrDefault(ChildCareData())

    fun save(data: ChildCareData) {
        data.profile?.let { require(validateChildProfile(it.name, it.age.toString(), it.school) == null) }
        require(data.schoolDay?.isSample != true) { "Sample school data must never replace personal data" }
        require(data.reminders.size <= 50)
        require(data.reminders.all { it.title.isNotBlank() && it.title.length <= 40 && it.note.length <= 120 && it.date.year in 2000..2100 })
        val root = JSONObject()
        data.profile?.let { profile -> root.put("profile", JSONObject().apply {
            put("name", profile.name); put("age", profile.age); put("gender", profile.gender.name); put("school", profile.school)
            profile.linkedSchool?.let { school -> put("schoolInfo", JSONObject().apply {
                put("officeCode", school.officeCode); put("schoolCode", school.schoolCode); put("name", school.name)
                put("address", school.address); put("region", school.region)
            }) }
        }) }
        data.schoolDay?.let { day -> root.put("schoolDay", JSONObject().apply {
            put("date", day.date.toString()); put("classes", day.classes); put("meal", day.meal)
        }) }
        root.put("reminders", JSONArray().apply { data.reminders.forEach { reminder -> put(JSONObject().apply {
            put("id", reminder.id); put("title", reminder.title); put("date", reminder.date.toString()); put("note", reminder.note)
            put("notificationsEnabled", reminder.notificationsEnabled)
            reminder.completedOn?.let { put("completedOn", it.toString()) }
        }) } })
        preferences.edit().putString("data", root.toString()).apply()
        ChildReminderNotifications.reschedule(appContext, data)
    }
}
