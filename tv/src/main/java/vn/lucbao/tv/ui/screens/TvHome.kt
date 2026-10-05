package vn.lucbao.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import vn.lucbao.tv.data.Format
import vn.lucbao.tv.data.Library
import vn.lucbao.tv.data.Video
import vn.lucbao.tv.ui.AppViewModel
import vn.lucbao.tv.ui.FeedState
import vn.lucbao.tv.ui.TvButton
import vn.lucbao.tv.ui.TvEdge
import vn.lucbao.tv.ui.TvRow
import vn.lucbao.tv.ui.focusIfVisible
import vn.lucbao.tv.ui.theme.BrandMark
import vn.lucbao.tv.ui.theme.Luc
import vn.lucbao.tv.ui.theme.LucIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val NEW_MS = 3L * 24 * 3600 * 1000

@Composable
fun TvHome(vm: AppViewModel, onPlay: (Video) -> Unit) {
    val c = Luc.colors
    val kiosks by vm.kiosks.collectAsStateWithLifecycle()
    val feeds by vm.feeds.collectAsStateWithLifecycle()
    val channels by Library.channels.collectAsStateWithLifecycle()
    val forYou = feeds[AppViewModel.FOR_YOU] ?: FeedState(loading = true)
    val featured = forYou.items.firstOrNull()
    val heroFocus = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(featured != null) {
        if (!focusedOnce) {
            delay(100)
            if (featured != null) heroFocus.focusIfVisible() else rowFocus.focusIfVisible()
            focusedOnce = featured != null
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item(key = "top") {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = TvEdge, top = 24.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandMark(26.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "0 QC", color = c.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .border(1.dp, c.primary.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
                Spacer(Modifier.weight(1f))
                Clock()
            }
        }
        item(key = "hero") {
            if (featured != null) {
                Hero(featured, heroFocus, onPlay)
            } else {
                Spacer(Modifier.height(8.dp))
            }
        }
        item(key = "foryou") {
            TvRow(
                "Dành cho bạn", forYou.items.drop(if (featured != null) 1 else 0), onPlay,
                loading = forYou.loading, newWithinMs = NEW_MS,
                onNearEnd = { vm.loadMoreOf(AppViewModel.FOR_YOU) },
                firstFocus = if (featured == null) rowFocus else null,
            )
        }
        if (channels.isNotEmpty()) {
            item(key = "following") {
                val f = feeds[AppViewModel.FOLLOWING] ?: FeedState(loading = true)
                TvRow("Kênh bạn theo dõi", f.items, onPlay, loading = f.loading, newWithinMs = NEW_MS)
            }
        }
        kiosks.forEach { k ->
            item(key = "k-${k.id}") {
                val f = feeds[k.id] ?: FeedState(loading = true)
                TvRow(k.name, f.items, onPlay, loading = f.loading, onNearEnd = { vm.loadMoreOf(k.id) })
            }
        }
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(20_000)
        }
    }
    Text(
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)),
        color = Luc.colors.muted, fontSize = 18.sp
    )
}

@Composable
private fun Hero(v: Video, focus: FocusRequester, onPlay: (Video) -> Unit) {
    val c = Luc.colors
    val channels by Library.channels.collectAsStateWithLifecycle()
    val following = remember(channels, v.channelUrl) { Library.isFollowing(v.channelUrl) }
    Box(
        Modifier
            .padding(start = 24.dp, end = TvEdge)
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.card)
    ) {
        AsyncImage(
            model = v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xEB030F0A), 0.5f to Color(0x8C030F0A), 0.8f to Color.Transparent
                    )
                )
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 22.dp, end = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "NỔI BẬT", color = c.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(c.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
            Text(
                v.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                lineHeight = 30.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.6f)
            )
            Text(
                Format.dot(v.channel, Format.views(v.views), v.uploaded, Format.duration(v.duration)),
                color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                TvButton("Xem ngay", LucIcons.Play, focusRequester = focus) { onPlay(v) }
                val url = v.channelUrl
                if (url != null && v.channel.isNotBlank()) {
                    TvButton(
                        if (following) "Đang theo dõi" else "Theo dõi kênh",
                        if (following) LucIcons.Check else LucIcons.PersonAdd,
                        selected = following
                    ) { Library.toggleFollow(url, v.channel, null) }
                }
            }
        }
    }
}
