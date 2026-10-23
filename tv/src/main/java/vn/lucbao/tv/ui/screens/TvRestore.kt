package vn.lucbao.tv.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.lucbao.tv.data.Library
import vn.lucbao.tv.ui.TvButton
import vn.lucbao.tv.ui.TvGlassDialog
import vn.lucbao.tv.ui.theme.Luc
import vn.lucbao.tv.ui.theme.LucIcons
import vn.lucbao.tv.ui.tvGlass
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Khôi phục dữ liệu từ điện thoại": the backup file made by Lục Bảo on the phone is sent to the
 * TV (Send Files to TV, USB…); this finds it by itself in Download and on USB drives, so it can be
 * picked with the remote. Data is added to what the TV already has.
 */
@Composable
fun TvRestoreDialog(onDismiss: () -> Unit) {
    val c = Luc.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<File>?>(null) }
    var scanTick by remember { mutableStateOf(0) }
    val first = remember { FocusRequester() }

    val needsPermission = Build.VERSION.SDK_INT in 23..32 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { scanTick++ }
    // Newer TVs hide other apps' files: the system file picker (when the TV has one) still works.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            restore(context, text)
            onDismiss()
        }
    }

    LaunchedEffect(scanTick) {
        if (needsPermission && scanTick == 0) {
            runCatching { permission.launch(Manifest.permission.READ_EXTERNAL_STORAGE) }
            return@LaunchedEffect
        }
        files = withContext(Dispatchers.IO) { findBackups() }
        delay(80)
        runCatching { first.requestFocus() }
    }

    TvGlassDialog(onDismiss = onDismiss) {
        Column(
            Modifier.width(560.dp).tvGlass().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Khôi phục dữ liệu từ điện thoại", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(
                "Trên điện thoại: Lục Bảo › Cài đặt › Sao lưu. Gửi tệp đó sang TV (Send Files to TV hoặc USB) rồi chọn ở đây. " +
                    "Dữ liệu được thêm vào, không mất gì đang có trên TV.",
                color = c.muted, fontSize = 13.sp, lineHeight = 19.sp
            )
            val list = files
            when {
                list == null -> CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp)
                list.isEmpty() -> {
                    Text("Chưa tìm thấy tệp sao lưu nào trong thư mục Download hoặc USB.", color = c.text, fontSize = 14.sp)
                    TvButton("Tìm lại", LucIcons.Refresh, focusRequester = first) { files = null; scanTick++ }
                }
                else -> LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(list) { i, f ->
                        val day = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT).format(Date(f.lastModified()))
                        TvButton(
                            "${f.name}  ·  $day", LucIcons.Download, modifier = Modifier.fillMaxWidth(),
                            focusRequester = if (i == 0) first else null
                        ) {
                            scope.launch {
                                val text = withContext(Dispatchers.IO) { runCatching { f.readText() }.getOrNull() }
                                restore(context, text)
                                onDismiss()
                            }
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 30) {
                TvButton("Chọn tệp bằng trình quản lý tệp của TV", LucIcons.Library) {
                    val ok = runCatching {
                        picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                    }.isSuccess
                    if (!ok) Toast.makeText(context, "TV này không có trình chọn tệp.", Toast.LENGTH_LONG).show()
                }
            }
            TvButton("Đóng", LucIcons.Close) { onDismiss() }
        }
    }
}

private fun restore(context: android.content.Context, text: String?) {
    val msg = runCatching { Library.importBackup(text ?: "") }.fold(
        onSuccess = { n ->
            "Đã khôi phục: ${n.channels} kênh, ${n.favorites} yêu thích, ${n.watchLater} xem sau, ${n.history} video đã xem."
        },
        onFailure = { "Tệp này không phải bản sao lưu của Lục Bảo." },
    )
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
}

/** Backup files (*.json with "lucbao" in the name) in Download and on USB drives, newest first. */
@Suppress("DEPRECATION")
private fun findBackups(): List<File> {
    val roots = buildList {
        val ext = Environment.getExternalStorageDirectory()
        add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
        add(ext)
        // USB drives and SD cards: /storage/XXXX-XXXX
        File("/storage").listFiles()?.forEach { v ->
            if (v.name != "emulated" && v.name != "self") add(v)
        }
        File("/mnt/media_rw").listFiles()?.forEach { add(it) }
    }
    val out = LinkedHashMap<String, File>()
    fun walk(dir: File, depth: Int) {
        if (depth > 3) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (f in children) {
            if (f.isDirectory) {
                if (!f.name.startsWith(".") && f.name != "Android") walk(f, depth + 1)
            } else if (f.name.endsWith(".json", ignoreCase = true) &&
                f.name.contains("lucbao", ignoreCase = true) && f.length() < 20L * 1024 * 1024
            ) {
                out[runCatching { f.canonicalPath }.getOrDefault(f.path)] = f
            }
        }
    }
    roots.forEach { walk(it, 0) }
    return out.values.sortedByDescending { it.lastModified() }
}
