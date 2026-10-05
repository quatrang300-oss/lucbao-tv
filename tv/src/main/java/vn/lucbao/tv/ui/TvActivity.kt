package vn.lucbao.tv.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import vn.lucbao.tv.player.PlayerController
import vn.lucbao.tv.update.Updater

class TvActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /** Bumped when the remote's search / microphone key is pressed. */
    private val voiceSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlayerController.init(this)
        setContent { TvRoot(vm, voiceSignal.intValue) }
    }

    override fun onResume() {
        super.onResume()
        // A downloaded app update is installed while the app is on screen (TVs hide notifications).
        Updater.installPendingIfAny(this)
    }

    override fun onStop() {
        super.onStop()
        // No background playback on TV: pause when the user leaves the app.
        if (!isChangingConfigurations) PlayerController.exo.pause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST) {
            voiceSignal.intValue++
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
