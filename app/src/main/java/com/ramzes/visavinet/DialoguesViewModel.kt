package com.ramzes.visavinet

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramzes.visavinet.network.DialogueData
import com.ramzes.visavinet.network.MessageData
import com.ramzes.visavinet.network.VisaviApi
import com.ramzes.visavinet.network.extractErrorMessage
import com.ramzes.visavinet.util.FileUtils
import kotlinx.coroutines.launch

class DialoguesViewModel : ViewModel() {

    var dialogues by mutableStateOf<List<DialogueData>>(emptyList())
        private set

    var messages by mutableStateOf<List<MessageData>>(emptyList())
        private set

    var messagesCurrentPage by mutableStateOf(1)
        private set
    var messagesLastPage by mutableStateOf(1)
        private set
    var isLoadingMoreMessages by mutableStateOf(false)
        private set

    var selectedDialogue by mutableStateOf<DialogueData?>(null)
        private set

    var isLoadingDialogues by mutableStateOf(false)
    var isLoadingMessages by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var isSendingMessage by mutableStateOf(false)
    var sendErrorMessage by mutableStateOf<String?>(null)

    var newMessagesCount by mutableStateOf(0)
        private set

    var scrollToBottom by mutableStateOf(false)
        private set

    var currentPage by mutableStateOf(1)
        private set
    var lastPage by mutableStateOf(1)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set

    private var itemsPerPage: Int = 10
    private val dialoguesMap = mutableMapOf<String, DialogueData>()

    private val _readDialogues = mutableSetOf<Int>()
    val readDialogues: Set<Int> get() = _readDialogues.toSet()

    private var lastDialoguesLoadTime: Long = 0L
    private val DIALOGUES_REFRESH_INTERVAL = 30_000L

    fun loadDialogues(context: Context, page: Int = 1, append: Boolean = false) {
        if (isLoadingDialogues || isLoadingMore) return

        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        itemsPerPage = prefs.getInt("items_per_page", 10)

        viewModelScope.launch {
            if (append) {
                isLoadingMore = true
            } else {
                isLoadingDialogues = true
            }
            errorMessage = null
            try {
                val response = VisaviApi.instance.getDialogues(page, itemsPerPage)
                if (response.isSuccessful && response.body() != null) {
                    val newDialogues = response.body()?.data ?: emptyList()

                    response.body()?.meta?.let { meta ->
                        currentPage = meta.currentPage
                        lastPage = meta.lastPage
                    }

                    if (!append) {
                        dialoguesMap.clear()
                    }

                    newDialogues.forEach { dialogue ->
                        val key = dialogue.login ?: dialogue.name ?: "dialogue_${dialogue.id}"
                        val existing = dialoguesMap[key]
                        if (existing == null || dialogue.createdAt > existing.createdAt) {
                            dialoguesMap[key] = dialogue
                        }
                    }

                    dialogues = dialoguesMap.values
                        .sortedByDescending { it.createdAt }
                        .toList()

                    lastDialoguesLoadTime = System.currentTimeMillis()
                } else {
                    errorMessage = response.extractErrorMessage("Ошибка загрузки диалогов")
                }
            } catch (e: Exception) {
                errorMessage = "Ошибка сети: ${e.message}"
            } finally {
                isLoadingDialogues = false
                isLoadingMore = false
            }
        }
    }

    fun needsRefresh(): Boolean {
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastDialoguesLoadTime) > DIALOGUES_REFRESH_INTERVAL
    }

    fun loadMoreDialogues(context: Context) {
        if (isLoadingMore || currentPage >= lastPage) return
        loadDialogues(context, currentPage + 1, append = true)
    }

    fun selectDialogue(dialogue: DialogueData, context: Context) {
        selectedDialogue = dialogue
        markDialogueAsRead(dialogue.id)
        val login = dialogue.login ?: dialogue.name ?: return
        loadMessages(context, login)
    }

    private fun markDialogueAsRead(dialogueId: Int) {
        _readDialogues.add(dialogueId)
        dialogues = dialogues
    }

    fun isDialogueRead(dialogueId: Int): Boolean = _readDialogues.contains(dialogueId)

    fun loadMessages(context: Context, login: String, page: Int = 1, append: Boolean = false) {
        val prefs = context.getSharedPreferences("visavi_prefs", Context.MODE_PRIVATE)
        itemsPerPage = prefs.getInt("items_per_page", 10)

        viewModelScope.launch {
            if (append) {
                isLoadingMoreMessages = true
            } else {
                isLoadingMessages = true
            }
            errorMessage = null
            try {
                val response = VisaviApi.instance.getTalk(login, page, itemsPerPage)
                if (response.isSuccessful && response.body() != null) {
                    val newMessages = response.body()?.data ?: emptyList()
                    val dialogueInfo = response.body()?.dialogue

                    dialogueInfo?.let { updatedDialogue ->
                        selectedDialogue = updatedDialogue
                    }

                    response.body()?.meta?.let { meta ->
                        messagesCurrentPage = meta.currentPage
                        messagesLastPage = meta.lastPage
                    }

                    if (append) {
                        messages = (newMessages + messages).sortedBy { it.createdAt }
                    } else {
                        messages = newMessages.sortedBy { it.createdAt }
                        scrollToBottom = true
                    }
                } else {
                    errorMessage = response.extractErrorMessage("Ошибка загрузки сообщений")
                }
            } catch (e: Exception) {
                errorMessage = "Ошибка сети: ${e.message}"
            } finally {
                isLoadingMessages = false
                isLoadingMoreMessages = false
            }
        }
    }

    fun loadMoreMessages(context: Context) {
        if (isLoadingMoreMessages || messagesCurrentPage >= messagesLastPage) return
        val dialogue = selectedDialogue ?: return
        val login = dialogue.login ?: dialogue.name ?: return
        loadMessages(context, login, messagesCurrentPage + 1, append = true)
    }

    fun backToDialogues() {
        selectedDialogue = null
        messages = emptyList()
    }

    fun refreshMessages(context: Context) {
        selectedDialogue?.let { dialogue ->
            val login = dialogue.login ?: dialogue.name ?: return
            messagesCurrentPage = 1
            messagesLastPage = 1
            loadMessages(context, login, 1, append = false)
        }
    }

    fun loadUserProfile(context: Context, login: String, onSuccess: (com.ramzes.visavinet.network.UserData) -> Unit, onError: (String) -> Unit) {
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

    /**
     * Отправка личного сообщения с вызовом POST /talk/{login}
     */
    fun sendMessage(
        context: Context,
        text: String,
        fileUris: List<Uri> = emptyList(),
        userRating: Int = 0,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val remainingWait = com.ramzes.visavinet.util.AntifloodManager.getRemainingWaitSeconds(userRating)
        if (remainingWait > 0) {
            onError("Антифлуд: подождите ещё $remainingWait сек. перед следующей отправкой")
            return
        }

        val dialogue = selectedDialogue ?: run {
            onError("Диалог не выбран")
            return
        }

        // Логин собеседника из диалога
        val login = dialogue.login ?: dialogue.name ?: run {
            onError("Невозможно определить получателя")
            return
        }

        if (text.isBlank()) {
            onError("Введите текст сообщения")
            return
        }

        viewModelScope.launch {
            isSendingMessage = true
            sendErrorMessage = null
            try {
                val response = if (fileUris.isNotEmpty()) {
                    val textPart = FileUtils.stringToTextPart(text)
                    val filesParts = fileUris.mapNotNull { uri ->
                        FileUtils.uriToMultipartBodyPart(context, uri)
                    }
                    VisaviApi.instance.sendTalkMultipart(login, textPart, filesParts.ifEmpty { null })
                } else {
                    VisaviApi.instance.sendTalkText(login, text)
                }

                if (response.isSuccessful) {
                    com.ramzes.visavinet.util.AntifloodManager.markMessageSent()
                    refreshMessages(context)
                    onSuccess()
                } else {
                    val errorText = response.extractErrorMessage("Ошибка отправки сообщения")
                    onError(errorText)
                }
            } catch (e: Exception) {
                onError("Ошибка сети: ${e.message}")
            } finally {
                isSendingMessage = false
            }
        }
    }

    fun updateNewMessagesCount(count: Int) {
        newMessagesCount = count
    }

    fun resetNewMessagesCount() {
        newMessagesCount = 0
    }

    fun resetScrollToBottom() {
        scrollToBottom = false
    }

    fun clear() {
        dialogues = emptyList()
        messages = emptyList()
        selectedDialogue = null
        errorMessage = null
        currentPage = 1
        lastPage = 1
        isLoadingMore = false
        dialoguesMap.clear()
        _readDialogues.clear()
        messagesCurrentPage = 1
        messagesLastPage = 1
        isLoadingMoreMessages = false
        newMessagesCount = 0
        scrollToBottom = false
        lastDialoguesLoadTime = 0
    }
}
