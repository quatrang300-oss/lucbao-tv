package vn.lucbao.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import vn.lucbao.tv.ui.TvButton
import vn.lucbao.tv.ui.theme.LeafLogo
import vn.lucbao.tv.ui.theme.Luc
import vn.lucbao.tv.ui.theme.LucIcons
import vn.lucbao.tv.ui.theme.Wordmark

/** First start: what Lục Bảo TV is, plus the one permission that makes updates hands-free. */
@Composable
fun TvOnboarding(onDone: () -> Unit) {
    val c = Luc.colors
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val installOk = remember(refresh) { canAutoUpdate(context) }
    val start = remember { FocusRequester() }
    val allow = remember { FocusRequester() }

    LaunchedEffect(installOk) {
        delay(120)
        runCatching { if (installOk) start.requestFocus() else allow.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF0E3B28), c.surface), radius = 1400f))
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LeafLogo(64.dp)
                    Spacer(Modifier.width(16.dp))
                    Wordmark(48.dp, textColor = c.text, accent = c.primary)
                }
                Text(
                    "YouTube trên TV, không một quảng cáo.",
                    color = c.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 22.dp)
                )
                Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Feature(LucIcons.Shield, "Không quảng cáo lúc mở, lúc tạm dừng hay khi tìm kiếm")
                    Feature(LucIcons.Play, "Chất lượng tới 4K, tự chọn mức TV phát mượt")
                    Feature(LucIcons.Mic, "Tìm bằng giọng nói, không cần gõ chữ")
                    Feature(LucIcons.PersonAdd, "Theo dõi kênh không cần tài khoản")
                    Feature(LucIcons.Refresh, "Tự cập nhật — cài một lần là xong")
                }
            }
            Spacer(Modifier.width(48.dp))
            Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!installOk) {
                    Text("Bước 1", color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Cho phép Lục Bảo TV tự cài bản cập nhật. Trong màn hình hiện ra, bật công tắc rồi bấm Back để quay lại.",
                        color = c.muted, fontSize = 15.sp, lineHeight = 22.sp
                    )
                    TvButton("Cho phép tự cập nhật", LucIcons.Shield, focusRequester = allow) {
                        openInstallPermission(context)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Bước 2", color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("✓ Đã bật tự cập nhật", color = c.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                TvButton("Bắt đầu xem", LucIcons.Play, focusRequester = start) { onDone() }
                if (!installOk) {
                    Text(
                        "Có thể bỏ qua bước 1 — làm sau trong Cài đặt.",
                        color = c.muted.copy(alpha = 0.7f), fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    val c = Luc.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = c.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = c.text.copy(alpha = 0.9f), fontSize = 16.sp)
    }
}
