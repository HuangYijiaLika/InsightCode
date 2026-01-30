package com.example.myapplication

import android.Manifest
import android.Manifest.permission.RECORD_AUDIO
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.example.myapplication.viewModule.PostViewModel
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.viewModule.Content
import com.example.myapplication.viewModule.ImageUrl
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.example.myapplication.viewModule.Message
import java.io.ByteArrayOutputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import java.nio.ByteBuffer
import com.example.myapplication.R
import android.content.Intent
import java.util.Locale
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.IntOffset
import android.speech.tts.TextToSpeech
import android.os.Vibrator
import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.example.myapplication.viewModule.AIResponseJson

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
                        PostScreen(
                            modifier = Modifier.padding(paddingValues),
                            viewModel = PostViewModel()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PostScreen(modifier: Modifier = Modifier, viewModel: PostViewModel = PostViewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 检查相机权限
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 检查麦克风权限
    var hasMicrophonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 同时请求相机和麦克风权限
    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasMicrophonePermission = permissions[RECORD_AUDIO] == true
    }

    // 自动申请权限
    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasMicrophonePermission) {
            multiplePermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, RECORD_AUDIO))
        }
    }

    // 音频录制状态
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    // 语音识别相关
    var isRecognizing by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var speechRecognizer: SpeechRecognizer? = remember { null }
    // 文字转语音相关
    var textToSpeech: TextToSpeech? = remember { null }
    // 震动相关
    var vibrator: Vibrator? = remember { null }
    // 加载状态
    var isLoading by remember { mutableStateOf(false) }
    // 错误信息
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // 协程作用域
    val coroutineScope = rememberCoroutineScope()
    // 历史voice_text
    var historyVoiceText by remember { mutableStateOf("") }
    // 自动发送处理器
    var autoSendHandler: Handler? = remember { null }
    // 任务完成状态
    var isTaskComplete by remember { mutableStateOf(false) }
    // 点击位置和光晕状态
    var isPressed by remember { mutableStateOf(false) }
    var pressPosition by remember { mutableStateOf(Offset.Zero) }
    // 解析后的voice_text
    var voiceText by remember { mutableStateOf("") }

    // 存储录制的音频数据
    val recordedAudioData = remember { mutableListOf<ByteArray>() }
    // 相机预览视图
    val previewView = remember { PreviewView(context) }
    var imageCapture: ImageCapture? = remember { null }
    var cameraProvider: ProcessCameraProvider? = remember { null }

    // 初始化相机
    fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({ 
            val provider = cameraProviderFuture.get()
            cameraProvider = provider
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                Log.d("CameraX", "相机初始化成功")
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
                errorMessage = "相机初始化失败: ${exc.message}"
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // 初始化语音识别
    fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("SpeechRecognition", "准备就绪")
                }

                override fun onBeginningOfSpeech() {
                    Log.d("SpeechRecognition", "开始说话")
                }

                override fun onRmsChanged(rmsdB: Float) {
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                }

                override fun onEndOfSpeech() {
                    Log.d("SpeechRecognition", "结束说话")
                }

                override fun onError(error: Int) {
                    Log.e("SpeechRecognition", "错误: $error")
                    isRecognizing = false
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        recognizedText = matches[0]
                        Log.d("SpeechRecognition", "识别结果: ${matches[0]}")
                    }
                    isRecognizing = false
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        recognizedText = matches[0]
                        Log.d("SpeechRecognition", "部分结果: ${matches[0]}")
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                }
            })
        } else {
            errorMessage = "设备不支持语音识别"
        }
    }

    // 初始化文字转语音
    fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context) {
            if (it == TextToSpeech.SUCCESS) {
                textToSpeech?.setLanguage(Locale.CHINA)
                Log.d("TextToSpeech", "初始化成功")
            } else {
                Log.e("TextToSpeech", "初始化失败")
            }
        }
    }

    // 初始化震动器
    fun initializeVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(Vibrator::class.java)
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // 文字转语音
    fun speakText(text: String) {
        if (textToSpeech == null) {
            initializeTextToSpeech()
        }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // 根据vibration_mode进行震动
    fun vibrateBasedOnMode(mode: String) {
        if (vibrator == null) {
            initializeVibrator()
        }

        val pattern = when (mode) {
            "low_freq" -> longArrayOf(0, 200, 100, 200) // 低频震动
            "high_freq" -> longArrayOf(0, 50, 50, 50, 50, 50) // 高频震动
            "swipe_left" -> longArrayOf(0, 100, 50, 100, 50, 200) // 向左滑动震动
            "swipe_right" -> longArrayOf(0, 200, 50, 100, 50, 100) // 向右滑动震动
            else -> longArrayOf(0, 100) // 默认震动
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrationEffect = android.os.VibrationEffect.createWaveform(pattern, -1)
            vibrator?.vibrate(vibrationEffect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, -1)
        }
    }

    
    // 拍照并发送给AI
    fun takePhotoAndSendToAI(includeHistory: Boolean = false) {
        isLoading = true
        errorMessage = null

        // 重新初始化相机以确保可用
        initializeCamera()

        // 等待相机初始化完成
        Handler(Looper.getMainLooper()).postDelayed({
            val imageCapture = imageCapture ?: run {
                errorMessage = "相机未初始化"
                isLoading = false
                return@postDelayed
            }

            // 创建临时文件
            val photoFile = File.createTempFile("temp_photo", ".jpg", context.cacheDir)
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            // 拍照
            imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        try {
                            // 将图片转换为Base64
                            val base64Image = bitmapToBase64(photoFile)
                            if (base64Image.isNullOrEmpty()) {
                                errorMessage = "图片转换失败"
                                isLoading = false
                                // 删除临时文件
                                photoFile.delete()
                                return
                            }

                            // 构建请求消息
                            val userMessages = mutableListOf<Content>()
                            
                            // 添加图片
                            userMessages.add(
                                Content(
                                    type = "image_url",
                                    image_url = ImageUrl(url = "data:image/jpeg;base64,$base64Image")
                                )
                            )
                            
                            // 添加提示词
                            userMessages.add(
                                Content(
                                    type = "text",
                                    text = context.getString(R.string.ai_prompt_what)
                                )
                            )
                            userMessages.add(
                                Content(
                                    type = "text",
                                    text = "请带我去灶台前."
                                )
                            )
                            // 如果需要包含历史voice_text
                            if (includeHistory && historyVoiceText.isNotEmpty()) {
                                userMessages.add(
                                    Content(
                                        type = "text",
                                        text = "历史语音指令，仅作为参考: $historyVoiceText"
                                    )
                                )
                            }

                            // 发送给AI
                            viewModel.fetchPost("qwen3-vl-flash", userMessages, "sk-ee10fa059ce846468490b65eb61a278a")

                            // 删除临时文件
                            photoFile.delete()
                        } catch (e: Exception) {
                            Log.e("PhotoAI", "处理图片失败", e)
                            errorMessage = "处理图片失败: ${e.message}"
                            // 删除临时文件
                            photoFile.delete()
                        } finally {
                            isLoading = false
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("PhotoAI", "拍照失败", exception)
                        errorMessage = "拍照失败: ${exception.message}"
                        isLoading = false
                    }
                }
            )
        }, 500)
    }
    // 处理AI响应的JSON
    fun processAIResponseJson(jsonString: String) {
        try {
            // 去除可能的```json```和```标记
            val cleanedJsonString = jsonString
                .trim()
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()
            
            val gson = Gson()
            val aiResponse = gson.fromJson(cleanedJsonString, AIResponseJson::class.java)

            // 保存voice_text到状态变量
            voiceText = aiResponse.voice_text

            // 1. 将voice_text转语音
            speakText(aiResponse.voice_text)

            
            // 2. 保存历史voice_text
            historyVoiceText +=aiResponse.voice_text+","
            // 3. 根据vibration_mode进行震动
            // 4. 如果is_task_complete为假，定时自动发送图片
            if (!aiResponse.is_task_complete) {
                vibrateBasedOnMode(aiResponse.vibration_mode)
                autoSendHandler?.removeCallbacksAndMessages(null)
                autoSendHandler = Handler(Looper.getMainLooper())
                autoSendHandler?.postDelayed({
                    takePhotoAndSendToAI(true)
                }, aiResponse.next_transmission_ms.toLong())
            } else {
                isTaskComplete = true
                autoSendHandler?.removeCallbacksAndMessages(null)
            }
        } catch (e: Exception) {
            Log.e("AIResponse", "解析JSON失败", e)
            errorMessage = "解析AI响应失败: ${e.message}"
        }
    }

    // 开始语音识别
    fun startSpeechRecognition() {
        if (speechRecognizer == null) {
            initializeSpeechRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toString())
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

        try {
            speechRecognizer?.startListening(intent)
            isRecognizing = true
            // 清空之前的文字
            recognizedText = ""
        } catch (e: Exception) {
            Log.e("SpeechRecognition", "启动失败", e)
            errorMessage = "语音识别启动失败: ${e.message}"
            isRecognizing = false
        }
    }

    // 停止语音识别
    fun stopSpeechRecognition() {
        speechRecognizer?.stopListening()
        isRecognizing = false
    }

    // 首次初始化相机
    LaunchedEffect(Unit) {
        initializeCamera()
    }


    Box(modifier = modifier.fillMaxSize()) {
        Column {
            // 显示错误信息
            if (!errorMessage.isNullOrEmpty()) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            }

            // 语音识别状态提示
            if (isRecognizing) {
                Text(
                    text = "🎤 正在聆听（点击/长按说话）",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            // 显示AI响应
            val currentResult = viewModel._postState.value?.choices?.firstOrNull()?.message?.content
            Log.d("currentResult", "currentResult: $currentResult")

            // 监听AI响应变化并处理
            LaunchedEffect(currentResult) {
                if (!currentResult.isNullOrEmpty()) {
                    // 处理AI响应的JSON
                    processAIResponseJson(currentResult)
                }
            }

            if (voiceText.isNotEmpty()) {
                Text(voiceText)
            } else if (!isLoading && errorMessage.isNullOrEmpty()) {
                Text("好的，为你导航到厨房灶台。")
            }

            // 显示相机预览
            Box(
                modifier = Modifier
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                /*onTap = { offset ->
                                    if (hasMicrophonePermission) {
                                        pressPosition = offset
                                        isPressed = true
                                        recognizedText = "正在识别..."
                                        startSpeechRecognition()
                                        coroutineScope.launch {
                                            delay(3000)
                                            stopSpeechRecognition()
                                            isPressed = false
                                        }
                                    } else {
                                        errorMessage = "请先授予麦克风权限"
                                    }
                                },*/
                                onPress = { offset ->
                                    if (hasMicrophonePermission) {
                                        pressPosition = offset
                                        isPressed = true
                                        // 按下时震动
                                        vibrateBasedOnMode("low_freq")
                                        recognizedText = "长按识别中..."
                                        startSpeechRecognition()
                                        tryAwaitRelease()
                                        stopSpeechRecognition()
                                        // 抬起时震动
                                        vibrateBasedOnMode("low_freq")
                                        isPressed = false
                                        takePhotoAndSendToAI()
                                    } else {
                                        errorMessage = "请先授予麦克风权限"
                                    }
                                }
                            )
                        }
                )

                // 光晕效果
                if (isPressed) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        drawCircle(
                            color = Color.Blue.copy(alpha = 0.3f),
                            radius = 50f,
                            center = pressPosition
                        )
                    }
                }
            }

            // 录音控制按钮已隐藏
            /*
            if (hasMicrophonePermission) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 长按录音按钮
                    Box(
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        // 长按开始录音和语音识别
                                        startSpeechRecognition()
                                        // 等待用户释放
                                        tryAwaitRelease()
                                        // 释放后停止识别
                                        stopSpeechRecognition()
                                    },
                                    onTap = {
                                        // 点击事件：刷新文字
                                        recognizedText = ""
                                    }
                                )
                            }
                    ) {
                        Button(
                            modifier = Modifier
                                .width(200.dp)
                                .height(60.dp),
                            onClick = {
                                // 点击事件：刷新文字
                                recognizedText = ""
                            }
                        ) {
                            Text(
                                if (isRecognizing) "正在识别..." else "长按录音",
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // 显示识别的文字
                    if (recognizedText.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .wrapContentHeight(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Text(
                                text = recognizedText,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    // 播放录音按钮
                    Button(
                        onClick = {
                            if (!isPlaying && recordedAudioData.isNotEmpty()) {
                                playRecordedAudio(recordedAudioData) {
                                    isPlaying = false // 播放完成后重置状态
                                }
                                isPlaying = true
                            }
                        },
                        modifier = Modifier
                            .width(200.dp)
                            .height(60.dp)
                            .padding(top = 8.dp)
                    ) {
                        Text("播放录音")
                    }
                }
            }
            */


        }
    }
}

// 将图片文件转换为Base64编码
private fun bitmapToBase64(file: File): String? {
    return try {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        Base64.encodeToString(byteArray, Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.e("Base64", "转换失败", e)
        null
    }
}




private var audioRecord: AudioRecord? = null
private var recordingThread: Thread? = null
private var isRecordingActive = false

// 将 Context 作为参数传入，避免在非 Composable 函数中使用 LocalContext.current
private fun startRecording(audioBuffer: MutableList<ByteArray>, context: android.content.Context) {
    // 检查麦克风权限
    if (ContextCompat.checkSelfPermission(
            context,
            RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {
        Log.w("AudioRecord", "No microphone permission granted")
        return
    }

    // 清空之前的录音数据
    audioBuffer.clear()

    val audioSource = MediaRecorder.AudioSource.MIC
    val sampleRate = 44100
    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    audioRecord = AudioRecord(
        audioSource,
        sampleRate,
        channelConfig,
        audioFormat,
        bufferSize
    ).apply {
        startRecording()
        isRecordingActive = true

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecordingActive) {
                val bytesRead = read(buffer, 0, bufferSize)
                if (bytesRead > 0) {
                    val dataCopy = buffer.copyOf(bytesRead)
                    audioBuffer.add(dataCopy)
                }
            }
        }
        recordingThread?.start()
    }
}

private fun playRecordedAudio(
    audioData: List<ByteArray>,
    onPlaybackFinished: () -> Unit // 添加回调参数
) {
    // 检查是否有音频数据
    if (audioData.isEmpty()) {
        Log.w("AudioPlay", "No audio data to play")
        return
    }

    val audioTrackThread = Thread {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        // 计算总大小
        val totalSize = audioData.sumOf { it.size }
        val playbackBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        // 确保缓冲区大小足够大
        val finalBufferSize = Math.max(playbackBufferSize, totalSize)

        val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            channelConfig,
            audioFormat,
            finalBufferSize,
            AudioTrack.MODE_STATIC
        )

        // 将录制的数据复制到 AudioTrack
        val combinedData = ByteArray(totalSize)
        var offset = 0
        for (chunk in audioData) {
            System.arraycopy(chunk, 0, combinedData, offset, chunk.size)
            offset += chunk.size
        }

        val writeResult = audioTrack.write(combinedData, 0, combinedData.size)
        if (writeResult > 0) {
            audioTrack.play()

            // 等待播放完成
            while (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                Thread.sleep(100)
            }
        } else {
            Log.e("AudioPlay", "Failed to write audio data to track")
        }

        audioTrack.release()

        // 调用回调更新状态
        Handler(Looper.getMainLooper()).post {
            onPlaybackFinished()
        }
    }

    audioTrackThread.start()
}




private fun stopRecording() {
    isRecordingActive = false
    audioRecord?.apply {
        stop()
        release()
    }
    audioRecord = null
    recordingThread?.interrupt()
    recordingThread = null
}

//val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current