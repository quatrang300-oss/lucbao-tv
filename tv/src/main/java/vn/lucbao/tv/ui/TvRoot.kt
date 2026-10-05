package vn.lucbao.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.lucbao.tv.data.Prefs
import vn.lucbao.tv.data.Video
import vn.lucbao.tv.player.PlayerController
import vn.lucbao.tv.ui.screens.TvFollowing
import vn.lucbao.tv.ui.screens.TvHome
import vn.lucbao.tv.ui.screens.TvLibrary
import vn.lucbao.tv.ui.screens.TvOnboarding
import vn.lucbao.tv.ui.screens.TvPlayer
import vn.lucbao.tv.ui.screens.TvSearch
import vn.lucbao.tv.ui.screens.TvSettings
import vn.lucbao.tv.ui.theme.Luc
import vn.lucbao.tv.ui.theme.LucIcons
import vn.lucbao.tv.ui.theme.LucTheme

enum class TvScreen { SEARCH, HOME, FOLLOWING, LIBRARY, SETTINGS }

/** Read by screens before grabbing focus, so nothing under the full-screen player takes it. */
internal object TvState {
    @Volatile
    var playerOpen: Boolean = false
}

/** Requests focus unless the player covers the screen. */
internal fun FocusRequester.focusIfVisible() {
    if (!TvState.playerOpen) runCatching { requestFocus() }
}

@Composable
fun TvRoot(vm: AppViewModel, voiceSignal: Int) {
    val settings by Prefs.state.collectAsStateWithLifecycle()
    LucTheme(dark = true, font = settings.font) {
        if (!settings.onboarded) {
            TvOnboarding(onDone = { Prefs.update { it.copy(onboarded = true) } })
            return@LucTheme
        }
        val c = Luc.colors
        val ui by PlayerController.ui.collectAsStateWithLifecycle()
        var screen by rememberSaveable { mutableStateOf(TvScreen.HOME) }
        var playerOpen by remember { mutableStateOf(false) }
        var voicePending by remember { mutableStateOf(false) }
        SideEffect { TvState.playerOpen = playerOpen && ui.video != null }
        val contentFocus = remember { FocusRequester() }
        var wasOpen by remember { mutableStateOf(false) }
        LaunchedEffect(playerOpen) {
            // Back from the player: give the remote a place to land again.
            if (wasOpen && !playerOpen) {
                delay(80)
                runCatching { contentFocus.requestFocus() }
            }
            wasOpen = playerOpen
        }

        val play: (Video) -> Unit = { v ->
            PlayerController.play(v)
            playerOpen = true
        }
        LaunchedEffect(voiceSignal) {
            if (voiceSignal > 0) {
                if (playerOpen) {
                    playerOpen = false
                    PlayerController.stop()
                }
                screen = TvScreen.SEARCH
                voicePending = true
            }
        }

        Box(Modifier.fillMaxSize().background(c.surface)) {
            Row(Modifier.fillMaxSize()) {
                Rail(screen) { screen = it }
                Box(Modifier.weight(1f).fillMaxHeight().focusRequester(contentFocus).focusGroup()) {
                    when (screen) {
                        TvScreen.HOME -> TvHome(vm, onPlay = play)
                        TvScreen.SEARCH -> TvSearch(vm, voicePending, onVoiceHandled = { voicePending = false }, onPlay = play)
                        TvScreen.FOLLOWING -> TvFollowing(vm, onPlay = play)
                        TvScreen.LIBRARY -> TvLibrary(onPlay = play)
                        TvScreen.SETTINGS -> TvSettings()
                    }
                }
            }
            if (playerOpen && ui.video != null) {
                TvPlayer(
                    ui = ui,
                    onClose = {
                        playerOpen = false
                        PlayerController.stop()
                    },
                    onPlay = play,
                )
            }
        }
        BackHandler(enabled = !(playerOpen && ui.video != null) && screen != TvScreen.HOME) { screen = TvScreen.HOME }
    }
}

@Composable
private fun Rail(current: TvScreen, onSelect: (TvScreen) -> Unit) {
    val items = listOf(
        TvScreen.SEARCH to LucIcons.Search,
        TvScreen.HOME to LucIcons.Home,
        TvScreen.FOLLOWING to LucIcons.PersonAdd,
        TvScreen.LIBRARY to LucIcons.Library,
        TvScreen.SETTINGS to LucIcons.Tune,
    )
    Column(
        Modifier
            .width(68.dp)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color(0xF2040F0B), Color(0x00040F0B))))
            .padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (screen, icon) -> RailItem(icon, screen == current) { onSelect(screen) } }
    }
}

@Composable
private fun RailItem(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = Luc.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "rail")
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(13.dp))
            .background(
                when {
                    focused -> c.primary
                    selected -> c.primary.copy(alpha = 0.16f)
                    else -> Color.Transparent
                }
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, null,
            tint = when {
                focused -> c.onPrimary
                selected -> c.primary
                else -> c.muted
            },
            modifier = Modifier.size(22.dp)
        )
    }
}
