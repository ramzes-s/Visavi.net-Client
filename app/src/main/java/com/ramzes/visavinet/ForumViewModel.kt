package com.ramzes.visavinet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramzes.visavinet.network.*
import com.ramzes.visavinet.util.FileUtils
import kotlinx.coroutines.launch

enum class ForumNavigationLevel {
    SECTIONS, SECTION, TOPIC
}

data class ForumNavigationState(
    val level: ForumNavigationLevel = ForumNavigationLevel.SECTIONS,
    val sectionId: Int? = null,
    val sectionTitle: String? = null,
    val topicId: Int? = null,
    val topicTitle: String? = null
)

class ForumViewModel : ViewModel() {

    var rootSections by mutableStateOf<List<ForumSection>>(emptyList())
        private set

    var currentSection by mutableStateOf<ForumInfo?>(null)
        private set

    var subsections by mutableStateOf<List<ForumSection>>(emptyList())
        private set

    var topics by mutableStateOf<List<ForumTopic>>(emptyList())
        private set

    var currentTopic by mutableStateOf<TopicInfo?>(null)
        private set

    var posts by mutableStateOf<List<ForumPost>>(emptyList())
        private set

    var navigationState by mutableStateOf(ForumNavigationState())
        private set

    var isLoadingSections by mutableStateOf(false)
    var isLoadingSection by mutableStateOf(false)
    var isLoadingMoreSection by mutableStateOf(false)
    var minLoadedPage by mutableStateOf(1)
        private set
    var maxLoadedPage by mutableStateOf(1)
        private set
    var isLoadingOlderPosts by mutableStateOf(false)
        private set
    var isLoadingNewerPosts by mutableStateOf(false)
        private set
    var isLoadingPosts by mutableStateOf(false)
    var isLoadingMorePosts by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var appendPostsErrorMessage by mutableStateOf<String?>(null)

    var sectionCurrentPage by mutableStateOf(1)
        private set
    var sectionLastPage by mutableStateOf(1)
        private set

    var postsCurrentPage by mutableStateOf(1)
        private set
    var postsLastPage by mutableStateOf(1)
        private set

    private val backStack = mutableListOf<ForumNavigationState>()

    fun loadRootSections(context: Context) {
        viewModelScope.launch {
            isLoadingSections = true
            errorMessage = null
            try {
                val response = VisaviApi.instance.getForumSections()
                if (response.isSuccessful && response.body() != null) {
                    rootSections = response.body()?.data ?: emptyList()
                    navigationState = ForumNavigationState(level = ForumNavigationLevel.SECTIONS)
                    backStack.clear()
                } else {
                    errorMessage = response.extractErrorMessage("Ошибка загрузки разделов форума")
                }
            } catch (e: Exception) {
                errorMessage = "Ошибка сети: ${e.message}"
            } finally {
                isLoadingSections = false
            }
        }
    }

    fun loadSection(context: Context, sectionId: Int, page: Int = 1, append: Boolean = false) {
        val itemsPerPage = getItemsPerPage(context)

        if (append && (isLoadingMoreSection || isLoadingSection)) return
        if (append) isLoadingMoreSection = true else isLoadingSection = true

        viewModelScope.launch {
            errorMessage = null
            try {
                val response = VisaviApi.instance.getForumSection(sectionId, page, itemsPerPage)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    currentSection = body.forum

                    body.meta?.let { meta ->
                        sectionCurrentPage = meta.currentPage
                        sectionLastPage = meta.lastPage
                    }

                    val rawTopics = body.data ?: emptyList()

                    if (append) {
                        topics = (topics + rawTopics).distinctBy { it.id }
                    } else {
                        topics = rawTopics
                        val rootSection = findSectionById(rootSections, sectionId)
                        subsections = rootSection?.children ?: emptyList()
                    }
                } else {
                    errorMessage = response.extractErrorMessage("Ошибка загрузки тем форума")
                }
            } catch (e: Exception) {
                errorMessage = "Ошибка сети: ${e.message}"
            } finally {
                isLoadingSection = false
                isLoadingMoreSection = false
            }
        }
    }

    fun loadMoreSection(context: Context) {
        if (isLoadingMoreSection || sectionCurrentPage >= sectionLastPage) return
        val sectionId = navigationState.sectionId ?: return
        loadSection(context, sectionId, sectionCurrentPage + 1, append = true)
    }

    fun loadTopic(context: Context, topicId: Int) {
        if (isLoadingPosts) return
        isLoadingPosts = true
        errorMessage = null
        appendPostsErrorMessage = null

        viewModelScope.launch {
            try {
                val itemsPerPage = getItemsPerPage(context)
                // Сначала запрашиваем 1-ю страницу для получения meta info
                val response = VisaviApi.instance.getTopicPosts(topicId, 1, itemsPerPage, order = "asc")
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    currentTopic = body.topic
                    val lastPage = body.meta?.lastPage ?: 1
                    postsLastPage = lastPage

                    if (lastPage > 1) {
                        // Загружаем последнюю страницу со свежими сообщениями
                        val lastPageResponse = VisaviApi.instance.getTopicPosts(topicId, lastPage, itemsPerPage, order = "asc")
                        if (lastPageResponse.isSuccessful && lastPageResponse.body() != null) {
                            val lastBody = lastPageResponse.body()!!
                            val newPosts = lastBody.data ?: emptyList()
                            posts = newPosts.distinctBy { it.id }.sortedBy { it.createdAt }
                            minLoadedPage = lastPage
                            maxLoadedPage = lastPage
                            postsCurrentPage = lastPage
                        } else {
                            val newPosts = body.data ?: emptyList()
                            posts = newPosts.distinctBy { it.id }.sortedBy { it.createdAt }
                            minLoadedPage = 1
                            maxLoadedPage = 1
                            postsCurrentPage = 1
                        }
                    } else {
                        val newPosts = body.data ?: emptyList()
                        posts = newPosts.distinctBy { it.id }.sortedBy { it.createdAt }
                        minLoadedPage = 1
                        maxLoadedPage = 1
                        postsCurrentPage = 1
                    }
                } else {
                    errorMessage = response.extractErrorMessage("Ошибка загрузки сообщений темы")
                }
            } catch (e: Exception) {
                errorMessage = "Ошибка сети: ${e.message}"
            } finally {
                isLoadingPosts = false
            }
        }
    }

    private var lastOlderLoadTimestamp = 0L

    fun loadOlderPosts(context: Context, onSuccess: ((addedCount: Int) -> Unit)? = null) {
        val now = System.currentTimeMillis()
        if (now - lastOlderLoadTimestamp < 600) return
        if (isLoadingOlderPosts || isLoadingPosts || minLoadedPage <= 1) return
        val topicId = navigationState.topicId ?: return
        val targetPage = minLoadedPage - 1

        isLoadingOlderPosts = true
        lastOlderLoadTimestamp = now
        appendPostsErrorMessage = null

        viewModelScope.launch {
            try {
                val itemsPerPage = getItemsPerPage(context)
                val response = VisaviApi.instance.getTopicPosts(topicId, targetPage, itemsPerPage, order = "asc")
                if (response.isSuccessful && response.body() != null) {
                    val newPosts = response.body()?.data ?: emptyList()
                    if (newPosts.isNotEmpty()) {
                        val oldSize = posts.size
                        val combined = (newPosts + posts).distinctBy { it.id }.sortedBy { it.createdAt }
                        val addedCount = combined.size - oldSize
                        posts = combined
                        minLoadedPage = targetPage
                        if (addedCount > 0) {
                            onSuccess?.invoke(addedCount)
                        }
                    } else {
                        minLoadedPage = targetPage
                    }
                } else {
                    appendPostsErrorMessage = response.extractErrorMessage("Ошибка подгрузки сообщений")
                }
            } catch (e: Exception) {
                appendPostsErrorMessage = "Ошибка сети: ${e.message}"
            } finally {
                isLoadingOlderPosts = false
            }
        }
    }

    private var lastNewerLoadTimestamp = 0L

    fun loadNewerPosts(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastNewerLoadTimestamp < 600) return
        if (isLoadingNewerPosts || isLoadingPosts || maxLoadedPage >= postsLastPage) return
        val topicId = navigationState.topicId ?: return
        val targetPage = maxLoadedPage + 1

        isLoadingNewerPosts = true
        lastNewerLoadTimestamp = now
        appendPostsErrorMessage = null

        viewModelScope.launch {
            try {
                val itemsPerPage = getItemsPerPage(context)
                val response = VisaviApi.instance.getTopicPosts(topicId, targetPage, itemsPerPage, order = "asc")
                if (response.isSuccessful && response.body() != null) {
                    val newPosts = response.body()?.data ?: emptyList()
                    if (newPosts.isNotEmpty()) {
                        posts = (posts + newPosts).distinctBy { it.id }.sortedBy { it.createdAt }
                    }
                    maxLoadedPage = targetPage
                } else {
                    appendPostsErrorMessage = response.extractErrorMessage("Ошибка подгрузки сообщений")
                }
            } catch (e: Exception) {
                appendPostsErrorMessage = "Ошибка сети: ${e.message}"
            } finally {
                isLoadingNewerPosts = false
            }
        }
    }

    fun navigateToSection(section: ForumSection, context: Context) {
        backStack.add(navigationState)
        navigationState = ForumNavigationState(
            level = ForumNavigationLevel.SECTION,
            sectionId = section.id,
            sectionTitle = section.title
        )
        sectionCurrentPage = 1
        sectionLastPage = 1
        subsections = section.children ?: emptyList()
        loadSection(context, section.id)
    }

    fun navigateToTopic(topic: ForumTopic, context: Context) {
        backStack.add(navigationState)
        navigationState = ForumNavigationState(
            level = ForumNavigationLevel.TOPIC,
            sectionId = navigationState.sectionId,
            sectionTitle = navigationState.sectionTitle,
            topicId = topic.id,
            topicTitle = topic.title
        )
        postsCurrentPage = 1
        postsLastPage = 1
        loadTopic(context, topic.id)
    }

    fun navigateBack(): Boolean {
        if (backStack.isNotEmpty()) {
            navigationState = backStack.removeAt(backStack.size - 1)
            return true
        }
        return false
    }

    fun refresh(context: Context) {
        when (navigationState.level) {
            ForumNavigationLevel.SECTIONS -> loadRootSections(context)
            ForumNavigationLevel.SECTION -> {
                navigationState.sectionId?.let {
                    sectionCurrentPage = 1
                    sectionLastPage = 1
                    loadSection(context, it)
                }
            }
            ForumNavigationLevel.TOPIC -> {
                navigationState.topicId?.let {
                    postsCurrentPage = 1
                    postsLastPage = 1
                    loadTopic(context, it)
                }
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
                if (response.isSuccessful && response.body()?.data != null) {
                    onSuccess(response.body()!!.data!!)
                } else {
                    onError(response.extractErrorMessage("Пользователь не найден"))
                }
            } catch (e: Exception) {
                onError("Ошибка сети: ${e.message}")
            }
        }
    }

    fun createPost(
        context: Context,
        topicId: Int,
        text: String,
        fileUris: List<android.net.Uri> = emptyList(),
        userRating: Int = 0,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val remainingWait = com.ramzes.visavinet.util.AntifloodManager.getRemainingWaitSeconds(userRating)
        if (remainingWait > 0) {
            onError("Антифлуд: подождите ещё $remainingWait сек. перед следующей отправкой")
            return
        }

        if (text.isBlank()) {
            onError("Введите текст сообщения")
            return
        }

        viewModelScope.launch {
            try {
                val response = if (fileUris.isNotEmpty()) {
                    val textPart = FileUtils.stringToTextPart(text)
                    val fileParts = fileUris.mapNotNull { uri ->
                        FileUtils.uriToMultipartBodyPart(context, uri)
                    }
                    VisaviApi.instance.createPostMultipart(topicId, textPart, fileParts.ifEmpty { null })
                } else {
                    VisaviApi.instance.createPostForm(topicId, text)
                }

                if (response.isSuccessful) {
                    com.ramzes.visavinet.util.AntifloodManager.markMessageSent()
                    loadTopic(context, topicId)
                    onSuccess()
                } else {
                    onError(response.extractErrorMessage("Ошибка создания ответа"))
                }
            } catch (e: Exception) {
                onError("Ошибка сети: ${e.message}")
            }
        }
    }

    fun createTopic(
        context: Context,
        sectionId: Int,
        title: String,
        text: String,
        fileUris: List<android.net.Uri> = emptyList(),
        userRating: Int = 0,
        onSuccess: (ForumTopic) -> Unit,
        onError: (String) -> Unit
    ) {
        val remainingWait = com.ramzes.visavinet.util.AntifloodManager.getRemainingWaitSeconds(userRating)
        if (remainingWait > 0) {
            onError("Антифлуд: подождите ещё $remainingWait сек. перед следующей отправкой")
            return
        }

        if (title.isBlank() || text.isBlank()) {
            onError("Заполните заголовок и текст темы")
            return
        }

        viewModelScope.launch {
            try {
                val response = if (fileUris.isNotEmpty()) {
                    val titlePart = FileUtils.stringToTitlePart(title)
                    val textPart = FileUtils.stringToTextPart(text)
                    val fileParts = fileUris.mapNotNull { uri ->
                        FileUtils.uriToMultipartBodyPart(context, uri)
                    }
                    VisaviApi.instance.createTopicMultipart(sectionId, titlePart, textPart, fileParts.ifEmpty { null })
                } else {
                    VisaviApi.instance.createTopicForm(sectionId, title, text)
                }

                val body = response.body()
                val createdTopic = body?.topic ?: body?.data

                if (response.isSuccessful && createdTopic != null) {
                    com.ramzes.visavinet.util.AntifloodManager.markMessageSent()
                    loadSection(context, sectionId, 1, append = false)
                    onSuccess(createdTopic)
                } else {
                    onError(response.extractErrorMessage("Ошибка создания темы"))
                }
            } catch (e: Exception) {
                onError("Ошибка сети: ${e.message}")
            }
        }
    }

    fun clear() {
        rootSections = emptyList()
        currentSection = null
        subsections = emptyList()
        topics = emptyList()
        currentTopic = null
        posts = emptyList()
        navigationState = ForumNavigationState()
        errorMessage = null
        backStack.clear()
    }

    private fun findSectionById(sections: List<ForumSection>, targetId: Int): ForumSection? {
        for (section in sections) {
            if (section.id == targetId) return section
            section.children?.let { children ->
                val found = findSectionById(children, targetId)
                if (found != null) return found
            }
        }
        return null
    }

    private fun getItemsPerPage(context: Context): Int {
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("items_per_page", 10)
    }
}
