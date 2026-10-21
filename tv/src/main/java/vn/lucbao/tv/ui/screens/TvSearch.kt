package vn.lucbao.tv.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.lucbao.tv.data.Video
import vn.lucbao.tv.ui.AppViewModel
import vn.lucbao.tv.ui.TvEdge
import vn.lucbao.tv.ui.TvRow
import vn.lucbao.tv.ui.focusIfVisible
import vn.lucbao.tv.ui.theme.Luc
import vn.lucbao.tv.ui.theme.LucIcons
import vn.lucbao.tv.ui.tvErrorText

private const val KEY = 38
private const val GAP = 6
private const val COLS = 10
private val FIELD_W = (KEY * COLS + GAP * (COLS - 1)).dp

private sealed interface KeyAction {
    data class Type(val ch: String) : KeyAction
    data object Shift : KeyAction
    data object Space : KeyAction
    data object Backspace : KeyAction
    data object Clear : KeyAction
    data object Search : KeyAction
}

private data class KeyDef(val label: String, val span: Int, val action: KeyAction, val icon: ImageVector? = null)

private fun letters(s: String) = s.split(' ').map { KeyDef(it, 1, KeyAction.Type(it)) }

/** Numbers on top, plain a–z; letters with accents open a small panel (the plain letter first). */
private val KEYBOARD: List<List<KeyDef>> = listOf(
    letters("1 2 3 4 5 6 7 8 9 0"),
    letters("a b c d e f g h i j"),
    letters("k l m n o p q r s t"),
    letters("u v w x y z") + listOf(
        KeyDef("Hoa", 2, KeyAction.Shift, LucIcons.Shift),
        KeyDef("xoá", 2, KeyAction.Backspace, LucIcons.Backspace),
    ),
    listOf(
        KeyDef("khoảng trắng", 4, KeyAction.Space),
        KeyDef("xoá hết", 3, KeyAction.Clear),
        KeyDef("TÌM", 3, KeyAction.Search, LucIcons.Search),
    ),
)

/** OK on one of these letters opens the panel: one row per base vowel, then its 5 tones. */
private val ACCENTS: Map<String, List<String>> = mapOf(
    "a" to listOf("aáàảãạ", "ăắằẳẵặ", "âấầẩẫậ"),
    "d" to listOf("dđ"),
    "e" to listOf("eéèẻẽẹ", "êếềểễệ"),
    "i" to listOf("iíìỉĩị"),
    "o" to listOf("oóòỏõọ", "ôốồổỗộ", "ơớờởỡợ"),
    "u" to listOf("uúùủũụ", "ưứừửữự"),
    "y" to listOf("yýỳỷỹỵ"),
)

// ------------------------------------------------------------------ screen

@Composable
fun TvSearch(vm: AppViewModel, voicePending: Boolean, onVoiceHandled: () -> Unit, onPlay: (Video) -> Unit) {
    val c = Luc.colors
    val ctx = LocalContext.current
    val query by vm.query.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val submitted by vm.submitted.collectAsStateWithLifecycle()

    val firstKey = remember { FocusRequester() }
    val resultsFocus = remember { FocusRequester() }
    var focusResults by remember { mutableStateOf(false) }

    fun search(text: String) {
        val direct = vm.submit(text)
        if (direct != null) onPlay(direct) else focusResults = true
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!said.isNullOrBlank()) {
                vm.onQueryChange(said)
                search(said)
            }
        }
    }

    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói điều bạn muốn xem")
        try {
            voice.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "TV này chưa hỗ trợ tìm bằng giọng nói. Dùng bàn phím bên dưới nhé.", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(ctx, "TV này chưa hỗ trợ tìm bằng giọng nói. Dùng bàn phím bên dưới nhé.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(voicePending) {
        if (voicePending) {
            delay(150)
            listen()
            onVoiceHandled()
        }
    }
    LaunchedEffect(Unit) {
        delay(100)
        firstKey.focusIfVisible()
    }
    LaunchedEffect(focusResults, results.items.isNotEmpty(), results.loaded, results.loading) {
        if (!focusResults) return@LaunchedEffect
        if (results.items.isNotEmpty()) {
            delay(120)
            resultsFocus.focusIfVisible()
            focusResults = false
        } else if (results.loaded && !results.loading) {
            // Nothing found (or an error): put the remote back on the keyboard.
            delay(80)
            firstKey.focusIfVisible()
            focusResults = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 28.dp, bottom = 32.dp)
    ) {
        // Field + mic
        Row(
            Modifier.padding(start = 24.dp, end = TvEdge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .width(FIELD_W)
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.card)
                    .border(2.dp, c.primary, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(LucIcons.Search, null, tint = c.muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    query.ifEmpty { "Tìm kiếm hoặc nói vào micro" },
                    color = if (query.isEmpty()) c.muted else c.text,
                    fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(Modifier.padding(start = 2.dp).width(2.dp).height(24.dp).background(c.primary))
            }
            Spacer(Modifier.width(20.dp))
            MicButton { listen() }
            Spacer(Modifier.width(16.dp))
            Text(
                "Chọn nút micro (hoặc bấm nút\ntìm kiếm trên điều khiển) rồi nói",
                color = c.muted, fontSize = 14.sp, lineHeight = 20.sp
            )
        }

        Row(Modifier.padding(start = 24.dp, end = TvEdge, top = 16.dp)) {
            // Keyboard
            TvKeyboard(
                query = query,
                onQueryChange = { vm.onQueryChange(it) },
                onSearch = { search(query) },
                firstKey = firstKey,
                onClear = { vm.clearSearch() },
            )
            Spacer(Modifier.width(28.dp))
            // Suggestions / recent searches
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val showRecent = query.isBlank()
                val list = if (showRecent) recent.take(6) else suggestions.take(6)
                if (showRecent && list.isNotEmpty()) {
                    Text("Tìm gần đây", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 14.dp, bottom = 4.dp))
                }
                list.forEach { s ->
                    SuggestionRow(s, query, if (showRecent) LucIcons.History else LucIcons.Search) {
                        vm.onQueryChange(s)
                        search(s)
                    }
                }
            }
        }

        val q = submitted
        if (q != null) {
            Spacer(Modifier.height(10.dp))
            if (results.error != null && results.items.isEmpty()) {
                Text(
                    tvErrorText(results.error), color = c.muted, fontSize = 15.sp,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp)
                )
            } else if (results.loaded && results.items.isEmpty() && !results.loading) {
                Text(
                    "Không tìm thấy video nào cho “$q”.", color = c.muted, fontSize = 15.sp,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp)
                )
            } else {
                TvRow(
                    "Kết quả cho “$q”", results.items, onPlay,
                    loading = results.loading,
                    onNearEnd = { vm.loadMoreResults() },
                    firstFocus = resultsFocus,
                )
            }
        }
    }
}

@Composable
private fun TvKey(
    k: KeyDef,
    width: Dp,
    focusRequesters: List<FocusRequester> = emptyList(),
    label: String = k.label,
    selected: Boolean = false,
    accents: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Luc.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "key")
    val accent = k.action == KeyAction.Search || selected
    Box(
        Modifier
            .width(width)
            .height(KEY.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(focusRequesters.fold(Modifier as Modifier) { m, r -> m.focusRequester(r) })
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(9.dp))
            .background(
                when {
                    focused -> c.primary
                    accent -> c.primary.copy(alpha = 0.22f)
                    else -> c.card
                }
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val fg = if (focused) c.onPrimary else if (accent) c.primary else c.text
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (k.icon != null) {
                Icon(k.icon, null, tint = fg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text(
                label, color = fg,
                fontSize = if (k.span == 1) 17.sp else 14.sp,
                fontWeight = if (k.span == 1) FontWeight.Medium else FontWeight.SemiBold,
                maxLines = 1
            )
        }
        // A small dot: this letter has accented forms (OK opens them).
        if (accents) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 5.dp, end = 5.dp).size(5.dp)
                    .clip(CircleShape).background(if (focused) c.onPrimary else c.primary)
            )
        }
    }
}

@Composable
private fun MicButton(onClick: () -> Unit) {
    val c = Luc.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "mic")
    Box(
        Modifier
            .size(76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(c.primary.copy(alpha = if (focused) 0.28f else 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(56.dp)
                .onFocusChanged { focused = it.isFocused }
                .clip(CircleShape)
                .background(if (focused) Color.White else c.primary)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(LucIcons.Mic, "Tìm bằng giọng nói", tint = c.onPrimary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun SuggestionRow(text: String, query: String, icon: ImageVector, onClick: () -> Unit) {
    val c = Luc.colors
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) c.primary else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fg = if (focused) c.onPrimary else c.muted
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        val q = query.trim()
        val label = buildAnnotatedString {
            if (q.isNotEmpty() && text.startsWith(q, ignoreCase = true)) {
                withStyle(SpanStyle(color = if (focused) c.onPrimary else c.text, fontWeight = FontWeight.SemiBold)) {
                    append(text.substring(0, q.length))
                }
                append(text.substring(q.length))
            } else append(text)
        }
        Text(label, color = fg, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The Vietnamese on-screen keyboard, shared by Tìm kiếm, Nhạc, Shorts and the kids' channels.
 * OK on a letter with accents (a, d, e, i, o, u, y) opens a small panel: the plain letter is
 * first (OK again types it), ◀ ▶ ▲ ▼ pick an accented one, Back closes. "Hoa" switches between
 * lower and upper case, for the keyboard and the panel alike.
 */
@Composable
fun TvKeyboard(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    firstKey: FocusRequester? = null,
    onClear: () -> Unit = { onQueryChange("") },
) {
    var upper by remember { mutableStateOf(false) }
    var accentOf by remember { mutableStateOf<String?>(null) }
    var refocus by remember { mutableStateOf<String?>(null) }
    val accentFocus = remember { ACCENTS.keys.associateWith { FocusRequester() } }
    fun cased(s: String) = if (upper) s.uppercase() else s

    // Back on the letter that opened the panel.
    LaunchedEffect(refocus) {
        val k = refocus ?: return@LaunchedEffect
        delay(60)
        runCatching { accentFocus[k]?.requestFocus() }
        refocus = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(GAP.dp)) {
        KEYBOARD.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
                row.forEach { k ->
                    val letter = (k.action as? KeyAction.Type)?.ch
                    val requesters = buildList<FocusRequester> {
                        if (letter == "a" && firstKey != null) add(firstKey)
                        letter?.let { accentFocus[it] }?.let { add(it) }
                    }
                    TvKey(
                        k,
                        width = (KEY * k.span + GAP * (k.span - 1)).dp,
                        focusRequesters = requesters,
                        label = if (letter != null) cased(k.label) else k.label,
                        selected = k.action == KeyAction.Shift && upper,
                        accents = letter != null && letter in ACCENTS,
                    ) {
                        when (val a = k.action) {
                            is KeyAction.Type ->
                                if (a.ch in ACCENTS) accentOf = a.ch else onQueryChange(query + cased(a.ch))
                            KeyAction.Shift -> upper = !upper
                            KeyAction.Space -> if (query.isNotEmpty() && !query.endsWith(' ')) onQueryChange("$query ")
                            KeyAction.Backspace -> if (query.isNotEmpty()) onQueryChange(query.dropLast(1))
                            KeyAction.Clear -> onClear()
                            KeyAction.Search -> onSearch()
                        }
                    }
                }
            }
        }
    }

    accentOf?.let { base ->
        AccentPanel(
            rows = ACCENTS.getValue(base).map { cased(it) },
            onPick = { ch ->
                onQueryChange(query + ch)
                accentOf = null
                refocus = base
            },
            onDismiss = {
                accentOf = null
                refocus = base
            },
        )
    }
}

/** The small panel with the accented forms of one letter. */
@Composable
private fun AccentPanel(rows: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Luc.colors
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(80)
            runCatching { first.requestFocus() }
        }
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF20A1E16))
                .border(1.5.dp, c.primary, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(GAP.dp)
        ) {
            rows.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
                    row.forEachIndexed { i, ch ->
                        val s = ch.toString()
                        TvKey(
                            KeyDef(s, 1, KeyAction.Type(s)),
                            width = (KEY + 6).dp,
                            focusRequesters = if (r == 0 && i == 0) listOf(first) else emptyList(),
                        ) { onPick(s) }
                    }
                }
            }
        }
    }
}

/** The text being typed on [TvKeyboard]. */
@Composable
fun TvQueryField(query: String, hint: String, modifier: Modifier = Modifier) {
    val c = Luc.colors
    Row(
        modifier
            .width(FIELD_W)
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .border(2.dp, c.primary, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(LucIcons.Search, null, tint = c.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            query.ifEmpty { hint },
            color = if (query.isEmpty()) c.muted else c.text,
            fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Box(Modifier.padding(start = 2.dp).width(2.dp).height(24.dp).background(c.primary))
    }
}
