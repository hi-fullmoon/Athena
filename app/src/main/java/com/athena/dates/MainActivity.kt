package com.athena.dates

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private val viewModel: AthenaViewModel by viewModels { AthenaViewModel.factory(this) }
    private var launchAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchAction = intent?.action
        enableEdgeToEdge()
        setContent { AthenaApp(viewModel, launchAction) { launchAction = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchAction = intent.action
    }
}

const val ACTION_SHORTCUT_ADD = "com.athena.dates.action.ADD_DATE"
const val ACTION_SHORTCUT_UPCOMING = "com.athena.dates.action.VIEW_UPCOMING"
const val ACTION_SHORTCUT_SETTINGS = "com.athena.dates.action.OPEN_SETTINGS"
