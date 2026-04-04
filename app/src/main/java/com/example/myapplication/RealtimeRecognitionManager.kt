package com.example.myapplication

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class RealtimeRecognitionManager(
    private val context: Context,
    private val apiKey: String,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    
    // 音频参数
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    
    // WebSocket URL
    private val websocketUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference"
    
    fun startRecognition() {
        scope.launch {
            try {
                connectWebSocket()
            } catch (e: Exception) {
                Log.e("RealtimeRecognition", "Start recognition error: ${e.message}")
                onError("启动语音识别失败: ${e.message}")
            }
        }
    }
    
    private fun connectWebSocket() {
        val request = Request.Builder()
            .url(websocketUrl)
            .header("Authorization", "Bearer $apiKey")
            .header("X-DashScope-DataInspection", "enable")
            .build()
        
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("RealtimeRecognition", "WebSocket connected")
                this@RealtimeRecognitionManager.webSocket = webSocket
                
                // 发送开始识别的消息
                val startMessage = JSONObject().apply {
                    put("model", "fun-asr-realtime")
                    put("input", JSONObject().apply {
                        put("format", "pcm")
                        put("sample_rate", sampleRate)
                    })
                    put("parameters", JSONObject().apply {
                        put("result_format", "message")
                    })
                }
                webSocket.send(startMessage.toString())
                
                // 开始录音
                startRecording()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("RealtimeRecognition", "Received message: $text")
                handleMessage(text)
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d("RealtimeRecognition", "Received bytes: ${bytes.size}")
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("RealtimeRecognition", "WebSocket closing: $code, $reason")
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("RealtimeRecognition", "WebSocket closed: $code, $reason")
                isRecording = false
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("RealtimeRecognition", "WebSocket error: ${t.message}")
                onError("WebSocket错误: ${t.message}")
                isRecording = false
            }
        }
        
        client.newWebSocket(request, listener)
    }
    
    private fun handleMessage(message: String) {
        try {
            val json = JSONObject(message)
            val output = json.optJSONObject("output")
            val sentence = output?.optJSONObject("sentence")
            
            if (sentence != null) {
                val text = sentence.optString("text", "")
                val isEnd = sentence.optBoolean("is_end", false)
                
                if (isEnd) {
                    Log.d("RealtimeRecognition", "Final Result: $text")
                    onResult(text)
                } else {
                    Log.d("RealtimeRecognition", "Intermediate Result: $text")
                }
            }
        } catch (e: Exception) {
            Log.e("RealtimeRecognition", "Parse message error: ${e.message}")
        }
    }
    
    private fun startRecording() {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            audioRecord?.startRecording()
            isRecording = true
            
            // 在协程中读取音频数据并发送给识别服务
            scope.launch {
                val buffer = ByteBuffer.allocate(1024)
                while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord?.read(buffer.array(), 0, buffer.capacity()) ?: 0
                    if (read > 0 && webSocket != null) {
                        // 发送音频数据
                        val audioData = buffer.array().copyOf(read)
                        webSocket?.send(ByteString.of(*audioData.toTypedArray()))
                        buffer.clear()
                        // 防止CPU占用过高
                        delay(20)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("RealtimeRecognition", "Start recording error: ${e.message}")
            onError("启动录音失败: ${e.message}")
        }
    }
    
    fun stopRecognition() {
        isRecording = false
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e("RealtimeRecognition", "Stop recording error: ${e.message}")
        }
        
        try {
            // 发送结束识别的消息
            val endMessage = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("is_end", true)
                })
            }
            webSocket?.send(endMessage.toString())
            webSocket?.close(1000, "bye")
            webSocket = null
        } catch (e: Exception) {
            Log.e("RealtimeRecognition", "Stop recognition error: ${e.message}")
        }
    }
    
    fun release() {
        stopRecognition()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }
}