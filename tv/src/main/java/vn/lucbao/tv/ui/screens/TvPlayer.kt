package vn.lucbao.tv.ui.screens

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import vn.lucbao.tv.data.Format
import vn.lucbao.tv.data.Library
import vn.lucbao.tv.data.Video
import vn.lucbao.tv.data.videoKey
import vn.lucbao.tv.player.PlayerController
import vn.lucbao.tv.player.PlayerUi
import vn.lucbao.tv.ui.TvButton
import vn.lucbao.tv.ui.TvCard
import vn.lucbao.tv.ui.TvEdge
import vn.lucbao.tv.ui.TvIconButton
import vn.lucbao.tv.ui.theme.Luc
import vn.lucbao.tv.ui.theme.LucIcons
import vn.lucbao.tv.ui.tvErrorText

private val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
private const val HIDE_AFTER_MS = 5_000L

private enum class FocusGoal { NONE, ROOT, PLAY, RELATED, RETRY }

private fun speedLabel(s: Float): String =
    if (s == 1f) "Tốc độ 1×" else "Tốc độ " + ("%.2f".format(s)).trimEnd('0').trimEnd('.', ',').replace('.', ',') + "×"

/**
 * Full-screen TV player.
 *
 * Overlay hidden: OK = pause/play, ◀ ▶ = tua 10 giây, ▲ = hiện điều khiển, ▼ = hiện video khác.
 * Overlay shown: the remote moves between buttons; it hides itself after 5 s while playing.
 */
@Suppress("DEPRECATION")
@kotlin.OptIn(ExperimentalComposeUiApi::class)
@OptIn(UnstableApi::class)
@Composable
fun TvPlayer(ui: PlayerUi, onClose: () -> Unit, onPlay: (Video) -> Unit) {
    val c = Luc.colors
    val exo = PlayerController.exo

    var overlay by remember { mutableStateOf(true) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var seekFlash by remember { mutableLongStateOf(0L) }
    var flashVisible by remember { mutableStateOf(false) }
    var goal by remember { mutableStateOf(FocusGoal.PLAY) }
    var focusTick by remember { mutableIntStateOf(0) }
    var qualityDialog by remember { mutableStateOf(false) }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val relatedFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    fun touch() {
        lastInput = System.currentTimeMillis()
    }

    fun show(target: FocusGoal = FocusGoal.PLAY) {
        overlay = true
        goal = if (ui.error != null) FocusGoal.RETRY else target
        focusTick++
        touch()
    }

    fun hide() {
        overlay = false
        goal = FocusGoal.ROOT
        focusTick++
    }

    fun seekBy(forward: Boolean) {
        if (ui.video?.live == true) return
        if (forward) exo.seekForward() else exo.seekBack()
        position = exo.currentPosition
        seekFlash = System.currentTimeMillis()
        touch()
    }

    LaunchedEffect(seekFlash) {
        if (seekFlash > 0) {
            flashVisible = true
            delay(2_000)
            flashVisible = false
        }
    }

    // Poll the position for the progress bar.
    LaunchedEffect(Unit) {
        while (true) {
            position = exo.currentPosition
            duration = exo.duration.takeIf { it > 0 } ?: 0L
            buffered = exo.bufferedPosition
            delay(500)
        }
    }

    // New video (e.g. picked from "Nhiều video hơn"): show its title for a moment.
    LaunchedEffect(ui.video?.url?.let(::videoKey)) { show() }
    LaunchedEffect(ui.error) { show(if (ui.error != null) FocusGoal.RETRY else FocusGoal.PLAY) }

    // Auto-hide while playing.
    LaunchedEffect(overlay, lastInput, ui.isPlaying, qualityDialog, ui.error) {
        if (overlay && ui.isPlaying && !qualityDialog && ui.error == null) {
            delay(HIDE_AFTER_MS)
            hide()
        }
    }

    // Move the remote's focus where it should be.
    LaunchedEffect(focusTick) {
        delay(60)
        runCatching {
            when (goal) {
                FocusGoal.ROOT -> rootFocus.requestFocus()
                FocusGoal.PLAY -> playFocus.requestFocus()
                FocusGoal.RELATED -> relatedFocus.requestFocus()
                FocusGoal.RETRY -> retryFocus.requestFocus()
                FocusGoal.NONE -> Unit
            }
        }.onFailure { runCatching { rootFocus.requestFocus() } }
    }

    BackHandler {
        when {
            qualityDialog -> qualityDialog = false
            overlay && ui.error == null -> hide()
            else -> onClose()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Keep the remote inside the player: the screens underneath stay composed.
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) touch()
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.MediaPlayPause -> { PlayerController.togglePlay(); show(); true }
                    Key.MediaPlay -> { if (!exo.isPlaying) PlayerController.togglePlay(); true }
                    Key.MediaPause -> { if (exo.isPlaying) exo.pause(); show(); true }
                    Key.MediaFastForward -> { seekBy(true); true }
                    Key.MediaRewind -> { seekBy(false); true }
                    Key.MediaNext -> { PlayerController.next(); true }
                    Key.MediaPrevious -> { PlayerController.previous(); true }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    player = exo
                }
            },
            update = { it.keepScreenOn = ui.isPlaying || ui.loading },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        // Invisible focus holder that receives the remote while the overlay is hidden.
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(rootFocus)
                .onKeyEvent { e ->
                    val center = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    if (center) {
                        // Act on key-up so the click doesn't land on the button that gets focus.
                        if (e.type == KeyEventType.KeyUp) {
                            PlayerController.togglePlay()
                            show()
                        }
                        return@onKeyEvent true
                    }
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> { seekBy(false); true }
                        Key.DirectionRight -> { seekBy(true); true }
                        Key.DirectionUp -> { show(); true }
                        Key.DirectionDown -> { show(if (ui.related.isNotEmpty()) FocusGoal.RELATED else FocusGoal.PLAY); true }
                        else -> false
                    }
                }
                .focusable()
        )

        if (ui.loading) {
            CircularProgressIndicator(
                color = c.primary, strokeWidth = 4.dp,
                modifier = Modifier.align(Alignment.Center).size(56.dp)
            )
        }

        // Slim progress bar after seeking with the overlay hidden.
        if (flashVisible && !overlay && duration > 0) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = TvEdge, vertical = 28.dp)
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text(Format.millis(position), color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(Format.millis(duration), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
                ProgressBar(position, buffered, duration, focused = false, modifier = Modifier.padding(top = 6.dp))
            }
        }

        AnimatedVisibility(overlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Overlay(
                ui = ui,
                position = position,
                buffered = buffered,
                duration = duration,
                playFocus = playFocus,
                relatedFocus = relatedFocus,
                retryFocus = retryFocus,
                onSeek = { forward -> seekBy(forward) },
                onQuality = { qualityDialog = true },
                onPlay = { v -> onPlay(v) },
                onClose = onClose,
            )
        }
    }

    if (qualityDialog) {
        QualityDialog(ui, onDismiss = {
            qualityDialog = false
            show()
        })
    }
}

@Composable
private fun Overlay(
    ui: PlayerUi,
    position: Long,
    buffered: Long,
    duration: Long,
    playFocus: FocusRequester,
    relatedFocus: FocusRequester,
    retryFocus: FocusRequester,
    onSeek: (Boolean) -> Unit,
    onQuality: () -> Unit,
    onPlay: (Video) -> Unit,
    onClose: () -> Unit,
) {
    val c = Luc.colors
    val v = ui.video ?: return
    val channels by Library.channels.collectAsStateWithLifecycle()
    val channelUrl = ui.details?.channelUrl ?: v.channelUrl
    val following = remember(channels, channelUrl) { Library.isFollowing(channelUrl) }

    Box(Modifier.fillMaxSize()) {
        // Top: title
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xE6000000), Color.Transparent)))
                .padding(start = TvEdge, end = TvEdge, top = 28.dp, bottom = 48.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        v.title.ifBlank { ui.details?.title ?: "" }, color = Color.White,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        Format.dot(v.channel.ifBlank { ui.details?.channel ?: "" }, Format.views(v.views), v.uploaded),
                        color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                val q = ui.quality
                if (q != null) {
                    Text(
                        q.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 16.dp, top = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.primary.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Bottom: progress, controls, more videos
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000), Color(0xF2000000))))
                .padding(top = 60.dp, bottom = 20.dp)
        ) {
            if (ui.error != null) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = TvEdge, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        tvErrorText(ui.error), color = Color.White, fontSize = 18.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
                        TvButton("Thử lại", LucIcons.Refresh, focusRequester = retryFocus) { PlayerController.retry() }
                        TvButton("Đóng", LucIcons.Close) { onClose() }
                    }
                }
            } else {
                if (duration > 0 && !v.live) {
                    SeekRow(position, buffered, duration, onSeek)
                } else if (v.live) {
                    Text(
                        "● TRỰC TIẾP", color = Color(0xFFFF6B6B), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = TvEdge)
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = TvEdge).padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TvIconButton(LucIcons.Prev, "Video trước") { PlayerController.previous() }
                    TvIconButton(LucIcons.Replay10, "Lùi 10 giây") { onSeek(false) }
                    TvIconButton(
                        if (ui.isPlaying) LucIcons.Pause else LucIcons.Play,
                        if (ui.isPlaying) "Tạm dừng" else "Phát",
                        size = 60.dp, focusRequester = playFocus
                    ) { PlayerController.togglePlay() }
                    TvIconButton(LucIcons.Forward10, "Tới 10 giây") { onSeek(true) }
                    TvIconButton(LucIcons.Next, "Video tiếp theo") { PlayerController.next() }
                    Spacer(Modifier.weight(1f))
                    TvButton(ui.quality?.label ?: "Chất lượng", LucIcons.Tune) { onQuality() }
                    TvButton(speedLabel(ui.speed), LucIcons.Speed) {
                        val i = SPEEDS.indexOf(ui.speed)
                        PlayerController.setSpeed(SPEEDS[(i + 1) % SPEEDS.size])
                    }
                    TvButton(
                        if (ui.favorite) "Đã thích" else "Yêu thích", LucIcons.Heart, selected = ui.favorite
                    ) { PlayerController.toggleFavorite() }
                    if (channelUrl != null) {
                        TvButton(
                            if (following) "Đang theo dõi" else "Theo dõi",
                            if (following) LucIcons.Check else LucIcons.PersonAdd,
                            selected = following
                        ) {
                            Library.toggleFollow(
                                channelUrl, v.channel.ifBlank { ui.details?.channel ?: "" },
                                ui.details?.channelAvatar
                            )
                        }
                    }
                }
            }
            if (ui.related.isNotEmpty()) {
                Text(
                    "Nhiều video hơn", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = TvEdge, top = 18.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(start = TvEdge, end = TvEdge, top = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(ui.related.take(30), key = { i, r -> "$i-${r.url}" }) { i, r ->
                        TvCard(
                            r, onClick = { onPlay(r) }, width = 168.dp,
                            focusRequester = if (i == 0) relatedFocus else null
                        )
                    }
                }
            }
        }
    }
}

/** Focusable progress bar: ◀ ▶ seek while it is focused. */
@Composable
private fun SeekRow(position: Long, buffered: Long, duration: Long, onSeek: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TvEdge)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onSeek(false); true }
                    Key.DirectionRight -> { onSeek(true); true }
                    else -> false
                }
            }
            .focusable()
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(Format.millis(position), color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(
                if (focused) "◀ ▶ để tua" else "", color = Luc.colors.primary, fontSize = 13.sp
            )
            Spacer(Modifier.weight(1f))
            Text(Format.millis(duration), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }
        ProgressBar(position, buffered, duration, focused, Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ProgressBar(position: Long, buffered: Long, duration: Long, focused: Boolean, modifier: Modifier = Modifier) {
    val c = Luc.colors
    val h = if (focused) 8.dp else 4.dp
    val p = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val b = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .fillMaxWidth()
            .height(h)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.22f))
    ) {
        Box(Modifier.fillMaxWidth(b).height(h).background(Color.White.copy(alpha = 0.35f)))
        Box(Modifier.fillMaxWidth(p).height(h).background(c.primary))
    }
}

@Composable
private fun QualityDialog(ui: PlayerUi, onDismiss: () -> Unit) {
    val c = Luc.colors
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.card)
                .padding(20.dp)
        ) {
            Text("Chất lượng video", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (ui.choices.isEmpty()) {
                Text("Đang tải…", color = c.muted, fontSize = 14.sp)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(320.dp)) {
                itemsIndexed(ui.choices, key = { _, q -> q.label }) { i, q ->
                    val selected = q.label == ui.quality?.label
                    val label = buildString {
                        append(q.label)
                        if (q.badge != null) append("  ·  ").append(q.badge)
                    }
                    TvButton(
                        label,
                        icon = if (selected) LucIcons.Check else if (q.audioOnly) LucIcons.Headphones else null,
                        selected = selected,
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = if (selected || (ui.quality == null && i == 0)) first else null,
                    ) {
                        PlayerController.setQuality(q)
                        onDismiss()
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { first.requestFocus() }
    }
}
