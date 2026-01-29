package com.example.myapplication.viewModule

import android.media.Image
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import com.google.gson.Gson

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false,
//    val stream_options: StreamOptions? = null // 新增流式传输选项
)

data class Message(
    val role: String,
    val content: List<Content>
)

data class Content(
    val type: String,
    val image_url: ImageUrl? = null, // 图像内容
    val text: String? = null         // 文本内容
)

data class ImageUrl(
    val url: String
)

data class StreamOptions(
    val include_usage: Boolean
)

// 响应数据模型（根据实际API响应结构调整）
data class ChatResponse(
    val id: String?,
    val choices: List<Choice>?,
    val created: Long?,
    val model: String?,
    val object_type: String?
)

data class Choice(
    val index: Int,
    val message: GetMessage,
    val finish_reason: String?
)

data class GetMessage(
    val content: String,
    val role: String,
    val reasoning_content: String
)

interface ApiService {
    @POST("chat/completions")
    suspend fun getPost(
        @Header("Authorization") apiKey: String,
        @Body request: ChatRequest
    ): ChatResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/"

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

class PostViewModel : ViewModel() {
    val _postState = mutableStateOf<ChatResponse?>(null)
    val _isLoading = mutableStateOf(false)
    val _error = mutableStateOf<String?>(null)

    fun fetchPost(model: String, userMessages: List<Content>, apiKey: String) {
        val chatRequest = ChatRequest(
            model = model,
            messages = listOf(
                Message(role = "user", content = userMessages)
            )
            ,
            stream = false,
//            stream_options = StreamOptions(include_usage = false)
        )
        val gson = Gson()
        val jsonOutput = gson.toJson(chatRequest)
        println("请求体 JSON 格式: $jsonOutput")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitClient.instance.getPost("Bearer $apiKey", chatRequest)
                _postState.value = response
                _error.value = null
                println(_postState.value)
            } catch (e: Exception) {
                _error.value = "Error: ${e.message}"
                println("请求失败，错误信息: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}