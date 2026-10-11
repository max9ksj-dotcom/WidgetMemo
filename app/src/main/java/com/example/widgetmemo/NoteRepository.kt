package com.example.widgetmemo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 앱 쪽 모든 쓰기는 여기를 거친다: 뮤텍스로 순서를 보장하고, 쓰기 후 위젯을 갱신한다. */
class NoteRepository private constructor(private val app: Context) {
    private val dao = NoteDb.get(app).dao()
    private val mutex = Mutex()

    val memos = dao.observeMemos()
    val allItems = dao.observeAllItems()
    fun observeMemo(id: Long) = dao.observeMemo(id)
    fun observeItems(id: Long) = dao.observeItems(id)
    suspend fun getMemo(id: Long) = dao.getMemo(id)

    private fun now() = System.currentTimeMillis()

    suspend fun createMemo(checklist: Boolean): Long =
        write { dao.insertMemo(Memo(isChecklist = checklist)) }

    suspend fun setTitle(id: Long, title: String) = write { dao.setTitle(id, title, now()) }
    suspend fun setBody(id: Long, body: String) = write { dao.setBody(id, body, now()) }

    suspend fun addItem(memoId: Long): Long = write {
        val pos = dao.maxPosition(memoId) + 1
        val id = dao.insertItem(CheckItem(memoId = memoId, position = pos))
        dao.touch(memoId, now())
        id
    }

    suspend fun setItemText(itemId: Long, text: String) = write {
        dao.updateItemText(itemId, text)
        dao.touchByItem(itemId, now())
    }

    suspend fun setChecked(itemId: Long, checked: Boolean) = write {
        dao.setChecked(itemId, checked)
        dao.touchByItem(itemId, now())
    }

    suspend fun deleteItem(itemId: Long) = write {
        dao.touchByItem(itemId, now())
        dao.deleteItem(itemId)
    }

    suspend fun deleteMemo(id: Long) = write { dao.deleteMemo(id) }

    /** 아무것도 입력하지 않고 나간 빈 메모는 정리한다. */
    suspend fun deleteIfEmpty(id: Long) = write {
        val m = dao.getMemo(id) ?: return@write
        if (m.title.isBlank() && m.body.isBlank() && dao.countNonBlankItems(id) == 0) {
            dao.deleteMemo(id)
        }
    }

    suspend fun bindWidget(widgetId: Int, memoId: Long) =
        write { dao.upsertBinding(WidgetBinding(widgetId, memoId)) }

    private suspend fun <T> write(block: suspend () -> T): T {
        val r = mutex.withLock { block() }
        withContext(Dispatchers.IO) { WidgetUpdater.refresh(app) }
        return r
    }

    companion object {
        @Volatile private var inst: NoteRepository? = null
        fun get(ctx: Context): NoteRepository = inst ?: synchronized(this) {
            inst ?: NoteRepository(ctx.applicationContext).also { inst = it }
        }
    }
}
