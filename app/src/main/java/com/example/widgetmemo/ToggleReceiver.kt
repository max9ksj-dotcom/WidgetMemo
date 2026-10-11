package com.example.widgetmemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 위젯의 체크박스 행을 눌렀을 때 호출된다. 앱이 꺼져 있어도 DB를 직접 갱신한다. */
class ToggleReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Extras.ACTION_TOGGLE) return
        val itemId = intent.getLongExtra(Extras.ITEM_ID, -1L)
        if (itemId < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = NoteDb.get(ctx).dao()
                dao.toggleItem(itemId)                       // 원자적 토글
                dao.touchByItem(itemId, System.currentTimeMillis())
                WidgetUpdater.refresh(ctx)                   // 모든 위젯 즉시 재그리기
            } catch (e: Exception) {
                Log.e("ToggleReceiver", "toggle failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
