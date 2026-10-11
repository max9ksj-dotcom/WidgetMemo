package com.example.widgetmemo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmt(t: Long) = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(t))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(vm: NotesViewModel, onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val memos by vm.repo.memos.collectAsState(emptyList())
    val allItems by vm.repo.allItems.collectAsState(emptyList())
    val itemsByMemo = remember(allItems) { allItems.groupBy { it.memoId } }
    var toDelete by remember { mutableStateOf<Memo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("메모") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "설정") }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { vm.create(false) },
                    icon = { Icon(Icons.Default.Edit, null) },
                    text = { Text("텍스트 메모") }
                )
                ExtendedFloatingActionButton(
                    onClick = { vm.create(true) },
                    icon = { Icon(Icons.Default.Check, null) },
                    text = { Text("체크리스트") }
                )
            }
        }
    ) { pad ->
        if (memos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("아래 버튼으로 첫 메모를 만들어 보세요.")
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 8.dp,
                bottom = pad.calculateBottomPadding() + 170.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(memos, key = { it.id }) { m ->
                val its = itemsByMemo[m.id].orEmpty()
                val preview = if (m.isChecklist)
                    "체크리스트 · ${its.count { it.checked }}/${its.size} 완료"
                else m.body.lineSequence().firstOrNull { it.isNotBlank() } ?: "내용 없음"
                Card(onClick = { onOpen(m.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.title.ifBlank { "제목 없음" }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(preview, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(fmt(m.updatedAt), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { toDelete = m }) { Icon(Icons.Default.Delete, "메모 삭제") }
                    }
                }
            }
        }
    }

    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("메모를 삭제할까요?") },
            text = { Text("삭제하면 이 메모를 표시하던 위젯에는 ‘삭제된 메모’ 안내가 나타납니다.") },
            confirmButton = { TextButton(onClick = { vm.deleteMemo(m.id); toDelete = null }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("취소") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: NotesViewModel, memoId: Long, addItem: Boolean) {
    val loaded by produceState<Pair<Boolean, Memo?>>(false to null, memoId) {
        value = true to vm.repo.getMemo(memoId)
    }
    val live by remember(memoId) { vm.repo.observeMemo(memoId) }.collectAsState(null)
    val scope = rememberCoroutineScope()
    var focusId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(memoId) {
        if (addItem) { vm.consumeAddItem(); focusId = vm.repo.addItem(memoId) }
    }

    val memo = loaded.second
    if (!loaded.first) return
    if (memo == null) { LaunchedEffect(Unit) { vm.route = Route.List }; return }

    var title by remember(memoId) { mutableStateOf(memo.title) }
    var body by remember(memoId) { mutableStateOf(memo.body) }
    val items by remember(memoId) { vm.repo.observeItems(memoId) }.collectAsState(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (memo.isChecklist) "체크리스트 편집" else "메모 편집") },
                navigationIcon = {
                    IconButton(onClick = { vm.leaveEditor(memoId) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; vm.setTitle(memoId, it) },
                    label = { Text("제목") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                live?.let {
                    Text("마지막 수정 ${fmt(it.updatedAt)}", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                }
            }
            if (!memo.isChecklist) {
                item {
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it; vm.setBody(memoId, it) },
                        label = { Text("내용") },
                        minLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                items(items, key = { it.id }) { item ->
                    ItemRow(
                        item = item, vm = vm,
                        requestFocus = focusId == item.id,
                        onFocused = { focusId = null }
                    )
                }
                item {
                    TextButton(onClick = { scope.launch { focusId = vm.repo.addItem(memoId) } }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("항목 추가")
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: CheckItem, vm: NotesViewModel, requestFocus: Boolean, onFocused: () -> Unit) {
    var text by remember(item.id) { mutableStateOf(item.text) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(requestFocus) { if (requestFocus) { fr.requestFocus(); onFocused() } }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = item.checked,
            onCheckedChange = { vm.setChecked(item.id, it) }
        )
        TextField(
            value = text,
            onValueChange = { text = it; vm.setItemText(item.id, it) },
            placeholder = { Text("할 일") },
            textStyle = TextStyle(
                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                color = if (item.checked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.weight(1f).focusRequester(fr)
        )
        IconButton(onClick = { vm.deleteItem(item.id) }) { Icon(Icons.Default.Close, "항목 삭제") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs by ctx.settingsStore.data.collectAsState(initial = null)
    val hideDone = prefs?.get(SettingsKeys.HIDE_DONE) ?: false
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("위젯에서 완료 항목 숨기기", style = MaterialTheme.typography.titleMedium)
                    Text("켜면 체크한 항목이 위젯 목록에서 사라집니다. 앱에서는 그대로 보입니다.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = hideDone, onCheckedChange = { v ->
                    scope.launch {
                        ctx.settingsStore.edit { it[SettingsKeys.HIDE_DONE] = v }
                        withContext(Dispatchers.IO) { WidgetUpdater.refresh(ctx) }
                    }
                })
            }
            Text("다크 모드는 시스템 설정을 따라갑니다.", style = MaterialTheme.typography.bodyMedium)
            Text("위젯 추가: 홈 화면 빈 곳 길게 누르기 → 위젯 → ‘위젯 메모’", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
