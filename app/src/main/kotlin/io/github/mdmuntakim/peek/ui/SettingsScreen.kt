package io.github.mdmuntakim.peek.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mdmuntakim.peek.BuildConfig
import io.github.mdmuntakim.peek.PeekBus
import io.github.mdmuntakim.peek.engine.Crawler
import io.github.mdmuntakim.peek.engine.Index
import io.github.mdmuntakim.peek.engine.Search as Engine
import io.github.mdmuntakim.peek.engine.Store
import io.github.mdmuntakim.peek.engine.Text
import io.github.mdmuntakim.peek.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private const val REPO_URL = "https://github.com/mdmuntakim/peek"

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var stats by remember { mutableStateOf(Index.stats()) }
    var seeds by remember { mutableStateOf(Store.seedsList()) }
    var autoIndex by remember { mutableStateOf(Store.autoIndex()) }
    var crawlBudget by remember { mutableStateOf(24) }
    var crawling by remember { mutableStateOf(Crawler.running) }
    var crawlNote by remember { mutableStateOf("") }
    var newSeed by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(Store.history()) }
    var bookmarks by remember { mutableStateOf(Store.bookmarks()) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        // ---------------------------------------------------------- engine
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "The Peek engine",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "A real search engine that lives in this app: PeekBot crawls " +
                        "the web, Peek's inverted index ranks it with BM25, and " +
                        "Peek AI answers from what was read. No other search " +
                        "engine is ever contacted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBubble("${stats.pages}", "pages")
                    StatBubble("${stats.sites}", "sites")
                    StatBubble("${stats.terms}", "terms")
                }
                Spacer(Modifier.height(12.dp))
                if (crawling) {
                    Text(
                        crawlNote.ifEmpty { "PeekBot is out crawling…" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { Crawler.cancel() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Stop PeekBot") }
                } else {
                    Text(
                        "Crawl budget (pages per run): $crawlBudget",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(12, 24, 48, 96).forEachIndexed { i, b ->
                            SegmentedButton(
                                selected = crawlBudget == b,
                                onClick = { crawlBudget = b },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = 4),
                            ) { Text("$b") }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            crawling = true
                            scope.launch {
                                val done = Engine.crawlNow(crawlBudget) { s ->
                                    crawlNote = "fetched ${s.fetched} · indexed ${s.indexed} · " +
                                        "skipped ${s.skipped} · failed ${s.failed}"
                                }
                                crawlNote = "done: ${done.indexed} pages indexed"
                                stats = Index.stats()
                                crawling = Crawler.running
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (stats.pages == 0) "Crawl the web for the first time" else "Crawl more")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ----------------------------------------------------------- seeds
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Crawl ring (seeds)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "The addresses PeekBot starts from. Pages they link to can " +
                        "enter your index.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newSeed,
                        onValueChange = { newSeed = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("https://example.org") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (Store.addSeed(newSeed)) {
                                    seeds = Store.seedsList()
                                    newSeed = ""
                                }
                            }
                        ),
                    )
                    Text(
                        "Add",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable {
                                if (Store.addSeed(newSeed)) {
                                    seeds = Store.seedsList()
                                    newSeed = ""
                                }
                            }
                            .padding(start = 12.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                seeds.take(8).forEach { s ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text(
                            Text.domainOf(s).ifEmpty { s },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Remove",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { Store.removeSeed(s); seeds = Store.seedsList() }
                                .padding(start = 8.dp),
                        )
                    }
                }
                if (seeds.size > 8) {
                    Text(
                        "… and ${seeds.size - 8} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Reset to defaults",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { Store.resetSeeds(); seeds = Store.seedsList() },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // -------------------------------------------------------- browser
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Browser",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Index pages I read", fontWeight = FontWeight.Medium)
                        Text(
                            "While you browse in Peek, each page is read into " +
                                "your engine. The fastest way to grow it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autoIndex, onCheckedChange = {
                        autoIndex = it
                        Store.setAutoIndex(it)
                    })
                }
                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("Recent searches", Modifier.padding(start = 0.dp))
                    history.take(6).forEach { h ->
                        Text(
                            h,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                        )
                    }
                    Text(
                        "Clear history",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { Store.clearHistory(); history = emptyList() },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ------------------------------------------------------- bookmarks
        if (bookmarks.isNotEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    SectionHeader("Bookmarks", Modifier.padding(start = 0.dp))
                    bookmarks.take(6).forEach { (t, u) ->
                        Text(
                            t.ifEmpty { u },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ------------------------------------------------------ appearance
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

        // ----------------------------------------------------------- about
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
                        accent = MaterialTheme.colorScheme.secondary,
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
                    "Crawler, index, ranker, answer engine and browser — " +
                        "all built into this app and all Peek's own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
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

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatBubble(value: String, label: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
