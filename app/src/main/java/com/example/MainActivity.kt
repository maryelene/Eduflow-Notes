package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.firebase.NoteAiExtractor
import com.example.data.firebase.NotesRepository
import com.example.data.firebase.configureAppCheck
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.NoteEditorScreen
import com.example.ui.screens.SubjectReviewerScreen
import com.example.ui.screens.SubjectsHomeScreen
import com.example.ui.theme.EduFlowNotesTheme
import com.example.ui.viewmodel.EduFlowViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val ROUTE_HOME = "home"
private const val ROUTE_SUBJECT = "subject/{subjectId}"
private const val ROUTE_NOTE_EDITOR = "note_editor?subjectId={subjectId}&noteId={noteId}&photoUri={photoUri}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before setContent, ViewModels, or Firebase ops.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            EduFlowNotesTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = user.uid
        val displayName = user.displayName.orEmpty().ifBlank { user.email.orEmpty() }
        key(uid) {
            EduFlowNavGraph(
                currentUserId = uid,
                userDisplayName = displayName
            )
        }
    }
}

@Composable
fun EduFlowNavGraph(
    currentUserId: String,
    userDisplayName: String
) {
    val navController = rememberNavController()
    val viewModel: EduFlowViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                EduFlowViewModel(
                    repository = NotesRepository(db),
                    aiExtractor = NoteAiExtractor(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val navigateToEditor: (String?, String?, Uri?) -> Unit = { subjectId, noteId, photoUri ->
        val encodedSubject = Uri.encode(subjectId.orEmpty())
        val encodedNote = Uri.encode(noteId.orEmpty())
        val encodedPhoto = Uri.encode(photoUri?.toString().orEmpty())
        navController.navigate(
            "note_editor?subjectId=$encodedSubject&noteId=$encodedNote&photoUri=$encodedPhoto"
        )
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME
    ) {
        composable(ROUTE_HOME) {
            SubjectsHomeScreen(
                viewModel = viewModel,
                userDisplayName = userDisplayName,
                onOpenSubject = { subjectId ->
                    navController.navigate("subject/${Uri.encode(subjectId)}")
                },
                onOpenNoteEditor = navigateToEditor
            )
        }

        composable(
            route = ROUTE_SUBJECT,
            arguments = listOf(navArgument("subjectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val subjectId = backStackEntry.arguments?.getString("subjectId").orEmpty()
            SubjectReviewerScreen(
                subjectId = subjectId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenNoteEditor = { sId, nId, pUri ->
                    navigateToEditor(sId, nId, pUri)
                }
            )
        }

        composable(
            route = ROUTE_NOTE_EDITOR,
            arguments = listOf(
                navArgument("subjectId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("noteId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("photoUri") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val sId = backStackEntry.arguments?.getString("subjectId").orEmpty().ifBlank { null }
            val nId = backStackEntry.arguments?.getString("noteId").orEmpty().ifBlank { null }
            val photoRaw = backStackEntry.arguments?.getString("photoUri").orEmpty()
            val photoUri = if (photoRaw.isNotBlank()) Uri.parse(photoRaw) else null

            NoteEditorScreen(
                initialSubjectId = sId,
                noteId = nId,
                initialPhotoUri = photoUri,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
