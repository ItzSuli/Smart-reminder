package com.itzsuli.smartreminder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itzsuli.smartreminder.schedule.Scheduler
import com.itzsuli.smartreminder.ui.AddEditScreen
import com.itzsuli.smartreminder.ui.HomeScreen
import com.itzsuli.smartreminder.ui.ImportScreen
import com.itzsuli.smartreminder.ui.MainViewModel
import com.itzsuli.smartreminder.ui.Screen
import com.itzsuli.smartreminder.ui.SettingsScreen
import com.itzsuli.smartreminder.ui.theme.SmartReminderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    companion object {
        const val ACTION_ADD = "com.itzsuli.smartreminder.action.ADD"
        const val ACTION_VOICE = "com.itzsuli.smartreminder.action.VOICE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            SmartReminderTheme(dynamicColors = settings.dynamicColors) {
                AppRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumed()
        Scheduler.reschedule(this)
    }

    /** Shared text, the widget, the quick-settings tile and launcher shortcuts all land here. */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> if (intent.type == "text/plain") {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
                if (text.isNotEmpty()) viewModel.startAdd(text)
            }
            ACTION_ADD -> viewModel.startAdd()
            ACTION_VOICE -> viewModel.startAdd(voice = true)
        }
    }
}

@Composable
private fun AppRoot(viewModel: MainViewModel) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    BackHandler(enabled = screen != Screen.Home) { viewModel.goHome() }
    when (screen) {
        Screen.Home -> HomeScreen(viewModel)
        Screen.Add -> AddEditScreen(viewModel)
        Screen.Settings -> SettingsScreen(viewModel)
        Screen.Import -> ImportScreen(viewModel)
    }
}
