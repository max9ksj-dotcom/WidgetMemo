package com.example.widgetmemo

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

object WidgetSizing {
    /** 세로 모드는 (최소 너비, 최대 높이), 가로 모드는 (최대 너비, 최소 높이)가 현재 위젯 크기다. */
    fun sizeDp(ctx: Context, widgetId: Int): Pair<Int, Int> {
        val o = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(widgetId)
        val landscape = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val w = if (landscape) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val h = if (landscape) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        return w to h
    }
}

/** 위젯 글자색: 사용자가 고른 색(없으면 라이트/다크 기본색)에 글씨 투명도만 곱한다. */
fun widgetTextColor(ctx: Context, s: AppSettings): Int {
    val base = if (s.textColor == 0) ctx.getColor(R.color.widget_text) else s.textColor
    return withAlpha(base, s.textAlphaPct)
}

/**
 * RemoteViews 는 TextView.setTypeface 를 지원하지 않으므로, 글꼴/굵기/취소선을 Span 으로 실어 보낸다.
 * (TypefaceSpan·StyleSpan·StrikethroughSpan 은 모두 Parcelable 이라 RemoteViews 로 전달된다.)
 */
fun styledText(text: String, font: FontChoice, bold: Boolean = false, strike: Boolean = false): CharSequence {
    val needsFont = font != FontChoice.DEFAULT
    if (text.isEmpty() || (!needsFont && !bold && !strike)) return text
    val sp = SpannableString(text)
    val flag = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    if (needsFont) sp.setSpan(TypefaceSpan(font.family), 0, text.length, flag)
    if (bold) sp.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, flag)
    if (strike) sp.setSpan(StrikethroughSpan(), 0, text.length, flag)
    return sp
}
