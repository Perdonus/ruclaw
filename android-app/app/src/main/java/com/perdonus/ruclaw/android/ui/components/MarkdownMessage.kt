package com.perdonus.ruclaw.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.perdonus.ruclaw.android.core.markdown.MarkdownBlock
import com.perdonus.ruclaw.android.core.markdown.MarkdownParser
import com.perdonus.ruclaw.android.core.markdown.TextKind

@Composable
fun MarkdownMessage(
    text: String,
    onDownloadLinkClick: (String) -> Unit,
    onExternalLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(text) { MarkdownParser().parse(text) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RoundedCornerShape(18.dp),
                            )
                            .padding(14.dp),
                    ) {
                        block.info?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        SelectionContainer {
                            Text(
                                text = block.code,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                color = textColor,
                            )
                        }
                    }
                }

                MarkdownBlock.Divider -> HorizontalDivider()

                is MarkdownBlock.TextBlock -> {
                    val baseStyle = when (block.kind) {
                        TextKind.HEADING_1 -> MaterialTheme.typography.headlineSmall
                        TextKind.HEADING_2 -> MaterialTheme.typography.titleLarge
                        TextKind.HEADING_3 -> MaterialTheme.typography.titleMedium
                        TextKind.QUOTE -> MaterialTheme.typography.bodyLarge
                        TextKind.BULLET_ITEM -> MaterialTheme.typography.bodyLarge
                        TextKind.PARAGRAPH -> MaterialTheme.typography.bodyLarge
                    }
                    val style = when (block.kind) {
                        TextKind.QUOTE -> baseStyle.copy(color = textColor.copy(alpha = 0.82f))
                        else -> baseStyle.copy(color = textColor)
                    }
                    val hasAnnotations = block.text.getStringAnnotations(
                        start = 0,
                        end = block.text.length,
                    ).isNotEmpty()
                    if (hasAnnotations) {
                        ClickableText(
                            text = block.text,
                            style = style,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { offset ->
                                block.text.getStringAnnotations(start = offset, end = offset)
                                    .firstOrNull()
                                    ?.let { annotation ->
                                        when (annotation.tag) {
                                            "download" -> onDownloadLinkClick(annotation.item)
                                            "url" -> onExternalLinkClick(annotation.item)
                                        }
                                    }
                            },
                        )
                    } else {
                        SelectionContainer {
                            Text(
                                text = block.text,
                                style = style,
                                overflow = TextOverflow.Visible,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        if (blocks.isEmpty()) {
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
