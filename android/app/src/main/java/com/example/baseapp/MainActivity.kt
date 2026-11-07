package com.example.baseapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.baseapp.i18n.AppLocale
import com.example.baseapp.i18n.LanguageStore
import com.example.baseapp.i18n.LocalLanguage
import com.example.baseapp.i18n.LocalSetLanguage
import com.example.baseapp.ui.AppNavHost
import com.example.baseapp.ui.theme.LocalDarkTheme
import com.example.baseapp.ui.theme.LocalToggleTheme
import com.example.baseapp.ui.theme.ThemeStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var language by remember { mutableStateOf(LanguageStore.load(context)) }

            val systemDark = isSystemInDarkTheme()
            var darkTheme by remember {
                mutableStateOf(ThemeStore.load(context) ?: systemDark)
            }

            // tr() читает это значение до композиции детей.
            AppLocale.language = language

            CompositionLocalProvider(
                LocalLanguage provides language,
                LocalSetLanguage provides { next ->
                    LanguageStore.save(context, next)
                    language = next
                },
                LocalDarkTheme provides darkTheme,
                LocalToggleTheme provides {
                    val next = !darkTheme
                    ThemeStore.save(context, next)
                    darkTheme = next
                },
            ) {
                BaseAppTheme(darkTheme = darkTheme) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavHost()
                    }
                }
            }
        }
    }
}

@Composable
fun BaseAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
