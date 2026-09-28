package com.ramzes.visavinet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramzes.visavinet.network.OnlineMeta
import com.ramzes.visavinet.network.OnlineUser
import com.ramzes.visavinet.network.VisaviApi
import com.ramzes.visavinet.network.extractErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OnlineViewModel : ViewModel() {

    /** Текущая (последняя) загрузка списка онлайн */
    private var loadJob: Job? = null

    var users by mutableStateOf<List<OnlineUser>>(emptyList())
        private set

    var meta by mutableStateOf<OnlineMeta?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var currentPage by mutableIntStateOf(1)
        private set

    var lastPage by mutableIntStateOf(1)
        private set

    var hasNextPage by mutableStateOf(false)
        private set

    var scrollItemIndex by mutableIntStateOf(0)
    var scrollOffset by mutableIntStateOf(0)

    private fun getPerPage(context: Context): Int {
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("items_per_page", 10)
    }

    fun loadOnline(context: Context, page: Int = 1) {
        if (page == 1) {
            // Отменяем незавершённую загрузку, чтобы старый ответ не перезаписал свежий список
            loadJob?.let { running ->
                loadJob = null
                running.cancel()
            }
            isLoading = true
            errorMessage = null
        } else {
            isLoadingMore = true
        }

        val perPage = getPerPage(context)

        val job = viewModelScope.launch {
            try {
                val response = VisaviApi.instance.getOnline(page = page, perPage = perPage)
                if (response.isSuccessful) {
                    val body = response.body()
                    val newUsers = body?.data ?: emptyList()
                    meta = body?.meta

                    if (page == 1) {
                        users = newUsers
                    } else {
                        users = users + newUsers
                    }

                    currentPage = body?.meta?.currentPage ?: page
                    lastPage = body?.meta?.lastPage ?: 1
                    hasNextPage = currentPage < lastPage
                    errorMessage = null
                } else {
                    val err = response.extractErrorMessage("Не удалось загрузить список пользователей онлайн")
                    if (page == 1) {
                        errorMessage = err
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (page == 1) {
                    errorMessage = e.message ?: "Ошибка сети"
                }
            } finally {
                // Флаги загрузки сбрасывает только актуальная (последняя) загрузка
                if (loadJob === coroutineContext[Job]) {
                    isLoading = false
                    isLoadingMore = false
                }
            }
        }
        loadJob = job
    }

    fun refresh(context: Context) {
        currentPage = 1
        lastPage = 1
        hasNextPage = false
        scrollItemIndex = 0
        scrollOffset = 0
        // Не показываем прежний список: данные всегда загружаются заново
        users = emptyList()
        loadOnline(context, 1)
    }

    fun loadMore(context: Context) {
        if (isLoading || isLoadingMore || !hasNextPage) return
        loadOnline(context, currentPage + 1)
    }
}
