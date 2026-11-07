
package com.example.baseapp.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.baseapp.ui.editor.TranscriptEditorScreen
import com.example.baseapp.ui.library.AudioDetailScreen
import com.example.baseapp.ui.library.AudioLibraryScreen

object Routes {
    const val AUDIO_LIBRARY = "audio_library"
    const val AUDIO_DETAILS = "audio_details/{id}"
    const val EDITOR = "editor/{id}"

    fun editor(id: Long): String = "editor/$id"

    fun audioDetails(id: Long): String = "audio_details/$id"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.AUDIO_LIBRARY,
    ) {
        composable(Routes.AUDIO_LIBRARY) {
            AudioLibraryScreen(
                onOpenAudioDetails = { id ->
                    navController.navigate(Routes.audioDetails(id))
                },
            )
        }

        composable(
            route = Routes.AUDIO_DETAILS,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.LongType
                },
            ),
        ) { entry ->
            val audioId = checkNotNull(
                entry.arguments?.getLong("id"),
            )

            AudioDetailScreen(
                audioId = audioId,
                onBack = {
                    navController.popBackStack()
                },
                onOpenTranscript = { id ->
                    navController.navigate(Routes.editor(id))
                },
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.LongType
                },
            ),
        ) {
            TranscriptEditorScreen(
                onBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}
