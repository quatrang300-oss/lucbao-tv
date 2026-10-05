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
import java.text.Normalizer

private const val KEY = 38
private const val GAP = 6

private sealed interface KeyAction {
    data class Type(val ch: String) : KeyAction
    data object Tone : KeyAction
    data object Space : KeyAction
    data object Backspace : KeyAction
    data object Clear : KeyAction
    data object Search : KeyAction
}

private data class KeyDef(val label: String, val span: Int, val action: KeyAction, val icon: ImageVector? = null)

private fun letters(s: String) = s.split(' ').map { KeyDef(it, 1, KeyAction.Type(it)) }

private val KEYBOARD: List<List<KeyDef>> = listOf(
    letters("a ă â b c d đ e ê"),
    letters("f g h i j k l m n"),
    letters("o ô ơ p q r s t u"),
    letters("ư v w x y z 1 2 3"),
    letters("4 5 6 7 8 9 0") + KeyDef("dấu", 2, KeyAction.Tone),
    listOf(
        KeyDef("khoảng trắng", 3, KeyAction.Space),
        KeyDef("xoá", 2, KeyAction.Backspace, LucIcons.Backspace),
        KeyDef("xoá hết", 2, KeyAction.Clear),
        KeyDef("TÌM", 2, KeyAction.Search, LucIcons.Search),
    ),
)

// ------------------------------------------------------------------ Vietnamese tone helper

/** sắc, huyền, hỏi, ngã, nặng */
private val TONES = charArrayOf('́', '̀', '̉', '̃', '̣')
private const val VOWELS = "aeiouy"
private const val BREVE = '̆'
private const val CIRCUMFLEX = '̂'
private const val HORN = '̛'

/**
 * Cycles the tone of the last word: none → sắc → huyền → hỏi → ngã → nặng → none.
 * "dấu" lets people type every Vietnamese word with a remote.
 */
internal fun cycleTone(text: String): String {
    if (text.isBlank()) return text
    val start = text.lastIndexOf(' ') + 1
    val head = text.substring(0, start)
    val word = Normalizer.normalize(text.substring(start), Normalizer.Form.NFD)
    if (word.isEmpty()) return text

    // Split into base letters, each with its combining marks.
    data class Ch(val base: Char, val marks: MutableList<Char>)
    val chars = ArrayList<Ch>()
    for (ch in word) {
        if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt() && chars.isNotEmpty()) chars.last().marks.add(ch)
        else chars.add(Ch(ch, mutableListOf()))
    }
    var current = -1
    chars.forEach { c ->
        val t = c.marks.firstOrNull { it in TONES }
        if (t != null) current = TONES.indexOf(t)
        c.marks.removeAll { it in TONES }
    }
    val next = if (current + 1 >= TONES.size) -1 else current + 1

    if (next >= 0) {
        val target = toneTarget(chars.map { it.base.lowercaseChar() to it.marks.toList() })
        if (target < 0) return text
        chars[target].marks.add(TONES[next])
    }
    val rebuilt = buildString { chars.forEach { c -> append(c.base); c.marks.forEach { append(it) } } }
    return head + Normalizer.normalize(rebuilt, Normalizer.Form.NFC)
}

/** Index of the vowel that carries the tone (common Vietnamese rule set). */
private fun toneTarget(chars: List<Pair<Char, List<Char>>>): Int {
    val isVowel = { i: Int -> chars[i].first in VOWELS }
    // Skip "qu" and "gi" (when followed by another vowel): their u / i are not the main vowel.
    var from = 0
    if (chars.size >= 2) {
        val a = chars[0].first
        val b = chars[1].first
        if ((a == 'q' && b == 'u') || (a == 'g' && b == 'i' && chars.size > 2 && isVowel(2))) from = 2
    }
    val vowels = (from until chars.size).filter(isVowel)
    if (vowels.isEmpty()) {
        // "gi" alone, e.g. "gi" → "gì"
        return (0 until chars.size).lastOrNull(isVowel) ?: -1
    }
    // A vowel with a hat/breve/horn wins (ơ beats ư in "ươ").
    val marked = vowels.filter { i -> chars[i].second.any { it == BREVE || it == CIRCUMFLEX || it == HORN } }
    if (marked.isNotEmpty()) return marked.last()
    // Only the contiguous vowel cluster matters.
    val cluster = ArrayList<Int>()
    for (i in vowels) {
        if (cluster.isEmpty() || i == cluster.last() + 1) cluster.add(i) else break
    }
    val endsWithVowel = cluster.last() == chars.size - 1
    return when (cluster.size) {
        1 -> cluster[0]
        2 -> {
            val pair = "${chars[cluster[0]].first}${chars[cluster[1]].first}"
            if (!endsWithVowel || pair == "oa" || pair == "oe" || pair == "uy") cluster[1] else cluster[0]
        }
        else -> cluster[1]
    }
}

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

    fun press(a: KeyAction) {
        when (a) {
            is KeyAction.Type -> vm.onQueryChange(query + a.ch)
            KeyAction.Tone -> vm.onQueryChange(cycleTone(query))
            KeyAction.Space -> if (query.isNotEmpty() && !query.endsWith(' ')) vm.onQueryChange("$query ")
            KeyAction.Backspace -> if (query.isNotEmpty()) vm.onQueryChange(query.dropLast(1))
            KeyAction.Clear -> vm.clearSearch()
            KeyAction.Search -> search(query)
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
                    .width((KEY * 9 + GAP * 8).dp)
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
            Column(verticalArrangement = Arrangement.spacedBy(GAP.dp)) {
                KEYBOARD.forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
                        row.forEachIndexed { i, k ->
                            TvKey(
                                k,
                                width = (KEY * k.span + GAP * (k.span - 1)).dp,
                                focusRequester = if (r == 0 && i == 0) firstKey else null,
                            ) { press(k.action) }
                        }
                    }
                }
            }
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
private fun TvKey(k: KeyDef, width: Dp, focusRequester: FocusRequester?, onClick: () -> Unit) {
    val c = Luc.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "key")
    val accent = k.action == KeyAction.Search
    Box(
        Modifier
            .width(width)
            .height(KEY.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
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
                k.label, color = fg,
                fontSize = if (k.span == 1) 17.sp else 14.sp,
                fontWeight = if (k.span == 1) FontWeight.Medium else FontWeight.SemiBold,
                maxLines = 1
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
