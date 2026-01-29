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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.example.myapplication.viewModule.Message

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

    // 存储录制的音频数据
    val recordedAudioData = remember { mutableListOf<ByteArray>() }
    // 相机预览视图
    val previewView = remember { PreviewView(context) }
    var imageCapture: ImageCapture? = remember { null }

    // 初始化相机
    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }


    Box(modifier = modifier.fillMaxSize()) {
        Column {
            // 显示相机预览
            AndroidView(factory = { previewView }, modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    // 触发网络请求
                    val userMessages = listOf(
                        Content(
                            type = "image_url",
                            image_url = ImageUrl(url = "https://dashscope.oss-cn-beijing.aliyuncs.com/images/dog_and_girl.jpeg")
                        ),
                        Content(
                            type = "text",
                            text = "用这个格式告诉我主体是什么，例如：这是狗"
                        )
                    )
                    viewModel.fetchPost("qwen3-vl-plus", userMessages, "sk-ee10fa059ce846468490b65eb61a278a")
                }
            ) {
                Text("发起请求")
            }
            val currentResult = viewModel._postState.value?.choices?.firstOrNull()?.message?.content
            Log.d("currentResult", "currentResult: $currentResult")
            if (!currentResult.isNullOrEmpty()) {
                Text(currentResult)
            } else {
                Text("等待响应...")
            }

            // 录音控制按钮
            if (hasMicrophonePermission) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (isRecording) {
                                stopRecording()
                            } else {
                                startRecording(recordedAudioData, context) // 传递 context 参数
                            }
                            isRecording = !isRecording
                        }
                    ) {
                        Text(if (isRecording) "停止录音" else "开始录音")
                    }

                    Button(
                        onClick = {
                            if (!isPlaying && recordedAudioData.isNotEmpty()) {
                                playRecordedAudio(recordedAudioData) {
                                    isPlaying = false // 播放完成后重置状态
                                }
                                isPlaying = true
                            }
                        },
//                        enabled = !isPlaying && recordedAudioData.isNotEmpty()
                    ) {
                        Text("播放录音")
                    }
                }
            }


        }
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