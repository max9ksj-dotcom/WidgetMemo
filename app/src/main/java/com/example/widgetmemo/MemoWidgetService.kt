package com.example.widgetmemo

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MemoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        Factory(
            applicationContext,
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        )

    private class Factory(private val ctx: Context, private val widgetId: Int) : RemoteViewsFactory {
        private var items: List<CheckItem> = emptyList()

        override fun onCreate() {}

        // 위젯 갱신 때마다 DB에서 다시 읽는다. 이 메서드는 바인더 스레드에서 호출되므로 블로킹 허용.
        override fun onDataSetChanged() {
            items = runBlocking {
                val dao = NoteDb.get(ctx).dao()
                val memoId = dao.getBoundMemoId(widgetId) ?: return@runBlocking emptyList()
                val hideDone = ctx.settingsStore.data.first()[SettingsKeys.HIDE_DONE] ?: false
                dao.getItems(memoId).filter { !hideDone || !it.checked }
            }
        }

        override fun onDestroy() { items = emptyList() }
        override fun getCount() = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_item)
            val item = items.getOrNull(position) ?: return rv
            val label = item.text.ifBlank { "(빈 항목)" }
            val text = SpannableString(label)
            if (item.checked) {
                text.setSpan(StrikethroughSpan(), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            rv.setTextViewText(R.id.item_text, text)
            rv.setTextColor(
                R.id.item_text,
                ctx.getColor(if (item.checked) R.color.widget_text_sub else R.color.widget_text)
            )
            rv.setImageViewResource(
                R.id.item_check,
                if (item.checked) R.drawable.ic_check_on else R.drawable.ic_check_off
            )
            rv.setContentDescription(
                R.id.item_check,
                if (item.checked) "완료됨: $label" else "미완료: $label"
            )
            rv.setOnClickFillInIntent(
                R.id.item_root,
                Intent().putExtra(Extras.ITEM_ID, item.id)
            )
            return rv
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = items.getOrNull(position)?.id ?: position.toLong()
        override fun hasStableIds() = true
    }
}
