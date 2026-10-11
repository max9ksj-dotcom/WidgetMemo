package com.example.widgetmemo

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

object WidgetUpdater {
    /** 배치된 모든 위젯을 DB 기준으로 다시 그린다. 실패해도 앱이 죽지 않고, 다음 갱신 때 복구된다. */
    fun refresh(ctx: Context) {
        try {
            val app = ctx.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, MemoWidgetProvider::class.java))
            if (ids.isEmpty()) return
            for (id in ids) mgr.updateAppWidget(id, MemoWidgetProvider.buildViews(app, id))
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        } catch (e: Exception) {
            Log.e("WidgetUpdater", "widget refresh failed", e)
        }
    }
}
