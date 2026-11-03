package com.example.baseapp.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.baseapp.ui.editor.TranscriptEditorScreen
import com.example.baseapp.ui.library.TranscriptListScreen

object Routes {
    const val TRANSCRIPTION = "transcription"
    const val LIBRARY = "library"
    const val EDITOR = "editor/{id}"

    fun editor(id: Long): String = "editor/$id"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.TRANSCRIPTION,
    ) {
        composable(Routes.TRANSCRIPTION) {
            TranscriptionScreen(
                onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                onOpenEditor = { id -> navController.navigate(Routes.editor(id)) },
            )
        }

        composable(Routes.LIBRARY) {
            TranscriptListScreen(
                onBack = { navController.popBackStack() },
                onOpenTranscript = { id -> navController.navigate(Routes.editor(id)) },
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
            ),
        ) {
            TranscriptEditorScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
