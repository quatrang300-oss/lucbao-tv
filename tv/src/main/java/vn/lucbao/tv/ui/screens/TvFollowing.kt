package vn.lucbao.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.lucbao.tv.data.Library
import vn.lucbao.tv.data.Video
import vn.lucbao.tv.ui.AppViewModel
import vn.lucbao.tv.ui.FeedState
import vn.lucbao.tv.ui.TvButton
import vn.lucbao.tv.ui.TvCard
import vn.lucbao.tv.ui.TvEdge
import vn.lucbao.tv.ui.focusIfVisible
import vn.lucbao.tv.ui.theme.Luc
import vn.lucbao.tv.ui.theme.LucIcons
import vn.lucbao.tv.ui.tvErrorText

private const val NEW_MS = 3L * 24 * 3600 * 1000

/** Newest videos from the channels followed on this TV. */
@Composable
fun TvFollowing(vm: AppViewModel, onPlay: (Video) -> Unit) {
    val c = Luc.colors
    val feeds by vm.feeds.collectAsStateWithLifecycle()
    val channels by Library.channels.collectAsStateWithLifecycle()
    val feed = feeds[AppViewModel.FOLLOWING] ?: FeedState(loading = channels.isNotEmpty())
    val firstFocus = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    val grid = rememberLazyGridState()

    val nearEnd by remember {
        derivedStateOf {
            val info = grid.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, feed.items.size) { if (nearEnd && feed.next != null) vm.loadMoreOf(AppViewModel.FOLLOWING) }
    LaunchedEffect(feed.items.isNotEmpty()) {
        delay(100)
        if (feed.items.isNotEmpty()) firstFocus.focusIfVisible() else refreshFocus.focusIfVisible()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = TvEdge, top = 28.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Kênh bạn theo dõi", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (channels.isEmpty()) "Chưa theo dõi kênh nào"
                    else "${channels.size} kênh · video mới nhất ở trên cùng",
                    color = c.muted, fontSize = 14.sp
                )
            }
            if (channels.isNotEmpty()) {
                TvButton("Làm mới", LucIcons.Refresh, focusRequester = refreshFocus) { vm.refreshAll() }
            }
        }

        when {
            channels.isEmpty() -> Box(Modifier.fillMaxSize().padding(bottom = 60.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(LucIcons.PersonAdd, null, tint = c.primary, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.size(14.dp))
                    Text(
                        "Khi đang xem video, bấm nút Theo dõi.\nVideo mới của kênh đó sẽ luôn hiện ở đây và ở Trang chủ.",
                        color = c.muted, fontSize = 17.sp, lineHeight = 26.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 520.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Không cần tài khoản — danh sách chỉ lưu trên TV này.",
                        color = c.muted.copy(alpha = 0.7f), fontSize = 14.sp
                    )
                }
            }
            feed.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(bottom = 60.dp), contentAlignment = Alignment.Center) {
                if (feed.loading || !feed.loaded) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                } else {
                    Text(
                        if (feed.error != null) tvErrorText(feed.error) else "Các kênh này chưa có video mới.",
                        color = c.muted, fontSize = 16.sp
                    )
                }
            }
            else -> {
                val now = System.currentTimeMillis()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    state = grid,
                    contentPadding = PaddingValues(start = 24.dp, end = TvEdge, top = 14.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(feed.items, key = { i, v -> "$i-${v.url}" }) { i, v ->
                        TvCard(
                            v, onClick = { onPlay(v) },
                            width = 190.dp,
                            isNew = v.publishedAt > 0 && now - v.publishedAt < NEW_MS,
                            focusRequester = if (i == 0) firstFocus else null
                        )
                    }
                    if (feed.loading) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
