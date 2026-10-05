package vn.lucbao.tv.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.lucbao.tv.BuildConfig
import vn.lucbao.tv.data.Format
import vn.lucbao.tv.data.Prefs
import vn.lucbao.tv.engine.EngineManager
import vn.lucbao.tv.ui.TvButton
import vn.lucbao.tv.ui.TvEdge
import vn.lucbao.tv.ui.focusIfVisible
import vn.lucbao.tv.ui.theme.FontChoices
import vn.lucbao.tv.ui.theme.Luc
import vn.lucbao.tv.ui.theme.LucIcons
import vn.lucbao.tv.update.Updater

private val QUALITIES = listOf(2160 to "4K", 1440 to "1440p", 1080 to "1080p", 720 to "720p", 480 to "480p")

fun canAutoUpdate(context: Context): Boolean =
    runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

/** Opens "install unknown apps" for Lục Bảo TV. Some TVs hide this screen: then explain where it is. */
fun openInstallPermission(context: Context) {
    val opened = runCatching {
        context.startActivity(
            Intent(
                AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    if (!opened) {
        Toast.makeText(
            context,
            "Vào Cài đặt của TV → Bảo mật (hoặc Quyền riêng tư) → Nguồn không xác định → bật cho Lục Bảo TV.",
            Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
fun TvSettings() {
    val c = Luc.colors
    val context = LocalContext.current
    val s by Prefs.state.collectAsStateWithLifecycle()
    val engine by EngineManager.loaded.collectAsStateWithLifecycle()
    val lastCheck by Updater.lastCheck.collectAsStateWithLifecycle()
    val status by Updater.status.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val installOk = remember(refresh) { canAutoUpdate(context) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        firstFocus.focusIfVisible()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = TvEdge, top = 28.dp, bottom = 40.dp)
    ) {
        Text("Cài đặt", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Section("Phông chữ") {
            FontChoices.forEachIndexed { i, (name, _) ->
                TvButton(
                    name, if (s.font == i) LucIcons.Check else null, selected = s.font == i,
                    focusRequester = if (i == 0) firstFocus else null
                ) { Prefs.update { it.copy(font = i) } }
            }
        }

        Section("Chất lượng mặc định", "Lục Bảo tự hạ xuống nếu TV không phát mượt mức này.") {
            QUALITIES.forEach { (p, label) ->
                val on = s.qualityWifi == p
                TvButton(label, if (on) LucIcons.Check else null, selected = on) {
                    Prefs.update { it.copy(qualityWifi = p, qualityMobile = p) }
                }
            }
        }

        Section("Tự phát video tiếp theo") {
            TvButton("Bật", if (s.autoplayNext) LucIcons.Check else null, selected = s.autoplayNext) {
                Prefs.update { it.copy(autoplayNext = true) }
            }
            TvButton("Tắt", if (!s.autoplayNext) LucIcons.Check else null, selected = !s.autoplayNext) {
                Prefs.update { it.copy(autoplayNext = false) }
            }
        }

        Section(
            "Tự cập nhật",
            if (installOk) "Đã bật. Lục Bảo TV tự tải và cài bản mới khi TV đang bật mà không phát video."
            else "Chưa bật. Bộ phát YouTube vẫn tự cập nhật, nhưng để cài bản ứng dụng mới cần cho phép một lần."
        ) {
            if (!installOk) {
                TvButton("Cho phép tự cập nhật", LucIcons.Shield) { openInstallPermission(context) }
            }
            TvButton("Kiểm tra ngay", LucIcons.Refresh) {
                Updater.requestEngineCheck(context, 0)
                Toast.makeText(context, "Đang kiểm tra bản cập nhật…", Toast.LENGTH_SHORT).show()
            }
        }

        Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Info("Phiên bản", BuildConfig.VERSION_NAME)
            Info("Bộ phát YouTube", engine.description.ifBlank { "#${engine.version}" })
            Info("Kiểm tra lần cuối", if (lastCheck > 0) Format.ago(lastCheck) else "chưa kiểm tra")
            if (status.isNotBlank()) Info("Trạng thái", status)
            if (!Updater.enabled) Info("Lưu ý", "Bản build này không có link cập nhật")
        }
        Text(
            "Lục Bảo TV không có quảng cáo, không cần tài khoản. Lịch sử, yêu thích và kênh theo dõi chỉ lưu trên TV này.",
            color = c.muted, fontSize = 13.sp, lineHeight = 19.sp,
            modifier = Modifier.padding(top = 18.dp).widthIn(max = 620.dp)
        )
    }
}

@Composable
private fun Section(title: String, note: String? = null, content: @Composable () -> Unit) {
    val c = Luc.colors
    Column(Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        if (note != null) {
            Text(
                note, color = c.muted, fontSize = 13.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(top = 2.dp).widthIn(max = 640.dp)
            )
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun Info(label: String, value: String) {
    val c = Luc.colors
    Row {
        Text("$label: ", color = c.muted, fontSize = 14.sp)
        Text(value, color = c.text, fontSize = 14.sp)
    }
}
