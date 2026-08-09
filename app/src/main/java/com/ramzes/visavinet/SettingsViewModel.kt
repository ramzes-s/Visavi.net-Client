package com.ramzes.visavinet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {

    var apiToken: String? by mutableStateOf(null)
        private set

    fun loadApiToken(context: Context) {
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        apiToken = prefs.getString("api_key", null)
    }
}
