package com.ramzes.visavinet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramzes.visavinet.network.AuthRequest
import com.ramzes.visavinet.network.ConfigData
import com.ramzes.visavinet.network.StatsResponse
import com.ramzes.visavinet.network.UserData
import com.ramzes.visavinet.network.VisaviApi
import com.ramzes.visavinet.network.extractErrorMessage
import com.ramzes.visavinet.service.NewMessagesService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    var currentUser by mutableStateOf<UserData?>(null)
        private set

    var siteConfig by mutableStateOf<ConfigData?>(null)
        private set

    var siteStats by mutableStateOf<StatsResponse?>(null)
        private set

    private var statsJob: Job? = null

    var isLoading by mutableStateOf(false)
    var isInitialChecking by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)
    var statusMessage by mutableStateOf<String?>(null)

    fun checkAutoLogin(context: Context) {
        // Загрузка или автообновление конфига API сайта при запуске приложения (кеш в памяти + 24ч лимит)
        fetchSiteConfig(context, force = false)

        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        val token = prefs.getString("api_token", null)

        if (!token.isNullOrBlank()) {
            VisaviApi.setToken(token)
            fetchUserData(context, token)
        } else {
            isInitialChecking = false
        }
    }

    /**
     * Вход по Логину и Паролю через POST /auth
     */
    fun loginWithCredentials(loginInput: String, passwordInput: String, context: Context) {
        if (loginInput.isBlank() || passwordInput.isBlank()) {
            errorMessage = "Заполните логин и пароль"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val authResponse = VisaviApi.instance.auth(AuthRequest(loginInput, passwordInput))
                if (authResponse.isSuccessful && authResponse.body()?.token != null) {
                    val token = authResponse.body()!!.token!!
                    saveToken(context, token)
                    VisaviApi.setToken(token)

                    val userResponse = VisaviApi.instance.getUser()
                    if (userResponse.isSuccessful && userResponse.body()?.data != null) {
                        currentUser = userResponse.body()!!.data
                        NewMessagesService.start(context)
                        fetchSiteConfig(context, force = true)
                    } else {
                        errorMessage = userResponse.extractErrorMessage("Не удалось загрузить данные профиля")
                    }
                } else {
                    errorMessage = authResponse.extractErrorMessage("Неверный логин или пароль")
                }
            } catch (e: Exception) {
                errorMessage = "Ошибка подключения к серверу: ${e.message}"
            } finally {
                isLoading = false
                isInitialChecking = false
            }
        }
    }

    /**
     * Вход по прямому API Токену
     */
    fun loginWithToken(tokenInput: String, context: Context) {
        if (tokenInput.isBlank()) {
            errorMessage = "Введите API-Токен"
            return
        }

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val trimmedToken = tokenInput.trim()
                VisaviApi.setToken(trimmedToken)

                val response = VisaviApi.instance.getUser()
                if (response.isSuccessful && response.body()?.data != null) {
                    currentUser = response.body()!!.data
                    saveToken(context, trimmedToken)
                    NewMessagesService.start(context)
                    fetchSiteConfig(context, force = true)
                } else {
                    VisaviApi.clearToken()
                    errorMessage = response.extractErrorMessage("Недействительный API-Токен")
                }
            } catch (e: Exception) {
                VisaviApi.clearToken()
                errorMessage = "Ошибка подключения: ${e.message}"
            } finally {
                isLoading = false
                isInitialChecking = false
            }
        }
    }

    fun loadCachedConfig(context: Context) {
        if (siteConfig != null) return
        try {
            val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("site_config_json", null)
            if (!json.isNullOrBlank()) {
                val gson = com.google.gson.Gson()
                siteConfig = gson.fromJson(json, ConfigData::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun shouldUpdateConfig(context: Context): Boolean {
        if (siteConfig == null) return true
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        val lastUpdated = prefs.getLong("site_config_updated_at", 0L)
        val now = System.currentTimeMillis()
        val dayInMs = 24 * 60 * 60 * 1000L
        return (now - lastUpdated) > dayInMs
    }

    fun fetchSiteConfig(context: Context, force: Boolean = false) {
        loadCachedConfig(context)
        if (!force && !shouldUpdateConfig(context)) {
            return
        }
        viewModelScope.launch {
            try {
                val response = VisaviApi.instance.getConfig()
                if (response.isSuccessful && response.body() != null) {
                    val newConfig = response.body()
                    siteConfig = newConfig
                    saveConfigToPrefs(context, newConfig)
                }
            } catch (e: Exception) {
                // При ошибке остаётся ранее загруженный из памяти/диска конфиг
            }
        }
    }

    private fun saveConfigToPrefs(context: Context, config: ConfigData?) {
        if (config == null) return
        try {
            val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
            val gson = com.google.gson.Gson()
            val json = gson.toJson(config)
            prefs.edit()
                .putString("site_config_json", json)
                .putLong("site_config_updated_at", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchUserData(context: Context, token: String) {
        viewModelScope.launch {
            try {
                val response = VisaviApi.instance.getUser()
                if (response.isSuccessful && response.body()?.data != null) {
                    currentUser = response.body()!!.data
                    NewMessagesService.start(context)
                } else {
                    logout(context)
                }
            } catch (e: Exception) {
                logout(context)
            } finally {
                isInitialChecking = false
            }
        }
    }

    private fun saveToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("api_token", token).apply()
    }

    fun startStatsPolling(context: Context) {
        if (statsJob?.isActive == true) return
        statsJob = viewModelScope.launch {
            while (isActive) {
                fetchStats(context)
                delay(5 * 60 * 1000L) // Интервал 5 минут
            }
        }
    }

    fun stopStatsPolling() {
        statsJob?.cancel()
        statsJob = null
    }

    fun fetchStats(context: Context) {
        viewModelScope.launch {
            try {
                val response = VisaviApi.instance.getStats()
                if (response.isSuccessful && response.body() != null) {
                    val stats = response.body()!!
                    siteStats = stats
                    com.ramzes.visavinet.util.SiteUpdatesNotificationManager.checkAndNotify(context, stats)
                }
            } catch (e: Exception) {
                // Игнорируем сетевые ошибки периодического опроса
            }
        }
    }

    fun logout(context: Context) {
        stopStatsPolling()
        siteStats = null
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("api_token").apply()
        VisaviApi.clearToken()
        currentUser = null
        siteConfig = null
        statusMessage = null
    }

    override fun onCleared() {
        super.onCleared()
        stopStatsPolling()
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()
}
