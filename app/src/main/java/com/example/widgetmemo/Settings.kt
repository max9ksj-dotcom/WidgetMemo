package com.example.widgetmemo

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsStore by preferencesDataStore(name = "settings")

/** 시스템에 실제로 있는 글꼴 계열만 제공한다. 없는 계열이어도 Typeface.create가 기본 글꼴로 대체하므로 앱이 죽지 않는다. */
enum class FontChoice(val key: String, val label: String, val family: String) {
    DEFAULT("default", "기본", "sans-serif"),
    SERIF("serif", "명조(세리프)", "serif"),
    MONO("mono", "고정폭", "monospace"),
    CONDENSED("condensed", "좁은 글씨", "sans-serif-condensed"),
    CURSIVE("cursive", "필기체", "cursive"),
    CASUAL("casual", "캐주얼", "casual");

    companion object {
        fun fromKey(k: String?): FontChoice = values().firstOrNull { it.key == k } ?: DEFAULT
    }
}

/** 앱 전체에 적용되는 설정. (위젯별 설정인 배경 투명도는 SettingsStore.widgetBg 로 따로 저장한다.) */
data class AppSettings(
    val titleSp: Int = 16,
    val bodySp: Int = 14,
    val fontKey: String = FontChoice.DEFAULT.key,
    /** 0 = 자동(라이트/다크에 맞춤), 그 외 = ARGB 색상. 투명도와 분리해서 저장한다. */
    val textColor: Int = 0,
    val textAlphaPct: Int = 100,
    /** 새 위젯의 기본 배경 투명도(불투명도 %). 위젯마다 따로 바꿀 수 있다. */
    val defaultBgAlphaPct: Int = 90,
    val hideDone: Boolean = false
) {
    fun font(): FontChoice = FontChoice.fromKey(fontKey)
}

val LocalAppSettings = compositionLocalOf { AppSettings() }

object SettingsKeys {
    val HIDE_DONE = booleanPreferencesKey("widget_hide_done")
    val TITLE_SP = intPreferencesKey("title_sp")
    val BODY_SP = intPreferencesKey("body_sp")
    val FONT = stringPreferencesKey("font_key")
    val TEXT_COLOR = intPreferencesKey("text_color")
    val TEXT_ALPHA = intPreferencesKey("text_alpha_pct")
    val DEFAULT_BG = intPreferencesKey("default_bg_alpha_pct")
}

private fun Preferences.toSettings(): AppSettings {
    val d = AppSettings()
    return AppSettings(
        titleSp = (this[SettingsKeys.TITLE_SP] ?: d.titleSp).coerceIn(12, 32),
        bodySp = (this[SettingsKeys.BODY_SP] ?: d.bodySp).coerceIn(10, 28),
        fontKey = FontChoice.fromKey(this[SettingsKeys.FONT]).key,
        textColor = this[SettingsKeys.TEXT_COLOR] ?: d.textColor,
        textAlphaPct = (this[SettingsKeys.TEXT_ALPHA] ?: d.textAlphaPct).coerceIn(10, 100),
        defaultBgAlphaPct = (this[SettingsKeys.DEFAULT_BG] ?: d.defaultBgAlphaPct).coerceIn(0, 100),
        hideDone = this[SettingsKeys.HIDE_DONE] ?: d.hideDone
    )
}

object SettingsStore {
    fun flow(ctx: Context): Flow<AppSettings> =
        ctx.applicationContext.settingsStore.data.map { it.toSettings() }

    suspend fun get(ctx: Context): AppSettings = flow(ctx).first()

    /** 전체 설정을 저장한다. 메모 데이터(Room)는 건드리지 않는다. */
    suspend fun save(ctx: Context, s: AppSettings) {
        ctx.applicationContext.settingsStore.edit { p ->
            p[SettingsKeys.TITLE_SP] = s.titleSp
            p[SettingsKeys.BODY_SP] = s.bodySp
            p[SettingsKeys.FONT] = s.fontKey
            p[SettingsKeys.TEXT_COLOR] = s.textColor
            p[SettingsKeys.TEXT_ALPHA] = s.textAlphaPct
            p[SettingsKeys.DEFAULT_BG] = s.defaultBgAlphaPct
            p[SettingsKeys.HIDE_DONE] = s.hideDone
        }
    }

    private fun bgKey(widgetId: Int) = intPreferencesKey("bg_alpha_$widgetId")

    suspend fun widgetBg(ctx: Context, widgetId: Int, default: Int): Int =
        (ctx.applicationContext.settingsStore.data.first()[bgKey(widgetId)] ?: default).coerceIn(0, 100)

    suspend fun setWidgetBg(ctx: Context, widgetId: Int, pct: Int) {
        ctx.applicationContext.settingsStore.edit { it[bgKey(widgetId)] = pct.coerceIn(0, 100) }
    }

    suspend fun clearWidgets(ctx: Context, widgetIds: IntArray) {
        ctx.applicationContext.settingsStore.edit { p -> widgetIds.forEach { p.remove(bgKey(it)) } }
    }
}
