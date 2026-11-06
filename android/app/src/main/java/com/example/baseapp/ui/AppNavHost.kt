
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
import com.example.baseapp.ui.library.AudioDetailScreen
import com.example.baseapp.ui.library.AudioLibraryScreen
import com.example.baseapp.ui.profile.AccountScreen

object Routes {
    const val AUDIO_LIBRARY = "audio_library"
    const val AUDIO_DETAILS = "audio_details/{id}"
    const val TRANSCRIPTION = "transcription"
    const val EDITOR = "editor/{id}"

    const val ACCOUNT = "account"
    const val ACCOUNT_LOGIN = "account_login"
    const val ACCOUNT_REGISTER = "account_register"

    fun editor(id: Long): String = "editor/$id"

    fun audioDetails(id: Long): String = "audio_details/$id"
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
    val app = LocalContext.current.applicationContext as BaseApp
    val container = app.container

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.AUDIO_LIBRARY,
    ) {
        composable(Routes.AUDIO_LIBRARY) {
            AudioLibraryScreen(
                onOpenProfile = {
                    navController.navigate(Routes.ACCOUNT)
                },
                onOpenAudioDetails = { id ->
                    navController.navigate(Routes.audioDetails(id))
                },
                onOpenTranscription = {
                    navController.navigate(Routes.TRANSCRIPTION)
                },
            )
        }

        composable(Routes.ACCOUNT) {
            AccountScreen(
                authRepository = container.authRepository,
                syncManager = container.syncManager,
                onBack = {
                    navController.popBackStack()
                },
                onLogin = {
                    navController.navigate(Routes.ACCOUNT_LOGIN)
                },
                onRegister = {
                    navController.navigate(Routes.ACCOUNT_REGISTER)
                },
            )
        }

        composable(Routes.ACCOUNT_LOGIN) {
            AuthScreen(
                mode = AuthMode.LOGIN,
                authRepository = container.authRepository,
                onBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(Routes.ACCOUNT_REGISTER) {
            AuthScreen(
                mode = AuthMode.REGISTER,
                authRepository = container.authRepository,
                onBack = {
                    navController.popBackStack()
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
        ) { backStackEntry ->
            val audioId = checkNotNull(
                backStackEntry.arguments?.getLong("id"),
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

        composable(Routes.TRANSCRIPTION) {
            TranscriptionScreen(
                onOpenLibrary = {
                    navController.popBackStack()
                },
                onOpenEditor = { id ->
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
