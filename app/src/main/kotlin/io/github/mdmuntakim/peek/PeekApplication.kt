package io.github.mdmuntakim.peek

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.mdmuntakim.peek.engine.Store
import java.io.File

class PeekApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = this
        Store.init(File(filesDir, "engine"))
    }

    companion object {
        /** Application context, safe for starting activities from anywhere. */
        lateinit var appContext: Context
            private set
    }
}

/** Where a hand-off from intents, results or images should land in the UI. */
sealed class PeekEvent {
    data class Search(val query: String) : PeekEvent()
    data class Open(val url: String) : PeekEvent()
    data object SwitchBrowser : PeekEvent()
}

/** Tiny in-app event channel for hand-offs from intents into compose state. */
object PeekBus {
    private val queue = ArrayDeque<PeekEvent>()

    fun submit(event: PeekEvent) {
        synchronized(queue) { queue.addLast(event) }
    }

    fun submitQuery(q: String) = submit(PeekEvent.Search(q))

    fun openUrl(url: String) = submit(PeekEvent.Open(url))

    fun poll(): PeekEvent? = synchronized(queue) { queue.removeFirstOrNull() }
}

/** Open a URL in the system browser (only used from the browser's menu). */
fun openExternally(url: String) {
    if (url.isEmpty()) return
    runCatching {
        PeekApplication.appContext.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
