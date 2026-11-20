package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.DefinitionItem
import com.example.data.model.HighlightColor
import com.example.data.model.UiState
import com.example.ui.components.Base64ThumbnailImage
import com.example.ui.components.DefinitionEditorDialog
import com.example.ui.components.DefinitionReviewerCard
import com.example.ui.components.buildHighlightedAnnotatedString
import com.example.ui.theme.HighlightInkRose
import com.example.ui.viewmodel.AiExtractionState
import com.example.ui.viewmodel.EduFlowViewModel
import java.util.UUID

private enum class EditorTab(val label: String) {
    EDIT("Edit Text"),
    HIGHLIGHT("Highlight"),
    DEFINITIONS("Definitions")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    initialSubjectId: String?,
    noteId: String?,
    initialPhotoUri: Uri?,
    viewModel: EduFlowViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val subjectsState by viewModel.subjectsState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val subjects = (subjectsState as? UiState.Success)?.data ?: emptyList()
    val notes = (notesState as? UiState.Success)?.data ?: emptyList()
    val existingNote = remember(notes, noteId) {
        if (noteId.isNullOrBlank()) null else notes.firstOrNull { it.id == noteId }
    }

    var initialized by remember(noteId) { mutableStateOf(false) }
    var selectedSubjectId by remember { mutableStateOf(initialSubjectId ?: "") }
    var newSubjectName by remember { mutableStateOf("General Study") }
    var title by remember { mutableStateOf("") }
    var extractedText by remember { mutableStateOf("") }
    var photoTag by remember { mutableStateOf("") }
    var thumbnailBase64 by remember { mutableStateOf("") }
    var highlights by remember { mutableStateOf<List<String>>(emptyList()) }
    var definitions by remember { mutableStateOf<List<DefinitionItem>>(emptyList()) }

    var activeTab by remember { mutableStateOf(EditorTab.EDIT) }
    var editingDefinition by remember { mutableStateOf<DefinitionItem?>(null) }
    var showAddDefinitionDialog by remember { mutableStateOf(false) }
    var prefillTerm by remember { mutableStateOf("") }
    var prefillDef by remember { mutableStateOf("") }
    var showCustomHighlightDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var consumedInitialPhoto by remember { mutableStateOf(false) }

    LaunchedEffect(existingNote, subjects) {
        if (!initialized) {
            if (existingNote != null) {
                selectedSubjectId = existingNote.subjectId
                title = existingNote.title
                extractedText = existingNote.extractedText
                photoTag = existingNote.photoTag
                thumbnailBase64 = existingNote.thumbnailBase64
                highlights = existingNote.highlights
                definitions = existingNote.definitions
                initialized = true
            } else if (noteId.isNullOrBlank()) {
                if (selectedSubjectId.isBlank() && subjects.isNotEmpty()) {
                    selectedSubjectId = subjects.first().id
                }
                initialized = true
            }
        }
    }

    LaunchedEffect(initialPhotoUri) {
        if (initialPhotoUri != null && !consumedInitialPhoto) {
            consumedInitialPhoto = true
            viewModel.extractNoteFromPhoto(context, initialPhotoUri)
        }
    }

    LaunchedEffect(aiState) {
        when (val state = aiState) {
            is AiExtractionState.Success -> {
                if (title.isBlank()) title = state.result.title
                extractedText = if (extractedText.isBlank()) {
                    state.result.extractedText
                } else {
                    "$extractedText\n\n${state.result.extractedText}"
                }
                if (photoTag.isBlank()) photoTag = state.result.suggestedPhotoTag
                if (state.thumbnailBase64.isNotBlank()) thumbnailBase64 = state.thumbnailBase64
                highlights = (highlights + state.result.suggestedHighlights).distinct().take(40)
                val existingTerms = definitions.map { it.term.lowercase() }.toSet()
                val newDefs = state.result.definitions.filter { it.term.lowercase() !in existingTerms }
                definitions = (definitions + newDefs).take(40)
                activeTab = EditorTab.HIGHLIGHT
                viewModel.resetAiState()
            }

            is AiExtractionState.DefinitionsFound -> {
                highlights = (highlights + state.highlights).distinct().take(40)
                val existingTerms = definitions.map { it.term.lowercase() }.toSet()
                val newDefs = state.definitions.filter { it.term.lowercase() !in existingTerms }
                definitions = (definitions + newDefs).take(40)
                activeTab = EditorTab.DEFINITIONS
                viewModel.resetAiState()
                snackbarHostState.showSnackbar("Found ${newDefs.size} new key definitions!")
            }

            else -> Unit
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.extractNoteFromPhoto(context, uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = pendingCameraUri
        if (success && captured != null) {
            viewModel.extractNoteFromPhoto(context, captured)
        }
    }

    val textSentences = remember(extractedText) {
        extractedText
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length >= 4 }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (noteId.isNullOrBlank()) "New Study Note" else "Edit & Highlight",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            viewModel.saveNote(
                                existingNoteId = noteId,
                                subjectId = selectedSubjectId.ifBlank { null },
                                fallbackSubjectName = newSubjectName,
                                title = title.ifBlank { "Study Note" },
                                extractedText = extractedText,
                                photoTag = photoTag,
                                thumbnailBase64 = thumbnailBase64,
                                highlights = highlights,
                                definitions = definitions,
                                onSaved = { onBack() }
                            )
                        },
                        enabled = !isSaving && aiState !is AiExtractionState.Extracting,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .height(48.dp)
                            .testTag("save_note_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.save_note))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    if (subjects.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Subject",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                subjects.forEach { subject ->
                                    FilterChip(
                                        selected = selectedSubjectId == subject.id,
                                        onClick = { selectedSubjectId = subject.id },
                                        label = { Text(subject.name) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = newSubjectName,
                            onValueChange = { if (it.length <= 60) newSubjectName = it },
                            label = { Text("Create Subject for this Note") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val uri = createTempCameraUri(context)
                                        pendingCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    },
                                    enabled = aiState !is AiExtractionState.Extracting,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("editor_camera_button")
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.take_photo), maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = aiState !is AiExtractionState.Extracting,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("editor_gallery_button")
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.pick_photo), maxLines = 1)
                                }
                            }

                            if (aiState is AiExtractionState.Extracting) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                        Text(
                                            text = stringResource(R.string.extracting_text),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            if (aiState is AiExtractionState.Error) {
                                val errMsg = (aiState as AiExtractionState.Error).message
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = errMsg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            if (thumbnailBase64.isNotBlank() || photoTag.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (thumbnailBase64.isNotBlank()) {
                                        Base64ThumbnailImage(
                                            base64 = thumbnailBase64,
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                        )
                                    }
                                    OutlinedTextField(
                                        value = photoTag,
                                        onValueChange = { if (it.length <= 80) photoTag = it },
                                        label = { Text("Photo Tag / Page Label") },
                                        placeholder = { Text("e.g., Bio Ch.4 Page 12") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                OutlinedTextField(
                                    value = photoTag,
                                    onValueChange = { if (it.length <= 80) photoTag = it },
                                    label = { Text("Source / Page Tag (optional)") },
                                    placeholder = { Text("e.g., Lecture Notes p. 4") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 120) title = it },
                        label = { Text("Note Title") },
                        placeholder = { Text("e.g., Cellular Respiration") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_title_input")
                    )
                }

                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        EditorTab.entries.forEachIndexed { index, tab ->
                            SegmentedButton(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = EditorTab.entries.size
                                ),
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            EditorTab.EDIT -> Icons.Default.Edit
                                            EditorTab.HIGHLIGHT -> Icons.Default.Brush
                                            EditorTab.DEFINITIONS -> Icons.Default.MenuBook
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            ) {
                                val badge = when (tab) {
                                    EditorTab.EDIT -> tab.label
                                    EditorTab.HIGHLIGHT -> "Highlight (${highlights.size})"
                                    EditorTab.DEFINITIONS -> "Defs (${definitions.size})"
                                }
                                Text(badge, maxLines = 1)
                            }
                        }
                    }
                }

                when (activeTab) {
                    EditorTab.EDIT -> {
                        item {
                            OutlinedTextField(
                                value = extractedText,
                                onValueChange = { if (it.length <= 25000) extractedText = it },
                                label = { Text("Editable Study Text") },
                                placeholder = {
                                    Text("Scan a printed or handwritten page above, or type/paste your class notes here…")
                                },
                                minLines = 8,
                                maxLines = 18,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("note_text_input")
                            )
                        }
                        item {
                            FilledTonalButton(
                                onClick = { viewModel.findDefinitionsInText(extractedText) },
                                enabled = extractedText.isNotBlank() && aiState !is AiExtractionState.Extracting,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("auto_find_definitions_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.find_definitions))
                            }
                        }
                    }

                    EditorTab.HIGHLIGHT -> {
                        item {
                            val annotatedPreview = buildHighlightedAnnotatedString(
                                fullText = extractedText,
                                highlights = highlights,
                                definitions = definitions,
                                highlightBg = HighlightInkRose,
                                termBg = MaterialTheme.colorScheme.primaryContainer,
                                termColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
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
                                        Text(
                                            text = "Pink Highlighted Preview",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        TextButton(onClick = { showCustomHighlightDialog = true }) {
                                            Text("+ Custom Phrase")
                                        }
                                    }
                                    Text(
                                        text = if (extractedText.isBlank()) {
                                            androidx.compose.ui.text.AnnotatedString(
                                                "No text yet. Scan a page or type notes in Edit Text first."
                                            )
                                        } else {
                                            annotatedPreview
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (textSentences.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Tap any line below to highlight in pink or tag as a Key Definition:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            items(textSentences) { sentence ->
                                val isHighlighted = highlights.any { it.equals(sentence, ignoreCase = true) }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isHighlighted) {
                                        HighlightInkRose.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    border = BorderStroke(
                                        width = if (isHighlighted) 1.5.dp else 1.dp,
                                        color = if (isHighlighted) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            highlights = if (isHighlighted) {
                                                highlights.filterNot { it.equals(sentence, ignoreCase = true) }
                                            } else {
                                                (highlights + sentence.take(400)).take(40)
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Brush,
                                            contentDescription = if (isHighlighted) "Highlighted" else "Tap to highlight",
                                            tint = if (isHighlighted) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = sentence,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilledTonalButton(
                                            onClick = {
                                                if (!isHighlighted) {
                                                    highlights = (highlights + sentence.take(400)).take(40)
                                                }
                                                val splitIdx = sentence.indexOf(':')
                                                if (splitIdx in 2..50) {
                                                    prefillTerm = sentence.substring(0, splitIdx).trim()
                                                    prefillDef = sentence.substring(splitIdx + 1).trim()
                                                } else {
                                                    prefillTerm = sentence.split(" ").take(3).joinToString(" ")
                                                    prefillDef = sentence
                                                }
                                                showAddDefinitionDialog = true
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Text("+ Def", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    EditorTab.DEFINITIONS -> {
                        item {
                            OutlinedButton(
                                onClick = {
                                    prefillTerm = ""
                                    prefillDef = ""
                                    showAddDefinitionDialog = true
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("add_definition_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Key Definition")
                            }
                        }
                        if (definitions.isEmpty()) {
                            item {
                                Text(
                                    text = "No key definitions tagged yet. Use Auto-Find Definitions or tap + Def on any line in Highlight mode.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(definitions, key = { it.id }) { def ->
                                DefinitionReviewerCard(
                                    definition = def,
                                    onEdit = { editingDefinition = def },
                                    onDelete = { definitions = definitions.filterNot { it.id == def.id } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDefinitionDialog) {
        DefinitionEditorDialog(
            initialTerm = prefillTerm,
            initialDefinition = prefillDef,
            onDismiss = { showAddDefinitionDialog = false },
            onConfirm = { term, def, color ->
                showAddDefinitionDialog = false
                val newItem = DefinitionItem(
                    id = UUID.randomUUID().toString(),
                    term = term,
                    definition = def,
                    highlightColor = color.name
                )
                definitions = (definitions + newItem).take(40)
            }
        )
    }

    editingDefinition?.let { target ->
        DefinitionEditorDialog(
            initialTerm = target.term,
            initialDefinition = target.definition,
            initialColor = target.resolvedColor(),
            titleText = "Edit Key Definition",
            onDismiss = { editingDefinition = null },
            onConfirm = { term, def, color ->
                editingDefinition = null
                definitions = definitions.map {
                    if (it.id == target.id) {
                        it.copy(term = term, definition = def, highlightColor = color.name)
                    } else {
                        it
                    }
                }
            }
        )
    }

    if (showCustomHighlightDialog) {
        var phrase by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCustomHighlightDialog = false },
            title = { Text("Highlight Custom Phrase") },
            text = {
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { if (it.length <= 400) phrase = it },
                    label = { Text("Phrase from note") },
                    placeholder = { Text("Type exact word or phrase to highlight in pink") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = phrase.trim()
                        if (clean.isNotEmpty()) {
                            highlights = (highlights + clean).distinct().take(40)
                        }
                        showCustomHighlightDialog = false
                    },
                    enabled = phrase.isNotBlank()
                ) {
                    Text("Highlight")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomHighlightDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
