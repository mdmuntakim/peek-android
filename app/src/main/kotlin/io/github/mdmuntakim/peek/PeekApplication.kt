package io.github.mdmuntakim.peek

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri

class PeekApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = this
    }

    companion object {
        /** Application context, safe for starting activities from anywhere. */
        lateinit var appContext: Context
            private set
    }
}

/** Open a URL in the user's browser, never crashing when nothing can handle it. */
fun openUrl(url: String) {
    if (url.isEmpty()) return
    runCatching {
        PeekApplication.appContext.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
