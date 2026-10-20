package vn.lucbao.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import vn.lucbao.tv.data.Format
import vn.lucbao.tv.data.Library
import vn.lucbao.tv.data.Video
import vn.lucbao.tv.data.videoKey
import vn.lucbao.tv.music.MusicItem
import vn.lucbao.tv.music.MusicViewModel
import vn.lucbao.tv.music.toMusicItem
import vn.lucbao.tv.player.PlayerController
import vn.lucbao.tv.ui.TvButton
import vn.lucbao.tv.ui.TvIconButton
import vn.lucbao.tv.ui.focusIfVisible
import vn.lucbao.tv.ui.theme.Luc
import vn.lucbao.tv.ui.theme.LucIcons

private val FILTERS = listOf(
    "music_songs" to "Bài hát",
    "music_videos" to "Video nhạc",
    "music_albums" to "Album",
    "music_playlists" to "Danh sách phát",
)

/**
 * Nhạc on TV: songs on the left (search, Thịnh hành, liked, recent, albums), the music player
 * on the right. Songs play audio only and keep playing while moving around the app.
 */
@Composable
fun TvMusic(onOpenPlayer: () -> Unit) {
    val c = Luc.colors
    val vm: MusicViewModel = viewModel()
    val submitted by vm.submitted.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val trending by vm.trending.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val liked by Library.likedSongs.collectAsStateWithLifecycle()
    val recent by Library.recentSongs.collectAsStateWithLifecycle()
    var keyboard by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { vm.loadTrending() }
    // Opening/closing an album or a search removes the focused row: land on the search box again.
    LaunchedEffect(detail?.id, submitted, keyboard) {
        if (!keyboard) {
            delay(120)
            firstFocus.focusIfVisible()
        }
    }
    BackHandler(enabled = detail != null) { vm.closeDetail() }
    BackHandler(enabled = detail == null && submitted != null) { vm.clearSearch() }

    Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 28.dp, top = 24.dp, bottom = 20.dp)) {
        // ---- Left: what to play
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val d = detail
            when {
                d != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TvButton("Quay lại", LucIcons.Back, focusRequester = firstFocus) { vm.closeDetail() }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (d.subtitle.isNotBlank()) Text(d.subtitle, color = c.muted, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                    val songs = when (d.kind) {
                        "liked" -> liked.map { it.toMusicItem() }
                        "recent" -> recent.map { it.toMusicItem() }
                        else -> d.list.items
                    }
                    val playable = songs.filter { it.playable }
                    if (playable.isNotEmpty()) {
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvButton("Phát tất cả", LucIcons.Play) {
                                PlayerController.playQueue(playable.map { it.toVideo() }, 0, d.title, shuffle = false)
                            }
                            TvButton("Trộn bài", LucIcons.Shuffle) {
                                PlayerController.playQueue(playable.map { it.toVideo() }, -1, d.title, shuffle = true)
                            }
                        }
                    }
                    SongList(
                        songs, d.list.loading && songs.isEmpty(), d.list.error,
                        onNearEnd = { vm.loadMoreDetail() },
                        onPick = { m ->
                            if (m.playable) PlayerController.playQueue(playable.map { it.toVideo() }, playable.indexOf(m), d.title)
                            else vm.openList(m)
                        }
                    )
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SearchBox(submitted, focusRequester = firstFocus) { keyboard = true }
                        if (submitted != null) {
                            Spacer(Modifier.width(10.dp))
                            TvButton("Xoá", LucIcons.Close) { vm.clearSearch() }
                        }
                    }
                    if (submitted != null) {
                        LazyRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(FILTERS, key = { it.first }) { (id, label) ->
                                TvButton(label, null, selected = filter == id) { vm.setFilter(id) }
                            }
                        }
                        val playable = results.items.filter { it.playable }
                        SongList(
                            results.items, results.loading && results.items.isEmpty(), results.error,
                            onNearEnd = { vm.loadMoreResults() },
                            onPick = { m ->
                                if (m.playable) PlayerController.playQueue(playable.map { it.toVideo() }, playable.indexOf(m), "Tìm: $submitted")
                                else vm.openList(m)
                            }
                        )
                    } else {
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvButton("Bài hát đã thích (${liked.size})", LucIcons.Heart) { vm.openLocal("liked", "Bài hát đã thích") }
                            TvButton("Nghe gần đây", LucIcons.History) { vm.openLocal("recent", "Nghe gần đây") }
                        }
                        Text("Thịnh hành", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
                        val chart = trending.items.filter { it.playable }
                        SongList(
                            trending.items, trending.loading && trending.items.isEmpty(), trending.error,
                            onNearEnd = {},
                            onPick = { m ->
                                if (m.playable) PlayerController.playQueue(chart.map { it.toVideo() }, chart.indexOf(m), "Thịnh hành")
                                else vm.openList(m)
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        // ---- Right: the music player
        MusicPanel(Modifier.width(360.dp).fillMaxHeight(), onOpenPlayer)
    }

    if (keyboard) {
        MusicSearchDialog(
            initial = vm.query.value,
            onSearch = { q ->
                keyboard = false
                if (q.isNotBlank()) vm.submit(q)
            },
            onDismiss = { keyboard = false }
        )
    }
}

@Composable
private fun SearchBox(submitted: String?, focusRequester: FocusRequester, onClick: () -> Unit) {
    val c = Luc.colors
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .width(420.dp)
            .height(50.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) c.primary else c.card)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(LucIcons.Search, null, tint = if (focused) c.onPrimary else c.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            submitted ?: "Tìm bài hát, ca sĩ, album…", color = if (focused) c.onPrimary else if (submitted == null) c.muted else c.text,
            fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SongList(
    items: List<MusicItem>,
    loading: Boolean,
    error: String?,
    onNearEnd: () -> Unit,
    onPick: (MusicItem) -> Unit,
) {
    val c = Luc.colors
    when {
        loading -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(40.dp))
        }
        items.isEmpty() -> Text(error ?: "Chưa có bài nào ở đây.", color = c.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 24.dp))
        else -> LazyColumn(
            Modifier.padding(top = 10.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(items, key = { i, m -> "$i-${m.url}" }) { i, m ->
                if (i >= items.size - 4) LaunchedEffect(items.size) { onNearEnd() }
                SongRow(m) { onPick(m) }
            }
        }
    }
}

@Composable
private fun SongRow(m: MusicItem, onClick: () -> Unit) {
    val c = Luc.colors
    val queue by PlayerController.queue.collectAsStateWithLifecycle()
    val playing = queue.current?.let { videoKey(it.url) == videoKey(m.url) } == true
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, label = "song")
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) c.primary else if (playing) c.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = m.thumb, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(c.card)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m.title, color = if (focused) c.onPrimary else if (playing) c.primary else c.text,
                fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val sub = when (m.type) {
                "album" -> "Album · ${m.artist}"
                "playlist" -> "Danh sách phát" + (if (m.count > 0) " · ${m.count} bài" else "")
                "artist" -> "Nghệ sĩ"
                else -> Format.dot(m.artist, if (m.duration > 0) Format.duration(m.duration) else null)
            }
            Text(sub, color = if (focused) c.onPrimary.copy(alpha = 0.8f) else c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Right side: the song playing, controls, sleep timer and the next songs. */
@Composable
private fun MusicPanel(modifier: Modifier, onOpenPlayer: () -> Unit) {
    val c = Luc.colors
    val ui by PlayerController.ui.collectAsStateWithLifecycle()
    val queue by PlayerController.queue.collectAsStateWithLifecycle()
    val sleepAt by PlayerController.sleepAt.collectAsStateWithLifecycle()
    val liked by Library.likedSongs.collectAsStateWithLifecycle()
    var timerDialog by remember { mutableStateOf(false) }
    val song = queue.current.takeIf { queue.music } ?: ui.video?.takeIf { queue.music }
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(c.card.copy(alpha = 0.6f)).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (song == null) {
            Spacer(Modifier.weight(1f))
            Icon(LucIcons.Music, null, tint = c.muted, modifier = Modifier.size(56.dp))
            Text("Chọn một bài để nghe", color = c.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.weight(1f))
            return@Column
        }
        AsyncImage(
            model = song.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(c.surface)
        )
        Text(
            song.title, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp)
        )
        Text(song.channel, color = c.muted, fontSize = 14.sp, maxLines = 1)
        if (ui.loading) Text("Đang tải…", color = c.muted, fontSize = 12.sp)
        Row(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvIconButton(LucIcons.Prev, "Bài trước", size = 42.dp) { PlayerController.previous() }
            TvIconButton(
                if (ui.isPlaying) LucIcons.Pause else LucIcons.Play, if (ui.isPlaying) "Tạm dừng" else "Phát",
                size = 54.dp
            ) { PlayerController.togglePlay() }
            TvIconButton(LucIcons.Next, "Bài sau", size = 42.dp) { PlayerController.next() }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TvIconButton(LucIcons.Shuffle, if (queue.shuffle) "Tắt trộn bài" else "Trộn bài", size = 40.dp) { PlayerController.toggleShuffle() }
            TvIconButton(
                if (queue.repeat == 2) LucIcons.RepeatOne else LucIcons.Repeat,
                when (queue.repeat) { 1 -> "Lặp lại tất cả"; 2 -> "Lặp lại một bài"; else -> "Không lặp" }, size = 40.dp
            ) { PlayerController.cycleRepeat() }
            val isLiked = liked.any { videoKey(it.url) == videoKey(song.url) }
            TvIconButton(if (isLiked) LucIcons.Heart else LucIcons.HeartOutline, if (isLiked) "Bỏ thích" else "Thích", size = 40.dp) {
                Library.toggleLikedSong(song)
            }
            TvIconButton(LucIcons.Radio, "Bài tương tự", size = 40.dp) { PlayerController.startRadio() }
            TvIconButton(LucIcons.Timer, "Hẹn giờ tắt", size = 40.dp) { timerDialog = true }
        }
        val status = buildList {
            if (queue.shuffle) add("Trộn bài")
            when (queue.repeat) { 1 -> add("Lặp tất cả"); 2 -> add("Lặp 1 bài") }
            if (sleepAt == -1L) add("Dừng khi hết bài") else if (sleepAt > 0) add("Hẹn giờ tắt")
        }.joinToString(" · ")
        if (status.isNotEmpty()) Text(status, color = c.primary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        TvButton("Xem video", LucIcons.Video, modifier = Modifier.padding(top = 10.dp)) {
            PlayerController.watchVideo()
            onOpenPlayer()
        }
        // Next songs
        val next = queue.items.drop(queue.index + 1).take(4)
        if (next.isNotEmpty()) {
            Text("Tiếp theo", color = c.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp))
            next.forEachIndexed { k, v ->
                Text(
                    "${k + 1}. ${v.title}", color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    if (timerDialog) {
        Dialog(onDismissRequest = { timerDialog = false }) {
            Column(
                Modifier.width(320.dp).clip(RoundedCornerShape(18.dp)).background(c.card).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Hẹn giờ tắt nhạc", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                val timerFirst = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    delay(80)
                    runCatching { timerFirst.requestFocus() }
                }
                listOf(15 to "15 phút", 30 to "30 phút", 60 to "1 giờ", -1 to "Khi hết bài này", 0 to "Tắt hẹn giờ").forEach { (m, label) ->
                    TvButton(label, LucIcons.Timer, modifier = Modifier.fillMaxWidth(), focusRequester = if (m == 15) timerFirst else null) {
                        PlayerController.setSleepTimer(m)
                        timerDialog = false
                    }
                }
            }
        }
    }
}

/** Typing a music search with the Vietnamese keyboard. */
@Composable
private fun MusicSearchDialog(initial: String, onSearch: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Luc.colors
    var q by remember { mutableStateOf(initial) }
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(18.dp)).background(c.surface).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Tìm nhạc", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TvQueryField(q, "Tên bài hát, ca sĩ…")
            TvKeyboard(q, onQueryChange = { q = it }, onSearch = { onSearch(q) }, firstKey = first)
        }
    }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { first.requestFocus() }
    }
}
