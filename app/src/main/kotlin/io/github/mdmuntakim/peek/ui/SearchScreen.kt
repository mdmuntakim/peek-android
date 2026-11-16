package io.github.mdmuntakim.peek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.mdmuntakim.peek.PeekBus
import io.github.mdmuntakim.peek.PeekEvent
import io.github.mdmuntakim.peek.R
import io.github.mdmuntakim.peek.engine.Crawler
import io.github.mdmuntakim.peek.engine.Index
import io.github.mdmuntakim.peek.engine.Search as Engine
import io.github.mdmuntakim.peek.engine.SearchPayload
import io.github.mdmuntakim.peek.engine.Suggest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface SearchState {
    data object Idle : SearchState
    data class Loading(val query: String) : SearchState
    data class Done(val payload: SearchPayload) : SearchState
    data class Failed(val query: String, val message: String) : SearchState
}

private val EXAMPLE_QUERIES =
    listOf("search engine", "public domain books", "web security", "photosynthesis")

@Composable
fun SearchScreen(
    padding: PaddingValues,
    searchRequest: PeekEvent.Search?,
    onConsumeSearchRequest: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    var suggestions by remember { mutableStateOf<List<Suggest>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var indexPages by remember { mutableStateOf(Index.stats().pages) }

    fun runSearch(q: String) {
        val clean = q.trim()
        if (clean.isEmpty()) return
        query = clean
        showSuggestions = false
        keyboard?.hide()
        state = SearchState.Loading(clean)
        scope.launch {
            try {
                state = SearchState.Done(Engine.search(clean))
                indexPages = Index.stats().pages
            } catch (e: Exception) {
                state = SearchState.Failed(clean, e.message ?: "Search failed")
            }
        }
    }

    // hand-off from PeekApp's bus: an external or in-app search request
    LaunchedEffect(searchRequest) {
        val req = searchRequest ?: return@LaunchedEffect
        onConsumeSearchRequest()
        runSearch(req.query)
    }

    // live suggestions from Peek's own history + index, debounced
    LaunchedEffect(query) {
        if (query.isBlank()) {
            suggestions = emptyList()
            showSuggestions = false
            return@LaunchedEffect
        }
        delay(150)
        val fetched = Engine.suggest(query, 8)
        if (query.isNotBlank()) {
            suggestions = fetched
            showSuggestions = fetched.isNotEmpty()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        SearchField(
            query = query,
            onChange = { query = it },
            onSearch = { runSearch(query) },
            onClear = {
                query = ""
                suggestions = emptyList()
                showSuggestions = false
                state = SearchState.Idle
            },
        )

        if (showSuggestions && suggestions.isNotEmpty()) {
            SuggestionsPane(
                suggestions = suggestions,
                onPickQuery = { runSearch(it) },
                onPickPage = { PeekBus.openUrl(it) },
            )
        } else {
            Box(Modifier.fillMaxSize().padding(top = 12.dp)) {
                when (val s = state) {
                    is SearchState.Idle -> WelcomePane(indexPages) { runSearch(it) }
                    is SearchState.Loading -> Box(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    ) { SkeletonPane() }
                    is SearchState.Failed -> ErrorPane(s.message) { runSearch(s.query) }
                    is SearchState.Done -> Results(
                        payload = s.payload,
                        onOpen = { url -> PeekBus.openUrl(url) },
                        onSearch = { q -> runSearch(q) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search your own engine…") },
        leadingIcon = { Icon(painter = painterResource(R.drawable.ic_tab_search), contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(painter = painterResource(R.drawable.ic_close), contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SuggestionsPane(
    suggestions: List<Suggest>,
    onPickQuery: (String) -> Unit,
    onPickPage: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column {
            suggestions.forEachIndexed { i, s ->
                val isPage = s.pageUrl.isNotEmpty()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isPage) onPickPage(s.pageUrl) else onPickQuery(s.query)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            if (isPage) R.drawable.ic_tab_images else R.drawable.ic_tab_search
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (isPage) s.pageTitle else s.query,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        if (isPage) {
                            Text(
                                "open in Peek Browser",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (i < suggestions.lastIndex) HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun WelcomePane(indexPages: Int, onExample: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Signature aurora hero with the mark.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(io.github.mdmuntakim.peek.ui.theme.aurora().brush)
                .padding(vertical = 34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PeekMark(
                    modifier = Modifier.size(width = 108.dp, height = 72.dp),
                    color = Color.White,
                    accent = Color(0xFFF5B860),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Peek",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    "your own search engine",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        StatusChip(
            text = "PeekBot index · $indexPages pages",
            pulsing = Crawler.running,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Peek crawls the web with its own robot, ranks it with its\n" +
                "own BM25 index and answers with its own AI synthesis.\n" +
                "No Google. No Bing. No DuckDuckGo. Ever.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(22.dp))
        SectionHeader("Try a search", Modifier.padding(start = 4.dp))
        EXAMPLE_QUERIES.forEach { q ->
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onExample(q) },
                modifier = Modifier.padding(vertical = 5.dp),
            ) {
                Text(
                    q,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Tip: open sites in the Browser tab — every page you read\n" +
                "makes your index smarter.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
        )
    }
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Something went wrong",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun Results(
    payload: SearchPayload,
    onOpen: (String) -> Unit,
    onSearch: (String) -> Unit,
) {
    val terms = Index.parseQuery(payload.query).terms
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            AnswerCard(
                text = payload.answer.text,
                intent = payload.answer.intent,
                confidence = payload.answer.confidence,
                sources = payload.answer.sources.map { it.title to it.url },
                terms = terms,
                onOpen = onOpen,
            )
        }
        payload.knowledge?.let { k ->
            item {
                KnowledgeCard(
                    title = k.title,
                    summary = k.summary,
                    domain = k.domain,
                    url = k.url,
                    onOpen = onOpen,
                )
            }
        }
        if (payload.related.isNotEmpty()) {
            item { RelatedChips(payload.related, onSearch) }
        }
        if (payload.results.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "Nothing in your index yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (payload.exploring)
                                "PeekBot is out crawling right now — search again in a minute."
                            else
                                "Peek only searches what its own robot has read. " +
                                    "Open the Browser tab and read a few pages, or run a " +
                                    "crawl from Settings, then search again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { SectionHeader("From your index") }
        items(payload.results) { r ->
            key(r.url) {
                ResultRow(
                    title = r.title,
                    domain = r.domain,
                    snippet = r.snippet,
                    url = r.url,
                    score = r.score,
                    terms = terms,
                    onOpen = onOpen,
                )
            }
        }
        item {
            Text(
                "${payload.providers.pages} pages · ${payload.providers.sites} sites · " +
                    "${payload.providers.terms} terms in your index" +
                    if (payload.cached) " · cached" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 18.dp),
            )
        }
    }
}

@Composable
private fun RelatedChips(related: List<String>, onPick: (String) -> Unit) {
    Column {
        SectionHeader("Related searches")
        // simple flow of chips: wrap by chunks to keep it dependency-free
        val rows = related.chunked(3)
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                row.forEach { label ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { onPick(label) },
                    ) {
                        Text(
                            label,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
