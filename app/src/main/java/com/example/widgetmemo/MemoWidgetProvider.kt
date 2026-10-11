package com.example.widgetmemo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.runBlocking

class MemoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, buildViews(ctx, id))
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
    }

    override fun onAppWidgetOptionsChanged(
        ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle
    ) {
        onUpdate(ctx, mgr, intArrayOf(id))
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        runBlocking { NoteDb.get(ctx).dao().deleteBindings(ids.toList()) }
    }

    companion object {
        private fun openPi(ctx: Context, widgetId: Int, memoId: Long, addItem: Boolean): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java).apply {
                data = Uri.parse("widgetmemo://memo/$memoId/$widgetId/${if (addItem) "add" else "open"}")
                putExtra(Extras.MEMO_ID, memoId)
                putExtra(Extras.ADD_ITEM, addItem)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val code = widgetId * 2 + if (addItem) 1 else 0
            return PendingIntent.getActivity(
                ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openAppPi(ctx: Context, widgetId: Int): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java).apply {
                data = Uri.parse("widgetmemo://app/$widgetId")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                ctx, 100000 + widgetId, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun buildViews(ctx: Context, widgetId: Int): RemoteViews {
            val dao = NoteDb.get(ctx).dao()
            val boundId = runBlocking { dao.getBoundMemoId(widgetId) }
            val memo = boundId?.let { runBlocking { dao.getMemo(it) } }
            val rv = RemoteViews(ctx.packageName, R.layout.widget_memo)

            // 메모 미선택 / 삭제된 메모: 오류 없이 안내 문구만 표시
            if (memo == null) {
                rv.setTextViewText(R.id.widget_title,
                    if (boundId == null) "메모 선택 필요" else "삭제된 메모")
                rv.setTextViewText(
                    R.id.widget_empty,
                    if (boundId == null) "위젯을 길게 눌러 ‘설정’에서 메모를 선택하세요."
                    else "이 메모는 삭제되었습니다.\n위젯을 길게 눌러 설정에서 다른 메모를 고르거나 위젯을 제거하세요."
                )
                rv.setViewVisibility(R.id.widget_list, View.GONE)
                rv.setViewVisibility(R.id.widget_body, View.GONE)
                rv.setViewVisibility(R.id.widget_add, View.GONE)
                rv.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                rv.setOnClickPendingIntent(R.id.widget_root, openAppPi(ctx, widgetId))
                return rv
            }

            rv.setTextViewText(R.id.widget_title, memo.title.ifBlank { "제목 없음" })
            rv.setOnClickPendingIntent(R.id.widget_title, openPi(ctx, widgetId, memo.id, false))
            rv.setViewVisibility(R.id.widget_empty, View.GONE)

            if (memo.isChecklist) {
                rv.setViewVisibility(R.id.widget_body, View.GONE)
                rv.setViewVisibility(R.id.widget_list, View.VISIBLE)
                rv.setViewVisibility(R.id.widget_add, View.VISIBLE)
                rv.setOnClickPendingIntent(R.id.widget_add, openPi(ctx, widgetId, memo.id, true))

                val svc = Intent(ctx, MemoWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                @Suppress("DEPRECATION")
                rv.setRemoteAdapter(R.id.widget_list, svc)

                // 항목 클릭 -> 브로드캐스트 템플릿(FLAG_MUTABLE, 명시적 인텐트). 항목별 ID는 fill-in 인텐트로 전달
                val template = Intent(ctx, ToggleReceiver::class.java).apply {
                    action = Extras.ACTION_TOGGLE
                }
                rv.setPendingIntentTemplate(
                    R.id.widget_list,
                    PendingIntent.getBroadcast(
                        ctx, widgetId, template,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                )
            } else {
                rv.setViewVisibility(R.id.widget_list, View.GONE)
                rv.setViewVisibility(R.id.widget_add, View.GONE)
                rv.setViewVisibility(R.id.widget_body, View.VISIBLE)
                rv.setTextViewText(R.id.widget_body, memo.body.ifBlank { "내용 없음" })
                rv.setOnClickPendingIntent(R.id.widget_body, openPi(ctx, widgetId, memo.id, false))
            }
            return rv
        }

    }
}
