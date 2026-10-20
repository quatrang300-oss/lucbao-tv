package vn.lucbao.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.lucbao.tv.data.Format
import vn.lucbao.tv.data.Library
import vn.lucbao.tv.data.Video
import vn.lucbao.tv.engine.EngineManager
import vn.lucbao.tv.kids.KIDS_TOPICS
import vn.lucbao.tv.kids.KidsChannel
import vn.lucbao.tv.kids.KidsStore
import vn.lucbao.tv.ui.TvButton
import vn.lucbao.tv.ui.focusIfVisible
import vn.lucbao.tv.ui.theme.Luc
import vn.lucbao.tv.ui.theme.LucIcons

// Kids screens use their own bright, friendly colours.
private val KidBg = Color(0xFFFFF8EC)
private val KidGreen = Color(0xFF1F8A5B)
private val KidOrange = Color(0xFFF59E0B)
private val KidText = Color(0xFF2B2B2B)
private val KidMuted = Color(0xFF6B6B6B)
private val KidLine = Color(0xFFEADFCA)
private val KidMint = Color(0xFF3DDC97)

/** The "KID" button (orange candy, three coloured tilted letters), next to the clock on Trang chủ. */
@Composable
fun TvKidBadge(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.15f else 1f, label = "kid")
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(18.dp))
            .border(3.dp, if (focused) KidMint else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.offset(y = 2.dp).size(width = 54.dp, height = 32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFC96F12)))
        Row(
            Modifier
                .size(width = 54.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFFD166), Color(0xFFFF9F43)))),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("K", color = Color(0xFFE63946), fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.rotate(-8f))
            Text("I", color = Color(0xFF1D6FE0), fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.offset(y = (-1).dp))
            Text("D", color = Color(0xFF11905A), fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.rotate(8f))
        }
    }
}

private enum class PinFor { PARENT, CHANGE }

/** Chế độ Trẻ em on TV, shown instead of everything else while it is on. */
@Composable
fun TvKids(playerOpen: Boolean, onPlay: (Video) -> Unit) {
    val channels by KidsStore.channels.collectAsStateWithLifecycle()
    val feed by KidsStore.feed.collectAsStateWithLifecycle()
    val sessionEnd by KidsStore.sessionEnd.collectAsStateWithLifecycle()
    var settingPin by remember { mutableStateOf(!KidsStore.hasPin()) }
    var askPin by remember { mutableStateOf<PinFor?>(null) }
    var parentOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var topic by remember { mutableStateOf<String?>(null) }
    var pinErrors by remember { mutableIntStateOf(0) }
    var parentTouch by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { KidsStore.refresh(force = false) }
    // TV switched off / app left with a parents' page open: close it.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (parentOpen || addOpen || askPin != null || (settingPin && KidsStore.hasPin())) {
            parentOpen = false
            addOpen = false
            askPin = null
            settingPin = !KidsStore.hasPin()
            KidsStore.startSession()
        }
    }
    // Parents' pages close by themselves after 2 minutes without a key press.
    LaunchedEffect(parentOpen, addOpen, parentTouch) {
        if (parentOpen || addOpen) {
            delay(120_000)
            parentOpen = false
            addOpen = false
            KidsStore.startSession()
        }
    }

    BackHandler(enabled = !playerOpen) {
        when {
            settingPin -> if (!KidsStore.hasPin()) KidsStore.exit() else settingPin = false
            askPin != null -> { askPin = null; pinErrors = 0 }
            addOpen -> addOpen = false
            parentOpen -> {
                parentOpen = false
                KidsStore.startSession()
            }
            else -> askPin = PinFor.PARENT
        }
    }

    Box(Modifier.fillMaxSize().background(KidBg)) {
        val overlayOpen = parentOpen || addOpen || settingPin || askPin != null
        KidsHome(
            channels = channels,
            items = feed.items,
            loading = feed.loading,
            failed = feed.failed,
            sessionEnd = sessionEnd,
            topic = topic,
            grabFocus = !overlayOpen && !playerOpen,
            onTopic = { topic = it },
            onParent = { askPin = PinFor.PARENT },
            onRetry = { KidsStore.refresh(force = true) },
            onPlay = onPlay,
        )
        if (parentOpen || addOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown) parentTouch++
                        false
                    }
                    .trapFocus()
                    .focusGroup()
            ) {
                if (addOpen) {
                    AddChannelPage(channels, onClose = { addOpen = false })
                } else {
                    ParentPage(
                        channels = channels,
                        active = askPin == null && !settingPin,
                        onAdd = { addOpen = true },
                        onChangePin = { askPin = PinFor.CHANGE },
                        onDone = {
                            parentOpen = false
                            KidsStore.startSession()
                            KidsStore.refresh(force = false)
                        },
                        onExit = { KidsStore.exit() },
                    )
                }
            }
        }
        if (settingPin) {
            SetPinOverlay(
                first = !KidsStore.hasPin(),
                onDone = { pin ->
                    val first = !KidsStore.hasPin()
                    KidsStore.setPin(pin)
                    settingPin = false
                    if (first) {
                        if (KidsStore.channels.value.isEmpty()) parentOpen = true else KidsStore.startSession()
                    }
                },
                onCancel = { if (!KidsStore.hasPin()) KidsStore.exit() else settingPin = false },
            )
        }
        askPin?.let { purpose ->
            PinOverlay(
                title = "Dành cho bố mẹ",
                subtitle = if (purpose == PinFor.CHANGE) "Nhập mã PIN hiện tại" else "Nhập mã PIN để thoát hoặc cài đặt",
                errors = pinErrors,
                onDone = { pin ->
                    when {
                        System.currentTimeMillis() < KidsStore.lockedUntil -> pinErrors++
                        KidsStore.checkPin(pin) -> {
                            KidsStore.recordOk()
                            pinErrors = 0
                            askPin = null
                            if (purpose == PinFor.CHANGE) settingPin = true else parentOpen = true
                        }
                        else -> {
                            KidsStore.recordFail()
                            pinErrors++
                        }
                    }
                },
                onCancel = { askPin = null; pinErrors = 0 },
            )
        }
    }
}

@Composable
private fun KidsHome(
    channels: List<KidsChannel>,
    items: List<Video>,
    loading: Boolean,
    failed: Boolean,
    sessionEnd: Long,
    topic: String?,
    grabFocus: Boolean,
    onTopic: (String?) -> Unit,
    onParent: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (Video) -> Unit,
) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val firstFocus = remember { FocusRequester() }
    val topics = KIDS_TOPICS.filter { (id, _) -> channels.any { it.topic == id } }
    val allowed = topic?.let { t -> channels.filter { it.topic == t }.map { it.url }.toSet() }
    val shown = if (allowed == null) items else items.filter { it.channelUrl in allowed }
    LaunchedEffect(grabFocus, shown.isNotEmpty(), sessionEnd) {
        if (grabFocus && !KidsStore.isTimeUp()) {
            delay(150)
            firstFocus.focusIfVisible()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lục Bảo ", color = KidGreen, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("Bé", color = KidOrange, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                if (sessionEnd > 0) {
                    val left = ((sessionEnd - now).coerceAtLeast(0L) + 59_999L) / 60_000L
                    Text(
                        "Còn $left phút xem", color = KidGreen, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 20.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFE7F7EF))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                KidChip("Bố mẹ", on = false, icon = true, onClick = onParent)
            }
        }
        if (topics.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { KidChip("Tất cả", topic == null) { onTopic(null) } }
                    items(topics, key = { it.first }) { (id, label) -> KidChip(label, topic == id) { onTopic(id) } }
                }
            }
        }
        when {
            channels.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                KidMessage("Chưa có kênh nào.\nBố mẹ chọn \"Bố mẹ\" ở góc trên để thêm kênh cho bé nhé.")
            }
            shown.isEmpty() && loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KidGreen, strokeWidth = 4.dp, modifier = Modifier.size(48.dp))
                }
            }
            shown.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    KidMessage(if (failed) "Chưa tải được video. Kiểm tra mạng rồi thử lại nhé." else "Chưa có video nào ở đây.")
                    KidChip("Thử lại", true, onClick = onRetry)
                }
            }
            else -> itemsIndexed(shown, key = { _, v -> v.url }) { i, v ->
                KidCard(v, if (i == 0) firstFocus else null) { onPlay(v) }
            }
        }
    }
}

@Composable
private fun KidCard(v: Video, focusRequester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "kidcard")
    Column(
        Modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(4.dp, if (focused) KidMint else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Box {
            AsyncImage(
                model = v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFFEDE6D8))
            )
            if (v.duration > 0) {
                Text(
                    Format.duration(v.duration), color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            v.title, color = KidText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun KidChip(text: String, on: Boolean, icon: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "kidchip")
    Row(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(50))
            .background(if (on || focused) KidMint else Color.White)
            .border(2.dp, if (focused) KidGreen else if (on) KidMint else KidLine, RoundedCornerShape(50))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon) {
            Icon(LucIcons.Lock, null, tint = if (on || focused) Color(0xFF06301E) else KidMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (on || focused) Color(0xFF06301E) else KidMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun KidMessage(text: String) {
    Text(
        text, color = KidMuted, fontSize = 18.sp, lineHeight = 26.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp)
    )
}

// ---------------------------------------------------------------------- PIN

/** A 4-digit keypad card (▲▼◀▶ + OK, or the remote's number keys). */
@Composable
private fun PinOverlay(title: String, subtitle: String, errors: Int, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var digits by remember { mutableStateOf("") }
    var shownError by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(errors) { if (errors > 0) { shownError = true; digits = "" } }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { first.requestFocus() }
    }
    fun type(k: String) {
        if (digits.length >= 4) return
        digits += k
        shownError = false
        if (digits.length == 4) {
            val pin = digits
            digits = ""
            onDone(pin)
        }
    }
    val numberKeys = mapOf(
        Key.Zero to "0", Key.One to "1", Key.Two to "2", Key.Three to "3", Key.Four to "4",
        Key.Five to "5", Key.Six to "6", Key.Seven to "7", Key.Eight to "8", Key.Nine to "9",
        Key.NumPad0 to "0", Key.NumPad1 to "1", Key.NumPad2 to "2", Key.NumPad3 to "3", Key.NumPad4 to "4",
        Key.NumPad5 to "5", Key.NumPad6 to "6", Key.NumPad7 to "7", Key.NumPad8 to "8", Key.NumPad9 to "9",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x8C0A1E14))
            .onPreviewKeyEvent { e ->
                val n = numberKeys[e.key] ?: return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) type(n)
                true
            }
            .trapFocus()
            .focusGroup(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(420.dp).clip(RoundedCornerShape(26.dp)).background(Color.White).padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = KidText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            val error = errors > 0 && shownError
            Text(
                when {
                    error && System.currentTimeMillis() < KidsStore.lockedUntil ->
                        "Sai nhiều lần. Thử lại sau ${(KidsStore.lockedUntil - System.currentTimeMillis()) / 1000 + 1} giây"
                    error -> "Sai mã PIN, thử lại nhé"
                    else -> subtitle
                },
                color = if (error) Color(0xFFD64545) else KidMuted, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 18.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier.size(18.dp).clip(CircleShape)
                            .background(if (i < digits.length) KidGreen else Color.Transparent)
                            .border(2.dp, KidGreen, CircleShape)
                    )
                }
            }
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Huỷ", "0", "⌫").chunked(3).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEachIndexed { i, k ->
                        PinKey(k, Modifier.weight(1f), if (r == 0 && i == 0) first else null) {
                            when (k) {
                                "Huỷ" -> onCancel()
                                "⌫" -> digits = digits.dropLast(1)
                                else -> type(k)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(k: String, modifier: Modifier, focusRequester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(56.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) KidMint else Color(0xFFF1F5F2))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(k, color = KidText, fontSize = if (k.length > 1) 15.sp else 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Choosing a PIN: typed twice. */
@Composable
private fun SetPinOverlay(first: Boolean, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var firstPin by remember { mutableStateOf<String?>(null) }
    var mismatch by remember { mutableIntStateOf(0) }
    PinOverlay(
        title = if (firstPin == null) (if (first) "Đặt mã PIN cho bố mẹ" else "Mã PIN mới") else "Nhập lại mã PIN",
        subtitle = if (firstPin == null) "4 số, dùng để thoát chế độ Trẻ em và cài đặt" else "Nhập lại để chắc chắn",
        errors = if (firstPin == null) mismatch else 0,
        onDone = { pin ->
            val p = firstPin
            when {
                p == null -> firstPin = pin
                p == pin -> onDone(pin)
                else -> {
                    firstPin = null
                    mismatch++
                }
            }
        },
        onCancel = onCancel,
    )
}

// ---------------------------------------------------------------------- parents

@Composable
private fun ParentPage(
    channels: List<KidsChannel>,
    active: Boolean,
    onAdd: () -> Unit,
    onChangePin: () -> Unit,
    onDone: () -> Unit,
    onExit: () -> Unit,
) {
    val c = Luc.colors
    val timer by KidsStore.timer.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    // Also after the PIN pad closes or a channel is removed: the focused button may be gone.
    LaunchedEffect(active, channels.size) {
        if (active) {
            delay(100)
            runCatching { first.requestFocus() }
        }
    }
    LazyColumn(
        Modifier.fillMaxSize().background(c.surface),
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Chế độ Trẻ em – dành cho bố mẹ", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Cho bé xem", LucIcons.Play, focusRequester = first, onClick = onDone)
                TvButton("Thêm kênh", LucIcons.PersonAdd, onClick = onAdd)
                TvButton("Đổi mã PIN", LucIcons.Lock, onClick = onChangePin)
                TvButton("Thoát chế độ Trẻ em", LucIcons.Close, onClick = onExit)
            }
        }
        item {
            Text("Thời gian xem mỗi lần", color = c.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(15, 30, 45, 60, 0).forEach { m ->
                    TvButton(if (m == 0) "Không giới hạn" else "$m phút", if (timer == m) LucIcons.Check else null, selected = timer == m) {
                        KidsStore.setTimer(m)
                    }
                }
            }
        }
        item {
            Text(
                "Kênh được xem (${channels.size}) — chọn một kênh để bỏ", color = c.muted, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (channels.isEmpty()) {
            item { Text("Chưa có kênh nào. Chọn \"Thêm kênh\" để bé có video xem.", color = c.muted, fontSize = 15.sp) }
        }
        items(channels, key = { it.url }) { ch ->
            TvButton("${ch.name}  ·  ${topicLabel(ch.topic)}   ✕", null) { KidsStore.removeChannel(ch.url) }
        }
        item {
            Text(
                "Bé chỉ xem được video của các kênh ở trên; video tiếp theo và tự phát cũng chỉ lấy từ các kênh này. " +
                    "Lịch sử xem của bé không lẫn vào gợi ý của người lớn.",
                color = c.muted, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun topicLabel(id: String) = KIDS_TOPICS.firstOrNull { it.first == id }?.second ?: "Khác"

private data class FoundChannel(val url: String, val name: String, val avatar: String?)

/** Adding channels: from the followed channels, or by searching a name with the keyboard. */
@Composable
private fun AddChannelPage(channels: List<KidsChannel>, onClose: () -> Unit) {
    val c = Luc.colors
    val followed by Library.channels.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var topic by remember { mutableStateOf("cartoon") }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<FoundChannel>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { first.requestFocus() }
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        searching = true
        failed = false
        scope.launch {
            val found = runCatching {
                withContext(Dispatchers.IO) {
                    EngineManager.get().search(q, null).items
                        .filter { !it.channelUrl.isNullOrBlank() && !it.channel.isNullOrBlank() }
                        .distinctBy { it.channelUrl }
                        .map { FoundChannel(it.channelUrl, it.channel, null) }
                }
            }
            results = found.getOrNull() ?: emptyList()
            failed = found.isFailure
            searching = false
        }
    }

    Row(Modifier.fillMaxSize().background(c.surface).padding(horizontal = 48.dp, vertical = 28.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvButton("Quay lại", LucIcons.Back, onClick = onClose)
                Spacer(Modifier.width(14.dp))
                Text("Thêm kênh cho bé", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text("Thêm vào chủ đề", color = c.muted, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KIDS_TOPICS.forEach { (id, label) -> TvButton(label, null, selected = topic == id) { topic = id } }
            }
            TvQueryField(query, "Tên kênh, ví dụ: hoạt hình thiếu nhi")
            TvKeyboard(query, onQueryChange = { query = it }, onSearch = { search() }, firstKey = first)
        }
        Spacer(Modifier.width(28.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (searching) {
                item { CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp)) }
            }
            results?.let { list ->
                item { Text(if (failed) "Chưa tìm được, kiểm tra mạng rồi thử lại" else "Kết quả tìm kiếm", color = c.muted, fontSize = 14.sp) }
                items(list, key = { "r" + it.url }) { ch -> ChannelPick(ch, channels, topic) }
            }
            if (followed.isNotEmpty()) {
                item { Text("Kênh đang theo dõi", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
                items(followed, key = { "f" + it.url }) { f -> ChannelPick(FoundChannel(f.url, f.name, f.avatar), channels, topic) }
            }
        }
    }
}

@Composable
private fun ChannelPick(ch: FoundChannel, channels: List<KidsChannel>, topic: String) {
    val added = channels.firstOrNull { it.url == ch.url }
    TvButton(
        if (added != null) "${ch.name}  ·  đã thêm (${topicLabel(added.topic)})" else ch.name,
        if (added != null) LucIcons.Check else LucIcons.PersonAdd,
        selected = added != null,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (added != null) KidsStore.removeChannel(ch.url)
        else KidsStore.addChannel(KidsChannel(ch.url, ch.name, ch.avatar, topic))
    }
}

// ---------------------------------------------------------------------- time up

/** Covers everything (also the player) when the kids' time is up; draws nothing otherwise. */
@Composable
fun TvKidsTimeUp(onTimeUp: () -> Unit) {
    val sessionEnd by KidsStore.sessionEnd.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis(), sessionEnd) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val up = sessionEnd > 0 && now >= sessionEnd
    var askPin by remember { mutableStateOf(false) }
    var pinErrors by remember { mutableIntStateOf(0) }
    val btn = remember { FocusRequester() }
    LaunchedEffect(up) {
        if (up) {
            runCatching { vn.lucbao.tv.player.PlayerController.exo.pause() }
            onTimeUp()
        }
    }
    LaunchedEffect(up, askPin) {
        if (up && !askPin) {
            delay(200)
            runCatching { btn.requestFocus() }
        }
    }
    if (!up) return
    BackHandler { if (askPin) { askPin = false; pinErrors = 0 } else askPin = true }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFBFE9FF), KidBg)))
            // The kids screen stays composed underneath: keep the remote on this screen.
            .trapFocus()
            .focusGroup(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(130.dp)) {
                drawCircle(Color(0xFFF7B733))
                drawCircle(Color(0xFFFFD56B), radius = size.minDimension * 0.46f, center = Offset(size.width * 0.42f, size.height * 0.45f))
            }
            Spacer(Modifier.height(26.dp))
            Text("Hết giờ xem rồi!", color = Color(0xFF1F5F43), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(
                "Bé nghỉ ngơi một chút nhé.\nHẹn gặp lại lần sau!", color = Color(0xFF4B5B53), fontSize = 19.sp,
                lineHeight = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, bottom = 30.dp)
            )
            Box(Modifier.focusRequester(btn)) {
                KidChip("Bố mẹ mở thêm giờ", on = false, icon = true) { askPin = true }
            }
        }
        if (askPin) {
            PinOverlay(
                title = "Dành cho bố mẹ",
                subtitle = "Nhập mã PIN để cho bé xem thêm",
                errors = pinErrors,
                onDone = { pin ->
                    when {
                        System.currentTimeMillis() < KidsStore.lockedUntil -> pinErrors++
                        KidsStore.checkPin(pin) -> {
                            KidsStore.recordOk()
                            pinErrors = 0
                            askPin = false
                            KidsStore.startSession()
                        }
                        else -> {
                            KidsStore.recordFail()
                            pinErrors++
                        }
                    }
                },
                onCancel = { askPin = false; pinErrors = 0 },
            )
        }
    }
}

/** Keeps the remote inside an overlay while the screen underneath stays composed. */
@Suppress("DEPRECATION")
@kotlin.OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.trapFocus(): Modifier = focusProperties { exit = { FocusRequester.Cancel } }
