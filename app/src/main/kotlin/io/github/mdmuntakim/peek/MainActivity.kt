package io.github.mdmuntakim.peek

import android.content.Intent
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
        eventFromIntent(intent)?.let { PeekBus.submit(it) }
    }

    private fun queryFromIntent(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != "peek" || data.host != "search") return null
        return data.getQueryParameter("q")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun eventFromIntent(intent: Intent?): PeekEvent? {
        val data = intent?.data ?: return null
        if (data.scheme != "peek") return null
        return when (data.host) {
            "search" -> data.getQueryParameter("q")?.trim()
                ?.takeIf { it.isNotEmpty() }?.let { PeekEvent.Search(it) }
            "open" -> data.getQueryParameter("url")?.trim()
                ?.takeIf { it.isNotEmpty() }?.let { PeekEvent.Open(it) }
            else -> null
        }
    }
}
