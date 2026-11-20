package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.firebase.ImageUtils
import com.example.data.model.DefinitionItem
import com.example.data.model.HighlightColor
import com.example.data.model.SubjectColor
import com.example.data.model.SubjectIcon
import com.example.ui.theme.HighlightInkBerry
import com.example.ui.theme.HighlightInkCoral
import com.example.ui.theme.HighlightInkDark
import com.example.ui.theme.HighlightInkRose
import com.example.ui.theme.SubjectBerry
import com.example.ui.theme.SubjectBlush
import com.example.ui.theme.SubjectCoral
import com.example.ui.theme.SubjectMagenta
import com.example.ui.theme.SubjectPlum
import com.example.ui.theme.SubjectRose

fun SubjectColor.toAccentColor(): Color = when (this) {
    SubjectColor.ROSE -> SubjectRose
    SubjectColor.BERRY -> SubjectBerry
    SubjectColor.BLUSH -> SubjectBlush
    SubjectColor.CORAL -> SubjectCoral
    SubjectColor.PLUM -> SubjectPlum
    SubjectColor.MAGENTA -> SubjectMagenta
}

fun SubjectIcon.toImageVector(): ImageVector = when (this) {
    SubjectIcon.BOOK -> Icons.AutoMirrored.Filled.MenuBook
    SubjectIcon.SCIENCE -> Icons.Default.Science
    SubjectIcon.HISTORY -> Icons.Default.HistoryEdu
    SubjectIcon.MATH -> Icons.Default.Calculate
    SubjectIcon.LANGUAGE -> Icons.Default.Translate
    SubjectIcon.ART -> Icons.Default.Brush
}

@Composable
fun HighlightColor.toInkColor(): Color {
    val dark = isSystemInDarkTheme()
    if (dark) return HighlightInkDark
    return when (this) {
        HighlightColor.ROSE -> HighlightInkRose
        HighlightColor.BERRY -> HighlightInkBerry
        HighlightColor.CORAL -> HighlightInkCoral
    }
}

fun buildHighlightedAnnotatedString(
    fullText: String,
    highlights: List<String>,
    definitions: List<DefinitionItem>,
    highlightBg: Color,
    termBg: Color,
    termColor: Color
): AnnotatedString {
    if (fullText.isEmpty()) return AnnotatedString("")
    return buildAnnotatedString {
        append(fullText)
        val lowerFull = fullText.lowercase()

        for (phrase in highlights) {
            val clean = phrase.trim()
            if (clean.length < 2) continue
            val lowerPhrase = clean.lowercase()
            var startIndex = lowerFull.indexOf(lowerPhrase)
            while (startIndex >= 0) {
                val endIndex = startIndex + clean.length
                addStyle(
                    style = SpanStyle(background = highlightBg),
                    start = startIndex,
                    end = endIndex
                )
                startIndex = lowerFull.indexOf(lowerPhrase, startIndex = endIndex)
            }
        }

        for (def in definitions) {
            val cleanTerm = def.term.trim()
            if (cleanTerm.length < 2) continue
            val lowerTerm = cleanTerm.lowercase()
            var startIndex = lowerFull.indexOf(lowerTerm)
            while (startIndex >= 0) {
                val endIndex = startIndex + cleanTerm.length
                addStyle(
                    style = SpanStyle(
                        background = termBg,
                        color = termColor,
                        fontWeight = FontWeight.Bold
                    ),
                    start = startIndex,
                    end = endIndex
                )
                startIndex = lowerFull.indexOf(lowerTerm, startIndex = endIndex)
            }
        }
    }
}

@Composable
fun Base64ThumbnailImage(
    base64: String,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.cd_photo_preview)
) {
    val bitmap = remember(base64) { ImageUtils.decodeThumbnailBase64(base64) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun DefinitionReviewerCard(
    definition: DefinitionItem,
    sourceNoteTitle: String? = null,
    photoTag: String? = null,
    quizMode: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onOpenNote: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var revealed by remember(quizMode, definition.id) { mutableStateOf(!quizMode) }
    val inkColor = definition.resolvedColor().toInkColor()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (quizMode) {
                    Modifier.clickable { revealed = !revealed }
                } else if (onOpenNote != null) {
                    Modifier.clickable { onOpenNote() }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(110.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = inkColor,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = definition.term,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (onEdit != null || onDelete != null) {
                        Row {
                            if (onEdit != null) {
                                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit definition",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (onDelete != null) {
                                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove definition",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = !quizMode || revealed, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = definition.definition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (quizMode && !revealed) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Reveal definition",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Tap card to reveal definition",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (!sourceNoteTitle.isNullOrBlank() || !photoTag.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        val metaText = buildString {
                            if (!sourceNoteTitle.isNullOrBlank()) append(sourceNoteTitle)
                            if (!photoTag.isNullOrBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(photoTag)
                            }
                        }
                        Text(
                            text = metaText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectEditorDialog(
    initialName: String = "",
    initialColor: SubjectColor = SubjectColor.ROSE,
    initialIcon: SubjectIcon = SubjectIcon.BOOK,
    titleText: String = "New Subject",
    confirmLabel: String = "Create",
    onDismiss: () -> Unit,
    onConfirm: (String, SubjectColor, SubjectIcon) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedIcon by remember { mutableStateOf(initialIcon) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text("Subject Name") },
                    placeholder = { Text("e.g., Biology, Algebra, History") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subject_name_input")
                )

                Text("Pink Accent", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SubjectColor.entries.forEach { color ->
                        val isSelected = selectedColor == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color.toAccentColor())
                                .clickable { selectedColor = color }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }

                Text("Subject Icon", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SubjectIcon.entries.forEach { icon ->
                        FilterChip(
                            selected = selectedIcon == icon,
                            onClick = { selectedIcon = icon },
                            label = { Text(icon.label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon.toImageVector(),
                                    contentDescription = icon.label,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), selectedColor, selectedIcon) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_subject_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DefinitionEditorDialog(
    initialTerm: String = "",
    initialDefinition: String = "",
    initialColor: HighlightColor = HighlightColor.ROSE,
    titleText: String = "Highlight Key Definition",
    onDismiss: () -> Unit,
    onConfirm: (String, String, HighlightColor) -> Unit
) {
    var term by remember { mutableStateOf(initialTerm) }
    var definition by remember { mutableStateOf(initialDefinition) }
    var color by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = term,
                    onValueChange = { if (it.length <= 120) term = it },
                    label = { Text("Key Term") },
                    placeholder = { Text("e.g., Photosynthesis") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("definition_term_input")
                )
                OutlinedTextField(
                    value = definition,
                    onValueChange = { if (it.length <= 1000) definition = it },
                    label = { Text("Definition / Explanation") },
                    placeholder = { Text("Clear explanation to review later...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("definition_body_input")
                )
                Text("Highlighter Shade", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HighlightColor.entries.forEach { shade ->
                        FilterChip(
                            selected = color == shade,
                            onClick = { color = shade },
                            label = { Text(shade.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = shade.toInkColor()
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (term.isNotBlank() && definition.isNotBlank()) {
                        onConfirm(term.trim(), definition.trim(), color)
                    }
                },
                enabled = term.isNotBlank() && definition.isNotBlank(),
                modifier = Modifier.testTag("confirm_definition_button")
            ) {
                Text("Save Highlight")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
