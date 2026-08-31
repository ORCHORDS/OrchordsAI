package com.orchords.orchordsai.ui.components.richtext

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach

internal val DiffAddedColor = Color(0xFF4CAF50)
internal val DiffRemovedColor = Color(0xFFEF5350)

internal data class DiffStats(val additions: Int, val deletions: Int)

internal fun parseDiffStats(diff: String): DiffStats {
    var additions = 0
    var deletions = 0
    diff.lineSequence().forEach { line ->
        when {
            line.startsWith("+++") || line.startsWith("---") -> {}
            line.startsWith("+") -> additions++
            line.startsWith("-") -> deletions++
        }
    }
    return DiffStats(additions, deletions)
}

/**
 *
 */
@Composable
fun DiffView(
    diff: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    showFileHeader: Boolean = true,
) {
    val allLines = remember(diff, showFileHeader) {
        val lines = diff.lines()
        if (!showFileHeader && lines.size >= 2 &&
            lines[0].startsWith("---") && lines[1].startsWith("+++")
        ) {
            lines.drop(2)
        } else {
            lines
        }
    }
    val lines = remember(allLines, maxLines) { allLines.take(maxLines) }
    val truncated = allLines.size - lines.size

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .horizontalScroll(rememberScrollState())
            .width(IntrinsicSize.Max)
            .padding(vertical = 4.dp),
    ) {
        lines.fastForEach { line ->
            DiffLine(line)
        }
        if (truncated > 0) {
            Text(
                text = "… +$truncated lines",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun DiffLine(line: String) {
    val (textColor, background) = when {
        line.startsWith("+++") || line.startsWith("---") ->
            MaterialTheme.colorScheme.onSurfaceVariant to Color.Transparent

        line.startsWith("@@") ->
            MaterialTheme.colorScheme.primary to Color.Transparent

        line.startsWith("+") ->
            DiffAddedColor to DiffAddedColor.copy(alpha = 0.12f)

        line.startsWith("-") ->
            DiffRemovedColor to DiffRemovedColor.copy(alpha = 0.12f)

        else ->
            MaterialTheme.colorScheme.onSurface to Color.Transparent
    }
    Text(
        text = line.ifEmpty { " " },
        color = textColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        softWrap = false,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 8.dp),
    )
}
