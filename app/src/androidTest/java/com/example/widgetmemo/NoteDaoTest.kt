package com.example.widgetmemo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDaoTest {
    private lateinit var db: NoteDb
    private lateinit var dao: MemoDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NoteDb::class.java).build()
        dao = db.dao()
    }

    @After fun tearDown() = db.close()

    @Test fun toggleFlipsStateEachCall() = runBlocking {
        val m = dao.insertMemo(Memo(isChecklist = true))
        val i = dao.insertItem(CheckItem(memoId = m, text = "a"))
        repeat(5) { dao.toggleItem(i) }
        assertTrue(dao.getItems(m).single().checked)
        dao.toggleItem(i)
        assertFalse(dao.getItems(m).single().checked)
    }

    @Test fun itemsKeepOrderAndContent() = runBlocking {
        val m = dao.insertMemo(Memo(isChecklist = true))
        dao.insertItem(CheckItem(memoId = m, text = "first", position = 0))
        dao.insertItem(CheckItem(memoId = m, text = "second", position = 1))
        assertEquals(listOf("first", "second"), dao.getItems(m).map { it.text })
    }

    @Test fun deletingMemoCascadesItems() = runBlocking {
        val m = dao.insertMemo(Memo(isChecklist = true))
        dao.insertItem(CheckItem(memoId = m, text = "x"))
        dao.deleteMemo(m)
        assertTrue(dao.getItems(m).isEmpty())
    }

    @Test fun widgetBindingSurvivesMemoDeletionSafely() = runBlocking {
        val m = dao.insertMemo(Memo())
        dao.upsertBinding(WidgetBinding(widgetId = 7, memoId = m))
        dao.deleteMemo(m)
        assertEquals(m, dao.getBoundMemoId(7))
        assertNull(dao.getMemo(m))
        dao.deleteBindings(listOf(7))
        assertNull(dao.getBoundMemoId(7))
    }
}
