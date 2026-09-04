package com.ramzes.visavinet

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramzes.visavinet.network.FeedItem
import com.ramzes.visavinet.network.VisaviApi
import com.ramzes.visavinet.network.VoteRequest
import com.ramzes.visavinet.network.extractErrorMessage
import kotlinx.coroutines.launch

class FeedViewModel : ViewModel() {

    var feedItems by mutableStateOf<List<FeedItem>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var currentPage by mutableIntStateOf(1)
        private set

    var hasNextPage by mutableStateOf(true)
        private set

    var scrollItemIndex by mutableIntStateOf(0)
    var scrollOffset by mutableIntStateOf(0)

    fun loadFeed(context: Context, page: Int = 1) {
        if (page == 1) {
            isLoading = true
            errorMessage = null
        } else {
            isLoadingMore = true
        }

        viewModelScope.launch {
            try {
                val response = VisaviApi.instance.getFeed(page = page)
                if (response.isSuccessful) {
                    val body = response.body()
                    val rawItems = body?.data ?: emptyList()
                    val filteredItems = rawItems.filter { it.isSupported }

                    if (page == 1) {
                        feedItems = filteredItems
                    } else {
                        feedItems = feedItems + filteredItems
                    }

                    currentPage = body?.meta?.currentPage ?: page
                    val hasNextFromLinks = !body?.links?.next.isNullOrBlank()
                    val hasNextFromItems = rawItems.isNotEmpty()
                    hasNextPage = hasNextFromLinks || (body?.links == null && hasNextFromItems)
                    errorMessage = null
                } else {
                    val err = response.extractErrorMessage("Не удалось загрузить ленту")
                    if (page == 1) {
                        errorMessage = err
                    }
                }
            } catch (e: Exception) {
                if (page == 1) {
                    errorMessage = e.message ?: "Ошибка соединения"
                }
            } finally {
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    fun refresh(context: Context) {
        currentPage = 1
        hasNextPage = true
        loadFeed(context, 1)
    }

    fun loadMore(context: Context) {
        if (isLoading || isLoadingMore || !hasNextPage) return
        loadFeed(context, currentPage + 1)
    }

    fun vote(
        type: String,
        id: Int,
        vote: String = "+",
        onSuccess: (newRating: Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val response = VisaviApi.instance.vote(VoteRequest(type = type, id = id, vote = vote))
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.success) {
                        onSuccess(body.rating)
                    }
                }
            } catch (e: Exception) {
                // Игнорируем ошибку голосования
            }
        }
    }
}
