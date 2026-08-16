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

class GalleryViewModel : ViewModel() {

    // --- Список элементов галереи ---
    var photosList by mutableStateOf<List<PhotoItem>>(emptyList())
        private set

    var isLoadingPhotos by mutableStateOf(false)
        private set

    var isLoadingMorePhotos by mutableStateOf(false)
        private set

    var photosErrorMessage by mutableStateOf<String?>(null)
        private set

    var photosCurrentPage by mutableIntStateOf(1)
        private set

    var photosLastPage by mutableIntStateOf(1)
        private set

    var scrollItemIndex by mutableIntStateOf(0)
    var scrollOffset by mutableIntStateOf(0)

    // --- Детальный просмотр элемента галереи ---
    var currentPhoto by mutableStateOf<PhotoItem?>(null)
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

    /**
     * Загрузка первой страницы галереи
     */
    fun loadPhotosList(context: Context, refresh: Boolean = false) {
        if (isLoadingPhotos) return

        viewModelScope.launch {
            isLoadingPhotos = true
            photosErrorMessage = null
            if (refresh) {
                photosCurrentPage = 1
            }

            try {
                val response = VisaviApi.instance.getPhotosList(page = 1, perPage = 20, order = "desc")
                if (response.isSuccessful) {
                    val body = response.body()
                    photosList = body?.data ?: emptyList()
                    photosCurrentPage = body?.meta?.currentPage ?: 1
                    photosLastPage = body?.meta?.lastPage ?: 1
                } else {
                    photosErrorMessage = response.extractErrorMessage("Ошибка загрузки галереи")
                }
            } catch (e: Exception) {
                photosErrorMessage = "Ошибка сети: ${e.localizedMessage ?: "не удалось загрузить галерею"}"
            } finally {
                isLoadingPhotos = false
            }
        }
    }

    /**
     * Подгрузка следующей страницы галереи
     */
    fun loadMorePhotos(context: Context) {
        if (isLoadingPhotos || isLoadingMorePhotos || photosCurrentPage >= photosLastPage) return

        viewModelScope.launch {
            isLoadingMorePhotos = true
            val nextPage = photosCurrentPage + 1

            try {
                val response = VisaviApi.instance.getPhotosList(page = nextPage, perPage = 20, order = "desc")
                if (response.isSuccessful) {
                    val body = response.body()
                    val newItems = body?.data ?: emptyList()
                    val existingIds = photosList.map { it.id }.toSet()
                    val filteredNew = newItems.filter { it.id !in existingIds }
                    photosList = photosList + filteredNew
                    photosCurrentPage = body?.meta?.currentPage ?: nextPage
                    photosLastPage = body?.meta?.lastPage ?: photosLastPage
                }
            } catch (e: Exception) {
                // тихо игнорируем ошибку пагинации
            } finally {
                isLoadingMorePhotos = false
            }
        }
    }

    /**
     * Загрузка элемента галереи и первой страницы комментариев
     */
    fun loadPhotoDetail(context: Context, photoId: Int, refresh: Boolean = false) {
        viewModelScope.launch {
            isLoadingDetail = true
            detailErrorMessage = null
            if (refresh) {
                commentsCurrentPage = 1
            }

            try {
                val response = VisaviApi.instance.getPhotoDetail(
                    id = photoId,
                    page = 1,
                    perPage = 20,
                    order = "asc"
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    body?.data?.let { currentPhoto = it }
                    comments = body?.comments?.data ?: emptyList()
                    commentsCurrentPage = body?.comments?.meta?.currentPage ?: 1
                    commentsLastPage = body?.comments?.meta?.lastPage ?: 1
                } else {
                    detailErrorMessage = response.extractErrorMessage("Ошибка загрузки записи")
                }
            } catch (e: Exception) {
                detailErrorMessage = "Ошибка сети: ${e.localizedMessage ?: "не удалось загрузить запись"}"
            } finally {
                isLoadingDetail = false
            }
        }
    }

    /**
     * Подгрузка следующей страницы комментариев
     */
    fun loadMoreComments(context: Context, photoId: Int) {
        if (isLoadingComments || isLoadingMoreComments || commentsCurrentPage >= commentsLastPage) return

        viewModelScope.launch {
            isLoadingMoreComments = true
            val nextPage = commentsCurrentPage + 1

            try {
                val response = VisaviApi.instance.getPhotoDetail(
                    id = photoId,
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
     * Создание комментария к записи галереи (type="photos")
     */
    fun createComment(
        context: Context,
        photoId: Int,
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
                    val typePart = "photos".toRequestBody("text/plain".toMediaTypeOrNull())
                    val idPart = photoId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
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
                        type = "photos",
                        id = photoId,
                        text = text,
                        parentId = parentId
                    )
                }

                if (response.isSuccessful) {
                    val msg = response.body()?.message ?: "Комментарий успешно добавлен"
                    onSuccess(msg)
                    // Перезагружаем элемент галереи и комментарии
                    loadPhotoDetail(context, photoId, refresh = true)
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

    fun selectPhoto(photo: PhotoItem?) {
        currentPhoto = photo
        comments = emptyList()
        commentsCurrentPage = 1
        commentsLastPage = 1
    }
}
