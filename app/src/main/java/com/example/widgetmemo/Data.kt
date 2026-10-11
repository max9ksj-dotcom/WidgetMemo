package com.example.widgetmemo

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

object Extras {
    const val MEMO_ID = "memo_id"
    const val ITEM_ID = "item_id"
    const val ADD_ITEM = "add_item"
    const val ACTION_TOGGLE = "com.example.widgetmemo.ACTION_TOGGLE"
}

@Entity(tableName = "memos")
data class Memo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val isChecklist: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "check_items",
    foreignKeys = [ForeignKey(
        entity = Memo::class,
        parentColumns = ["id"],
        childColumns = ["memoId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("memoId")]
)
data class CheckItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memoId: Long,
    val text: String = "",
    val checked: Boolean = false,
    val position: Int = 0
)

/** 위젯 ID -> 표시할 메모 ID. 메모가 삭제돼도 행은 남겨 두고 위젯이 안전하게 안내 문구를 보여준다. */
@Entity(tableName = "widget_bindings")
data class WidgetBinding(
    @PrimaryKey val widgetId: Int,
    val memoId: Long
)

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos ORDER BY updatedAt DESC")
    fun observeMemos(): Flow<List<Memo>>

    @Query("SELECT * FROM memos WHERE id = :id")
    fun observeMemo(id: Long): Flow<Memo?>

    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun getMemo(id: Long): Memo?

    @Insert
    suspend fun insertMemo(memo: Memo): Long

    @Query("UPDATE memos SET title = :title, updatedAt = :t WHERE id = :id")
    suspend fun setTitle(id: Long, title: String, t: Long)

    @Query("UPDATE memos SET body = :body, updatedAt = :t WHERE id = :id")
    suspend fun setBody(id: Long, body: String, t: Long)

    @Query("UPDATE memos SET updatedAt = :t WHERE id = (SELECT memoId FROM check_items WHERE id = :itemId)")
    suspend fun touchByItem(itemId: Long, t: Long)

    @Query("UPDATE memos SET updatedAt = :t WHERE id = :id")
    suspend fun touch(id: Long, t: Long)

    @Query("DELETE FROM memos WHERE id = :id")
    suspend fun deleteMemo(id: Long)

    @Query("SELECT * FROM check_items WHERE memoId = :memoId ORDER BY position, id")
    fun observeItems(memoId: Long): Flow<List<CheckItem>>

    @Query("SELECT * FROM check_items ORDER BY position, id")
    fun observeAllItems(): Flow<List<CheckItem>>

    @Query("SELECT * FROM check_items WHERE memoId = :memoId ORDER BY position, id")
    suspend fun getItems(memoId: Long): List<CheckItem>

    @Insert
    suspend fun insertItem(item: CheckItem): Long

    @Query("UPDATE check_items SET text = :text WHERE id = :id")
    suspend fun updateItemText(id: Long, text: String)

    @Query("UPDATE check_items SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    /** 읽기-수정-쓰기 없이 SQL 한 문장으로 뒤집으므로 빠르게 여러 번 눌러도 꼬이지 않는다. */
    @Query("UPDATE check_items SET checked = NOT checked WHERE id = :id")
    suspend fun toggleItem(id: Long)

    @Query("DELETE FROM check_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM check_items WHERE memoId = :memoId")
    suspend fun maxPosition(memoId: Long): Int

    @Query("SELECT COUNT(*) FROM check_items WHERE memoId = :memoId AND TRIM(text) <> ''")
    suspend fun countNonBlankItems(memoId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBinding(binding: WidgetBinding)

    @Query("SELECT memoId FROM widget_bindings WHERE widgetId = :widgetId")
    suspend fun getBoundMemoId(widgetId: Int): Long?

    @Query("DELETE FROM widget_bindings WHERE widgetId IN (:widgetIds)")
    suspend fun deleteBindings(widgetIds: List<Int>)
}

@Database(
    entities = [Memo::class, CheckItem::class, WidgetBinding::class],
    version = 1,
    exportSchema = false
)
abstract class NoteDb : RoomDatabase() {
    abstract fun dao(): MemoDao

    companion object {
        @Volatile private var inst: NoteDb? = null
        fun get(ctx: Context): NoteDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, NoteDb::class.java, "notes.db")
                .build().also { inst = it }
        }
    }
}
