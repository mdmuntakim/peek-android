package io.github.mdmuntakim.peek.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.mdmuntakim.peek.PeekBus
import io.github.mdmuntakim.peek.R
import io.github.mdmuntakim.peek.engine.ImageResult
import io.github.mdmuntakim.peek.engine.Index
import io.github.mdmuntakim.peek.engine.Store
import kotlinx.coroutines.launch

sealed interface ImagesState {
    data object Idle : ImagesState
    data object Loading : ImagesState
    data class Done(val results: List<ImageResult>) : ImagesState
    data object Empty : ImagesState
}

@Composable
fun ImagesScreen(padding: PaddingValues) {
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<ImagesState>(ImagesState.Idle) }

    fun runImageSearch(q: String) {
        val clean = q.trim()
        if (clean.isEmpty()) return
        keyboard?.hide()
        state = ImagesState.Loading
        scope.launch {
            val results = Store.imageSearch(clean)
            state = if (results.isEmpty()) ImagesState.Empty else ImagesState.Done(results)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Pictures in your index…") },
            leadingIcon = { Icon(painter = painterResource(R.drawable.ic_tab_search), contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; state = ImagesState.Idle }) {
                        Icon(painter = painterResource(R.drawable.ic_close), contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runImageSearch(query) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
            ),
        )

        when (val s = state) {
            is ImagesState.Idle -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PeekMark(modifier = Modifier.size(width = 72.dp, height = 46.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Image gallery of your index",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "PeekBot collects every picture it reads while crawling.\n" +
                        "Browse or crawl more to grow the gallery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            is ImagesState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is ImagesState.Empty -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No images match", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The gallery only holds pictures from pages PeekBot has read.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is ImagesState.Done -> LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(s.results, key = { it.image }) { img ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        onClick = { PeekBus.openUrl(img.url) },
                        modifier = Modifier.aspectRatio(1f),
                    ) {
                        AsyncImage(
                            model = img.image,
                            contentDescription = img.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                        )
                    }
                }
            }
        }
    }
}
