package com.example.widgetmemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            NoteTheme {
                when (val r = vm.route) {
                    is Route.List -> ListScreen(
                        vm,
                        onOpen = { vm.route = Route.Editor(it) },
                        onSettings = { vm.route = Route.Settings }
                    )
                    is Route.Editor -> {
                        BackHandler { vm.leaveEditor(r.memoId) }
                        EditorScreen(vm, r.memoId, r.addItem)
                    }
                    is Route.Settings -> {
                        BackHandler { vm.route = Route.List }
                        SettingsScreen(onBack = { vm.route = Route.List })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        val id = i?.getLongExtra(Extras.MEMO_ID, -1L) ?: -1L
        if (id > 0) vm.route = Route.Editor(id, i?.getBooleanExtra(Extras.ADD_ITEM, false) ?: false)
    }
}
