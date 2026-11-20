package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.DefinitionItem
import com.example.data.model.StudyNote
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StudyNoteRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user operation." }
        }
        return uid
    }

    private fun notesCollection(uid: String) =
        db.collection("users").document(uid).collection("notes")

    fun observeNotes(userId: String): Flow<List<StudyNote>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/notes"
        emitAll(
            notesCollection(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudyNote::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeNotesBySubject(userId: String, subjectId: String): Flow<List<StudyNote>> = flow {
        val uid = requireUserId(userId)
        require(subjectId.isNotBlank()) { "Subject ID cannot be blank." }
        val path = "users/$uid/notes"
        emitAll(
            notesCollection(uid)
                .whereEqualTo("subjectId", subjectId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudyNote::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getNotes(userId: String): Result<List<StudyNote>> {
        val path = "users/$userId/notes"
        return try {
            val uid = requireUserId(userId)
            val snapshot = notesCollection(uid).get().await()
            val notes = snapshot.toObjects(
                StudyNote::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedByDescending { it.updatedAt ?: it.createdAt }
            Result.success(notes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun getNote(userId: String, noteId: String): Result<StudyNote?> {
        val docPath = "users/$userId/notes/$noteId"
        return try {
            val uid = requireUserId(userId)
            require(noteId.isNotBlank()) { "Note ID cannot be blank." }
            val snapshot = notesCollection(uid).document(noteId).get().await()
            val note = if (snapshot.exists()) {
                snapshot.toObject(
                    StudyNote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
            Result.success(note)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveNote(userId: String, note: StudyNote): Result<String> {
        return if (note.id.isBlank()) {
            createNote(
                userId = userId,
                subjectId = note.subjectId,
                title = note.title,
                extractedText = note.extractedText,
                photoTag = note.photoTag,
                thumbnailBase64 = note.thumbnailBase64,
                highlights = note.highlights,
                definitions = note.definitions
            )
        } else {
            updateNote(
                userId = userId,
                noteId = note.id,
                subjectId = note.subjectId,
                title = note.title,
                extractedText = note.extractedText,
                photoTag = note.photoTag,
                thumbnailBase64 = note.thumbnailBase64,
                highlights = note.highlights,
                definitions = note.definitions
            ).map { note.id }
        }
    }

    suspend fun createNote(
        userId: String,
        subjectId: String,
        title: String,
        extractedText: String,
        photoTag: String = "",
        thumbnailBase64: String = "",
        highlights: List<String> = emptyList(),
        definitions: List<DefinitionItem> = emptyList()
    ): Result<String> {
        val cleanTitle = title.trim().ifEmpty { "Untitled Note" }.take(120)
        var docPath = "users/$userId/notes"
        return try {
            val uid = requireUserId(userId)
            validateNoteFields(subjectId, extractedText, photoTag, thumbnailBase64)

            val docRef = notesCollection(uid).document()
            docPath = docRef.path
            val note = StudyNote(
                userId = uid,
                subjectId = subjectId,
                title = cleanTitle,
                extractedText = extractedText,
                photoTag = photoTag.trim().take(80),
                thumbnailBase64 = thumbnailBase64,
                highlights = highlights.take(40),
                definitions = definitions.take(40)
            )
            docRef.set(note.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateNote(
        userId: String,
        noteId: String,
        subjectId: String,
        title: String,
        extractedText: String,
        photoTag: String = "",
        thumbnailBase64: String = "",
        highlights: List<String> = emptyList(),
        definitions: List<DefinitionItem> = emptyList()
    ): Result<Unit> {
        val cleanTitle = title.trim().ifEmpty { "Untitled Note" }.take(120)
        val docPath = "users/$userId/notes/$noteId"
        return try {
            val uid = requireUserId(userId)
            require(noteId.isNotBlank() && ID_REGEX.matches(noteId)) {
                "Note ID is invalid."
            }
            validateNoteFields(subjectId, extractedText, photoTag, thumbnailBase64)

            notesCollection(uid).document(noteId)
                .update(
                    mapOf(
                        "subjectId" to subjectId,
                        "title" to cleanTitle,
                        "extractedText" to extractedText,
                        "photoTag" to photoTag.trim().take(80),
                        "thumbnailBase64" to thumbnailBase64,
                        "highlights" to highlights.take(40),
                        "definitions" to definitions.take(40).map { it.toMap() },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(userId: String, noteId: String): Result<Unit> {
        val docPath = "users/$userId/notes/$noteId"
        return try {
            val uid = requireUserId(userId)
            require(noteId.isNotBlank() && ID_REGEX.matches(noteId)) {
                "Note ID cannot be blank."
            }
            notesCollection(uid).document(noteId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun validateNoteFields(
        subjectId: String,
        extractedText: String,
        photoTag: String,
        thumbnailBase64: String
    ) {
        require(subjectId.isNotBlank() && subjectId.length <= 128 && ID_REGEX.matches(subjectId)) {
            "Please select a valid subject."
        }
        require(extractedText.length <= 25000) { "Note text exceeds 25,000 characters." }
        require(photoTag.trim().length <= 80) { "Photo tag exceeds 80 characters." }
        require(thumbnailBase64.length <= 120000) { "Photo thumbnail exceeds size limit." }
    }

    private companion object {
        val ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
    }
}
