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

    fun observeNotes(userId: String): Flow<List<StudyNote>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/notes"
        emitAll(
            db.collection("users").document(uid).collection("notes")
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
    ): Result<String> {
        val cleanTitle = title.trim().ifEmpty { "Untitled Note" }.take(120)
        var docPath = "users/$userId/notes"
        return try {
            val uid = requireUserId(userId)
            require(subjectId.isNotBlank()) { "Please select a subject first." }
            require(extractedText.length <= 25000) { "Note text exceeds 25,000 characters." }
            require(thumbnailBase64.length <= 120000) { "Photo thumbnail exceeds size limit." }

            val docRef = db.collection("users").document(uid).collection("notes").document()
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
        photoTag: String,
        thumbnailBase64: String,
        highlights: List<String>,
        definitions: List<DefinitionItem>
    ): Result<Unit> {
        val cleanTitle = title.trim().ifEmpty { "Untitled Note" }.take(120)
        val docPath = "users/$userId/notes/$noteId"
        return try {
            val uid = requireUserId(userId)
            require(noteId.isNotBlank()) { "Note ID cannot be blank." }
            require(subjectId.isNotBlank()) { "Please select a subject." }
            require(extractedText.length <= 25000) { "Note text exceeds 25,000 characters." }
            require(thumbnailBase64.length <= 120000) { "Photo thumbnail exceeds size limit." }

            db.collection("users").document(uid).collection("notes").document(noteId)
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
            require(noteId.isNotBlank()) { "Note ID cannot be blank." }
            db.collection("users").document(uid).collection("notes").document(noteId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
