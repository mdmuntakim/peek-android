package io.github.mdmuntakim.peek.ui

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import io.github.mdmuntakim.peek.PeekBus
import io.github.mdmuntakim.peek.PeekEvent
import io.github.mdmuntakim.peek.R
import io.github.mdmuntakim.peek.ui.theme.PeekTheme
import io.github.mdmuntakim.peek.ui.theme.ThemeMode
import io.github.mdmuntakim.peek.ui.theme.ThemeStore

private data class Tab(val icon: Int, val label: String)

private val TABS = listOf(
    Tab(R.drawable.ic_tab_search, "Search"),
    Tab(R.drawable.ic_tab_browser, "Browser"),
    Tab(R.drawable.ic_tab_images, "Images"),
    Tab(R.drawable.ic_tab_settings, "Settings"),
)

@Composable
fun PeekApp(initialQuery: String? = null) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf<ThemeMode>(ThemeMode.SYSTEM) }
    LaunchedEffect(Unit) {
        mode = ThemeStore.load(context)
        initialQuery?.let { PeekBus.submitQuery(it) }
    }

    PeekTheme(mode = mode) {
        var tab by rememberSaveable { mutableStateOf(0) }
        var searchReq by rememberBusState<PeekEvent.Search>()
        var openReq by rememberBusState<PeekEvent.Open>()

        // Single owner of the bus: search requests route to the Search tab,
        // open requests to the built-in Browser.
        LaunchedEffect(Unit) {
            while (true) {
                when (val e = PeekBus.poll()) {
                    null -> break
                    is PeekEvent.Search -> {
                        tab = 0
                        searchReq = e
                    }
                    is PeekEvent.Open -> {
                        tab = 1
                        openReq = e
                    }
                    PeekEvent.SwitchBrowser -> tab = 1
                }
            }
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { TabIcon(t.icon, t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            when (tab) {
                0 -> SearchScreen(
                    padding = padding,
                    searchRequest = searchReq,
                    onConsumeSearchRequest = { searchReq = null },
                )
                1 -> BrowserScreen(
                    padding = padding,
                    openRequest = openReq,
                    onConsumeOpenRequest = { openReq = null },
                )
                2 -> ImagesScreen(padding)
                else -> SettingsScreen(
                    padding = padding,
                    mode = mode,
                    onModeChange = { m ->
                        mode = m
                        ThemeStore.save(context, m)
                    },
                )
            }
        }
    }
}

/**
 * remember holder for bus events, keyed by type. Events are transient:
 * screens consume the request right after receiving it.
 */
@Composable
private inline fun <reified T> rememberBusState(): androidx.compose.runtime.MutableState<T?> {
    return remember { mutableStateOf<T?>(null) }
}

@Composable
private fun TabIcon(resId: Int, description: String) {
    // drawables are drawn white; Icon applies the default content-color tint,
    // which NavigationBarItem switches for the selected destination
    Icon(
        painter = painterResource(resId),
        contentDescription = description,
    )
}
