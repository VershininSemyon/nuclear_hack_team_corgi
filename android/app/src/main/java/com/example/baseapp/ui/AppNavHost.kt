
package com.example.baseapp.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.baseapp.BaseApp
import com.example.baseapp.data.repository.SessionState
import com.example.baseapp.ui.auth.AuthMode
import com.example.baseapp.ui.auth.AuthScreen
import com.example.baseapp.ui.auth.WelcomeScreen
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
    val app = LocalContext.current.applicationContext as BaseApp
    val container = app.container

    val sessionState by container.sessionManager.state.collectAsState()

    var authMode by remember {
        mutableStateOf<AuthMode?>(null)
    }

    when (sessionState) {
        SessionState.NOT_SELECTED -> {
            when (authMode) {
                null -> {
                    WelcomeScreen(
                        onLogin = {
                            authMode = AuthMode.LOGIN
                        },
                        onRegister = {
                            authMode = AuthMode.REGISTER
                        },
                        onContinueAsGuest = {
                            container.authRepository.continueAsGuest()
                        },
                    )
                }

                else -> {
                    AuthScreen(
                        mode = authMode!!,
                        authRepository = container.authRepository,
                        onBack = {
                            authMode = null
                        },
                    )
                }
            }
        }

        SessionState.GUEST,
        SessionState.AUTHENTICATED -> {
            MainAppNavigation()
        }
    }
}

@Composable
private fun MainAppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.TRANSCRIPTION,
    ) {
        composable(Routes.TRANSCRIPTION) {
            TranscriptionScreen(
                onOpenLibrary = {
                    navController.navigate(Routes.LIBRARY)
                },
                onOpenEditor = { id ->
                    navController.navigate(Routes.editor(id))
                },
            )
        }

        composable(Routes.LIBRARY) {
            TranscriptListScreen(
                onTranscriptClick = { id ->
                    navController.navigate(Routes.editor(id))
                },
                onNewTranscriptClick = {
                    navController.navigate(Routes.TRANSCRIPTION)
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
