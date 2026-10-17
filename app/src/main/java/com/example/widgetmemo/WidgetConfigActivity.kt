package com.example.widgetmemo

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 위젯을 놓을 때(또는 길게 눌러 ‘설정’을 고를 때) 표시할 메모와 이 위젯의 배경 투명도를 정하는 화면. */
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
                val ctx = LocalContext.current
                val memos by repo.memos.collectAsState(emptyList())
                val allItems by repo.allItems.collectAsState(emptyList())
                val byMemo = remember(allItems) { allItems.groupBy { it.memoId } }
                val scope = rememberCoroutineScope()
                var bgPct by remember { mutableFloatStateOf(-1f) }
                LaunchedEffect(Unit) {
                    val d = SettingsStore.get(ctx).defaultBgAlphaPct
                    bgPct = SettingsStore.widgetBg(ctx, widgetId, d).toFloat()
                }

                Scaffold(topBar = { TopAppBar(title = { Text("위젯 설정") }) }) { pad ->
                    Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
                        if (bgPct >= 0f) {
                            Text("이 위젯의 배경 투명도 (불투명도 ${bgPct.roundToInt()}%)",
                                style = MaterialTheme.typography.titleSmall)
                            Slider(value = bgPct, onValueChange = { bgPct = it }, valueRange = 0f..100f)
                            BackdropPreview(bgPct.roundToInt()) { Text("배경이 비치는 정도를 확인하세요") }
                            Spacer(Modifier.height(12.dp))
                        }
                        Text("표시할 메모를 선택하세요", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))

                        if (memos.isEmpty()) {
                            Text("아직 메모가 없습니다. 먼저 체크리스트를 하나 만들어 보세요.")
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { scope.launch { repo.createMemo(true) } }) {
                                Text("새 체크리스트 만들기")
                            }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(memos, key = { it.id }) { m ->
                                val its = byMemo[m.id].orEmpty()
                                val sub = if (m.isChecklist) "체크리스트 · ${its.count { it.checked }}/${its.size} 완료"
                                else (m.body.lineSequence().firstOrNull { it.isNotBlank() } ?: "텍스트 메모")
                                Card(
                                    onClick = {
                                        lifecycleScope.launch {
                                            if (bgPct >= 0f) SettingsStore.setWidgetBg(ctx, widgetId, bgPct.roundToInt())
                                            repo.bindWidget(widgetId, m.id)
                                            setResult(
                                                Activity.RESULT_OK,
                                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                                            )
                                            finish()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        // 제목이 비어 있으면 제목 줄 자체를 표시하지 않는다(임의 문구 없음).
                                        if (m.title.isNotBlank()) {
                                            Text(m.title, style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Text(sub, style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** 배경화면처럼 알록달록한 바탕 위에서 위젯 배경 투명도가 어떻게 보이는지 보여주는 간단한 미리보기. */
@Composable
fun BackdropPreview(bgAlphaPct: Int, content: @Composable ColumnScope.() -> Unit = {}) {
    val dark = isSystemInDarkTheme()
    val panel = if (dark) Color(0xFF2B2B30) else Color.White
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFFF8A65), Color(0xFF4FC3F7), Color(0xFF81C784))))
            .padding(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(panel.copy(alpha = bgAlphaPct / 100f))
                .padding(12.dp),
            horizontalAlignment = Alignment.Start,
            content = content
        )
    }
}
