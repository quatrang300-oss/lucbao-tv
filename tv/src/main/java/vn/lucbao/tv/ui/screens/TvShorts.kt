package vn.lucbao.tv.ui.screens

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxWidth
import vn.lucbao.tv.shorts.ShortsTopic
import androidx.compose.ui.window.Dialog
import vn.lucbao.tv.data.Format
import vn.lucbao.tv.data.Library
import vn.lucbao.tv.data.Video
import vn.lucbao.tv.data.videoKey
import vn.lucbao.tv.player.ShortsPlayer
import vn.lucbao.tv.shorts.SHORTS_TOPICS
import vn.lucbao.tv.shorts.ShortsViewModel
import vn.lucbao.tv.ui.TvButton
import vn.lucbao.tv.ui.focusIfVisible
import vn.lucbao.tv.ui.theme.Luc
import vn.lucbao.tv.ui.theme.LucIcons
import vn.lucbao.tv.ui.tvErrorText

/** Counts visits to the Shorts screen: each new visit shows new shorts. */
private object ShortsVisits {
    var count = 0
}

/**
 * Shorts on TV: one vertical video in the middle, the sides blurred, title and buttons on
 * the right. ▲ ▼ change the short, OK pauses, ▶ goes to the buttons.
 */
@OptIn(UnstableApi::class)
@Composable
fun TvShorts(active: Boolean, onOpenVideo: (Video) -> Unit) {
    val c = Luc.colors
    val context = LocalContext.current
    val vm: ShortsViewModel = viewModel()
    // A new visit (from another screen) loads new shorts, before the old list is read.
    remember(vm) { vm.enterTab(++ShortsVisits.count); true }
    val topic by vm.topic.collectAsStateWithLifecycle()
    val feed by vm.feed.collectAsStateWithLifecycle()
    val state by ShortsPlayer.state.collectAsStateWithLifecycle()
    val favorites by Library.favorites.collectAsStateWithLifecycle()
    val channels by Library.channels.collectAsStateWithLifecycle()
    val items = feed.items
    var index by remember { mutableIntStateOf(0) }
    val videoFocus = remember { FocusRequester() }
    val buttonsFocus = remember { FocusRequester() }
    val topicFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    var searching by remember { mutableStateOf(false) }

    // New feed (topic, refresh, new visit): back to the first short.
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) index = 0 }
    val cur = items.getOrNull(index)

    DisposableEffect(Unit) { onDispose { ShortsPlayer.stop() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        ShortsPlayer.stop()
        vm.onAppStopped()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (!vm.refreshIfStale() && active) cur?.let { ShortsPlayer.play(context, it) }
    }
    LaunchedEffect(active) { if (!active) ShortsPlayer.stop() }
    LaunchedEffect(cur?.url, active) {
        if (active && cur != null) {
            // Holding ▼ skips quickly: only the short the remote stops on loads and counts as seen.
            ShortsPlayer.pause()
            delay(180)
            ShortsPlayer.play(context, cur)
            vm.markWatched(cur)
        } else if (cur == null) {
            ShortsPlayer.stop()
        }
    }
    LaunchedEffect(index, items.size) {
        items.getOrNull(index + 1)?.let { ShortsPlayer.prefetch(it) }
        items.getOrNull(index + 2)?.let { ShortsPlayer.prefetch(it) }
        if (items.isNotEmpty() && index >= items.size - 4) vm.loadMore()
    }
    // On entering, after the full player closes, and when the first shorts arrive.
    LaunchedEffect(active, items.isEmpty()) {
        if (active && items.isNotEmpty()) {
            delay(150)
            videoFocus.focusIfVisible()
        }
    }
    LaunchedEffect(items.isEmpty() && !feed.loading) {
        if (items.isEmpty() && !feed.loading) {
            delay(150)
            retryFocus.focusIfVisible()
        }
    }

    if (searching) {
        ShortsSearchDialog(
            initial = if (topic.id.startsWith(SEARCH_TOPIC)) topic.label else "",
            onSearch = { q ->
                searching = false
                val text = q.trim()
                if (text.isNotEmpty()) {
                    vm.selectTopic(ShortsTopic(SEARCH_TOPIC + text, text, listOf("$text #shorts", "$text shorts", text)))
                    runCatching { videoFocus.requestFocus() }
                }
            },
            onDismiss = { searching = false },
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        // Three zones that never overlap: topics left, the 9:16 short in the middle, info right.
        val videoW = minOf(maxHeight * 9f / 16f, maxWidth * 0.5f)
        val sideW = (maxWidth - videoW) / 2
        val fullH = maxHeight
        // Blurred picture of the short behind everything.
        AsyncImage(
            model = cur?.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(40.dp)
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

        // The short, 9:16 in the middle. Holds the remote while watching.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(videoW)
                .background(Color.Black)
                .focusRequester(videoFocus)
                .onKeyEvent { e ->
                    val center = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    if (center) {
                        if (e.type == KeyEventType.KeyUp) ShortsPlayer.togglePlay()
                        return@onKeyEvent true
                    }
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> {
                            if (index < items.lastIndex) index++
                            true
                        }
                        Key.DirectionUp -> {
                            if (index > 0) index-- else runCatching { topicFocus.requestFocus() }
                            true
                        }
                        Key.DirectionRight -> {
                            runCatching { buttonsFocus.requestFocus() }
                            true
                        }
                        else -> false // ◀ goes to the left column
                    }
                }
                // Nothing to watch (loading / failed): let the remote reach "Thử lại" instead.
                .focusProperties { canFocus = items.isNotEmpty() }
                .focusable()
        ) {
            AsyncImage(
                model = cur?.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (cur != null && state.current == videoKey(cur.url) && state.error == null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            isFocusable = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            keepScreenOn = true
                            player = ShortsPlayer.player(ctx)
                        }
                    },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (cur != null && state.current == videoKey(cur.url) && state.loading) {
                CircularProgressIndicator(
                    color = c.primary, strokeWidth = 4.dp, modifier = Modifier.align(Alignment.Center).size(48.dp)
                )
            }
            if (cur != null && state.current == videoKey(cur.url) && !state.isPlaying && !state.loading && state.error == null) {
                Icon(LucIcons.Play, "Đang tạm dừng", tint = Color.White, modifier = Modifier.align(Alignment.Center).size(64.dp))
            }
            if (cur != null && state.error != null && state.current == videoKey(cur.url)) {
                Text(
                    tvErrorText(state.error) + "\n▼ để xem video khác",
                    color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(14.dp)
                )
            }
            if (items.isNotEmpty()) {
                Text(
                    if (index > 0) "▲" else "", color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                )
                Text(
                    if (index < items.lastIndex) "▼" else "", color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                )
            }
        }

        // Topics, top left. Picking the current topic again loads new shorts.
        Column(
            Modifier
                .align(Alignment.TopStart)
                .width(sideW)
                .padding(start = 24.dp, top = 22.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search shorts: the result becomes a topic of its own, first in the row.
            vn.lucbao.tv.ui.TvSearchBar(
                if (topic.id.startsWith(SEARCH_TOPIC)) topic.label else "Tìm Shorts…",
                Modifier.fillMaxWidth(),
                typed = topic.id.startsWith(SEARCH_TOPIC),
            ) {
                ShortsPlayer.pause()
                searching = true
            }
            LazyRow(
                Modifier
                    // The short fills the whole height, so ▼ would skip it: go straight back to it.
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown && items.isNotEmpty()) {
                            runCatching { videoFocus.requestFocus() }
                            true
                        } else false
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val topics = if (topic.id.startsWith(SEARCH_TOPIC)) listOf(topic) + SHORTS_TOPICS else SHORTS_TOPICS
                items(topics, key = { it.id }) { t ->
                    TvButton(
                        t.label, if (t.id.startsWith(SEARCH_TOPIC)) LucIcons.Search else null, selected = t.id == topic.id,
                        focusRequester = if (t.id == topic.id) topicFocus else null
                    ) {
                        vm.selectTopic(t)
                        runCatching { videoFocus.requestFocus() }
                    }
                }
            }
        }

        // Title and buttons, right of the short.
        if (cur != null) {
            val liked = favorites.any { videoKey(it.video.url) == videoKey(cur.url) }
            val following = remember(channels, cur.channelUrl) { Library.isFollowing(cur.channelUrl) }
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .width(sideW)
                    .heightIn(max = fullH)
                    // Small screens: scrolls (the remote's focus scrolls it) instead of running over the topics.
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 28.dp, top = 24.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "@" + cur.channel.ifBlank { "YouTube" }, color = Color.White, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(cur.title, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (cur.views > 0) Text(Format.views(cur.views), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                TvButton(
                    if (liked) "Đã thích" else "Thích", LucIcons.Heart, selected = liked, focusRequester = buttonsFocus
                ) { Library.toggleFavorite(cur) }
                val url = cur.channelUrl
                if (url != null && cur.channel.isNotBlank()) {
                    TvButton(
                        if (following) "Đang theo dõi" else "Theo dõi",
                        if (following) LucIcons.Check else LucIcons.PersonAdd, selected = following
                    ) { Library.toggleFollow(url, cur.channel, null) }
                }
                TvButton("Mở video", LucIcons.Fullscreen) {
                    ShortsPlayer.pause()
                    onOpenVideo(cur)
                }
                TvButton("◀ Quay lại video", null) { runCatching { videoFocus.requestFocus() } }
            }
        }

        if (items.isEmpty()) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (feed.loading) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(48.dp))
                } else {
                    Text(
                        feed.error ?: "Chưa có video ngắn nào.", color = Color.White, fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    TvButton("Thử lại", LucIcons.Refresh, focusRequester = retryFocus) { vm.refresh() }
                }
            }
        }
    }
}

private const val SEARCH_TOPIC = "search:"

/** Typing a Shorts search with the Vietnamese keyboard. */
@Composable
private fun ShortsSearchDialog(initial: String, onSearch: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Luc.colors
    var q by remember { mutableStateOf(initial) }
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(18.dp)).background(c.surface).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Tìm Shorts", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TvQueryField(q, "Gõ điều bạn muốn xem")
            TvKeyboard(q, onQueryChange = { q = it }, onSearch = { onSearch(q) }, firstKey = first)
        }
        LaunchedEffect(Unit) {
            delay(100)
            runCatching { first.requestFocus() }
        }
    }
}
