package vn.lucbao.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Option panels (Phụ đề, Chất lượng, Hẹn giờ…) over a video: no dark layer over the whole
 * screen, and the panel itself is only 20 % dark so the picture stays visible. Text gets a
 * shadow so it stays readable on bright scenes.
 */
@Composable
fun TvGlassDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(
                shadow = Shadow(Color.Black.copy(alpha = 0.9f), Offset(0f, 2f), 6f)
            )
        ) { content() }
    }
}

/** The see-through panel used inside [TvGlassDialog]. */
fun Modifier.tvGlass(): Modifier {
    val shape = RoundedCornerShape(18.dp)
    return this
        .clip(shape)
        .background(Color.Black.copy(alpha = 0.2f))
        .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
}
