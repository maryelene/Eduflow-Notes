package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.DefinitionItem
import com.example.data.model.StudyNote
import com.example.data.model.Subject
import com.example.data.model.SubjectColor
import com.example.data.model.SubjectIcon
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

class NotesRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val studyNoteRepository: StudyNoteRepository = StudyNoteRepository(db, auth)
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

    fun observeSubjects(userId: String): Flow<List<Subject>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/subjects"
        emitAll(
            db.collection("users").document(uid).collection("subjects")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Subject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.name.lowercase() }
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

    fun observeNotes(userId: String): Flow<List<StudyNote>> =
        studyNoteRepository.observeNotes(userId)

    fun observeNotesBySubject(userId: String, subjectId: String): Flow<List<StudyNote>> =
        studyNoteRepository.observeNotesBySubject(userId, subjectId)

    suspend fun getNotes(userId: String): Result<List<StudyNote>> =
        studyNoteRepository.getNotes(userId)

    suspend fun getNote(userId: String, noteId: String): Result<StudyNote?> =
        studyNoteRepository.getNote(userId, noteId)

    suspend fun saveNote(userId: String, note: StudyNote): Result<String> =
        studyNoteRepository.saveNote(userId, note)

    suspend fun createSubject(
        userId: String,
        name: String,
        color: SubjectColor,
        icon: SubjectIcon
    ): Result<String> {
        val cleanName = name.trim()
        var docPath = "users/$userId/subjects"
        return try {
            val uid = requireUserId(userId)
            require(cleanName.isNotEmpty() && cleanName.length <= 60) {
                "Subject name must be 1 to 60 characters."
            }
            val docRef = db.collection("users").document(uid).collection("subjects").document()
            docPath = docRef.path
            val subject = Subject(
                userId = uid,
                name = cleanName,
                colorTag = color.name,
                iconName = icon.name
            )
            docRef.set(subject.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSubject(
        userId: String,
        subjectId: String,
        name: String,
        color: SubjectColor,
        icon: SubjectIcon
    ): Result<Unit> {
        val cleanName = name.trim()
        val docPath = "users/$userId/subjects/$subjectId"
        return try {
            val uid = requireUserId(userId)
            require(subjectId.isNotBlank()) { "Subject ID cannot be blank." }
            require(cleanName.isNotEmpty() && cleanName.length <= 60) {
                "Subject name must be 1 to 60 characters."
            }
            db.collection("users").document(uid).collection("subjects").document(subjectId)
                .update(
                    mapOf(
                        "name" to cleanName,
                        "colorTag" to color.name,
                        "iconName" to icon.name,
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

    suspend fun deleteSubject(userId: String, subjectId: String): Result<Unit> {
        val docPath = "users/$userId/subjects/$subjectId"
        return try {
            val uid = requireUserId(userId)
            require(subjectId.isNotBlank()) { "Subject ID cannot be blank." }
            val notesSnapshot = db.collection("users").document(uid).collection("notes")
                .whereEqualTo("subjectId", subjectId)
                .get()
                .await()
            val batch = db.batch()
            for (noteDoc in notesSnapshot.documents) {
                batch.delete(noteDoc.reference)
            }
            val subjectRef = db.collection("users").document(uid).collection("subjects").document(subjectId)
            batch.delete(subjectRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createNote(
        userId: String,
        subjectId: String,
        title: String,
        extractedText: String,
        photoTag: String,
        thumbnailBase64: String,
        highlights: List<String>,
        definitions: List<DefinitionItem>
    ): Result<String> = studyNoteRepository.createNote(
        userId = userId,
        subjectId = subjectId,
        title = title,
        extractedText = extractedText,
        photoTag = photoTag,
        thumbnailBase64 = thumbnailBase64,
        highlights = highlights,
        definitions = definitions
    )

    suspend fun updateNote(
        userId: String,
        noteId: String,
        subjectId: String,
        title: String,
        extractedText: String,
        photoTag: String,
        thumbnailBase64: String,
        highlights: List<String>,
        definitions: List<DefinitionItem>
    ): Result<Unit> = studyNoteRepository.updateNote(
        userId = userId,
        noteId = noteId,
        subjectId = subjectId,
        title = title,
        extractedText = extractedText,
        photoTag = photoTag,
        thumbnailBase64 = thumbnailBase64,
        highlights = highlights,
        definitions = definitions
    )

    suspend fun deleteNote(userId: String, noteId: String): Result<Unit> =
        studyNoteRepository.deleteNote(userId, noteId)
}
