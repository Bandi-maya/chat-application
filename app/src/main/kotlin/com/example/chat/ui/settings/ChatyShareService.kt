package com.example.chat.ui.settings

import android.content.Context
import android.content.Intent

/** Port of Dart ChatyShareService (data/services/chaty_share_service.dart): system share sheet with Chaty text + store link. */
object ChatyShareService {
    const val appName = "Chaty"
    /** Dart: String.fromEnvironment('CHATY_PLAY_STORE_URL', defaultValue: ...). */
    val playStoreUrl: String = com.example.chat.BuildConfig.CHATY_PLAY_STORE_URL
    val shareMessage: String
        get() = "Connect with me on $appName \u2014 private, fast, and customizable messaging!\n\nDownload $appName here: $playStoreUrl"

    /** Returns true if the chooser was launched; on failure returns false and reports the error text. */
    fun shareApp(context: Context, onError: (String) -> Unit = {}): Boolean = try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareMessage)
            putExtra(Intent.EXTRA_SUBJECT, "Join me on $appName")
        }
        val chooser = Intent.createChooser(send, null).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    } catch (e: Exception) {
        onError("Could not open share menu: $e")
        false
    }
}
