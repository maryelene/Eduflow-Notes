package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SubjectColor(val label: String) {
    ROSE("Rose Pink"),
    BERRY("Berry"),
    BLUSH("Soft Blush"),
    CORAL("Coral Pink"),
    PLUM("Plum Rose"),
    MAGENTA("Magenta");

    companion object {
        fun fromRaw(raw: String?): SubjectColor =
            entries.firstOrNull { it.name == raw } ?: ROSE
    }
}

enum class SubjectIcon(val label: String) {
    BOOK("General"),
    SCIENCE("Science"),
    HISTORY("History"),
    MATH("Math"),
    LANGUAGE("Languages"),
    ART("Arts");

    companion object {
        fun fromRaw(raw: String?): SubjectIcon =
            entries.firstOrNull { it.name == raw } ?: BOOK
    }
}

enum class HighlightColor(val label: String) {
    ROSE("Rose Ink"),
    BERRY("Berry Glow"),
    CORAL("Coral Blush");

    companion object {
        fun fromRaw(raw: String?): HighlightColor =
            entries.firstOrNull { it.name == raw } ?: ROSE
    }
}

@Keep
@IgnoreExtraProperties
data class Subject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val colorTag: String = SubjectColor.ROSE.name,
    val iconName: String = SubjectIcon.BOOK.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedColor(): SubjectColor = SubjectColor.fromRaw(colorTag)
    fun resolvedIcon(): SubjectIcon = SubjectIcon.fromRaw(iconName)

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.trim().take(60),
        "colorTag" to resolvedColor().name,
        "iconName" to resolvedIcon().name,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class DefinitionItem(
    val id: String = "",
    val term: String = "",
    val definition: String = "",
    val highlightColor: String = HighlightColor.ROSE.name
) {
    fun resolvedColor(): HighlightColor = HighlightColor.fromRaw(highlightColor)

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "term" to term.trim().take(120),
        "definition" to definition.trim().take(1000),
        "highlightColor" to resolvedColor().name
    )
}

@Keep
@IgnoreExtraProperties
data class StudyNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val title: String = "",
    val extractedText: String = "",
    val photoTag: String = "",
    val thumbnailBase64: String = "",
    val highlights: List<String> = emptyList(),
    val definitions: List<DefinitionItem> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "subjectId" to subjectId,
        "title" to title.trim().take(120),
        "extractedText" to extractedText.take(25000),
        "photoTag" to photoTag.trim().take(80),
        "thumbnailBase64" to thumbnailBase64.take(120000),
        "highlights" to highlights.take(40),
        "definitions" to definitions.take(40).map { it.toMap() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
