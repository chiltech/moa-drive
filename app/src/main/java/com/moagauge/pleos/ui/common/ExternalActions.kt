package com.moagauge.pleos.ui.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Explicit user-initiated handoff only. Never places calls or submits insurance requests. */
object ExternalActions {
    fun dial(context: Context, phone: String): Boolean = launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter(Char::isDigit)}")))
    fun web(context: Context, url: String): Boolean = url.startsWith("https://") && launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    fun appSettings(context: Context): Boolean = launch(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    fun soundSettings(context: Context): Boolean = launch(context, Intent(Settings.ACTION_SOUND_SETTINGS)) ||
        launch(context, Intent(Settings.ACTION_SETTINGS))
    fun copy(context: Context, label: String, value: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, value))
    }
    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) { false }
      catch (_: SecurityException) { false }
}
