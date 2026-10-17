package com.example.widgetmemo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.runBlocking

/** 중형 위젯(4x2). 소형/대형은 같은 로직을 쓰는 하위 클래스이며, 실제 레이아웃은 현재 크기에 따라 정해진다. */
open class MemoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, buildViews(ctx, id))
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
    }

    // 사용자가 홈 화면에서 위젯 크기를 바꾸면 호출된다 -> 크기에 맞는 레이아웃으로 다시 그린다.
    override fun onAppWidgetOptionsChanged(
        ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle
    ) {
        onUpdate(ctx, mgr, intArrayOf(id))
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        runBlocking {
            NoteDb.get(ctx).dao().deleteBindings(ids.toList())
            SettingsStore.clearWidgets(ctx, ids)
        }
    }

    companion object {
        private const val CODE_OPEN = 0
        private const val CODE_ADD = 1
        private const val CODE_APP = 2
        private const val CODE_LIST_ACTIVITY = 3
        private const val CODE_LIST_TOGGLE = 4

        /**
         * 위젯 인스턴스(widgetId)마다 서로 다른 data URI 와 requestCode 를 써서 PendingIntent 가 섞이지 않게 한다.
         * (PendingIntent 는 extras 를 구분하지 않으므로 data 로 식별한다.)
         */
        private fun openPi(ctx: Context, widgetId: Int, memoId: Long, addItem: Boolean): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java).apply {
                data = Uri.parse("widgetmemo://memo/$memoId/$widgetId/${if (addItem) "add" else "open"}")
                putExtra(Extras.MEMO_ID, memoId)
                putExtra(Extras.ADD_ITEM, addItem)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val code = widgetId * 10 + if (addItem) CODE_ADD else CODE_OPEN
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
                ctx, widgetId * 10 + CODE_APP, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun buildViews(ctx: Context, widgetId: Int): RemoteViews {
            val dao = NoteDb.get(ctx).dao()
            val s = runBlocking { SettingsStore.get(ctx) }
            val bgPct = runBlocking { SettingsStore.widgetBg(ctx, widgetId, s.defaultBgAlphaPct) }
            val (w, h) = WidgetSizing.sizeDp(ctx, widgetId)
            val compact = isCompactSize(w, h)
            val rv = RemoteViews(
                ctx.packageName,
                if (compact) R.layout.widget_memo_compact else R.layout.widget_memo
            )

            // 배경 투명도: 위젯별 값. 글씨 투명도와는 완전히 독립이다.
            rv.setInt(R.id.widget_bg_img, "setImageAlpha", 255 * bgPct / 100)

            val color = widgetTextColor(ctx, s)
            val font = s.font()
            rv.setTextColor(R.id.widget_title, color)
            rv.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, s.titleSp.toFloat())
            rv.setTextColor(R.id.widget_empty, withAlpha(color, 70))
            rv.setTextViewTextSize(R.id.widget_empty, TypedValue.COMPLEX_UNIT_SP, s.bodySp.toFloat())

            val boundId = runBlocking { dao.getBoundMemoId(widgetId) }
            val memo = boundId?.let { runBlocking { dao.getMemo(it) } }

            // 메모 미선택 / 삭제된 메모: 앱이 죽지 않도록 안내 문구만 보여주고, 누르면 앱 목록을 연다.
            if (memo == null) {
                rv.setTextViewText(R.id.widget_title, "")
                rv.setTextViewText(
                    R.id.widget_empty,
                    if (boundId == null) "위젯을 길게 눌러 ‘설정’에서 메모를 선택하세요."
                    else "이 메모는 삭제되었습니다.\n위젯을 길게 눌러 ‘설정’에서 다른 메모를 고르거나 위젯을 제거하세요."
                )
                rv.setViewVisibility(R.id.widget_list, View.GONE)
                rv.setViewVisibility(R.id.widget_add, View.GONE)
                rv.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                rv.setOnClickPendingIntent(R.id.widget_root, openAppPi(ctx, widgetId))
                return rv
            }

            // 제목이 비어 있으면 비어 있는 그대로 둔다(임의 문구 없음).
            rv.setTextViewText(R.id.widget_title, styledText(memo.title, font, bold = true))

            // 제목/편집 아이콘 -> 해당 메모 편집 화면. (체크박스와는 별도의 클릭 영역)
            val open = openPi(ctx, widgetId, memo.id, false)
            rv.setOnClickPendingIntent(R.id.widget_title, open)
            rv.setViewVisibility(R.id.widget_add, View.VISIBLE)
            if (memo.isChecklist) {
                rv.setImageViewIcon(R.id.widget_add, Icon.createWithResource(ctx, R.drawable.ic_add).setTint(color))
                rv.setContentDescription(R.id.widget_add, "항목 추가")
                rv.setOnClickPendingIntent(R.id.widget_add, openPi(ctx, widgetId, memo.id, true))
            } else {
                rv.setImageViewIcon(R.id.widget_add, Icon.createWithResource(ctx, R.drawable.ic_edit).setTint(color))
                rv.setContentDescription(R.id.widget_add, "메모 편집")
                rv.setOnClickPendingIntent(R.id.widget_add, open)
            }
            rv.setOnClickPendingIntent(R.id.widget_empty, open)

            val itemCount = if (memo.isChecklist) runBlocking { dao.getItems(memo.id).size } else 0
            val emptyMsg = when {
                memo.isChecklist && itemCount == 0 -> "항목이 없습니다.\n오른쪽 위 + 로 추가하세요."
                !memo.isChecklist && memo.body.isBlank() -> "내용이 없습니다."
                else -> null
            }
            if (emptyMsg != null) {
                rv.setTextViewText(R.id.widget_empty, emptyMsg)
                rv.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                rv.setViewVisibility(R.id.widget_list, View.GONE)
                return rv
            }
            rv.setViewVisibility(R.id.widget_empty, View.GONE)
            rv.setViewVisibility(R.id.widget_list, View.VISIBLE)

            val svc = Intent(ctx, MemoWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            @Suppress("DEPRECATION")
            rv.setRemoteAdapter(R.id.widget_list, svc)

            if (memo.isChecklist) {
                // 체크박스 행 -> 브로드캐스트(앱 실행 없음). 항목 ID 는 fill-in 인텐트로 전달된다.
                val template = Intent(ctx, ToggleReceiver::class.java).apply {
                    action = Extras.ACTION_TOGGLE
                }
                rv.setPendingIntentTemplate(
                    R.id.widget_list,
                    PendingIntent.getBroadcast(
                        ctx, widgetId * 10 + CODE_LIST_TOGGLE, template,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                )
            } else {
                // 텍스트 메모의 본문 줄 -> 해당 메모 편집 화면
                val template = Intent(ctx, MainActivity::class.java).apply {
                    data = Uri.parse("widgetmemo://memo/${memo.id}/$widgetId/body")
                    putExtra(Extras.MEMO_ID, memo.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                rv.setPendingIntentTemplate(
                    R.id.widget_list,
                    PendingIntent.getActivity(
                        ctx, widgetId * 10 + CODE_LIST_ACTIVITY, template,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                )
            }
            return rv
        }
    }
}
