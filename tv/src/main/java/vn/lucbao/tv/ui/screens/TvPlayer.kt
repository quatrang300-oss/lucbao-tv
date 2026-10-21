package vn.lucbao.tv.ui.screens

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.lucbao.tv.data.Format
import vn.lucbao.tv.data.Library
import vn.lucbao.tv.data.Prefs
import vn.lucbao.tv.data.Video
import vn.lucbao.tv.data.videoKey
import vn.lucbao.tv.player.CaptionStatus
import vn.lucbao.tv.player.PlayerController
import vn.lucbao.tv.player.PlayerStats
import vn.lucbao.tv.player.VoiceOver
import vn.lucbao.tv.player.PlayerUi
import vn.lucbao.tv.ui.TvButton
import vn.lucbao.tv.ui.TvCard
import vn.lucbao.tv.ui.TvEdge
import vn.lucbao.tv.ui.tvGlass
import vn.lucbao.tv.ui.TvIconButton
import vn.lucbao.tv.ui.theme.Luc
import vn.lucbao.tv.ui.theme.LucIcons
import vn.lucbao.tv.ui.tvErrorText

/** Playback speeds, 0.25x to 10x. */
private val SPEEDS = listOf(
    0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 3.5f, 4f,
    4.5f, 5f, 5.5f, 6f, 6.5f, 7f, 7.5f, 8f, 8.5f, 9f, 9.5f, 10f,
)

/** The next speed up; after 10x it starts again at 0.25x. */
private fun nextSpeed(s: Float): Float = SPEEDS.firstOrNull { it > s + 0.001f } ?: SPEEDS.first()

private fun speedIndex(s: Float): Int =
    SPEEDS.indices.minByOrNull { kotlin.math.abs(SPEEDS[it] - s) } ?: SPEEDS.indexOf(1f)

/** "1x", "1.25x", "10x" */
private fun speedText(s: Float): String =
    (if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()) + "x"

private fun speedButtonText(s: Float): String = if (s == 1f) "Tốc độ" else "Tốc độ " + speedText(s)
private const val HIDE_AFTER_MS = 5_000L
/** Longer while the remote is in "Nhiều video hơn", so there is time to look around. */
private const val HIDE_RELATED_MS = 10_000L

private enum class FocusGoal { NONE, ROOT, PLAY, RELATED, RETRY }


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

    // A picked video plays full screen at once: nothing over the picture until ▲ / OK / ▼.
    var overlay by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var seekFlash by remember { mutableLongStateOf(0L) }
    var flashVisible by remember { mutableStateOf(false) }
    var goal by remember { mutableStateOf(FocusGoal.ROOT) }
    var focusTick by remember { mutableIntStateOf(0) }
    var qualityDialog by remember { mutableStateOf(false) }
    var ccDialog by remember { mutableStateOf(false) }
    // Speed slider (OK held 1.5 s on "Tốc độ"): ◀ ▶ change the speed, closes after 3 s idle.
    var speedRuler by remember { mutableStateOf(false) }
    var rulerTouch by remember { mutableIntStateOf(0) }
    val speedFocus = remember { FocusRequester() }
    val rulerFocus = remember { FocusRequester() }
    val showStats by PlayerController.showStats.collectAsStateWithLifecycle()
    val caption by PlayerController.caption.collectAsStateWithLifecycle()
    val settings by Prefs.state.collectAsStateWithLifecycle()

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val relatedFocus = remember { FocusRequester() }
    // "Nhiều video hơn" remembers where it was left when the overlay hides and comes back.
    val relatedState = rememberLazyListState()
    var relatedIndex by remember { mutableIntStateOf(0) }
    var inRelated by remember { mutableStateOf(false) }
    var skipShow by remember { mutableStateOf(false) }
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
        inRelated = false
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
    LaunchedEffect(ui.video?.url?.let(::videoKey)) {
        // Every new video (search, Trang chủ, "Nhiều video hơn", autoplay) starts full screen.
        skipShow = false
        hide()
        relatedIndex = 0
        relatedState.scrollToItem(0)
    }
    LaunchedEffect(ui.error) { if (ui.error != null) show(FocusGoal.RETRY) else if (overlay) show(FocusGoal.PLAY) }

    // The speed slider closes 3 s after the last key press on it; the remote goes back to the button.
    LaunchedEffect(speedRuler, rulerTouch) {
        if (speedRuler) {
            delay(60)
            runCatching { rulerFocus.requestFocus() }
            delay(3_000)
            speedRuler = false
        }
    }
    LaunchedEffect(speedRuler) {
        if (!speedRuler && overlay) {
            delay(60)
            runCatching { speedFocus.requestFocus() }
        }
    }
    // Another video: no slider.
    LaunchedEffect(ui.video?.url) { speedRuler = false }

    // Auto-hide while playing.
    LaunchedEffect(overlay, lastInput, ui.isPlaying, qualityDialog, ccDialog, ui.error, inRelated, speedRuler) {
        if (overlay && ui.isPlaying && !qualityDialog && !ccDialog && !speedRuler && ui.error == null) {
            delay(if (inRelated) HIDE_RELATED_MS else HIDE_AFTER_MS)
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
            speedRuler -> speedRuler = false
            ccDialog -> ccDialog = false
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

        // Vietnamese subtitles, moved up while the controls are showing.
        val line = caption
        if (PlayerController.showsText(settings.vietnamese) && !line.isNullOrBlank() && ui.error == null) {
            Text(
                line, color = Color.White, fontSize = 26.sp, lineHeight = 34.sp, textAlign = TextAlign.Center,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 80.dp)
                    .padding(bottom = if (overlay) 260.dp else 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        if (showStats && ui.error == null && ui.quality?.audioOnly != true) {
            StatsOverlay(Modifier.align(Alignment.TopStart).padding(start = TvEdge, top = 110.dp))
        }

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
                relatedState = relatedState,
                relatedIndex = relatedIndex,
                onRelatedIndex = { i -> relatedIndex = i },
                onRelatedRowFocus = { has -> inRelated = has },
                retryFocus = retryFocus,
                onSeek = { forward -> seekBy(forward) },
                onQuality = { qualityDialog = true },
                onCaptions = { ccDialog = true },
                speedRuler = speedRuler,
                speedFocus = speedFocus,
                rulerFocus = rulerFocus,
                onSpeedHold = { speedRuler = true; touch() },
                onRulerTouch = { rulerTouch++; touch() },
                onRulerClose = { speedRuler = false },
                onPlay = { v ->
                    // Hide "Nhiều video hơn" at once and play the pick full screen.
                    if (videoKey(v.url) != ui.video?.url?.let(::videoKey)) skipShow = true
                    hide()
                    onPlay(v)
                },
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
    if (ccDialog) {
        CaptionsDialog(onDismiss = {
            ccDialog = false
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
    relatedState: LazyListState,
    relatedIndex: Int,
    onRelatedIndex: (Int) -> Unit,
    onRelatedRowFocus: (Boolean) -> Unit,
    retryFocus: FocusRequester,
    onSeek: (Boolean) -> Unit,
    onQuality: () -> Unit,
    onCaptions: () -> Unit,
    speedRuler: Boolean,
    speedFocus: FocusRequester,
    rulerFocus: FocusRequester,
    onSpeedHold: () -> Unit,
    onRulerTouch: () -> Unit,
    onRulerClose: () -> Unit,
    onPlay: (Video) -> Unit,
    onClose: () -> Unit,
) {
    val c = Luc.colors
    val v = ui.video ?: return
    val loop by PlayerController.loopVideo.collectAsStateWithLifecycle()
    val settings by Prefs.state.collectAsStateWithLifecycle()
    // Chế độ Trẻ em: no following (a way out of the kids' list).
    val kids by vn.lucbao.tv.kids.KidsStore.active.collectAsStateWithLifecycle()
    val live = v.live || ui.details?.live == true
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
                // Speed slider: left half, just above the time bar.
                if (speedRuler && !live) {
                    SpeedRuler(
                        speed = ui.speed,
                        focusRequester = rulerFocus,
                        onTouch = onRulerTouch,
                        onClose = onRulerClose,
                        modifier = Modifier.padding(start = TvEdge, bottom = 6.dp).fillMaxWidth(0.5f)
                    )
                }
                if (duration > 0 && !v.live) {
                    SeekRow(position, buffered, duration, onSeek)
                } else if (v.live) {
                    Text(
                        "● TRỰC TIẾP", color = Color(0xFFFF6B6B), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = TvEdge)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = TvEdge)
                        .padding(top = 14.dp),
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
                    Spacer(Modifier.width(28.dp))
                    TvButton("Phụ đề / Thuyết minh", LucIcons.Subtitles, selected = settings.vietnamese > 0) { onCaptions() }
                    if (!live) {
                        SpeedButton(
                            text = speedButtonText(ui.speed),
                            selected = ui.speed != 1f,
                            focusRequester = speedFocus,
                            onTap = { PlayerController.setSpeed(nextSpeed(PlayerController.ui.value.speed)) },
                            onHold = onSpeedHold,
                        )
                    }
                    TvButton("Lặp lại", LucIcons.RepeatOne, selected = loop) { PlayerController.setLoopVideo(!loop) }
                    TvButton(ui.quality?.label ?: "Chất lượng", LucIcons.Tune) { onQuality() }
                    // Kids mode keeps the grown-ups' favourites and follows untouched.
                    if (!kids) TvButton(
                        if (ui.favorite) "Đã thích" else "Yêu thích", LucIcons.Heart, selected = ui.favorite
                    ) { PlayerController.toggleFavorite() }
                    if (channelUrl != null && !kids) {
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
                val related = ui.related.take(30)
                val target = relatedIndex.coerceIn(0, related.lastIndex)
                LazyRow(
                    modifier = Modifier.onFocusChanged { onRelatedRowFocus(it.hasFocus) },
                    state = relatedState,
                    contentPadding = PaddingValues(start = TvEdge, end = TvEdge, top = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(related, key = { i, r -> "$i-${r.url}" }) { i, r ->
                        Box(Modifier.onFocusChanged { if (it.hasFocus) onRelatedIndex(i) }) {
                            TvCard(
                                r, onClick = { onPlay(r) }, width = 168.dp,
                                focusRequester = if (i == target) relatedFocus else null
                            )
                        }
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
    vn.lucbao.tv.ui.TvGlassDialog(onDismiss = onDismiss) {
        Column(
            Modifier
                .width(340.dp)
                .tvGlass()
                .padding(20.dp)
        ) {
            Text("Chất lượng video", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (ui.choices.isEmpty()) {
                Text("Đang tải…", color = c.muted, fontSize = 14.sp)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(280.dp)) {
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
            val statsOn by PlayerController.showStats.collectAsStateWithLifecycle()
            Text(
                "Mạng yếu thì video tự giảm độ nét một chút, mạng ổn lại tự nét như mức đã chọn.",
                color = c.muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
            )
            TvButton(
                if (statsOn) "Ẩn thông số video" else "Hiện thông số video", LucIcons.Tune, selected = statsOn,
                modifier = Modifier.fillMaxWidth()
            ) { PlayerController.setShowStats(!statsOn) }
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { first.requestFocus() }
    }
}

/**
 * "Tốc độ" button. OK pressed and let go: the next speed. OK held for 1.5 s: [onHold]
 * (the speed slider), and letting go then does nothing more.
 */
@Composable
private fun SpeedButton(
    text: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    onTap: () -> Unit,
    onHold: () -> Unit,
) {
    val c = Luc.colors
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var held by remember { mutableStateOf(false) }
    Text(
        text,
        color = if (focused) c.onPrimary else if (selected) c.primary else Color.White,
        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                // Back from the slider: its key-up went there, start clean.
                if (it.isFocused) {
                    holdJob?.cancel()
                    holdJob = null
                    held = false
                }
            }
            .onKeyEvent { e ->
                val center = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (!center) return@onKeyEvent false
                when (e.type) {
                    KeyEventType.KeyDown -> {
                        if (holdJob == null && !held) {
                            holdJob = scope.launch {
                                delay(1_500)
                                held = true
                                onHold()
                            }
                        }
                    }
                    KeyEventType.KeyUp -> {
                        holdJob?.cancel()
                        holdJob = null
                        if (!held) onTap()
                        held = false
                    }
                }
                true
            }
            .focusable()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    focused -> c.primary
                    selected -> c.primary.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.10f)
                }
            )
            .padding(horizontal = 16.dp, vertical = 9.dp)
    )
}

/**
 * Speed slider: a straight ruler with a mark per speed (longer at 1x, 2x, 3x…), only the
 * current speed written. ◀ ▶ change the speed at once; OK closes it.
 */
@Composable
private fun SpeedRuler(
    speed: Float,
    focusRequester: FocusRequester,
    onTouch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val c = Luc.colors
    val measurer = rememberTextMeasurer()
    val index = speedIndex(speed)
    // OK closes the slider on key-up, but only for a press that started here (the key-up of
    // the long press that opened it also lands here).
    var centerDown by remember { mutableStateOf(false) }
    Canvas(
        modifier
            .height(64.dp)
            .focusRequester(focusRequester)
            .onKeyEvent { e ->
                val center = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (center) {
                    // Remotes repeat OK while held: only a fresh press counts.
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) centerDown = true
                    else if (e.type == KeyEventType.KeyUp) {
                        if (centerDown) onClose()
                        centerDown = false
                    }
                    return@onKeyEvent true
                }
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> {
                        val i = (speedIndex(PlayerController.ui.value.speed) - 1).coerceAtLeast(0)
                        PlayerController.setSpeed(SPEEDS[i]); onTouch(); true
                    }
                    Key.DirectionRight -> {
                        val i = (speedIndex(PlayerController.ui.value.speed) + 1).coerceAtMost(SPEEDS.lastIndex)
                        PlayerController.setSpeed(SPEEDS[i]); onTouch(); true
                    }
                    Key.DirectionUp, Key.DirectionDown -> { onTouch(); true } // stay on the slider
                    else -> false
                }
            }
            .focusable()
    ) {
        val w = size.width
        val base = size.height - 8.dp.toPx()
        val step = w / SPEEDS.lastIndex
        val shade = Color.Black.copy(alpha = 0.45f)
        fun mark(x: Float, top: Float, bottom: Float, color: Color, width: Float, cap: StrokeCap = StrokeCap.Butt) {
            drawLine(shade, Offset(x + 1f, top + 1.5f), Offset(x + 1f, bottom + 1.5f), width + 1.5f, cap)
            drawLine(color, Offset(x, top), Offset(x, bottom), width, cap)
        }
        drawLine(Color.White.copy(alpha = 0.6f), Offset(0f, base), Offset(w, base), 1.5.dp.toPx())
        SPEEDS.forEachIndexed { i, rate ->
            val x = i * step
            val major = rate == rate.toInt().toFloat()
            mark(
                x, base - (if (major) 16.dp else 9.dp).toPx(), base,
                Color.White.copy(alpha = if (major) 0.95f else 0.7f), (if (major) 2.dp else 1.4.dp).toPx()
            )
            if (i < SPEEDS.lastIndex) mark(x + step / 2, base - 5.dp.toPx(), base, Color.White.copy(alpha = 0.4f), 1.dp.toPx())
        }
        val x = index * step
        mark(x, base - 26.dp.toPx(), base + 4.dp.toPx(), c.primary, 4.dp.toPx(), StrokeCap.Round)
        val label = measurer.measure(
            speedText(SPEEDS[index]),
            TextStyle(
                color = c.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1f), 4f)
            )
        )
        val lx = (x - label.size.width / 2f).coerceIn(0f, (w - label.size.width).coerceAtLeast(0f))
        drawText(label, topLeft = Offset(lx, 0f))
    }
}

/** "Thông số video": what is really playing and how well the network keeps up. */
@Composable
private fun StatsOverlay(modifier: Modifier) {
    var st by remember { mutableStateOf(PlayerStats()) }
    LaunchedEffect(Unit) {
        while (true) {
            st = PlayerController.stats()
            delay(1_000)
        }
    }
    fun mbps(bits: Long) = if (bits <= 0) "—" else String.format(java.util.Locale.ROOT, "%.1f Mb/s", bits / 1_000_000.0).replace('.', ',')
    val playing = if (st.width > 0 && st.height > 0) {
        "${st.width}×${st.height}" + (if (st.fps > 0) " · ${st.fps}fps" else "") +
            (st.codec?.let { " · " + it.substringBefore('.') } ?: "")
    } else "—"
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        listOf(
            "Đang phát" to playing,
            "Mức đã chọn" to (st.cap ?: "—"),
            "Độ nặng video" to mbps(st.videoBitrate.toLong()),
            "Tốc độ mạng" to mbps(st.network),
            "Đã tải trước" to "${st.bufferedMs / 1000} giây",
            "Số lần phải chờ tải" to st.stalls.toString(),
        ).forEach { (k, v) ->
            Row {
                Text("$k: ", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                Text(v, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Phụ đề tiếng Việt / thuyết minh, the same 4 modes as on the phone. */
@Composable
private fun CaptionsDialog(onDismiss: () -> Unit) {
    val c = Luc.colors
    val context = LocalContext.current
    val settings by Prefs.state.collectAsStateWithLifecycle()
    val info by PlayerController.captionInfo.collectAsStateWithLifecycle()
    val voice by VoiceOver.state.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    vn.lucbao.tv.ui.TvGlassDialog(onDismiss = onDismiss) {
        Column(
            Modifier
                .width(440.dp)
                .tvGlass()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Dịch sang tiếng Việt", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Dùng phụ đề của video, YouTube tự dịch nếu video không có sẵn tiếng Việt. Áp dụng cho cả các video sau.",
                color = c.muted, fontSize = 12.sp, lineHeight = 16.sp
            )
            listOf(
                0 to "Tắt",
                1 to "Phụ đề tiếng Việt",
                2 to "Phụ đề + thuyết minh",
                3 to "Chỉ thuyết minh (không hiện chữ)",
            ).forEach { (mode, label) ->
                val on = settings.vietnamese == mode
                TvButton(
                    label, if (on) LucIcons.Check else null, selected = on, modifier = Modifier.fillMaxWidth(),
                    focusRequester = if (on) first else null
                ) { PlayerController.setVietnamese(mode) }
            }
            if (settings.vietnamese > 0) {
                val status = when (info.status) {
                    CaptionStatus.LOADING -> "Đang tải phụ đề…"
                    CaptionStatus.READY -> if (info.translated) "Phụ đề được YouTube dịch tự động." else "Video có sẵn phụ đề tiếng Việt."
                    CaptionStatus.NONE -> "Video này không có phụ đề nên chưa dịch được."
                    CaptionStatus.ERROR -> "Chưa tải được phụ đề. Chọn lại để thử lần nữa."
                    CaptionStatus.UNSUPPORTED -> "Bộ phát YouTube đang được cập nhật để hỗ trợ tính năng này. Thử lại sau ít phút."
                    CaptionStatus.OFF -> "Phụ đề sẽ hiện khi video bắt đầu phát."
                }
                Text(status, color = c.muted, fontSize = 12.sp)
            }
            // No way out to the TV's system settings from Chế độ Trẻ em.
            if (!vn.lucbao.tv.kids.KidsStore.active.value && PlayerController.speaks(settings.vietnamese) &&
                (voice == VoiceOver.State.NO_VOICE || voice == VoiceOver.State.NO_ENGINE)
            ) {
                Text(
                    "TV chưa có giọng đọc tiếng Việt. Vào cài đặt Chuyển văn bản thành giọng nói của TV và tải giọng Tiếng Việt.",
                    color = c.text, fontSize = 12.sp, lineHeight = 16.sp
                )
                TvButton("Mở cài đặt giọng đọc", LucIcons.Download) { VoiceOver.openVoiceSettings(context) }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { first.requestFocus() }
    }
}
