package com.example.widgetmemo

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking

class MemoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(
            applicationContext,
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        )

    private sealed interface Row {
        data class Check(val item: CheckItem) : Row
        data class Line(val text: String, val index: Int) : Row
    }

    private class Factory(private val ctx: Context, private val widgetId: Int) : RemoteViewsFactory {
        private var rows: List<Row> = emptyList()
        private var settings = AppSettings()
        private var compact = false

        override fun onCreate() {}

        // 위젯 갱신 때마다 DB 와 설정을 다시 읽는다. 바인더 스레드에서 호출되므로 블로킹해도 된다.
        override fun onDataSetChanged() {
            val (w, h) = WidgetSizing.sizeDp(ctx, widgetId)
            compact = isCompactSize(w, h)
            runBlocking {
                val dao = NoteDb.get(ctx).dao()
                settings = SettingsStore.get(ctx)
                val memoId = dao.getBoundMemoId(widgetId)
                val memo = memoId?.let { dao.getMemo(it) }
                rows = when {
                    memo == null -> emptyList()
                    memo.isChecklist -> dao.getItems(memo.id)
                        .filter { !settings.hideDone || !it.checked }
                        .map { Row.Check(it) }
                    // 텍스트 메모: 줄 단위로 나눠 목록에 넣으면 긴 메모도 위젯 안에서 스크롤된다.
                    else -> memo.body.split("\n").mapIndexed { i, t -> Row.Line(t, i) }
                }
            }
        }

        override fun onDestroy() { rows = emptyList() }
        override fun getCount() = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val s = settings
            val font = s.font()
            val color = widgetTextColor(ctx, s)
            return when (val row = rows.getOrNull(position)) {
                is Row.Check -> {
                    val item = row.item
                    val rv = RemoteViews(
                        ctx.packageName,
                        if (compact) R.layout.widget_item_compact else R.layout.widget_item
                    )
                    val label = item.text.ifBlank { "(빈 항목)" }
                    rv.setTextViewText(R.id.item_text, styledText(label, font, strike = item.checked))
                    rv.setTextViewTextSize(R.id.item_text, TypedValue.COMPLEX_UNIT_SP, s.bodySp.toFloat())
                    // 완료 항목은 같은 색을 흐리게(글씨 색/투명도 설정과 별개로 완료 상태는 검은 체크박스로 구분)
                    rv.setTextColor(R.id.item_text, if (item.checked) withAlpha(color, 60) else color)
                    rv.setImageViewResource(
                        R.id.item_check,
                        if (item.checked) R.drawable.ic_check_on else R.drawable.ic_check_off
                    )
                    rv.setContentDescription(
                        R.id.item_check,
                        if (item.checked) "완료됨: $label" else "미완료: $label"
                    )
                    rv.setOnClickFillInIntent(R.id.item_root, Intent().putExtra(Extras.ITEM_ID, item.id))
                    rv
                }
                is Row.Line -> {
                    val rv = RemoteViews(ctx.packageName, R.layout.widget_line)
                    rv.setTextViewText(R.id.line_text, styledText(row.text.ifBlank { " " }, font))
                    rv.setTextViewTextSize(R.id.line_text, TypedValue.COMPLEX_UNIT_SP, s.bodySp.toFloat())
                    rv.setTextColor(R.id.line_text, color)
                    rv.setOnClickFillInIntent(R.id.line_text, Intent())
                    rv
                }
                null -> RemoteViews(ctx.packageName, R.layout.widget_line)
            }
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 3
        override fun getItemId(position: Int): Long = when (val r = rows.getOrNull(position)) {
            is Row.Check -> r.item.id
            is Row.Line -> r.index.toLong()
            null -> position.toLong()
        }
        override fun hasStableIds() = true
    }
}
