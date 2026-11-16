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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.mdmuntakim.peek.R
import io.github.mdmuntakim.peek.ui.theme.PeekTheme
import io.github.mdmuntakim.peek.ui.theme.ThemeMode
import io.github.mdmuntakim.peek.ui.theme.ThemeStore

@Composable
fun PeekApp(initialQuery: String? = null) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf<ThemeMode>(ThemeMode.SYSTEM) }
    LaunchedEffect(Unit) {
        mode = ThemeStore.load(context)
    }
    var initial by rememberSaveable { mutableStateOf(initialQuery) }

    PeekTheme(mode = mode) {
        var tab by rememberSaveable { mutableStateOf(0) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { TabIcon(R.drawable.ic_tab_search, "Search") },
                        label = { Text("Search") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { TabIcon(R.drawable.ic_tab_images, "Images") },
                        label = { Text("Images") },
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { TabIcon(R.drawable.ic_tab_settings, "Settings") },
                        label = { Text("Settings") },
                    )
                }
            },
        ) { padding ->
            when (tab) {
                0 -> SearchScreen(
                    padding = padding,
                    initialQuery = initial,
                    onConsumeInitialQuery = { initial = null },
                )
                1 -> ImagesScreen(padding)
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

@Composable
private fun TabIcon(resId: Int, description: String) {
    // drawables are drawn white; Icon applies the default content-color tint,
    // which NavigationBarItem switches for the selected destination
    Icon(
        painter = painterResource(resId),
        contentDescription = description,
    )
}
