package com.example.widgetmemo

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

sealed interface Route {
    data object List : Route
    data class Editor(val memoId: Long, val addItem: Boolean = false) : Route
    data object Settings : Route
}

class NotesViewModel(app: Application) : AndroidViewModel(app) {
    val repo = NoteRepository.get(app)
    var route: Route by mutableStateOf(Route.List)

    fun create(checklist: Boolean) = viewModelScope.launch {
        val id = repo.createMemo(checklist)
        route = Route.Editor(id, addItem = checklist)
    }

    fun consumeAddItem() {
        (route as? Route.Editor)?.let { route = it.copy(addItem = false) }
    }

    fun leaveEditor(memoId: Long) {
        viewModelScope.launch { repo.deleteIfEmpty(memoId) }
        route = Route.List
    }

    fun deleteMemo(id: Long) = viewModelScope.launch { repo.deleteMemo(id) }
    fun setTitle(id: Long, t: String) = viewModelScope.launch { repo.setTitle(id, t) }
    fun setBody(id: Long, t: String) = viewModelScope.launch { repo.setBody(id, t) }
    fun setItemText(id: Long, t: String) = viewModelScope.launch { repo.setItemText(id, t) }
    fun setChecked(id: Long, c: Boolean) = viewModelScope.launch { repo.setChecked(id, c) }
    fun deleteItem(id: Long) = viewModelScope.launch { repo.deleteItem(id) }
}
