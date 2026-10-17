package com.example.widgetmemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    private val vm: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 회전 등으로 다시 만들어질 때는 현재 화면(ViewModel)을 유지하고, 처음 실행일 때만 인텐트를 해석한다.
        if (savedInstanceState == null) handle(intent)
        setContent {
            val settingsFlow = remember { SettingsStore.flow(applicationContext) }
            val settings by settingsFlow.collectAsState(AppSettings())
            CompositionLocalProvider(LocalAppSettings provides settings) {
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
    }

    // singleTop + CLEAR_TOP: 앱이 이미 떠 있으면 새 화면을 쌓지 않고 이 메서드로 들어와 해당 메모만 연다.
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
