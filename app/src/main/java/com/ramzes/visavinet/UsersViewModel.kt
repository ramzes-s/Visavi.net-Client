package com.ramzes.visavinet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

    companion object {
        private const val CACHE_DURATION_MS = 15 * 60 * 1000L // 15 минут
        private val CACHED_SORTS = setOf(UserSort.POINT, UserSort.RATING, UserSort.CREATED)
    }

    private val loadJobs = mutableMapOf<UserSort, Job>()
    private var searchJob: Job? = null
    private var cacheTimestampBySort: Map<UserSort, Long> = emptyMap()

    var usersBySort by mutableStateOf<Map<UserSort, List<UserData>>>(emptyMap())
        private set

    var isLoadingBySort by mutableStateOf<Map<UserSort, Boolean>>(emptyMap())
        private set

    var isLoadingMoreBySort by mutableStateOf<Map<UserSort, Boolean>>(emptyMap())
        private set

    var errorBySort by mutableStateOf<Map<UserSort, String?>>(emptyMap())
        private set

    var pageBySort by mutableStateOf<Map<UserSort, Int>>(emptyMap())
        private set

    var lastPageBySort by mutableStateOf<Map<UserSort, Int>>(emptyMap())
        private set

    fun getUsers(sort: UserSort): List<UserData> = usersBySort[sort] ?: emptyList()
    fun isLoading(sort: UserSort): Boolean = isLoadingBySort[sort] == true
    fun isLoadingMore(sort: UserSort): Boolean = isLoadingMoreBySort[sort] == true
    fun getErrorMessage(sort: UserSort): String? = errorBySort[sort]
    fun hasNextPage(sort: UserSort): Boolean = (pageBySort[sort] ?: 1) < (lastPageBySort[sort] ?: 1)

    var meta by mutableStateOf<PaginationMeta?>(null)
        private set

    var currentSort by mutableStateOf(UserSort.POINT)
        private set

    val users: List<UserData> get() = getUsers(currentSort)
    val isLoading: Boolean get() = isLoading(currentSort)
    val isLoadingMore: Boolean get() = isLoadingMore(currentSort)
    val errorMessage: String? get() = getErrorMessage(currentSort)
    val currentPage: Int get() = pageBySort[currentSort] ?: 1
    val lastPage: Int get() = lastPageBySort[currentSort] ?: 1
    val hasNextPage: Boolean get() = hasNextPage(currentSort)

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

    fun isCacheValid(context: Context, sort: UserSort): Boolean {
        if (sort !in CACHED_SORTS) return false
        val memTime = cacheTimestampBySort[sort]
        if (memTime != null && (System.currentTimeMillis() - memTime) < CACHE_DURATION_MS) {
            return true
        }
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        val spTime = prefs.getLong("users_cache_${sort.key}_timestamp", 0L)
        return (System.currentTimeMillis() - spTime) < CACHE_DURATION_MS
    }

    fun loadCachedUsers(context: Context, sort: UserSort): Boolean {
        if (sort !in CACHED_SORTS) return false
        val currentList = usersBySort[sort]
        if (!currentList.isNullOrEmpty()) {
            return isCacheValid(context, sort)
        }
        try {
            val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
            val timestamp = prefs.getLong("users_cache_${sort.key}_timestamp", 0L)
            val json = prefs.getString("users_cache_${sort.key}_json", null)
            if (!json.isNullOrBlank()) {
                val type = object : TypeToken<List<UserData>>() {}.type
                val list: List<UserData> = Gson().fromJson(json, type)
                if (list.isNotEmpty()) {
                    usersBySort = usersBySort + (sort to list)
                    pageBySort = pageBySort + (sort to 1)
                    lastPageBySort = lastPageBySort + (sort to prefs.getInt("users_cache_${sort.key}_last_page", 1))
                    cacheTimestampBySort = cacheTimestampBySort + (sort to timestamp)
                    return (System.currentTimeMillis() - timestamp) < CACHE_DURATION_MS
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun saveUsersCache(context: Context, sort: UserSort, users: List<UserData>, lastPage: Int) {
        if (sort !in CACHED_SORTS) return
        val now = System.currentTimeMillis()
        cacheTimestampBySort = cacheTimestampBySort + (sort to now)
        try {
            val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
            val json = Gson().toJson(users)
            prefs.edit()
                .putString("users_cache_${sort.key}_json", json)
                .putLong("users_cache_${sort.key}_timestamp", now)
                .putInt("users_cache_${sort.key}_last_page", lastPage)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadUsers(
        context: Context,
        page: Int = 1,
        sort: UserSort = currentSort,
        force: Boolean = false
    ) {
        if (page == 1) {
            if (!force && loadCachedUsers(context, sort)) {
                return
            }
            loadJobs[sort]?.cancel()
            isLoadingBySort = isLoadingBySort + (sort to true)
            errorBySort = errorBySort + (sort to null)
        } else {
            isLoadingMoreBySort = isLoadingMoreBySort + (sort to true)
        }

        val perPage = getPerPage(context)

        val job = viewModelScope.launch {
            try {
                val response = VisaviApi.instance.getUsers(page = page, perPage = perPage, sort = sort.key)
                if (response.isSuccessful) {
                    val body = response.body()
                    val newUsers = body?.data ?: emptyList()
                    meta = body?.meta

                    val currentList = if (page == 1) emptyList() else (usersBySort[sort] ?: emptyList())
                    usersBySort = usersBySort + (sort to (currentList + newUsers))

                    val curPage = body?.meta?.currentPage ?: page
                    val maxPage = body?.meta?.lastPage ?: 1
                    pageBySort = pageBySort + (sort to curPage)
                    lastPageBySort = lastPageBySort + (sort to maxPage)
                    errorBySort = errorBySort + (sort to null)

                    if (page == 1 && newUsers.isNotEmpty()) {
                        saveUsersCache(context, sort, newUsers, maxPage)
                    }
                } else {
                    val err = response.extractErrorMessage("Не удалось загрузить список пользователей")
                    if (page == 1) {
                        errorBySort = errorBySort + (sort to err)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (page == 1) {
                    errorBySort = errorBySort + (sort to (e.message ?: "Ошибка сети"))
                }
            } finally {
                isLoadingBySort = isLoadingBySort + (sort to false)
                isLoadingMoreBySort = isLoadingMoreBySort + (sort to false)
            }
        }
        loadJobs[sort] = job
    }

    fun refresh(context: Context, sort: UserSort = currentSort) {
        pageBySort = pageBySort + (sort to 1)
        lastPageBySort = lastPageBySort + (sort to 1)
        loadUsers(context, 1, sort, force = true)
    }

    fun setSort(context: Context, sort: UserSort) {
        currentSort = sort
        loadUsers(context, 1, sort, force = false)
    }

    fun loadMore(context: Context, sort: UserSort = currentSort) {
        if (isLoading(sort) || isLoadingMore(sort) || !hasNextPage(sort)) return
        val nextPage = (pageBySort[sort] ?: 1) + 1
        loadUsers(context, nextPage, sort)
    }
}
