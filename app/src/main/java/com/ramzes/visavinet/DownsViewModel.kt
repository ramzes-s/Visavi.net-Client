package com.ramzes.visavinet

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramzes.visavinet.network.*
import com.ramzes.visavinet.util.FileUtils
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

enum class DownsNavigationLevel {
    CATEGORIES,
    DOWNS_LIST,
    DETAIL
}

class DownsViewModel : ViewModel() {

    // --- Навигация ---
    var navigationLevel by mutableStateOf(DownsNavigationLevel.CATEGORIES)
        private set

    var currentCategory by mutableStateOf<CategoryItem?>(null)
        private set

    var categoryStack by mutableStateOf<List<CategoryItem>>(emptyList())
        private set

    var selectedDown by mutableStateOf<DownItem?>(null)
        private set

    // --- Категории ---
    var categories by mutableStateOf<List<CategoryItem>>(emptyList())
        private set

    var isLoadingCategories by mutableStateOf(false)
        private set

    var categoriesErrorMessage by mutableStateOf<String?>(null)
        private set

    // --- Список загрузок ---
    var downsList by mutableStateOf<List<DownItem>>(emptyList())
        private set

    var isLoadingDowns by mutableStateOf(false)
        private set

    var isLoadingMoreDowns by mutableStateOf(false)
        private set

    var downsErrorMessage by mutableStateOf<String?>(null)
        private set

    var downsCurrentPage by mutableIntStateOf(1)
        private set

    var downsLastPage by mutableIntStateOf(1)
        private set

    // --- Детальный просмотр загрузки ---
    var currentDown by mutableStateOf<DownItem?>(null)
        private set

    var isLoadingDetail by mutableStateOf(false)
        private set

    var detailErrorMessage by mutableStateOf<String?>(null)
        private set

    // --- Комментарии к загрузке ---
    var comments by mutableStateOf<List<NewsCommentItem>>(emptyList())
        private set

    var isLoadingComments by mutableStateOf(false)
        private set

    var isLoadingMoreComments by mutableStateOf(false)
        private set

    var commentsErrorMessage by mutableStateOf<String?>(null)
        private set

    var commentsCurrentPage by mutableIntStateOf(1)
        private set

    var commentsLastPage by mutableIntStateOf(1)
        private set

    var isSubmittingComment by mutableStateOf(false)
        private set

    /**
     * Загрузка списка категорий
     */
    fun loadCategories(context: Context, refresh: Boolean = false) {
        if (isLoadingCategories) return

        viewModelScope.launch {
            isLoadingCategories = true
            categoriesErrorMessage = null

            try {
                val response = VisaviApi.instance.getLoadCategories()
                if (response.isSuccessful) {
                    categories = response.body()?.data ?: emptyList()
                } else {
                    categoriesErrorMessage = response.extractErrorMessage("Ошибка загрузки категорий")
                }
            } catch (e: Exception) {
                categoriesErrorMessage = "Ошибка сети: ${e.localizedMessage ?: "не удалось загрузить категории"}"
            } finally {
                isLoadingCategories = false
            }
        }
    }

    /**
     * Открытие категории или подкатегории
     */
    fun openCategory(category: CategoryItem, context: Context) {
        currentCategory?.let { prev ->
            categoryStack = categoryStack + prev
        }
        currentCategory = category
        navigationLevel = DownsNavigationLevel.DOWNS_LIST
        loadDownsForCurrentCategory(context, refresh = true)
    }

    /**
     * Открытие списка «Все новые загрузки»
     */
    fun openAllNewDowns(context: Context) {
        categoryStack = emptyList()
        currentCategory = null
        navigationLevel = DownsNavigationLevel.DOWNS_LIST
        loadDownsForCurrentCategory(context, refresh = true)
    }

    /**
     * Загрузка файлов текущей категории
     */
    fun loadDownsForCurrentCategory(context: Context, refresh: Boolean = false) {
        if (isLoadingDowns) return

        viewModelScope.launch {
            isLoadingDowns = true
            downsErrorMessage = null
            if (refresh) {
                downsCurrentPage = 1
            }

            try {
                val response = VisaviApi.instance.getDownsList(
                    categoryId = currentCategory?.id,
                    page = 1,
                    perPage = 20,
                    order = "desc"
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    downsList = body?.data ?: emptyList()
                    downsCurrentPage = body?.meta?.currentPage ?: 1
                    downsLastPage = body?.meta?.lastPage ?: 1
                } else {
                    downsErrorMessage = response.extractErrorMessage("Ошибка загрузки файлов")
                }
            } catch (e: Exception) {
                downsErrorMessage = "Ошибка сети: ${e.localizedMessage ?: "не удалось загрузить файлы"}"
            } finally {
                isLoadingDowns = false
            }
        }
    }

    /**
     * Подгрузка следующей страницы загрузок
     */
    fun loadMoreDowns(context: Context) {
        if (isLoadingDowns || isLoadingMoreDowns || downsCurrentPage >= downsLastPage) return

        viewModelScope.launch {
            isLoadingMoreDowns = true
            val nextPage = downsCurrentPage + 1

            try {
                val response = VisaviApi.instance.getDownsList(
                    categoryId = currentCategory?.id,
                    page = nextPage,
                    perPage = 20,
                    order = "desc"
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    val newItems = body?.data ?: emptyList()
                    val existingIds = downsList.map { it.id }.toSet()
                    val filteredNew = newItems.filter { it.id !in existingIds }
                    downsList = downsList + filteredNew
                    downsCurrentPage = body?.meta?.currentPage ?: nextPage
                    downsLastPage = body?.meta?.lastPage ?: downsLastPage
                }
            } catch (e: Exception) {
                // игнорируем
            } finally {
                isLoadingMoreDowns = false
            }
        }
    }

    /**
     * Открытие детального просмотра загрузки
     */
    fun openDownDetail(down: DownItem, context: Context) {
        selectedDown = down
        currentDown = down
        comments = emptyList()
        commentsCurrentPage = 1
        commentsLastPage = 1
        navigationLevel = DownsNavigationLevel.DETAIL
        loadDownDetail(down.id, context)
    }

    /**
     * Загрузка детальной информации о загрузке и комментариев
     */
    fun loadDownDetail(downId: Int, context: Context, refresh: Boolean = false) {
        viewModelScope.launch {
            isLoadingDetail = true
            detailErrorMessage = null
            if (refresh) {
                commentsCurrentPage = 1
            }

            try {
                val response = VisaviApi.instance.getDownDetail(
                    id = downId,
                    page = 1,
                    perPage = 20,
                    order = "asc"
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    body?.data?.let { currentDown = it }
                    comments = body?.comments?.data ?: emptyList()
                    commentsCurrentPage = body?.comments?.meta?.currentPage ?: 1
                    commentsLastPage = body?.comments?.meta?.lastPage ?: 1
                } else {
                    detailErrorMessage = response.extractErrorMessage("Ошибка загрузки данных файла")
                }
            } catch (e: Exception) {
                detailErrorMessage = "Ошибка сети: ${e.localizedMessage ?: "не удалось загрузить файл"}"
            } finally {
                isLoadingDetail = false
            }
        }
    }

    /**
     * Подгрузка следующей страницы комментариев
     */
    fun loadMoreComments(downId: Int, context: Context) {
        if (isLoadingComments || isLoadingMoreComments || commentsCurrentPage >= commentsLastPage) return

        viewModelScope.launch {
            isLoadingMoreComments = true
            val nextPage = commentsCurrentPage + 1

            try {
                val response = VisaviApi.instance.getDownDetail(
                    id = downId,
                    page = nextPage,
                    perPage = 20,
                    order = "asc"
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    val newComments = body?.comments?.data ?: emptyList()
                    val existingIds = comments.map { it.id }.toSet()
                    val filteredNew = newComments.filter { it.id !in existingIds }
                    comments = comments + filteredNew
                    commentsCurrentPage = body?.comments?.meta?.currentPage ?: nextPage
                    commentsLastPage = body?.comments?.meta?.lastPage ?: commentsLastPage
                }
            } catch (e: Exception) {
                // игнорируем
            } finally {
                isLoadingMoreComments = false
            }
        }
    }

    /**
     * Создание комментария к загрузке (type="downs")
     */
    fun createComment(
        context: Context,
        downId: Int,
        text: String,
        parentId: Int? = null,
        fileUris: List<Uri> = emptyList(),
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isSubmittingComment) return

        viewModelScope.launch {
            isSubmittingComment = true
            try {
                val response = if (fileUris.isNotEmpty()) {
                    val typePart = "downs".toRequestBody("text/plain".toMediaTypeOrNull())
                    val idPart = downId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val textPart = text.toRequestBody("text/plain".toMediaTypeOrNull())
                    val parentIdPart = parentId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
                    val fileParts = fileUris.mapNotNull { uri ->
                        FileUtils.uriToMultipartBodyPart(context, uri, "files[]")
                    }

                    VisaviApi.instance.createCommentMultipart(
                        type = typePart,
                        id = idPart,
                        text = textPart,
                        parentId = parentIdPart,
                        files = fileParts
                    )
                } else {
                    VisaviApi.instance.createCommentForm(
                        type = "downs",
                        id = downId,
                        text = text,
                        parentId = parentId
                    )
                }

                if (response.isSuccessful) {
                    val msg = response.body()?.message ?: "Комментарий успешно добавлен"
                    onSuccess(msg)
                    loadDownDetail(downId, context, refresh = true)
                } else {
                    onError(response.extractErrorMessage("Ошибка добавления комментария"))
                }
            } catch (e: Exception) {
                onError("Ошибка отправки: ${e.localizedMessage ?: "неизвестная ошибка"}")
            } finally {
                isSubmittingComment = false
            }
        }
    }

    /**
     * Навигация «Назад» по иерархии категорий
     * Возвращает true если возврат обработан внутри раздела Загрузки, false если нужно выйти в главное меню
     */
    fun navigateBack(context: Context): Boolean {
        return when (navigationLevel) {
            DownsNavigationLevel.DETAIL -> {
                navigationLevel = DownsNavigationLevel.DOWNS_LIST
                selectedDown = null
                true
            }
            DownsNavigationLevel.DOWNS_LIST -> {
                if (categoryStack.isNotEmpty()) {
                    val prevCategory = categoryStack.last()
                    categoryStack = categoryStack.dropLast(1)
                    currentCategory = prevCategory
                    loadDownsForCurrentCategory(context, refresh = true)
                    true
                } else {
                    navigationLevel = DownsNavigationLevel.CATEGORIES
                    currentCategory = null
                    true
                }
            }
            DownsNavigationLevel.CATEGORIES -> {
                false
            }
        }
    }

    fun loadUserProfile(
        context: Context,
        login: String,
        onSuccess: (UserData) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val response = VisaviApi.instance.getUserByLogin(login)
                if (response.isSuccessful) {
                    val user = response.body()?.data
                    if (user != null) {
                        onSuccess(user)
                    } else {
                        onError("Пользователь не найден")
                    }
                } else {
                    onError(response.extractErrorMessage("Пользователь не найден"))
                }
            } catch (e: Exception) {
                onError("Ошибка сети: ${e.localizedMessage ?: "не удалось загрузить профиль"}")
            }
        }
    }

    fun clear() {
        navigationLevel = DownsNavigationLevel.CATEGORIES
        currentCategory = null
        categoryStack = emptyList()
        selectedDown = null
        downsList = emptyList()
        categories = emptyList()
    }
}
