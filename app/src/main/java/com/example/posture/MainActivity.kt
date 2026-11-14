package com.example.posture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.pose.Pose
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val vm: PostureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { PostureApp(vm) } }
    }
}

@Composable
fun PostureApp(vm: PostureViewModel) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!granted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Для работы нужен доступ к камере")
        }
        return
    }

    val state by vm.state.collectAsState()

    Box(Modifier.fillMaxSize()) {
        CameraPreview(onPose = vm::onPose)

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusCard(state)
            Button(
                onClick = vm::calibrate,
                enabled = state.personVisible,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.calibrated) "Перекалибровать" else "Я сижу правильно — запомнить")
            }
        }
    }
}

@Composable
fun StatusCard(state: UiState) {
    val (color, icon, text) = when {
        !state.personVisible -> Triple(
            Color.Gray, Icons.Default.Warning,
            "Вас не видно. Сядьте так, чтобы в кадре были голова и плечи."
        )
        state.issues.isEmpty() -> Triple(
            Color(0xFF2E7D32), Icons.Default.CheckCircle,
            "Осанка хорошая 👍"
        )
        else -> Triple(
            Color(0xFFC62828), Icons.Default.Warning,
            state.issues.joinToString("\n") { it.advice }
        )
    }
    Card(colors = CardDefaults.cardColors(containerColor = color)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Text(text, color = Color.White)
        }
    }
}

@Composable
fun CameraPreview(onPose: (Pose?, Int) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()

            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, PoseAnalyzer(onPose)) }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis
            )
        }, ContextCompat.getMainExecutor(context))

        onDispose { executor.shutdown() }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}