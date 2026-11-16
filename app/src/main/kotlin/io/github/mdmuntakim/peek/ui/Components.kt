package io.github.mdmuntakim.peek.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * The Peek logo mark: a vesica eye inside a segmented aperture ring with an
 * offset pupil -- Peek's own original artwork, drawn in-process with the
 * same geometry family as the launcher icon.
 */
@Composable
fun PeekMark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    accent: Color = MaterialTheme.colorScheme.secondary,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f

        // Segmented aperture ring (4 arcs, rotated like a camera aperture).
        val ring = Stroke(width = r * 0.11f, cap = StrokeCap.Round)
        var start = -30f
        repeat(4) {
            drawArc(
                color = if (it % 2 == 0) color else accent,
                startAngle = start,
                sweepAngle = 62f,
                useCenter = false,
                topLeft = Offset(cx - r * 0.92f, cy - r * 0.92f),
                size = androidx.compose.ui.geometry.Size(r * 1.84f, r * 1.84f),
                style = ring,
            )
            start += 90f
        }
        // Vesica eye.
        val eyeW = r * 1.15f
        val eyeH = r * 0.95f
        val eye = Path().apply {
            moveTo(cx - eyeW / 2f, cy)
            quadraticTo(cx, cy - eyeH * 0.95f, cx + eyeW / 2f, cy)
            quadraticTo(cx, cy + eyeH * 0.95f, cx - eyeW / 2f, cy)
            close()
        }
        drawPath(
            eye, color = color, style = Stroke(
                width = r * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round,
            )
        )
        // Offset pupil with a glint.
        drawCircle(color, radius = r * 0.26f, center = Offset(cx, cy))
        drawCircle(accent, radius = r * 0.08f, center = Offset(cx + r * 0.09f, cy - r * 0.10f))
    }
}

/** Small monospace-feel header used to label result sections. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
    )
}

/** The live index status chip ("PeekBot · 128 pages · 14 sites"). */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(if (pulsing) alpha else 1f)
                    .clip(CircleShape)
                    .background(if (pulsing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Snippet text with the query terms highlighted. */
@Composable
fun HighlightText(
    text: String,
    terms: List<String>,
    style: TextStyle,
    color: Color,
    maxLines: Int = 3,
) {
    val annotated: AnnotatedString = buildAnnotatedString {
        append(text)
        val low = text.lowercase()
        for (t in terms) {
            var idx = low.indexOf(t)
            while (idx >= 0) {
                addStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    idx,
                    idx + t.length,
                )
                idx = low.indexOf(t, idx + t.length)
            }
        }
    }
    Text(annotated, style = style, color = color, maxLines = maxLines)
}

/** "Peek AI" synthesized answer card. */
@Composable
fun AnswerCard(
    text: String,
    intent: String,
    confidence: Double,
    sources: List<Pair<String, String>>, // title to url
    terms: List<String>,
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
            HighlightText(
                text = text,
                terms = terms,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 8,
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

/** Knowledge panel, built from Peek's own index. */
@Composable
fun KnowledgeCard(
    title: String,
    summary: String,
    domain: String,
    url: String,
    onOpen: (String) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpen(url) },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                domain,
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
    }
}

/** One web result row, opened in the in-app browser. */
@Composable
fun ResultRow(
    title: String,
    domain: String,
    snippet: String,
    url: String,
    score: Double,
    terms: List<String>,
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
                shape = RoundedCornerShape(12.dp),
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
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                )
                if (domain.isNotEmpty()) {
                    Text(
                        "$domain · score ${"%.1f".format(score)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (snippet.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    HighlightText(
                        text = snippet,
                        terms = terms,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Shimmering placeholder skeleton shown while the engine answers. */
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
