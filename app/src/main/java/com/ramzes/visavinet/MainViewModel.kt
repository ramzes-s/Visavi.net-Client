package com.ramzes.visavinet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramzes.visavinet.network.AuthRequest
import com.ramzes.visavinet.network.ConfigData
import com.ramzes.visavinet.network.UserData
import com.ramzes.visavinet.network.VisaviApi
import com.ramzes.visavinet.network.extractErrorMessage
import com.ramzes.visavinet.service.NewMessagesService
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    var currentUser by mutableStateOf<UserData?>(null)
        private set

    var siteConfig by mutableStateOf<ConfigData?>(null)
        private set

    var isLoading by mutableStateOf(false)
    var isInitialChecking by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)
    var statusMessage by mutableStateOf<String?>(null)

    fun checkAutoLogin(context: Context) {
        // Безусловная загрузка базового открытого конфига API сайта при запуске приложения
        fetchSiteConfig()

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
                        fetchSiteConfig()
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
                    fetchSiteConfig()
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

    fun fetchSiteConfig() {
        viewModelScope.launch {
            try {
                val response = VisaviApi.instance.getConfig()
                if (response.isSuccessful) {
                    siteConfig = response.body()
                }
            } catch (e: Exception) {
                // Неблокирующая загрузка конфига
            }
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

    fun logout(context: Context) {
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("api_token").apply()
        VisaviApi.clearToken()
        currentUser = null
        siteConfig = null
        statusMessage = null
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()
}
