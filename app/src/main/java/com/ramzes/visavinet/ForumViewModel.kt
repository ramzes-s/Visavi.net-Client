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
                        val rootSection = findSectionById(sectionId)
                        subsections = rootSection?.children ?: emptyList()

                        // Если стек навигации пуст, но у подраздела есть родительский раздел
                        if (backStack.isEmpty() && body.forum?.parent != null) {
                            val chain = buildForumHierarchyChain(body.forum.parent)
                            val newStack = mutableListOf<ForumNavigationState>()
                            newStack.add(ForumNavigationState(level = ForumNavigationLevel.SECTIONS))
                            for (sec in chain) {
                                newStack.add(
                                    ForumNavigationState(
                                        level = ForumNavigationLevel.SECTION,
                                        sectionId = sec.id,
                                        sectionTitle = sec.title
                                    )
                                )
                            }
                            backStack.addAll(newStack)
                        }
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

    fun findSectionById(sectionId: Int, sections: List<ForumSection> = rootSections): ForumSection? {
        for (sec in sections) {
            if (sec.id == sectionId) return sec
            sec.children?.let { sub ->
                val found = findSectionById(sectionId, sub)
                if (found != null) return found
            }
        }
        return null
    }

    fun buildForumHierarchyChain(forum: ForumSection?): List<ForumSection> {
        if (forum == null) return emptyList()
        val chain = mutableListOf<ForumSection>()
        var current: ForumSection? = forum
        val visited = mutableSetOf<Int>()
        while (current != null && !visited.contains(current.id)) {
            visited.add(current.id)
            chain.add(0, current) // Добавляем от корня к текущему элементу
            current = current.parent
        }
        return chain
    }

    fun getSectionChain(sectionId: Int, sections: List<ForumSection> = rootSections): List<ForumSection> {
        for (sec in sections) {
            if (sec.id == sectionId) {
                return listOf(sec)
            }
            sec.children?.let { childs ->
                val subChain = getSectionChain(sectionId, childs)
                if (subChain.isNotEmpty()) {
                    return listOf(sec) + subChain
                }
            }
        }
        return emptyList()
    }

    fun loadTopic(context: Context, topicId: Int, isFromDirectLink: Boolean = false) {
        if (isLoadingPosts) return
        isLoadingPosts = true
        errorMessage = null
        appendPostsErrorMessage = null

        viewModelScope.launch {
            try {
                val itemsPerPage = getItemsPerPage(context)
                val response = VisaviApi.instance.getTopicPosts(topicId, 1, itemsPerPage, order = "asc")
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    currentTopic = body.topic

                    val forum = body.topic?.forum
                    val forumId = forum?.id ?: body.forum?.id ?: body.topic?.realForumId
                    val forumTitle = forum?.title ?: body.forum?.title

                    // Формируем цепочку разделов от корня к подразделу темы
                    val chain = buildForumHierarchyChain(forum)

                    val targetSectionTitle = forumTitle
                        ?: chain.lastOrNull()?.title
                        ?: navigationState.sectionTitle
                        ?: "Форум"
                    val targetSectionId = forumId
                        ?: chain.lastOrNull()?.id
                        ?: navigationState.sectionId

                    if (isFromDirectLink || backStack.isEmpty()) {
                        val newStack = mutableListOf<ForumNavigationState>()
                        newStack.add(ForumNavigationState(level = ForumNavigationLevel.SECTIONS))
                        for (sec in chain) {
                            newStack.add(
                                ForumNavigationState(
                                    level = ForumNavigationLevel.SECTION,
                                    sectionId = sec.id,
                                    sectionTitle = sec.title
                                )
                            )
                        }
                        backStack.clear()
                        backStack.addAll(newStack)
                    }

                    navigationState = navigationState.copy(
                        sectionId = targetSectionId,
                        sectionTitle = targetSectionTitle,
                        topicTitle = body.topic?.title ?: navigationState.topicTitle
                    )

                    val lastPage = body.meta?.lastPage ?: 1
                    postsLastPage = lastPage

                    if (lastPage > 1) {
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

    fun navigateToTopicId(context: Context, topicId: Int, page: Int? = null, postId: Int? = null) {
        navigationState = ForumNavigationState(
            level = ForumNavigationLevel.TOPIC,
            topicId = topicId,
            topicTitle = "Загрузка темы..."
        )
        posts = emptyList()
        postsCurrentPage = page ?: 1
        postsLastPage = 1
        loadTopic(context, topicId, isFromDirectLink = true)
    }

    fun navigateBack(context: Context? = null): Boolean {
        // Убираем элементы уровня TOPIC со входа стека при выходе из темы
        while (backStack.isNotEmpty() && backStack.last().level == ForumNavigationLevel.TOPIC) {
            backStack.removeAt(backStack.size - 1)
        }

        if (backStack.isNotEmpty()) {
            val prevState = backStack.removeAt(backStack.size - 1)
            navigationState = prevState
            if (context != null) {
                when (prevState.level) {
                    ForumNavigationLevel.SECTIONS -> {
                        if (rootSections.isEmpty()) loadRootSections(context)
                    }
                    ForumNavigationLevel.SECTION -> {
                        prevState.sectionId?.let { loadSection(context, it) }
                    }
                    ForumNavigationLevel.TOPIC -> {
                        // Не перегружаем заново
                    }
                }
            }
            return true
        }

        if (navigationState.level == ForumNavigationLevel.TOPIC) {
            val parentForum = currentTopic?.forum
            val targetSectionId = parentForum?.id ?: navigationState.sectionId
            val targetSectionTitle = parentForum?.title ?: navigationState.sectionTitle ?: "Раздел"

            if (targetSectionId != null && targetSectionId > 0) {
                if (parentForum?.parent != null) {
                    val chain = buildForumHierarchyChain(parentForum.parent)
                    backStack.add(ForumNavigationState(level = ForumNavigationLevel.SECTIONS))
                    for (sec in chain) {
                        backStack.add(
                            ForumNavigationState(
                                level = ForumNavigationLevel.SECTION,
                                sectionId = sec.id,
                                sectionTitle = sec.title
                            )
                        )
                    }
                } else {
                    backStack.add(ForumNavigationState(level = ForumNavigationLevel.SECTIONS))
                }

                navigationState = ForumNavigationState(
                    level = ForumNavigationLevel.SECTION,
                    sectionId = targetSectionId,
                    sectionTitle = targetSectionTitle
                )
                if (context != null) loadSection(context, targetSectionId)
                return true
            } else {
                navigationState = ForumNavigationState(level = ForumNavigationLevel.SECTIONS)
                if (context != null) loadRootSections(context)
                return true
            }
        }

        if (navigationState.level == ForumNavigationLevel.SECTION) {
            val currentParentId = currentSection?.parentId
                ?: navigationState.sectionId?.let { secId -> findSectionById(secId)?.parentId }
                ?: 0

            if (currentParentId > 0) {
                val parentSec = currentSection?.parent ?: findSectionById(currentParentId)
                val parentTitle = parentSec?.title ?: findSectionById(currentParentId)?.title ?: "Раздел"

                if (parentSec?.parent != null || (parentSec?.parentId != null && parentSec.parentId > 0)) {
                    val grandParentChain = buildForumHierarchyChain(parentSec.parent)
                    backStack.add(ForumNavigationState(level = ForumNavigationLevel.SECTIONS))
                    for (sec in grandParentChain) {
                        backStack.add(
                            ForumNavigationState(
                                level = ForumNavigationLevel.SECTION,
                                sectionId = sec.id,
                                sectionTitle = sec.title
                            )
                        )
                    }
                } else {
                    backStack.add(ForumNavigationState(level = ForumNavigationLevel.SECTIONS))
                }

                navigationState = ForumNavigationState(
                    level = ForumNavigationLevel.SECTION,
                    sectionId = currentParentId,
                    sectionTitle = parentTitle
                )
                if (context != null) loadSection(context, currentParentId)
                return true
            } else {
                navigationState = ForumNavigationState(level = ForumNavigationLevel.SECTIONS)
                if (context != null) loadRootSections(context)
                return true
            }
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
