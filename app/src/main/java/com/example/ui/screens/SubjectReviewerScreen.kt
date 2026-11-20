package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.UiState
import com.example.ui.components.Base64ThumbnailImage
import com.example.ui.components.DefinitionReviewerCard
import com.example.ui.components.buildHighlightedAnnotatedString
import com.example.ui.components.toAccentColor
import com.example.ui.components.toImageVector
import com.example.ui.theme.HighlightInkRose
import com.example.ui.viewmodel.EduFlowViewModel

private enum class ReviewerFilter(val label: String) {
    ALL("All Reviewer"),
    DEFINITIONS("Key Definitions"),
    PHOTOS("Tagged Photos"),
    NOTES("Class Notes")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectReviewerScreen(
    subjectId: String,
    viewModel: EduFlowViewModel,
    onBack: () -> Unit,
    onOpenNoteEditor: (subjectId: String, noteId: String?, photoUri: Uri?) -> Unit
) {
    val context = LocalContext.current
    val subjectsState by viewModel.subjectsState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val subjects = (subjectsState as? UiState.Success)?.data ?: emptyList()
    val allNotes = (notesState as? UiState.Success)?.data ?: emptyList()
    val subject: Subject? = subjects.firstOrNull { it.id == subjectId }
    val subjectNotes = remember(allNotes, subjectId) {
        allNotes.filter { it.subjectId == subjectId }
    }

    var selectedFilter by remember { mutableStateOf(ReviewerFilter.ALL) }
    var quizMode by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<StudyNote?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onOpenNoteEditor(subjectId, null, uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = pendingCameraUri
        if (success && captured != null) {
            onOpenNoteEditor(subjectId, null, captured)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val allDefinitions = remember(subjectNotes) {
        subjectNotes.flatMap { note ->
            note.definitions.map { def -> Triple(note, def, note.photoTag) }
        }
    }
    val taggedPhotoNotes = remember(subjectNotes) {
        subjectNotes.filter { it.thumbnailBase64.isNotBlank() || it.photoTag.isNotBlank() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = subject?.name ?: stringResource(R.string.reviewer_title),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.reviewer_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("reviewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenNoteEditor(subjectId, null, null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_note)) },
                modifier = Modifier.testTag("reviewer_add_note_fab")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ReviewerSummaryBanner(
                        subject = subject,
                        notesCount = subjectNotes.size,
                        definitionsCount = allDefinitions.size,
                        photosCount = taggedPhotoNotes.size,
                        onTakePhoto = {
                            val uri = createTempCameraUri(context)
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        },
                        onPickPhoto = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReviewerFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }

                if (allDefinitions.isNotEmpty() &&
                    (selectedFilter == ReviewerFilter.ALL || selectedFilter == ReviewerFilter.DEFINITIONS)
                ) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Quiz,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = stringResource(R.string.quiz_mode_label),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "Hide answers to test yourself while scrolling",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = quizMode,
                                    onCheckedChange = { quizMode = it },
                                    modifier = Modifier.testTag("quiz_mode_switch")
                                )
                            }
                        }
                    }
                }

                if (subjectNotes.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "No notes in ${subject?.name ?: "this subject"} yet",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Snap a page, pick a gallery photo, or type a note to automatically build your reviewer.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    if ((selectedFilter == ReviewerFilter.ALL || selectedFilter == ReviewerFilter.PHOTOS) &&
                        taggedPhotoNotes.isNotEmpty()
                    ) {
                        item {
                            Text(
                                text = "Tagged Study Photos (${taggedPhotoNotes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(taggedPhotoNotes, key = { "photo_${it.id}" }) { note ->
                                    TaggedPhotoCard(
                                        note = note,
                                        onClick = { onOpenNoteEditor(subjectId, note.id, null) }
                                    )
                                }
                            }
                        }
                    }

                    if (selectedFilter == ReviewerFilter.ALL || selectedFilter == ReviewerFilter.DEFINITIONS) {
                        item {
                            Text(
                                text = "Key Definitions Reviewer (${allDefinitions.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        if (allDefinitions.isEmpty()) {
                            item {
                                Text(
                                    text = "Open any note and tap sentences to highlight key definitions into this reviewer.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(allDefinitions, key = { "${it.first.id}_${it.second.id}" }) { (note, def, tag) ->
                                DefinitionReviewerCard(
                                    definition = def,
                                    sourceNoteTitle = note.title,
                                    photoTag = tag,
                                    quizMode = quizMode,
                                    onOpenNote = { onOpenNoteEditor(subjectId, note.id, null) }
                                )
                            }
                        }
                    }

                    if (selectedFilter == ReviewerFilter.ALL || selectedFilter == ReviewerFilter.NOTES) {
                        item {
                            Text(
                                text = "Class Notes (${subjectNotes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        items(subjectNotes, key = { "note_${it.id}" }) { note ->
                            ReviewerNoteCard(
                                note = note,
                                onEdit = { onOpenNoteEditor(subjectId, note.id, null) },
                                onDelete = { noteToDelete = note }
                            )
                        }
                    }
                }
            }
        }
    }

    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete \"${note.title}\"?") },
            text = { Text("This note and its highlighted definitions will be removed from this reviewer.") },
            confirmButton = {
                Button(
                    onClick = {
                        val id = note.id
                        noteToDelete = null
                        viewModel.deleteNote(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReviewerSummaryBanner(
    subject: Subject?,
    notesCount: Int,
    definitionsCount: Int,
    photosCount: Int,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit
) {
    val accent = subject?.resolvedColor()?.toAccentColor() ?: MaterialTheme.colorScheme.primary
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accent.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = (subject?.resolvedIcon() ?: com.example.data.model.SubjectIcon.BOOK).toImageVector(),
                            contentDescription = null,
                            tint = accent
                        )
                    }
                }
                Column {
                    Text(
                        text = "$definitionsCount Key Definitions • $notesCount Notes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$photosCount tagged page photos ready to review",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTakePhoto,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("reviewer_camera_button")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.take_photo), maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onPickPhoto,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("reviewer_gallery_button")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.pick_photo), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TaggedPhotoCard(
    note: StudyNote,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(190.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            if (note.thumbnailBase64.isNotBlank()) {
                Base64ThumbnailImage(
                    base64 = note.thumbnailBase64,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                )
            }
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (note.photoTag.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = note.photoTag,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${note.definitions.size} definitions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReviewerNoteCard(
    note: StudyNote,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val highlightedPreview = buildHighlightedAnnotatedString(
        fullText = note.extractedText.take(360),
        highlights = note.highlights,
        definitions = note.definitions,
        highlightBg = HighlightInkRose,
        termBg = MaterialTheme.colorScheme.primaryContainer,
        termColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (note.photoTag.isNotBlank()) {
                        Text(
                            text = "Photo: ${note.photoTag}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit and highlight note",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete note",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (note.extractedText.isNotBlank()) {
                Text(
                    text = highlightedPreview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${note.highlights.size} highlights",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${note.definitions.size} definitions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
