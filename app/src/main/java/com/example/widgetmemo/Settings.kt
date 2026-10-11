package com.example.widgetmemo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.settingsStore by preferencesDataStore(name = "settings")

object SettingsKeys {
    val HIDE_DONE = booleanPreferencesKey("widget_hide_done")
}
