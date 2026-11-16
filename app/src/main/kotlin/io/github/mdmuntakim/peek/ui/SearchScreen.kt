package io.github.mdmuntakim.peek.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.mdmuntakim.peek.PeekBus
import io.github.mdmuntakim.peek.engine.Search as Engine
import io.github.mdmuntakim.peek.engine.SearchPayload
import io.github.mdmuntakim.peek.R
import io.github.mdmuntakim.peek.openUrl
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface SearchState {
    data object Idle : SearchState
    data class Loading(val query: String) : SearchState
    data class Done(val payload: SearchPayload) : SearchState
    data class Failed(val query: String, val message: String) : SearchState
}

private val EXAMPLE_QUERIES =
    listOf("what is TLS", "how does photosynthesis work", "best budget mechanical keyboards")

@Composable
fun SearchScreen(
    padding: PaddingValues,
    initialQuery: String?,
    onConsumeInitialQuery: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }

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
            } catch (e: Exception) {
                state = SearchState.Failed(clean, e.message ?: "Search failed")
            }
        }
    }

    // initial query handed over through a peek://search intent
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrEmpty()) {
            onConsumeInitialQuery()
            runSearch(initialQuery)
        }
    }

    // hand-off from onNewIntent
    LaunchedEffect(Unit) {
        while (true) {
            val pending = PeekBus.poll() ?: break
            runSearch(pending)
        }
    }

    // live suggestions, debounced
    LaunchedEffect(query) {
        if (query.isBlank()) {
            suggestions = emptyList()
            showSuggestions = false
            return@LaunchedEffect
        }
        delay(180)
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
                onPick = { runSearch(it) },
            )
        } else {
            Box(Modifier.fillMaxSize().padding(top = 12.dp)) {
                when (val s = state) {
                    is SearchState.Idle -> WelcomePane { runSearch(it) }
                    is SearchState.Loading -> Box(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    ) { SkeletonPane() }
                    is SearchState.Failed -> ErrorPane(s.message) { runSearch(s.query) }
                    is SearchState.Done -> Results(
                        payload = s.payload,
                        onOpen = { url -> openUrl(url) },
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
        placeholder = { Text("Search the web…") },
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
private fun SuggestionsPane(suggestions: List<String>, onPick: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column {
            suggestions.forEachIndexed { i, s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(s) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tab_search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        s,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
                if (i < suggestions.lastIndex) HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun WelcomePane(onExample: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PeekMark(modifier = Modifier.width(88.dp).height(56.dp))
        Spacer(Modifier.height(18.dp))
        Text(
            "Peek",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Search, summarized. Ask anything and get an\nAI answer on top of ranked results.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(26.dp))
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
                onOpen = onOpen,
            )
        }
        payload.instant?.let { instant ->
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        instant.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        payload.knowledge?.let { k ->
            item {
                KnowledgeCard(
                    title = k.title,
                    summary = k.summary,
                    imageUrl = k.image,
                    url = k.url,
                    onOpen = onOpen,
                )
            }
        }
        if (payload.related.isNotEmpty()) {
            item {
                RelatedChips(
                    related = payload.related,
                    onPick = onSearch,
                )
            }
        }
        items(payload.results) { r ->
            key(r.url) {
                ResultRow(
                    title = r.title,
                    domain = r.domain,
                    snippet = r.snippet,
                    url = r.url,
                    onOpen = onOpen,
                )
            }
        }
        item {
            Text(
                "web ${payload.providers.web} · instant ${payload.providers.instant} · " +
                    "wikipedia ${payload.providers.wikipedia}" +
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
        Text(
            "Related",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
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
