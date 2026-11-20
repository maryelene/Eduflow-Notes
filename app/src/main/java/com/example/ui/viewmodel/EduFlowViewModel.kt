package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.ExtractedNoteResult
import com.example.data.firebase.ImageUtils
import com.example.data.firebase.NoteAiExtractor
import com.example.data.firebase.NotesRepository
import com.example.data.model.DefinitionItem
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.SubjectColor
import com.example.data.model.SubjectIcon
import com.example.data.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AiExtractionState {
    data object Idle : AiExtractionState
    data object Extracting : AiExtractionState
    data class Success(val result: ExtractedNoteResult, val thumbnailBase64: String) : AiExtractionState
    data class DefinitionsFound(
        val definitions: List<DefinitionItem>,
        val highlights: List<String>
    ) : AiExtractionState
    data class Error(val message: String) : AiExtractionState
}

class EduFlowViewModel(
    private val repository: NotesRepository,
    private val aiExtractor: NoteAiExtractor,
    val currentUserId: String
) : ViewModel() {

    val subjectsState: StateFlow<UiState<List<Subject>>> = repository.observeSubjects(currentUserId)
        .map<List<Subject>, UiState<List<Subject>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing subjects", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load subjects."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val notesState: StateFlow<UiState<List<StudyNote>>> = repository.observeNotes(currentUserId)
        .map<List<StudyNote>, UiState<List<StudyNote>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing notes", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load study notes."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _aiState = MutableStateFlow<AiExtractionState>(AiExtractionState.Idle)
    val aiState: StateFlow<AiExtractionState> = _aiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun resetAiState() {
        _aiState.value = AiExtractionState.Idle
    }

    fun extractNoteFromPhoto(context: Context, uri: Uri) {
        viewModelScope.launch {
            _aiState.value = AiExtractionState.Extracting
            val decodedResult = ImageUtils.decodeForVisionAndThumbnail(context, uri)
            if (decodedResult.isFailure) {
                val msg = decodedResult.exceptionOrNull()?.localizedMessage ?: "Failed to open photo."
                _aiState.value = AiExtractionState.Error(msg)
                return@launch
            }
            val (bitmap, thumbnailBase64) = decodedResult.getOrThrow()
            try {
                val aiResult = aiExtractor.extractFromImage(bitmap)
                aiResult.fold(
                    onSuccess = { extracted ->
                        _aiState.value = AiExtractionState.Success(extracted, thumbnailBase64)
                    },
                    onFailure = { err ->
                        _aiState.value = AiExtractionState.Error(
                            err.localizedMessage ?: "Could not read text from this photo."
                        )
                    }
                )
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    fun findDefinitionsInText(text: String) {
        viewModelScope.launch {
            _aiState.value = AiExtractionState.Extracting
            val result = aiExtractor.extractDefinitionsFromText(text)
            result.fold(
                onSuccess = { (defs, highlights) ->
                    _aiState.value = AiExtractionState.DefinitionsFound(defs, highlights)
                },
                onFailure = { err ->
                    _aiState.value = AiExtractionState.Error(
                        err.localizedMessage ?: "Could not extract definitions from text."
                    )
                }
            )
        }
    }

    fun createSubject(
        name: String,
        color: SubjectColor,
        icon: SubjectIcon,
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.createSubject(currentUserId, name, color, icon)
            _isSaving.value = false
            result.fold(
                onSuccess = { id ->
                    _events.tryEmit("Subject \"$name\" created")
                    onCreated(id)
                },
                onFailure = { err ->
                    _events.tryEmit(err.localizedMessage ?: "Failed to create subject.")
                }
            )
        }
    }

    fun updateSubject(
        subjectId: String,
        name: String,
        color: SubjectColor,
        icon: SubjectIcon
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateSubject(currentUserId, subjectId, name, color, icon)
            _isSaving.value = false
            result.fold(
                onSuccess = { _events.tryEmit("Subject updated") },
                onFailure = { err -> _events.tryEmit(err.localizedMessage ?: "Failed to update subject.") }
            )
        }
    }

    fun deleteSubject(subjectId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteSubject(currentUserId, subjectId)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _events.tryEmit("Subject deleted")
                    onDeleted()
                },
                onFailure = { err ->
                    _events.tryEmit(err.localizedMessage ?: "Failed to delete subject.")
                }
            )
        }
    }

    fun saveNote(
        existingNoteId: String?,
        subjectId: String?,
        fallbackSubjectName: String,
        title: String,
        extractedText: String,
        photoTag: String,
        thumbnailBase64: String,
        highlights: List<String>,
        definitions: List<DefinitionItem>,
        onSaved: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val resolvedSubjectId = if (!subjectId.isNullOrBlank()) {
                    subjectId
                } else {
                    val subjectName = fallbackSubjectName.trim().ifEmpty { "General Study" }
                    val createSubjRes = repository.createSubject(
                        userId = currentUserId,
                        name = subjectName,
                        color = SubjectColor.ROSE,
                        icon = SubjectIcon.BOOK
                    )
                    if (createSubjRes.isFailure) {
                        _events.tryEmit(
                            createSubjRes.exceptionOrNull()?.localizedMessage
                                ?: "Failed to create subject for note."
                        )
                        return@launch
                    }
                    createSubjRes.getOrThrow()
                }

                if (existingNoteId.isNullOrBlank()) {
                    val res = repository.createNote(
                        userId = currentUserId,
                        subjectId = resolvedSubjectId,
                        title = title,
                        extractedText = extractedText,
                        photoTag = photoTag,
                        thumbnailBase64 = thumbnailBase64,
                        highlights = highlights,
                        definitions = definitions
                    )
                    res.fold(
                        onSuccess = { newId ->
                            _events.tryEmit("Study note saved")
                            onSaved(newId)
                        },
                        onFailure = { err ->
                            _events.tryEmit(err.localizedMessage ?: "Failed to save note.")
                        }
                    )
                } else {
                    val res = repository.updateNote(
                        userId = currentUserId,
                        noteId = existingNoteId,
                        subjectId = resolvedSubjectId,
                        title = title,
                        extractedText = extractedText,
                        photoTag = photoTag,
                        thumbnailBase64 = thumbnailBase64,
                        highlights = highlights,
                        definitions = definitions
                    )
                    res.fold(
                        onSuccess = {
                            _events.tryEmit("Study note updated")
                            onSaved(existingNoteId)
                        },
                        onFailure = { err ->
                            _events.tryEmit(err.localizedMessage ?: "Failed to update note.")
                        }
                    )
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteNote(noteId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteNote(currentUserId, noteId)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _events.tryEmit("Note deleted")
                    onDeleted()
                },
                onFailure = { err ->
                    _events.tryEmit(err.localizedMessage ?: "Failed to delete note.")
                }
            )
        }
    }

    private companion object {
        const val TAG = "EduFlowVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
