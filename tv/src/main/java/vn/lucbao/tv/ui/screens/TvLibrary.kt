package vn.lucbao.tv.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.lucbao.tv.data.Channel
import vn.lucbao.tv.data.Library
import vn.lucbao.tv.data.Video
import vn.lucbao.tv.ui.TvButton
import vn.lucbao.tv.ui.TvCard
import vn.lucbao.tv.ui.TvEdge
import vn.lucbao.tv.ui.focusIfVisible
import vn.lucbao.tv.ui.theme.Luc
import vn.lucbao.tv.ui.theme.LucIcons

/** Xem tiếp · Yêu thích · Kênh đang theo dõi. Everything is stored on this TV only. */
@Composable
fun TvLibrary(onPlay: (Video) -> Unit) {
    val c = Luc.colors
    val history by Library.history.collectAsStateWithLifecycle()
    val favorites by Library.favorites.collectAsStateWithLifecycle()
    val channels by Library.channels.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    var unfollow by remember { mutableStateOf<Channel?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var refocus by remember { mutableIntStateOf(0) }
    val actionFocus = remember { FocusRequester() }

    LaunchedEffect(refocus) {
        delay(if (refocus == 0) 100 else 150)
        if (history.isNotEmpty() || favorites.isNotEmpty()) firstFocus.focusIfVisible()
        else actionFocus.focusIfVisible()
    }

    val empty = history.isEmpty() && favorites.isEmpty() && channels.isEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 28.dp, bottom = 40.dp)) {
        item(key = "title") {
          Column {
            Text(
                "Thư viện", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, bottom = 4.dp)
            )
            Text(
                "Lưu trên TV này, không cần tài khoản", color = c.muted, fontSize = 14.sp,
                modifier = Modifier.padding(start = 24.dp)
            )
          }
        }
        if (empty) {
            item(key = "empty") {
                Column(
                    Modifier.fillMaxWidth().padding(top = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(LucIcons.Library, null, tint = c.primary, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Video bạn đã xem và đã thích sẽ hiện ở đây.",
                        color = c.muted, fontSize = 17.sp, textAlign = TextAlign.Center
                    )
                }
            }
        }
        if (history.isNotEmpty()) {
            item(key = "history") {
                Section("Xem tiếp") {
                    LazyRow(
                        contentPadding = PaddingValues(start = 24.dp, end = TvEdge, top = 10.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        itemsIndexed(history.take(60), key = { i, h -> "h$i-${h.video.url}" }) { i, h ->
                            val total = h.video.duration * 1000
                            TvCard(
                                h.video, onClick = { onPlay(h.video) },
                                progress = if (total > 0) h.positionMs.toFloat() / total else null,
                                focusRequester = if (i == 0) firstFocus else null
                            )
                        }
                    }
                }
            }
        }
        if (favorites.isNotEmpty()) {
            item(key = "fav") {
                Section("Yêu thích") {
                    LazyRow(
                        contentPadding = PaddingValues(start = 24.dp, end = TvEdge, top = 10.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        itemsIndexed(favorites, key = { i, f -> "f$i-${f.video.url}" }) { i, f ->
                            TvCard(
                                f.video, onClick = { onPlay(f.video) },
                                focusRequester = if (i == 0 && history.isEmpty()) firstFocus else null
                            )
                        }
                    }
                }
            }
        }
        if (channels.isNotEmpty()) {
            item(key = "channels") {
                Section("Kênh đang theo dõi") {
                    LazyRow(
                        contentPadding = PaddingValues(start = 24.dp, end = TvEdge, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(channels, key = { _, ch -> "c-${ch.url}" }) { i, ch ->
                            TvButton(
                                ch.name.ifBlank { "Kênh" }, LucIcons.Check, selected = true,
                                focusRequester = if (i == 0 && history.isEmpty() && favorites.isEmpty()) actionFocus else null
                            ) { unfollow = ch }
                        }
                    }
                    Text(
                        "Chọn một kênh để bỏ theo dõi", color = c.muted, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 24.dp)
                    )
                }
            }
        }
        if (history.isNotEmpty()) {
            item(key = "clear") {
                Row(Modifier.padding(start = 24.dp, top = 22.dp)) {
                    TvButton("Xoá lịch sử xem", LucIcons.Delete) { confirmClear = true }
                }
            }
        }
    }

    unfollow?.let { ch ->
        Confirm(
            title = "Bỏ theo dõi “${ch.name}”?",
            message = "Video mới của kênh này sẽ không còn hiện ở Trang chủ.",
            yes = "Bỏ theo dõi",
            onYes = { Library.toggleFollow(ch.url, ch.name, ch.avatar); refocus++ },
            onDismiss = { unfollow = null }
        )
    }
    if (confirmClear) {
        Confirm(
            title = "Xoá toàn bộ lịch sử xem?",
            message = "Mục Xem tiếp và gợi ý Dành cho bạn sẽ bắt đầu lại từ đầu.",
            yes = "Xoá",
            onYes = { Library.clearHistory(); refocus++ },
            onDismiss = { confirmClear = false }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            title, color = Luc.colors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp)
        )
        content()
    }
}

/** Two-button confirmation; "Huỷ" has the focus so a stray OK does nothing harmful. */
@Composable
internal fun Confirm(title: String, message: String, yes: String, onYes: () -> Unit, onDismiss: () -> Unit) {
    val c = Luc.colors
    val cancel = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.card)
                .padding(24.dp)
        ) {
            Text(title, color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(message, color = c.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TvButton("Huỷ", focusRequester = cancel) { onDismiss() }
                TvButton(yes) {
                    onYes()
                    onDismiss()
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { cancel.requestFocus() }
    }
}
