package vn.lucbao.tv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import vn.lucbao.tv.data.Library
import vn.lucbao.tv.data.Prefs
import vn.lucbao.tv.engine.EngineManager
import vn.lucbao.tv.player.PlayerController
import vn.lucbao.tv.update.Notifications
import vn.lucbao.tv.update.Updater

class TvApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Library.init(this)
        EngineManager.init(this)
        PlayerController.init(this)
        Notifications.createChannels(this)
        Updater.init(this)
        Updater.schedule(this)
        Updater.requestEngineCheck(this, minIntervalMs = 60 * 60_000L)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).crossfade(true).build()
}
