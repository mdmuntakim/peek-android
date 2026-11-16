package io.github.mdmuntakim.peek

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.mdmuntakim.peek.ui.PeekApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialQuery = queryFromIntent(intent)
        setContent { PeekApp(initialQuery = initialQuery) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        queryFromIntent(intent)?.let { q -> PeekBus.submitQuery(q) }
    }

    private fun queryFromIntent(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != "peek" || data.host != "search") return null
        return data.getQueryParameter("q")?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/** Tiny in-app event channel for hand-offs from intents into compose state. */
object PeekBus {
    private val queue = ArrayDeque<String>()

    fun submitQuery(q: String) {
        synchronized(queue) { queue.addLast(q) }
    }

    fun poll(): String? = synchronized(queue) { queue.removeFirstOrNull() }
}
