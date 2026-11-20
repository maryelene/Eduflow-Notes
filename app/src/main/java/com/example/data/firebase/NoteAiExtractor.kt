package com.example.data.firebase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.model.DefinitionItem
import com.example.data.model.HighlightColor
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

data class ExtractedNoteResult(
    val title: String,
    val extractedText: String,
    val suggestedPhotoTag: String,
    val definitions: List<DefinitionItem>,
    val suggestedHighlights: List<String>
)

class NoteAiExtractor {

    private val noteSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "A concise, clear student study title for this page (3-7 words)."),
            "extractedText" to Schema.string(description = "Accurate, clean, editable transcription of all printed text or clean handwriting visible on the page, preserving paragraphs and bullet points."),
            "suggestedPhotoTag" to Schema.string(description = "Short tag describing the source page, e.g. 'Biology Ch. 4 Notes' or 'Handwritten Summary'."),
            "definitions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "term" to Schema.string(description = "The key vocabulary term or concept name."),
                        "definition" to Schema.string(description = "Clear student-friendly definition or explanation from the notes.")
                    )
                ),
                description = "Key terms and definitions extracted from the page (up to 12 most important)."
            ),
            "suggestedHighlights" to Schema.array(
                items = Schema.string(),
                description = "Exact short sentences or key phrases from extractedText worth highlighting."
            )
        )
    )

    private val definitionsOnlySchema = Schema.obj(
        properties = mapOf(
            "definitions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "term" to Schema.string(description = "Key concept or vocabulary word."),
                        "definition" to Schema.string(description = "Clear, concise definition based on the text.")
                    )
                ),
                description = "Key definitions found in the student note text."
            ),
            "suggestedHighlights" to Schema.array(
                items = Schema.string(),
                description = "Key sentences or phrases directly present in the note text."
            )
        )
    )

    private val imageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = noteSchema
            }
        )
    }

    private val textModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = definitionsOnlySchema
            }
        )
    }

    suspend fun extractFromImage(bitmap: Bitmap): Result<ExtractedNoteResult> {
        return try {
            val response = withTimeout(60_000L) {
                imageModel.generateContent(
                    content {
                        image(bitmap)
                        text(
                            "You are helping a young student digitize study notes. " +
                                "Read this photo of a printed textbook/worksheet page or clean handwritten notes. " +
                                "Extract all readable text accurately into clean, editable plain text. " +
                                "Identify key terms and their definitions, and list exact key phrases from the extracted text to highlight."
                        )
                    }
                )
            }
            val jsonStr = response.text ?: return Result.failure(
                IllegalStateException("Gemini returned an empty response.")
            )
            val json = JSONObject(jsonStr)
            val title = json.optString("title", "Scanned Study Note").trim().ifEmpty { "Scanned Study Note" }
            val extractedText = json.optString("extractedText", "").trim()
            val photoTag = json.optString("suggestedPhotoTag", "Scanned Page").trim()
            val definitions = parseDefinitions(json)
            val highlights = parseHighlights(json)

            Result.success(
                ExtractedNoteResult(
                    title = title,
                    extractedText = extractedText,
                    suggestedPhotoTag = photoTag,
                    definitions = definitions,
                    suggestedHighlights = highlights
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image extraction timed out", e)
            Result.failure(Exception("Reading the page timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image extraction failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Could not extract text from the photo."))
        }
    }

    suspend fun extractDefinitionsFromText(noteText: String): Result<Pair<List<DefinitionItem>, List<String>>> {
        if (noteText.isBlank()) {
            return Result.failure(IllegalArgumentException("Add some note text first before finding definitions."))
        }
        return try {
            val response = withTimeout(45_000L) {
                textModel.generateContent(
                    "Analyze these student study notes and extract the most important key terms with clear definitions, " +
                        "plus exact key sentences or phrases from the text to highlight for exam review:\n\n$noteText"
                )
            }
            val jsonStr = response.text ?: return Result.failure(
                IllegalStateException("Gemini returned an empty response.")
            )
            val json = JSONObject(jsonStr)
            val definitions = parseDefinitions(json)
            val highlights = parseHighlights(json)
            Result.success(definitions to highlights)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Definition extraction timed out", e)
            Result.failure(Exception("Finding definitions timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Definition extraction failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to analyze definitions."))
        }
    }

    private fun parseDefinitions(json: JSONObject): List<DefinitionItem> {
        val arr = json.optJSONArray("definitions") ?: return emptyList()
        val colors = HighlightColor.entries
        return buildList {
            for (i in 0 until minOf(arr.length(), 20)) {
                val obj = arr.optJSONObject(i) ?: continue
                val term = obj.optString("term", "").trim()
                val def = obj.optString("definition", "").trim()
                if (term.isNotEmpty() && def.isNotEmpty()) {
                    add(
                        DefinitionItem(
                            id = UUID.randomUUID().toString(),
                            term = term.take(120),
                            definition = def.take(1000),
                            highlightColor = colors[i % colors.size].name
                        )
                    )
                }
            }
        }
    }

    private fun parseHighlights(json: JSONObject): List<String> {
        val arr = json.optJSONArray("suggestedHighlights") ?: return emptyList()
        return buildList {
            for (i in 0 until minOf(arr.length(), 25)) {
                val phrase = arr.optString(i, "").trim()
                if (phrase.isNotEmpty()) {
                    add(phrase.take(400))
                }
            }
        }
    }

    private companion object {
        const val TAG = "NoteAiExtractor"
    }
}

object ImageUtils {
    suspend fun decodeForVisionAndThumbnail(
        context: Context,
        uri: Uri
    ): Result<Pair<Bitmap, String>> = withContext(Dispatchers.IO) {
        try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Unable to read selected image."))
            }

            var sampleSize = 1
            val maxDim = 1600
            while (boundsOpts.outWidth / sampleSize > maxDim || boundsOpts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@withContext Result.failure(IllegalStateException("Failed to decode image."))

            val visionBitmap = scaleDown(decoded, maxDimension = 1400)
            val thumbnailBitmap = scaleDown(visionBitmap, maxDimension = 320)
            val thumbBase64 = encodeThumbnailBase64(thumbnailBitmap)

            if (thumbnailBitmap != visionBitmap) {
                thumbnailBitmap.recycle()
            }
            if (decoded != visionBitmap) {
                decoded.recycle()
            }

            Result.success(visionBitmap to thumbBase64)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ImageUtils", "Image decode failed", e)
            Result.failure(e)
        }
    }

    private fun scaleDown(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source

        val scale = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        val matrix = Matrix().apply { postScale(scale, scale) }
        return Bitmap.createBitmap(source, 0, 0, width, height, matrix, true)
    }

    private fun encodeThumbnailBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        var quality = 60
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        while (baos.size() > 75_000 && quality > 20) {
            baos.reset()
            quality -= 15
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        }
        val bytes = baos.toByteArray()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return if (b64.length <= 110_000) b64 else ""
    }

    fun decodeThumbnailBase64(base64: String): Bitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}
