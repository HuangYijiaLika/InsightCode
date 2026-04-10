package com.example.myapplication

import android.content.Context
import android.media.MediaPlayer
import com.alibaba.dashscope.aigc.multimodalconversation.AudioParameters
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult
import android.util.Log
import com.alibaba.dashscope.exception.ApiException
import com.alibaba.dashscope.exception.NoApiKeyException
import com.alibaba.dashscope.exception.UploadFileException
import com.alibaba.dashscope.protocol.Protocol
import com.alibaba.dashscope.utils.Constants
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TTSManager(private val context: Context) {
    private val MODEL = "qwen3-tts-flash"
    private val apiKey = context.getString(R.string.ai_api_key)
    private var mediaPlayer: MediaPlayer? = null
    
    init {
        // 设置API基础URL为北京地域
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1"
    }
    
    suspend fun textToSpeech(text: String, callback: (Boolean, String?) -> Unit) = withContext(Dispatchers.IO) {
        try {
            Log.d("TTS", "开始语音合成: $text")

            val conv = MultiModalConversation()
            val param = MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model(MODEL)
                .text(text)
                .voice(AudioParameters.Voice.CHERRY)
                .languageType("Chinese")
                .build()
            
            val result = conv.call(param)
            val audioUrl = result.output.audio.url

            Log.d("TTS", "获取到音频URL: $audioUrl")
            
            // 下载音频文件
            val audioFile = downloadAudio(audioUrl)
            if (audioFile != null) {
                Log.d("TTS", "音频下载成功: ${audioFile.absolutePath}")
                // 播放音频
                playAudio(audioFile)
                callback(true, null)
            } else {
                Log.e("TTS", "音频下载失败")
                callback(false, "音频下载失败")
            }
        } catch (e: ApiException) {
            Log.e("TTS", "API错误: ${e.message}", e)
            callback(false, "API错误: ${e.message}")
        } catch (e: NoApiKeyException) {
            Log.e("TTS", "API Key错误: ${e.message}", e)
            callback(false, "API Key错误: ${e.message}")
        } catch (e: UploadFileException) {
            Log.e("TTS", "上传文件错误: ${e.message}", e)
            callback(false, "上传文件错误: ${e.message}")
        } catch (e: Exception) {
            Log.e("TTS", "未知错误: ${e.message}", e)
            callback(false, "未知错误: ${e.message}")
        }
    }
    
    private fun downloadAudio(audioUrl: String): File? {
        try {
            val cacheDir = context.cacheDir
            val audioFile = File(cacheDir, "tts_audio.wav")
            
            URL(audioUrl).openStream().use { inputStream ->
                FileOutputStream(audioFile).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }
            return audioFile
        } catch (e: Exception) {
            Log.e("TTS", "下载音频失败: ${e.message}", e)
            e.printStackTrace()
            return null
        }
    }
    
    private fun playAudio(audioFile: File) {
        try {
            Log.d("TTS", "开始播放音频: ${audioFile.absolutePath}")
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnPreparedListener { mp ->
                    Log.d("TTS", "音频准备完成，开始播放")
                    mp.start()
                }
                setOnCompletionListener { mp ->
                    Log.d("TTS", "音频播放完成")
                    mp.release()
                    mediaPlayer = null
                    // 播放完成后删除临时文件
                    audioFile.delete()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("TTS", "MediaPlayer错误: what=$what, extra=$extra")
                    true
                }
                prepareAsync()
            }
//            mediaPlayer?.setDataSource(audioFile.absolutePath)
//            mediaPlayer?.prepare()
//            mediaPlayer?.start()
//
//            mediaPlayer?.setOnCompletionListener {
//                it.release()
//                mediaPlayer = null
//                // 播放完成后删除临时文件
//                audioFile.delete()
//            }
        } catch (e: Exception) {
            Log.e("TTS", "播放音频失败: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}