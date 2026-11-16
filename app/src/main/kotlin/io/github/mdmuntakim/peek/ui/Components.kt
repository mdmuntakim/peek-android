package io.github.mdmuntakim.peek.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The Peek logo mark drawn in-process (same geometry as the launcher icon). */
@Composable
fun PeekMark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    catchColor: Color? = null,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val cx = w / 2f
        val cy = h / 2f
        val eyeW = w * 0.92f
        val eyeH = h * 0.92f

        val eye = Path().apply {
            moveTo(cx - eyeW / 2f, cy)
            quadraticTo(cx, cy - eyeH * 0.80f, cx + eyeW / 2f, cy)
            quadraticTo(cx, cy + eyeH * 0.80f, cx - eyeW / 2f, cy)
            close()
        }
        drawPath(
            eye,
            color = color,
            style = Stroke(
                width = minOf(w, h) * 0.15f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        val pupilR = minOf(eyeW, eyeH) * 0.24f
        drawCircle(color, radius = pupilR, center = Offset(cx, cy))
        if (catchColor != null) {
            drawCircle(
                catchColor,
                radius = pupilR * 0.30f,
                center = Offset(cx + pupilR * 0.34f, cy - pupilR * 0.34f),
            )
        }
    }
}

/** "Peek AI" synthesized answer card. */
@Composable
fun AnswerCard(
    text: String,
    intent: String,
    confidence: Double,
    sources: List<Pair<String, String>>, // title to url
    onOpen: (String) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PeekMark(modifier = Modifier.height(26.dp).width(34.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Peek AI",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        intentLabel(intent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { confidence.toFloat() },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
            )
            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sources.take(3).forEach { (title, url) ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
                            onClick = { onOpen(url) },
                        ) {
                            Text(
                                title,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun intentLabel(intent: String) = when (intent) {
    "define" -> "Definition"
    "howto" -> "Step by step"
    "compare" -> "Comparison"
    "list" -> "Curated list"
    else -> "Synthesized answer"
}

/** Wikipedia knowledge panel. */
@Composable
fun KnowledgeCard(
    title: String,
    summary: String,
    imageUrl: String,
    url: String,
    onOpen: (String) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpen(url) },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Wikipedia",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                )
            }
            if (imageUrl.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                coil.compose.AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier.size(86.dp).clip(RoundedCornerShape(14.dp)),
                )
            }
        }
    }
}

/** One web result row. */
@Composable
fun ResultRow(
    title: String,
    domain: String,
    snippet: String,
    url: String,
    onOpen: (String) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpen(url) },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        (domain.takeIf { it.isNotEmpty() } ?: title).first().uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                )
                if (domain.isNotEmpty()) {
                    Text(
                        domain,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (snippet.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

/** Shimmering placeholder skeleton shown while providers answer. */
@Composable
fun SkeletonPane() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .alpha(alpha)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        repeat(4) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .alpha(alpha)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}
