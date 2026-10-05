package vn.lucbao.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import vn.lucbao.api.ErrorKind
import vn.lucbao.tv.data.Format
import vn.lucbao.tv.data.Video
import vn.lucbao.tv.ui.theme.Luc

/** Safe-area padding recommended for TVs (overscan). */
val TvEdge = 40.dp

fun tvErrorText(kind: Int?): String = when (kind) {
    ErrorKind.NETWORK -> "Không có kết nối mạng. Kiểm tra Wi-Fi rồi thử lại."
    ErrorKind.BROKEN -> "YouTube vừa thay đổi. Lục Bảo đang tự cập nhật bản sửa — thử lại sau ít phút nhé."
    ErrorKind.UNAVAILABLE -> "Video này không còn xem được."
    ErrorKind.AGE_RESTRICTED -> "Video giới hạn độ tuổi nên chưa phát được."
    ErrorKind.GEO_BLOCKED -> "Video không xem được ở quốc gia của bạn."
    ErrorKind.PRIVATE -> "Đây là video riêng tư."
    ErrorKind.BOT_CHECK -> "YouTube đang tạm chặn. Thử lại sau ít phút."
    ErrorKind.PAID -> "Video này yêu cầu trả phí."
    ErrorKind.NOT_STARTED_YET -> "Buổi phát trực tiếp / công chiếu chưa bắt đầu."
    else -> "Có lỗi xảy ra. Thử lại nhé."
}

/** Button that lights up green when the remote's focus is on it. */
@Composable
fun TvButton(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val c = Luc.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "btn")
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    focused -> c.primary
                    selected -> c.primary.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.10f)
                }
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fg = if (focused) c.onPrimary else if (selected) c.primary else Color.White
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Round icon button (player controls). */
@Composable
fun TvIconButton(
    icon: ImageVector,
    desc: String,
    size: Dp = 48.dp,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val c = Luc.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "ib")
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(if (focused) c.primary else Color.White.copy(alpha = 0.14f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = if (focused) c.onPrimary else Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

/** Video card: grows and gets a green border when focused. */
@Composable
fun TvCard(
    v: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 196.dp,
    isNew: Boolean = false,
    progress: Float? = null,
    focusRequester: FocusRequester? = null,
) {
    val c = Luc.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "card")
    Column(
        modifier
            .width(width)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(c.card)
                .then(if (focused) Modifier.border(3.dp, c.primary, RoundedCornerShape(12.dp)) else Modifier)
        ) {
            AsyncImage(
                model = v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            val badge = if (v.live) "TRỰC TIẾP" else Format.duration(v.duration)
            if (badge.isNotEmpty()) {
                Text(
                    badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (v.live) Color(0xFFD64545) else Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            if (isNew) {
                Text(
                    "MỚI", color = c.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.primary)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
            if (progress != null && progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(c.primary)
                )
            }
        }
        Text(
            v.title, color = if (focused) c.text else c.text.copy(alpha = 0.75f),
            fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp)
        )
        Text(
            Format.dot(v.channel, v.uploaded), color = c.muted.copy(alpha = if (focused) 1f else 0.75f),
            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** A titled horizontal row of cards that asks for more when the end is near. */
@Composable
fun TvRow(
    title: String,
    items: List<Video>,
    onPlay: (Video) -> Unit,
    loading: Boolean = false,
    newWithinMs: Long = 0,
    onNearEnd: (() -> Unit)? = null,
    firstFocus: FocusRequester? = null,
) {
    val c = Luc.colors
    val state = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, items.size) { if (nearEnd) onNearEnd?.invoke() }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(
            title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
        )
        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(150.dp).padding(start = 24.dp), contentAlignment = Alignment.CenterStart) {
                if (loading) CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                else Text("Chưa có video", color = c.muted, fontSize = 13.sp)
            }
        } else {
            val now = System.currentTimeMillis()
            LazyRow(
                state = state,
                contentPadding = PaddingValues(start = 24.dp, end = TvEdge, top = 10.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(items, key = { i, v -> "$i-${v.url}" }) { i, v ->
                    TvCard(
                        v, onClick = { onPlay(v) },
                        isNew = newWithinMs > 0 && v.publishedAt > 0 && now - v.publishedAt < newWithinMs,
                        focusRequester = if (i == 0) firstFocus else null
                    )
                }
            }
        }
    }
}
