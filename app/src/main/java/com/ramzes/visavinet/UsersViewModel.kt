package com.ramzes.visavinet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramzes.visavinet.network.PaginationMeta
import com.ramzes.visavinet.network.UserData
import com.ramzes.visavinet.network.VisaviApi
import com.ramzes.visavinet.network.extractErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class UserSort(val key: String, val title: String) {
    POINT("point", "Актив"),
    RATING("rating", "Авторитет"),
    CREATED("created", "Новые"),
    UPDATED("updated", "Активность")
}

class UsersViewModel : ViewModel() {

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    var users by mutableStateOf<List<UserData>>(emptyList())
        private set

    var meta by mutableStateOf<PaginationMeta?>(null)
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

    var currentSort by mutableStateOf(UserSort.POINT)
        private set

    var scrollItemIndex by mutableIntStateOf(0)
    var scrollOffset by mutableIntStateOf(0)

    // Поиск пользователей
    var isSearchActive by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")
        private set

    var searchResults by mutableStateOf<List<UserData>>(emptyList())
        private set

    var isSearching by mutableStateOf(false)
        private set

    var searchError by mutableStateOf<String?>(null)
        private set

    fun openSearch() {
        isSearchActive = true
        searchQuery = ""
        searchResults = emptyList()
        searchError = null
    }

    fun closeSearch() {
        searchJob?.cancel()
        searchJob = null
        isSearchActive = false
        searchQuery = ""
        searchResults = emptyList()
        searchError = null
        isSearching = false
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery = query
        val trimmed = query.trim()
        searchJob?.cancel()
        if (trimmed.length < 2) {
            searchResults = emptyList()
            searchError = null
            isSearching = false
            return
        }

        searchJob = viewModelScope.launch {
            delay(300L)
            isSearching = true
            searchError = null
            try {
                val response = VisaviApi.instance.searchUsers(trimmed)
                if (response.isSuccessful) {
                    val rawList = response.body()?.data ?: emptyList()
                    searchResults = rawList.map { searchUser ->
                        UserData(
                            login = searchUser.login,
                            name = searchUser.name,
                            level = searchUser.level,
                            color = searchUser.color,
                            avatar = searchUser.avatar,
                            status = searchUser.status
                        )
                    }
                    searchError = null
                } else {
                    searchError = response.extractErrorMessage("Ошибка поиска пользователей")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchError = e.message ?: "Ошибка сети"
            } finally {
                isSearching = false
            }
        }
    }

    private fun getPerPage(context: Context): Int {
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("items_per_page", 20).coerceAtLeast(10)
    }

    fun loadUsers(context: Context, page: Int = 1, sort: UserSort = currentSort) {
        if (page == 1) {
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
                val response = VisaviApi.instance.getUsers(page = page, perPage = perPage, sort = sort.key)
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
                    val err = response.extractErrorMessage("Не удалось загрузить список пользователей")
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
        loadUsers(context, 1, currentSort)
    }

    fun setSort(context: Context, sort: UserSort) {
        if (currentSort == sort && users.isNotEmpty()) return
        currentSort = sort
        currentPage = 1
        lastPage = 1
        hasNextPage = false
        scrollItemIndex = 0
        scrollOffset = 0
        users = emptyList()
        loadUsers(context, 1, sort)
    }

    fun loadMore(context: Context) {
        if (isLoading || isLoadingMore || !hasNextPage) return
        loadUsers(context, currentPage + 1, currentSort)
    }
}
