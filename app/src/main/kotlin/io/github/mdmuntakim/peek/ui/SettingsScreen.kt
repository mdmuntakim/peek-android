package io.github.mdmuntakim.peek.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mdmuntakim.peek.BuildConfig
import io.github.mdmuntakim.peek.openUrl
import io.github.mdmuntakim.peek.ui.theme.ThemeMode
import io.github.mdmuntakim.peek.ui.theme.ThemeStore

private const val REPO_URL = "https://github.com/mdmuntakim/peek"

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pick how Peek should look, including in dark hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { onModeChange(m) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                        ) {
                            Text(
                                when (m) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PeekMark(
                        modifier = Modifier.width(52.dp).height(34.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        catchColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Peek",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "v${BuildConfig.VERSION_NAME} · arm64",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "A professional search engine with AI-smart answers: " +
                        "every query gets a synthesized answer paragraph on top of " +
                        "ranked results, a knowledge panel, related searches and image search. " +
                        "Key-free by design — results come from public endpoints.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
                    onClick = { openUrl(REPO_URL) },
                ) {
                    Text(
                        "github.com/mdmuntakim/peek",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Providers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                ProviderLine("Web index", "DuckDuckGo HTML results")
                ProviderLine("Knowledge", "DuckDuckGo Instant Answers + Wikipedia")
                ProviderLine("Images", "DuckDuckGo Images")
                ProviderLine("Answers", "Peek AI, deterministic extractive synthesis")
                Spacer(Modifier.height(6.dp))
                Text(
                    "No API keys, no accounts, no tracking.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProviderLine(title: String, detail: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(110.dp),
        )
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
