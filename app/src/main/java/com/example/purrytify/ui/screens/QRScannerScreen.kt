package com.example.purrytify.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.example.purrytify.util.ShareUtils
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun QRScannerScreen(
    onQRCodeDetected: (String) -> Unit,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Camera executor
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    // Check camera permission on start
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Cleanup executor
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasPermission) {
            // Camera preview
            CameraPreview(
                onQRCodeDetected = { qrCode ->
                    if (!isProcessing && scanResult == null) {
                        isProcessing = true
                        scanResult = qrCode

                        // Validate QR code
                        if (ShareUtils.isValidPurrytifyDeepLink(qrCode)) {
                            Log.d("QRScanner", "Valid Purrytify QR code detected: $qrCode")
                            onQRCodeDetected(qrCode)
                        } else {
                            Log.d("QRScanner", "Invalid QR code format: $qrCode")
                            // Reset after 2 seconds to allow new scans
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                kotlinx.coroutines.delay(2000)
                                scanResult = null
                                isProcessing = false
                            }
                        }
                    }
                },
                isFlashOn = isFlashOn,
                modifier = Modifier.fillMaxSize()
            )

            // Overlay UI
            QRScannerOverlay(
                onBackPressed = onBackPressed,
                onFlashToggle = { isFlashOn = !isFlashOn },
                isFlashOn = isFlashOn,
                scanResult = scanResult
            )
        } else {
            // Permission denied screen
            PermissionDeniedScreen(
                onBackPressed = onBackPressed,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )
        }
    }
}

@Composable
fun CameraPreview(
    onQRCodeDetected: (String) -> Unit,
    isFlashOn: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // Preview use case
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // Image analysis use case for QR code detection
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(ContextCompat.getMainExecutor(ctx), QRCodeAnalyzer(onQRCodeDetected))
                    }

                // Camera selector
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )

                    // Handle flash
                    if (camera.cameraInfo.hasFlashUnit()) {
                        camera.cameraControl.enableTorch(isFlashOn)
                    }
                } catch (exc: Exception) {
                    Log.e("QRScanner", "Camera binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

@Composable
fun QRScannerOverlay(
    onBackPressed: () -> Unit,
    onFlashToggle: () -> Unit,
    isFlashOn: Boolean,
    scanResult: String?
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackPressed,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = onFlashToggle,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = if (isFlashOn) "Turn off flash" else "Turn on flash",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Center content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Scanning frame
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                // Corner indicators
                QRScannerFrame()
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Instructions or result
            if (scanResult != null) {
                if (ShareUtils.isValidPurrytifyDeepLink(scanResult)) {
                    Text(
                        text = "✅ Valid Purrytify QR Code detected!",
                        color = Color.Green,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = "❌ Invalid QR Code format",
                        color = Color.Red,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = "Point your camera at a Purrytify QR code",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun QRScannerFrame() {
    // Simple frame corners to indicate scanning area
    Box(modifier = Modifier.fillMaxSize()) {
        // Top-left corner
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(30.dp)
                .background(Color.White.copy(alpha = 0.8f))
        )

        // Top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(30.dp)
                .background(Color.White.copy(alpha = 0.8f))
        )

        // Bottom-left corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(30.dp)
                .background(Color.White.copy(alpha = 0.8f))
        )

        // Bottom-right corner
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(30.dp)
                .background(Color.White.copy(alpha = 0.8f))
        )
    }
}

@Composable
fun PermissionDeniedScreen(
    onBackPressed: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📷",
            fontSize = 64.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Camera Permission Required",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "To scan QR codes, please allow camera access",
            color = Color.Gray,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }

            OutlinedButton(onClick = onBackPressed) {
                Text("Back")
            }
        }
    }
}

// QR Code analyzer
class QRCodeAnalyzer(
    private val onQRCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    override fun analyze(image: ImageProxy) {
        try {
            @Suppress("UnsafeOptInUsageError")
            val mediaImage = image.image

            if (mediaImage != null) {
                val inputImage = InputImage.fromMediaImage(
                    mediaImage,
                    image.imageInfo.rotationDegrees
                )

                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        barcodes.forEach { barcode ->
                            barcode.rawValue?.let { qrCode ->
                                onQRCodeDetected(qrCode)
                            }
                        }
                    }
                    .addOnCompleteListener {
                        image.close()
                    }
            } else {
                image.close()
            }
        } catch (e: Exception) {
            Log.e("QRCodeAnalyzer", "Error analyzing image", e)
            image.close()
        }
    }
}