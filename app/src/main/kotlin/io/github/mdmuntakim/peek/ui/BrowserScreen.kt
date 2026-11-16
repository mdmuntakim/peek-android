package io.github.mdmuntakim.peek.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.mdmuntakim.peek.PeekBus
import io.github.mdmuntakim.peek.PeekEvent
import io.github.mdmuntakim.peek.R
import io.github.mdmuntakim.peek.engine.HtmlDoc
import io.github.mdmuntakim.peek.engine.Index
import io.github.mdmuntakim.peek.engine.PageDoc
import io.github.mdmuntakim.peek.engine.Store
import io.github.mdmuntakim.peek.engine.Text
import io.github.mdmuntakim.peek.openExternally
import kotlinx.coroutines.launch

/**
 * browser.kt screen -- Peek's built-in browser. Browsing is how the engine
 * grows: every page you read can be read into Peek's index ("browse to
 * build your search engine"). Results and images from Search open here
 * directly; nothing ever leaves the app for another search engine.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    padding: PaddingValues,
    openRequest: PeekEvent.Open?,
    onConsumeOpenRequest: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var indexedNote by remember { mutableStateOf("") }
    var bookmarked by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = io.github.mdmuntakim.peek.engine.Http.UA
            settings.allowContentAccess = true
            settings.allowFileAccess = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress / 100f
                    canGoBack = view.canGoBack()
                    canGoForward = view.canGoForward()
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme ?: return false
                    return scheme != "http" && scheme != "https"
                }

                override fun doUpdateVisitedHistory(view: WebView, u: String, isReload: Boolean) {
                    url = u
                    input = u
                    bookmarked = Store.isBookmarked(u)
                }

                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    super.onPageFinished(view, finishedUrl)
                    if (!finishedUrl.startsWith("http")) return
                    canGoBack = view.canGoBack()
                    canGoForward = view.canGoForward()
                    if (!Store.autoIndex()) return
                    // Read the rendered document into Peek's own index.
                    view.evaluateJavascript(
                        "(document.documentElement.outerHTML || '')"
                    ) { html ->
                        if (html.length > 2 && html.length < 2_000_000) {
                            scope.launch {
                                try {
                                    val doc = HtmlDoc.extract(
                                        unescapeJs(html),
                                        finishedUrl,
                                    )
                                    val text = doc.text
                                    if (text.length >= 120) {
                                        val ok = Index.upsert(
                                            PageDoc(
                                                url = finishedUrl,
                                                domain = Text.domainOf(finishedUrl),
                                                title = doc.title.ifEmpty { finishedUrl },
                                                text = text,
                                            )
                                        )
                                        indexedNote = if (ok) "Indexed by PeekBot" else "Already in your index"
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun navigate(target: String) {
        val t = target.trim()
        if (t.isEmpty()) return
        val isUrl = t.startsWith("http://") || t.startsWith("https://") ||
            Regex("^[\\w-]+\\.[\\w.-]+(/|$)").containsMatchIn(t)
        val target2 = if (isUrl) {
            if (t.startsWith("http")) t else "https://$t"
        } else {
            // not a URL: treat as a Peek search
            PeekBus.submitQuery(t)
            return
        }
        input = target2
        webView.loadUrl(target2)
    }

    // Bus hand-offs: results and images open right here.
    LaunchedEffect(openRequest) {
        val req = openRequest ?: return@LaunchedEffect
        onConsumeOpenRequest()
        navigate(req.url)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        // Address bar row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = {
                if (webView.canGoBack()) webView.goBack()
            }, enabled = canGoBack) {
                Text("‹", style = MaterialTheme.typography.headlineMedium)
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Address or search Peek") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { navigate(input) },
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Go,
                ),
            )
            IconButton(onClick = {
                if (webView.canGoForward()) webView.goForward()
            }, enabled = canGoForward) {
                Text("›", style = MaterialTheme.typography.headlineMedium)
            }
        }

        if (progress in 0.01f..0.99f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
        }

        // Action row: reload, bookmark, index-now, open externally.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            IconButton(onClick = { webView.reload() }, modifier = Modifier.size(36.dp)) {
                Text("↻", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(
                onClick = {
                    if (url.isNotEmpty()) {
                        if (Store.addBookmark(webView.title ?: url, url)) bookmarked = true
                        else Store.removeBookmark(url).also { bookmarked = false }
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Text(if (bookmarked) "★" else "☆", style = MaterialTheme.typography.titleMedium)
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = {
                    if (url.startsWith("http")) {
                        scope.launch { indexNow(url, { indexedNote = it }) }
                    }
                },
            ) {
                Text(
                    "Index page",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (indexedNote.isNotEmpty()) {
                Text(
                    indexedNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            IconButton(
                onClick = { openExternally(url) },
                modifier = Modifier.size(36.dp),
            ) {
                Text("↗", style = MaterialTheme.typography.titleMedium)
            }
        }

        Box(Modifier.weight(1f).background(MaterialTheme.colorScheme.background)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize()) {}
        }
    }
}

private suspend fun indexNow(pageUrl: String, note: (String) -> Unit) {
    try {
        val html = io.github.mdmuntakim.peek.engine.Http.get(pageUrl, 12_000)
        val doc = HtmlDoc.extract(html, pageUrl)
        val ok = Index.upsert(
            PageDoc(
                url = pageUrl,
                domain = Text.domainOf(pageUrl),
                title = doc.title.ifEmpty { pageUrl },
                text = doc.text,
            )
        )
        for (img in doc.images) Store.addImage(
            io.github.mdmuntakim.peek.engine.ImageResult(img.second, pageUrl, img.first, 0, 0)
        )
        note(if (ok) "Indexed by PeekBot" else "Refreshed in index")
    } catch (e: Exception) {
        note("Could not index: ${e.message?.take(40) ?: "error"}")
    }
}

/** `evaluateJavascript` returns a JSON-encoded string; unwrap it. */
private fun unescapeJs(s: String): String {
    val v = io.github.mdmuntakim.peek.engine.Json.parse(s)
    return v as? String ?: s
}
