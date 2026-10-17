package com.example.widgetmemo

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

object WidgetUpdater {
    private val providers = listOf(
        MemoWidgetSmallProvider::class.java,
        MemoWidgetProvider::class.java,
        MemoWidgetLargeProvider::class.java
    )

    /** 배치된 모든 크기의 위젯을 DB/설정 기준으로 다시 그린다. 실패해도 앱이 죽지 않고, 다음 갱신 때 복구된다. */
    fun refresh(ctx: Context) {
        try {
            val app = ctx.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val all = ArrayList<Int>()
            for (p in providers) {
                for (id in mgr.getAppWidgetIds(ComponentName(app, p))) {
                    mgr.updateAppWidget(id, MemoWidgetProvider.buildViews(app, id))
                    all.add(id)
                }
            }
            if (all.isNotEmpty()) mgr.notifyAppWidgetViewDataChanged(all.toIntArray(), R.id.widget_list)
        } catch (e: Exception) {
            Log.e("WidgetUpdater", "widget refresh failed", e)
        }
    }
}
