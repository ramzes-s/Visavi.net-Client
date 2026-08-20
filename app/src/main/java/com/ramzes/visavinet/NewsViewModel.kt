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

class NewsViewModel : ViewModel() {

    // --- Список новостей ---
    var newsList by mutableStateOf<List<NewsItem>>(emptyList())
        private set

    var isLoadingNews by mutableStateOf(false)
        private set

    var isLoadingMoreNews by mutableStateOf(false)
        private set

    var newsErrorMessage by mutableStateOf<String?>(null)
        private set

    var newsCurrentPage by mutableIntStateOf(1)
        private set

    var newsLastPage by mutableIntStateOf(1)
        private set

    var scrollItemIndex by mutableIntStateOf(0)
    var scrollOffset by mutableIntStateOf(0)

    // --- Детальный просмотр новости ---
    var currentNews by mutableStateOf<NewsItem?>(null)
        private set

    var isLoadingDetail by mutableStateOf(false)
        private set

    var detailErrorMessage by mutableStateOf<String?>(null)
        private set

    // --- Комментарии ---
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

    // Набор id новостей, за которые в данный момент отправляется голос
    var votingNewsIds by mutableStateOf<Set<Int>>(emptySet())
        private set

    /**
     * Загрузка первой страницы списка новостей
     */
    fun loadNewsList(context: Context, refresh: Boolean = false) {
        if (isLoadingNews) return

        viewModelScope.launch {
            isLoadingNews = true
            newsErrorMessage = null
            if (refresh) {
                newsCurrentPage = 1
            }

            try {
                val response = VisaviApi.instance.getNewsList(page = 1, perPage = 20, order = "desc")
                if (response.isSuccessful) {
                    val body = response.body()
                    newsList = body?.data ?: emptyList()
                    newsCurrentPage = body?.meta?.currentPage ?: 1
                    newsLastPage = body?.meta?.lastPage ?: 1
                } else {
                    newsErrorMessage = response.extractErrorMessage("Ошибка загрузки списка новостей")
                }
            } catch (e: Exception) {
                newsErrorMessage = "Ошибка сети: ${e.localizedMessage ?: "не удалось загрузить новости"}"
            } finally {
                isLoadingNews = false
            }
        }
    }

    /**
     * Подгрузка следующей страницы новостей
     */
    fun loadMoreNews(context: Context) {
        if (isLoadingNews || isLoadingMoreNews || newsCurrentPage >= newsLastPage) return

        viewModelScope.launch {
            isLoadingMoreNews = true
            val nextPage = newsCurrentPage + 1

            try {
                val response = VisaviApi.instance.getNewsList(page = nextPage, perPage = 20, order = "desc")
                if (response.isSuccessful) {
                    val body = response.body()
                    val newItems = body?.data ?: emptyList()
                    val existingIds = newsList.map { it.id }.toSet()
                    val filteredNew = newItems.filter { it.id !in existingIds }
                    newsList = newsList + filteredNew
                    newsCurrentPage = body?.meta?.currentPage ?: nextPage
                    newsLastPage = body?.meta?.lastPage ?: newsLastPage
                }
            } catch (e: Exception) {
                // тихо игнорируем ошибку пагинации
            } finally {
                isLoadingMoreNews = false
            }
        }
    }

    /**
     * Загрузка новости и первой страницы комментариев
     */
    fun loadNewsDetail(context: Context, newsId: Int, refresh: Boolean = false) {
        viewModelScope.launch {
            isLoadingDetail = true
            detailErrorMessage = null
            if (refresh) {
                commentsCurrentPage = 1
            }

            try {
                val response = VisaviApi.instance.getNewsDetail(
                    id = newsId,
                    page = 1,
                    perPage = 20,
                    order = "asc"
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    body?.data?.let { currentNews = it }
                    comments = body?.comments?.data ?: emptyList()
                    commentsCurrentPage = body?.comments?.meta?.currentPage ?: 1
                    commentsLastPage = body?.comments?.meta?.lastPage ?: 1
                } else {
                    detailErrorMessage = response.extractErrorMessage("Ошибка загрузки новости")
                }
            } catch (e: Exception) {
                detailErrorMessage = "Ошибка сети: ${e.localizedMessage ?: "не удалось загрузить новость"}"
            } finally {
                isLoadingDetail = false
            }
        }
    }

    /**
     * Подгрузка следующей страницы комментариев
     */
    fun loadMoreComments(context: Context, newsId: Int) {
        if (isLoadingComments || isLoadingMoreComments || commentsCurrentPage >= commentsLastPage) return

        viewModelScope.launch {
            isLoadingMoreComments = true
            val nextPage = commentsCurrentPage + 1

            try {
                val response = VisaviApi.instance.getNewsDetail(
                    id = newsId,
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
                // тихо игнорируем
            } finally {
                isLoadingMoreComments = false
            }
        }
    }

    /**
     * Создание комментария к новости
     */
    fun createComment(
        context: Context,
        newsId: Int,
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
                    val typePart = "news".toRequestBody("text/plain".toMediaTypeOrNull())
                    val idPart = newsId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
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
                        type = "news",
                        id = newsId,
                        text = text,
                        parentId = parentId
                    )
                }

                if (response.isSuccessful) {
                    val msg = response.body()?.message ?: "Комментарий успешно добавлен"
                    onSuccess(msg)
                    // Перезагружаем новость и комментарии
                    loadNewsDetail(context, newsId, refresh = true)
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
     * Загрузка профиля пользователя по логину
     */
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

    /**
     * Положительное голосование за новость
     */
    fun voteNews(
        newsId: Int,
        context: Context,
        onSuccess: ((newRating: Int) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (newsId in votingNewsIds) return

        viewModelScope.launch {
            votingNewsIds = votingNewsIds + newsId
            try {
                val targetNews = currentNews?.takeIf { it.id == newsId } ?: newsList.find { it.id == newsId }
                val actualType = targetNews?.vote?.type?.ifBlank { null } ?: "news"
                val actualId = targetNews?.vote?.id ?: newsId
                val response = VisaviApi.vote(
                    type = actualType,
                    id = actualId,
                    vote = "+"
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    val newRating = body?.rating ?: ((targetNews?.rating ?: 0) + 1)

                    // Обновляем текущую выбранную новость
                    if (currentNews?.id == newsId) {
                        currentNews = currentNews?.copy(
                            rating = newRating,
                            vote = currentNews?.vote?.copy(value = "+") ?: VoteData(type = actualType, id = newsId, value = "+")
                        )
                    }

                    // Обновляем в списке новостей
                    newsList = newsList.map { item ->
                        if (item.id == newsId) {
                            item.copy(
                                rating = newRating,
                                vote = item.vote?.copy(value = "+") ?: VoteData(type = actualType, id = newsId, value = "+")
                            )
                        } else {
                            item
                        }
                    }

                    onSuccess?.invoke(newRating)
                } else {
                    val errorMsg = response.extractErrorMessage("Не удалось проголосовать")
                    onError?.invoke(errorMsg)
                }
            } catch (e: Exception) {
                onError?.invoke("Ошибка сети: ${e.localizedMessage ?: "не удалось отправить голос"}")
            } finally {
                votingNewsIds = votingNewsIds - newsId
            }
        }
    }

    fun selectNews(news: NewsItem?) {
        currentNews = news
        comments = emptyList()
        commentsCurrentPage = 1
        commentsLastPage = 1
    }
}
