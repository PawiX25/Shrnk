package com.pawix25.shrnk

import android.content.Intent
import android.net.Uri
import android.content.Context
import android.provider.OpenableColumns
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pawix25.shrnk.ui.theme.ShrnkTheme
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.media3.transformer.Transformer
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.isSystemInDarkTheme

fun getFileName(context: Context, uri: Uri?): String? {
    uri ?: return null
    // Try querying the content resolver first
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    return cursor.getString(index)
                }
            }
        }
    }
    // Fallback to parsing the path segment
    val path = uri.path ?: return null
    val lastSlash = path.lastIndexOf('/')
    return if (lastSlash != -1) path.substring(lastSlash + 1) else path
}

fun getFileSize(context: Context, uri: Uri?): Long {
    uri ?: return 0L
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cur ->
            if (cur.moveToFirst()) {
                val idx = cur.getColumnIndex(OpenableColumns.SIZE)
                if (idx != -1) return cur.getLong(idx)
            }
        }
    }
    // fallback: open file descriptor
    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
    } catch (e: Exception) {
        0L
    }
}

class MainActivity : ComponentActivity() {

    private val settingsManager by lazy { SettingsManager(this) }
    private var themeState by mutableStateOf("System")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        lifecycleScope.launch {
            settingsManager.theme.collect { theme ->
                themeState = theme
            }
        }

        setContent {
            ShrnkTheme(darkTheme = shouldUseDarkTheme(themeState)) {
                ShrnkApp(intent)
            }
        }
    }
}

@Composable
private fun shouldUseDarkTheme(theme: String): Boolean {
    return when (theme) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ShrnkApp(intent: Intent? = null) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settingsManager = remember { SettingsManager(context) }

    val imageQuality by settingsManager.imageQuality.collectAsState(initial = 80)
    val videoPreset by settingsManager.videoPreset.collectAsState(initial = VideoPreset.MEDIUM.name)
    val customSizeMb by settingsManager.customSizeMb.collectAsState(initial = "")
    val copyMetadata by settingsManager.copyMetadata.collectAsState(initial = true)

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var compressing by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var progressFraction by remember { mutableStateOf(0f) }
    
    var selectedPreset by remember { mutableStateOf(VideoPreset.valueOf(videoPreset)) }
    var customSizeMbState by remember { mutableStateOf(customSizeMb) }
    var imageQualityState by remember { mutableStateOf(imageQuality) }

    var compressionJob by remember { mutableStateOf<Job?>(null) }
    val transformer = remember { MutableStateFlow<Transformer?>(null) }
    LaunchedEffect(videoPreset) {
        selectedPreset = VideoPreset.valueOf(videoPreset)
    }
    LaunchedEffect(customSizeMb) {
        customSizeMbState = customSizeMb
    }
    LaunchedEffect(imageQuality) {
        imageQualityState = imageQuality
    }
    LaunchedEffect(selectedPreset) {
        coroutineScope.launch {
            settingsManager.setVideoPreset(selectedPreset.name)
        }
    }
    LaunchedEffect(customSizeMbState) {
        coroutineScope.launch {
            settingsManager.setCustomSizeMb(customSizeMbState)
        }
    }
    LaunchedEffect(imageQualityState) {
        coroutineScope.launch {
            settingsManager.setImageQuality(imageQualityState)
        }
    }

    LaunchedEffect(intent) {
        if (intent?.action == Intent.ACTION_SEND && intent.type != null) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                selectedUri = uri
            }
        }
    }
    
    val infiniteTransition = rememberInfiniteTransition()
    val animatedGradientAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )


    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
        }
    }

    val handleSave: (Uri?) -> Unit = { destUri: Uri? ->
        val src = selectedUri
        if (destUri != null && src != null) {
            compressing = true
            compressionJob = coroutineScope.launch {
                val mime = context.contentResolver.getType(src) ?: ""
                val beforeBytes = getFileSize(context, src)

                progressFraction = 0f
                progressMessage = if (mime.startsWith("image")) "Compressing image…" else "Compressing video…"

                val success = if (mime.startsWith("image")) {
                    MediaCompressor.compressImage(context, src, destUri, quality = imageQualityState, copyMetadata = copyMetadata)
                } else {
                    val size = customSizeMb.toIntOrNull()
                    MediaCompressor.compressVideo(
                        context,
                        src,
                        destUri,
                        preset = selectedPreset,
                        maxFileSizeMb = size,
                        onProgress = { frac -> progressFraction = frac },
                        transformer = transformer.value
                    )
                }

                val afterBytes = if (success) getFileSize(context, destUri) else 0L

                compressing = false

                val human = { size: Long ->
                    if (size <= 0) "0 B" else {
                        val units = arrayOf("B","KB","MB","GB")
                        var s = size.toDouble()
                        var idx = 0
                        while (s >= 1024 && idx < units.lastIndex) { s /= 1024; idx++ }
                        String.format("%.1f %s", s, units[idx])
                    }
                }

                val resultMessage = if (success) {
                    val saved = beforeBytes - afterBytes
                    "Success: ${human(beforeBytes)} → ${human(afterBytes)} (saved ${human(saved)})"
                } else {
                    "Compression failed."
                }

                val snackbarResult = snackbarHostState.showSnackbar(
                    message = resultMessage,
                    actionLabel = if (success) "Share" else null,
                    duration = SnackbarDuration.Long
                )
                if (snackbarResult == SnackbarResult.ActionPerformed) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = context.contentResolver.getType(destUri)
                        putExtra(Intent.EXTRA_STREAM, destUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share compressed file"))
                }

                if (success) {
                    selectedUri = null
                }
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Save cancelled.")
            }
        }
    }

    val imageDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { destUri -> handleSave(destUri) }

    val videoDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { destUri -> handleSave(destUri) }

    val fileName = remember(selectedUri) { getFileName(context, selectedUri) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            "Shrnk", 
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (compressing) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp), progress = progressFraction, strokeWidth = 6.dp)
                    Spacer(Modifier.height(16.dp))
                    val pct = String.format("%.0f%%", progressFraction * 100)
                    Text("$progressMessage $pct", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        compressionJob?.cancel()
                        transformer.value?.cancel()
                        compressing = false
                    }) {
                        Text("Cancel")
                    }
                }
            } else {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.elevatedCardElevation()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                    if (selectedUri == null) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = "Select a file",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Select an image or video to shrink",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { openDocLauncher.launch(arrayOf("image/*", "video/*")) },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Text("Select File")
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "File Ready to Shrink",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = fileName ?: "Unknown file",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(24.dp))
                                val mime = context.contentResolver.getType(selectedUri!!) ?: ""
                                val isImage = mime.startsWith("image")
                                
                                if (isImage) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .padding(bottom = 16.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        AsyncImage(
                                            model = selectedUri,
                                            contentDescription = "Image preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                                
                                CompressionSettings(
                                    selectedPreset = selectedPreset,
                                    onPresetSelected = { selectedPreset = it },
                                    customSizeMb = customSizeMbState,
                                    onCustomSizeChanged = { customSizeMbState = it },
                                    isImage = isImage,
                                    imageQuality = imageQualityState,
                                    onImageQualityChanged = { imageQualityState = it }
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = {
                                        val mime = context.contentResolver.getType(selectedUri!!) ?: ""
                                        val suggestedName = if (mime.startsWith("image")) {
                                            "shrnk_${fileName ?: "image"}.jpg"
                                        } else {
                                            "shrnk_${fileName ?: "video"}.mp4"
                                        }
                                        if (mime.startsWith("image")) {
                                            imageDocLauncher.launch(suggestedName)
                                        } else {
                                            videoDocLauncher.launch(suggestedName)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Choose Destination & Compress")
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { selectedUri = null }) {
                            Text("Clear Selection")
                        }
                    }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ShrnkPreview() {
    ShrnkTheme {
        ShrnkApp()
    }
}