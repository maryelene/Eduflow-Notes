package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.firebase.signOutUser
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.UiState
import com.example.ui.components.DefinitionReviewerCard
import com.example.ui.components.SubjectEditorDialog
import com.example.ui.components.toAccentColor
import com.example.ui.components.toImageVector
import com.example.ui.viewmodel.EduFlowViewModel
import java.io.File
import java.util.UUID

fun createTempCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
    val file = File(dir, "scan_${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsHomeScreen(
    viewModel: EduFlowViewModel,
    userDisplayName: String,
    onOpenSubject: (String) -> Unit,
    onOpenNoteEditor: (subjectId: String?, noteId: String?, photoUri: Uri?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val subjectsState by viewModel.subjectsState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }
    var showCreateSubjectDialog by remember { mutableStateOf(false) }
    var editingSubject by remember { mutableStateOf<Subject?>(null) }
    var deletingSubject by remember { mutableStateOf<Subject?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onOpenNoteEditor(null, null, uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = pendingCameraUri
        if (success && captured != null) {
            onOpenNoteEditor(null, null, captured)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val subjects = (subjectsState as? UiState.Success)?.data ?: emptyList()
    val notes = (notesState as? UiState.Success)?.data ?: emptyList()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (userDisplayName.isNotBlank()) {
                            Text(
                                text = "Hi, $userDisplayName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { signOutUser(credentialManager, scope) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = stringResource(R.string.sign_out),
                            tint = MaterialTheme.colorScheme.primary
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
                onClick = { showCreateSubjectDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_subject)) },
                modifier = Modifier.testTag("add_subject_fab")
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
                    QuickScanHeroCard(
                        onTakePhoto = {
                            val uri = createTempCameraUri(context)
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        },
                        onPickPhoto = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onBlankNote = {
                            onOpenNoteEditor(subjects.firstOrNull()?.id, null, null)
                        }
                    )
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search subjects, notes, or key definitions…") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_notes_input")
                    )
                }

                val trimmedQuery = searchQuery.trim().lowercase()
                if (trimmedQuery.isNotEmpty()) {
                    val matchingNotes = notes.filter { note ->
                        note.title.lowercase().contains(trimmedQuery) ||
                            note.extractedText.lowercase().contains(trimmedQuery) ||
                            note.photoTag.lowercase().contains(trimmedQuery) ||
                            note.definitions.any {
                                it.term.lowercase().contains(trimmedQuery) ||
                                    it.definition.lowercase().contains(trimmedQuery)
                            }
                    }
                    item {
                        Text(
                            text = "Matching Study Results (${matchingNotes.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (matchingNotes.isEmpty()) {
                        item {
                            Text(
                                text = "No notes or definitions match \"$searchQuery\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(matchingNotes, key = { it.id }) { note ->
                            val subjectName = subjects.firstOrNull { it.id == note.subjectId }?.name ?: "Study"
                            SearchNoteResultCard(
                                note = note,
                                subjectName = subjectName,
                                onClick = { onOpenNoteEditor(note.subjectId, note.id, null) }
                            )
                        }
                        val matchingDefs = matchingNotes.flatMap { note ->
                            note.definitions.filter {
                                it.term.lowercase().contains(trimmedQuery) ||
                                    it.definition.lowercase().contains(trimmedQuery)
                            }.map { def -> Triple(note, def, note.photoTag) }
                        }
                        if (matchingDefs.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Matching Definitions",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(matchingDefs, key = { "${it.first.id}_${it.second.id}" }) { (note, def, tag) ->
                                DefinitionReviewerCard(
                                    definition = def,
                                    sourceNoteTitle = note.title,
                                    photoTag = tag,
                                    onOpenNote = { onOpenNoteEditor(note.subjectId, note.id, null) }
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.subjects_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${subjects.size} subjects • ${notes.sumOf { it.definitions.size }} definitions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    when (val state = subjectsState) {
                        is UiState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        is UiState.Error -> {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.message,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }

                        is UiState.Success -> {
                            if (state.data.isEmpty()) {
                                item {
                                    EmptySubjectsCard(
                                        onCreateSubject = { showCreateSubjectDialog = true }
                                    )
                                }
                            } else {
                                item {
                                    SubjectResponsiveGrid(
                                        subjects = state.data,
                                        notes = notes,
                                        onOpenSubject = onOpenSubject,
                                        onEditSubject = { editingSubject = it },
                                        onDeleteSubject = { deletingSubject = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSubjectDialog) {
        SubjectEditorDialog(
            onDismiss = { showCreateSubjectDialog = false },
            onConfirm = { name, color, icon ->
                showCreateSubjectDialog = false
                viewModel.createSubject(name, color, icon)
            }
        )
    }

    editingSubject?.let { subject ->
        SubjectEditorDialog(
            initialName = subject.name,
            initialColor = subject.resolvedColor(),
            initialIcon = subject.resolvedIcon(),
            titleText = "Edit Subject",
            confirmLabel = "Save",
            onDismiss = { editingSubject = null },
            onConfirm = { name, color, icon ->
                editingSubject = null
                viewModel.updateSubject(subject.id, name, color, icon)
            }
        )
    }

    deletingSubject?.let { subject ->
        AlertDialog(
            onDismissRequest = { deletingSubject = null },
            title = { Text("Delete ${subject.name}?") },
            text = { Text("This will remove the subject and all notes & definitions saved inside it.") },
            confirmButton = {
                Button(
                    onClick = {
                        val id = subject.id
                        deletingSubject = null
                        viewModel.deleteSubject(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSubject = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun QuickScanHeroCard(
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onBlankNote: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Extract Text & Highlight Definitions",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Snap printed pages or clean handwriting to build your subject reviewer.",
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
                            .testTag("home_camera_scan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.take_photo), maxLines = 1)
                    }

                    FilledTonalButton(
                        onClick = onPickPhoto,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("home_gallery_scan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.pick_photo), maxLines = 1)
                    }
                }

                OutlinedButton(
                    onClick = onBlankNote,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("home_type_note_button")
                ) {
                    Text("Or type / paste a new note")
                }
            }
        }
    }
}

@Composable
private fun SubjectResponsiveGrid(
    subjects: List<Subject>,
    notes: List<StudyNote>,
    onOpenSubject: (String) -> Unit,
    onEditSubject: (Subject) -> Unit,
    onDeleteSubject: (Subject) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 600.dp) 2 else 1
        val rows = subjects.chunked(columns)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { rowSubjects ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowSubjects.forEach { subject ->
                        val subjectNotes = notes.filter { it.subjectId == subject.id }
                        SubjectCard(
                            subject = subject,
                            noteCount = subjectNotes.size,
                            definitionCount = subjectNotes.sumOf { it.definitions.size },
                            photoCount = subjectNotes.count {
                                it.thumbnailBase64.isNotBlank() || it.photoTag.isNotBlank()
                            },
                            onClick = { onOpenSubject(subject.id) },
                            onEdit = { onEditSubject(subject) },
                            onDelete = { onDeleteSubject(subject) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowSubjects.size < columns) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectCard(
    subject: Subject,
    noteCount: Int,
    definitionCount: Int,
    photoCount: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val accent = subject.resolvedColor().toAccentColor()

    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("subject_card_${subject.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = accent.copy(alpha = 0.16f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = subject.resolvedIcon().toImageVector(),
                        contentDescription = subject.name,
                        tint = accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = subject.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$noteCount notes • $definitionCount definitions • $photoCount photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Subject options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Subject") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Subject", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchNoteResultCard(
    note: StudyNote,
    subjectName: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = subjectName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (note.extractedText.isNotBlank()) {
                Text(
                    text = note.extractedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptySubjectsCard(onCreateSubject: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_notes),
                contentDescription = stringResource(R.string.cd_empty_illustration),
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = "Organize your first subject",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Create a subject like Biology or History, or tap Camera Snap above to scan a page right away.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onCreateSubject,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("empty_create_subject_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create First Subject")
            }
        }
    }
}
