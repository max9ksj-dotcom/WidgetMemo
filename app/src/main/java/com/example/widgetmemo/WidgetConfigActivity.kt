package com.example.widgetmemo

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** 위젯을 홈 화면에 놓을 때(또는 재설정할 때) 표시할 메모를 고르는 화면. */
class WidgetConfigActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val repo = NoteRepository.get(this)
        setContent {
            NoteTheme {
                val memos by repo.memos.collectAsState(emptyList())
                val scope = rememberCoroutineScope()
                Scaffold(topBar = { TopAppBar(title = { Text("위젯에 표시할 메모 선택") }) }) { pad ->
                    Column(Modifier.padding(pad).padding(16.dp)) {
                        if (memos.isEmpty()) {
                            Text("아직 메모가 없습니다. 먼저 체크리스트를 하나 만들어 보세요.")
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { scope.launch { repo.createMemo(true) } }) {
                                Text("새 체크리스트 만들기")
                            }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(memos, key = { it.id }) { m ->
                                Card(onClick = {
                                    lifecycleScope.launch {
                                        repo.bindWidget(widgetId, m.id)
                                        setResult(
                                            Activity.RESULT_OK,
                                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                                        )
                                        finish()
                                    }
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(m.title.ifBlank { "제목 없음" }, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                if (m.isChecklist) "체크리스트" else "텍스트 메모",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
