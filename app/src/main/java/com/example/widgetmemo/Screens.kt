package com.example.widgetmemo

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun fmt(t: Long) = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(t))

// ───────────── 글꼴 / 색 / 스타일 (설정 반영) ─────────────

private val fontCache = HashMap<String, FontFamily>()

/** 글씨체: 시스템 글꼴 계열을 Typeface 로 만든다. 계열이 없으면 시스템이 기본 글꼴로 대체하므로 앱이 종료되지 않는다. */
fun fontFamilyOf(c: FontChoice): FontFamily =
    if (c == FontChoice.DEFAULT) FontFamily.Default
    else fontCache.getOrPut(c.key) { FontFamily(Typeface.create(c.family, Typeface.NORMAL)) }

/** 글씨 색(없으면 테마 기본색)에 글씨 투명도만 곱한다. 색상과 투명도는 따로 저장·적용된다. */
@Composable
fun baseTextColor(s: AppSettings): Color {
    val base = if (s.textColor == 0) MaterialTheme.colorScheme.onSurface else Color(s.textColor)
    return base.copy(alpha = base.alpha * (s.textAlphaPct / 100f))
}

@Composable
private fun bodyStyle(s: AppSettings, done: Boolean = false): TextStyle {
    val c = baseTextColor(s)
    return TextStyle(
        fontSize = s.bodySp.sp,
        fontFamily = fontFamilyOf(s.font()),
        color = if (done) c.copy(alpha = c.alpha * 0.6f) else c,
        textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
    )
}

@Composable
private fun titleStyle(s: AppSettings): TextStyle =
    TextStyle(
        fontSize = s.titleSp.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = fontFamilyOf(s.font()),
        color = baseTextColor(s)
    )

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent
)

// ───────────── 체크박스: 완료 = 검은색으로 채움 + 흰 체크 ─────────────

@Composable
fun CheckVisual(checked: Boolean, size: Int = 24) {
    val shape = RoundedCornerShape(6.dp)
    val ring = if (checked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.size(size.dp).clip(shape)
            .background(if (checked) Color.Black else Color.Transparent)
            .border(2.dp, ring, shape),
        contentAlignment = Alignment.Center
    ) {
        if (checked) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size((size * 0.75f).dp))
    }
}

/** 보이는 크기는 24dp, 누를 수 있는 영역은 48dp. */
@Composable
fun AppCheckbox(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.size(48.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        contentAlignment = Alignment.Center
    ) { CheckVisual(checked) }
}

// ───────────── 메모 목록 ─────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(vm: NotesViewModel, onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val s = LocalAppSettings.current
    val memos by vm.repo.memos.collectAsState(emptyList())
    val allItems by vm.repo.allItems.collectAsState(emptyList())
    val itemsByMemo = remember(allItems) { allItems.groupBy { it.memoId } }
    var toDelete by remember { mutableStateOf<Memo?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("메모") },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "설정") } }
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { menuOpen = true }) { Icon(Icons.Default.Add, "새 메모") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("텍스트 메모") }, onClick = { menuOpen = false; vm.create(false) })
                    DropdownMenuItem(text = { Text("체크리스트") }, onClick = { menuOpen = false; vm.create(true) })
                }
            }
        }
    ) { pad ->
        if (memos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("+ 버튼으로 첫 메모를 만들어 보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 88.dp)
        ) {
            items(memos, key = { it.id }) { m ->
                val its = itemsByMemo[m.id].orEmpty()
                val done = its.count { it.checked }
                val preview = if (m.isChecklist) "" else m.body.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(m.id) }
                        .padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        // 제목이 비어 있으면 제목 줄을 아예 그리지 않는다(임의 문구·본문 대체 없음).
                        if (m.title.isNotBlank()) {
                            Text(m.title, style = titleStyle(s), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (m.isChecklist) {
                            Text(
                                if (its.isEmpty()) "항목 없음" else "$done / ${its.size} 완료",
                                style = bodyStyle(s)
                            )
                            if (its.isNotEmpty()) {
                                LinearProgressIndicator(
                                    progress = { done / its.size.toFloat() },
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, end = 16.dp)
                                )
                            }
                        } else if (preview.isNotEmpty()) {
                            Text(preview, style = bodyStyle(s), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            fmt(m.updatedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    IconButton(onClick = { toDelete = m }) { Icon(Icons.Default.Delete, "메모 삭제") }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            }
        }
    }

    toDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("메모를 삭제할까요?") },
            text = { Text("삭제하면 이 메모를 표시하던 위젯에는 삭제 안내가 나타납니다.") },
            confirmButton = { TextButton(onClick = { vm.deleteMemo(m.id); toDelete = null }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("취소") } }
        )
    }
}

// ───────────── 편집 화면 ─────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: NotesViewModel, memoId: Long, addItem: Boolean) {
    val s = LocalAppSettings.current
    val loaded by produceState<Pair<Boolean, Memo?>>(false to null, memoId) {
        value = true to vm.repo.getMemo(memoId)
    }
    val live by remember(memoId) { vm.repo.observeMemo(memoId) }.collectAsState(null)
    val items by remember(memoId) { vm.repo.observeItems(memoId) }.collectAsState(emptyList())
    var focusId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    // 위젯의 ‘+’ 로 들어온 경우: 맨 아래에 새 항목을 만들고 커서를 둔다. (한 번만 실행)
    LaunchedEffect(memoId) {
        if (addItem) { vm.consumeAddItem(); vm.addItemAtEnd(memoId) { focusId = it } }
    }
    // 새 항목이 키보드에 가려지지 않도록 그 위치로 스크롤한다.
    LaunchedEffect(focusId, items.size) {
        val i = items.indexOfFirst { it.id == focusId }
        if (i >= 0) listState.animateScrollToItem(i + 1)
    }

    // 삭제되었거나 없는 메모(예: 위젯이 가리키던 메모)는 목록으로 안전하게 돌아간다.
    val memo = loaded.second
    if (!loaded.first) return
    if (memo == null) { LaunchedEffect(Unit) { vm.route = Route.List }; return }

    var title by remember(memoId) { mutableStateOf(memo.title) }
    var body by remember(memoId) { mutableStateOf(memo.body) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (memo.isChecklist) "체크리스트" else "메모") },
                navigationIcon = {
                    IconButton(onClick = { vm.leaveEditor(memoId) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            item {
                TextField(
                    value = title,
                    onValueChange = { title = it; vm.setTitle(memoId, it) },   // 빈 문자열도 그대로 저장
                    placeholder = { Text("제목", style = titleStyle(s).copy(color = MaterialTheme.colorScheme.outline)) },
                    textStyle = titleStyle(s),
                    singleLine = true,
                    colors = transparentFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                live?.let {
                    Text(
                        "마지막 수정 ${fmt(it.updatedAt)} · 자동 저장됨",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                }
            }
            if (!memo.isChecklist) {
                // 텍스트 메모: Enter 는 평범한 줄바꿈
                item {
                    TextField(
                        value = body,
                        onValueChange = { body = it; vm.setBody(memoId, it) },
                        placeholder = { Text("내용", style = bodyStyle(s).copy(color = MaterialTheme.colorScheme.outline)) },
                        textStyle = bodyStyle(s),
                        minLines = 10,
                        colors = transparentFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                items(items, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        s = s,
                        requestFocus = focusId == item.id,
                        onFocusHandled = { focusId = null },
                        onToggle = { vm.setChecked(item.id, it) },
                        onText = { vm.setItemText(item.id, it) },
                        onEnter = { vm.addItemAfter(item.id) { id -> focusId = id } },
                        onBackspaceEmpty = { vm.deleteItemFocusPrev(item.id) { prev -> if (prev != null) focusId = prev } },
                        onDelete = { vm.deleteItem(item.id) }
                    )
                }
                item {
                    TextButton(
                        onClick = { vm.addItemAtEnd(memoId) { focusId = it } },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
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
private fun ItemRow(
    item: CheckItem,
    s: AppSettings,
    requestFocus: Boolean,
    onFocusHandled: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onText: (String) -> Unit,
    onEnter: () -> Unit,
    onBackspaceEmpty: () -> Unit,
    onDelete: () -> Unit
) {
    // 커서는 글자 끝에서 시작. 텍스트는 로컬 상태가 원본이고 DB 쓰기는 입력 순서대로 직렬 처리된다.
    var tfv by remember(item.id) { mutableStateOf(TextFieldValue(item.text, TextRange(item.text.length))) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(requestFocus) { if (requestFocus) { fr.requestFocus(); onFocusHandled() } }

    Row(verticalAlignment = Alignment.CenterVertically) {
        AppCheckbox(checked = item.checked, onChange = onToggle)
        TextField(
            value = tfv,
            onValueChange = { n ->
                val changed = n.text != tfv.text
                tfv = n
                if (changed) onText(n.text)
            },
            placeholder = { Text("할 일", style = bodyStyle(s).copy(color = MaterialTheme.colorScheme.outline)) },
            textStyle = bodyStyle(s, done = item.checked),
            // 한 줄 입력 + IME 동작(다음/엔터) 처리: 한글 조합 중에는 키보드가 글자를 확정한 뒤 이 동작을 보낸다.
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.None),
            keyboardActions = KeyboardActions(onNext = { onEnter() }),
            colors = transparentFieldColors(),
            modifier = Modifier.weight(1f).focusRequester(fr).onPreviewKeyEvent { e ->
                // 빈 항목에서 Backspace: 항목을 지우고 이전 항목으로 이동. 글자가 있으면 평소처럼 지운다.
                if (e.type == KeyEventType.KeyDown && e.key == Key.Backspace && tfv.text.isEmpty()) {
                    onBackspaceEmpty(); true
                } else false
            }
        )
        IconButton(onClick = onDelete) { Icon(Icons.Default.Close, "항목 삭제") }
    }
}

// ───────────── 설정 화면 ─────────────

private val textColors = listOf(
    0 to "자동", 0xFF000000.toInt() to "검정", 0xFFFFFFFF.toInt() to "흰색",
    0xFF1565C0.toInt() to "파랑", 0xFFC62828.toInt() to "빨강",
    0xFF2E7D32.toInt() to "초록", 0xFF6A1B9A.toInt() to "보라"
)

private val sizePresets = listOf(
    "작게" to (13 to 11), "보통" to (16 to 14), "크게" to (20 to 18), "아주 크게" to (24 to 22)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val saved = LocalAppSettings.current
    val scope = rememberCoroutineScope()
    // draft: 슬라이더를 끄는 동안 즉시 미리보기에 반영, 손을 떼거나 칩을 누르면 자동 저장 + 위젯 갱신
    var draft by remember(saved) { mutableStateOf(saved) }
    fun commit(n: AppSettings) {
        draft = n
        scope.launch {
            SettingsStore.save(ctx, n)
            withContext(Dispatchers.IO) { WidgetUpdater.refresh(ctx) }
        }
    }

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
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle("미리보기")
            SettingsPreview(draft)

            SectionTitle("글씨 크기")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sizePresets.forEach { (label, sz) ->
                    FilterChip(
                        selected = draft.titleSp == sz.first && draft.bodySp == sz.second,
                        onClick = { commit(draft.copy(titleSp = sz.first, bodySp = sz.second)) },
                        label = { Text(label) }
                    )
                }
            }
            SliderRow("제목 크기", "${draft.titleSp}sp", draft.titleSp.toFloat(), 12f..32f,
                onChange = { draft = draft.copy(titleSp = it.roundToInt()) }, onDone = { commit(draft) })
            SliderRow("본문 크기", "${draft.bodySp}sp", draft.bodySp.toFloat(), 10f..28f,
                onChange = { draft = draft.copy(bodySp = it.roundToInt()) }, onDone = { commit(draft) })

            SectionTitle("글씨체")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FontChoice.values().forEach { f ->
                    FilterChip(
                        selected = draft.fontKey == f.key,
                        onClick = { commit(draft.copy(fontKey = f.key)) },
                        label = { Text(f.label) }
                    )
                }
            }
            Text(
                "한글은 기기에 해당 글꼴의 한글이 없으면 기본 글꼴로 표시될 수 있습니다.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionTitle("글씨 색상 · 투명도")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                textColors.forEach { (c, label) ->
                    val selected = draft.textColor == c
                    Box(
                        Modifier.size(48.dp).clickable { commit(draft.copy(textColor = c)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(if (c == 0) MaterialTheme.colorScheme.surfaceVariant else Color(c))
                                .border(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) { if (c == 0) Text("A", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            SliderRow("글씨 투명도", "불투명 ${draft.textAlphaPct}%", draft.textAlphaPct.toFloat(), 10f..100f,
                onChange = { draft = draft.copy(textAlphaPct = it.roundToInt()) }, onDone = { commit(draft) })

            SectionTitle("위젯")
            SliderRow("새 위젯 기본 배경 투명도", "불투명 ${draft.defaultBgAlphaPct}%", draft.defaultBgAlphaPct.toFloat(), 0f..100f,
                onChange = { draft = draft.copy(defaultBgAlphaPct = it.roundToInt()) }, onDone = { commit(draft) })
            Text(
                "이미 놓은 위젯은 위젯을 길게 눌러 ‘설정’에서 위젯별 배경 투명도를 바꿀 수 있습니다.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("위젯에서 완료 항목 숨기기", style = MaterialTheme.typography.bodyLarge)
                    Text("앱에서는 그대로 보입니다.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = draft.hideDone, onCheckedChange = { commit(draft.copy(hideDone = it)) })
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { commit(AppSettings()) }, modifier = Modifier.fillMaxWidth()) {
                Text("기본값으로 되돌리기")
            }
            Text(
                "설정을 바꿔도 메모 내용과 체크 상태는 바뀌지 않습니다. 다크 모드는 시스템 설정을 따릅니다.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun SliderRow(
    label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit, onDone: () -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onDone, valueRange = range)
    }
}

/** 현재 설정이 위젯에서 어떻게 보일지 보여준다(글씨 크기·글씨체·색·투명도·배경 투명도·검은 체크박스). */
@Composable
private fun SettingsPreview(d: AppSettings) {
    BackdropPreview(d.defaultBgAlphaPct) {
        Text("예시 제목", style = titleStyle(d))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckVisual(checked = false, size = 22)
            Spacer(Modifier.width(10.dp))
            Text("우유 사기", style = bodyStyle(d))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckVisual(checked = true, size = 22)
            Spacer(Modifier.width(10.dp))
            Text("빨래 널기", style = bodyStyle(d, done = true))
        }
    }
}
