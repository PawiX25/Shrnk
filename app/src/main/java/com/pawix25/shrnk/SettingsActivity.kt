package com.pawix25.shrnk

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.unit.dp
import com.pawix25.shrnk.ui.theme.ShrnkTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.roundToInt


class SettingsActivity : ComponentActivity() {

    private val settingsManager by lazy { SettingsManager(this) }
    private var themeState by mutableStateOf("System")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            settingsManager.theme.collect { theme ->
                themeState = theme
            }
        }

        setContent {
            val coroutineScope = rememberCoroutineScope()
            ShrnkTheme(darkTheme = shouldUseDarkTheme(themeState)) {
                SettingsScreen(
                    settingsManager = settingsManager,
                    currentTheme = themeState,
                    onThemeChange = { newTheme ->
                        coroutineScope.launch {
                            settingsManager.setTheme(newTheme)
                        }
                    }
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    currentTheme: String,
    onThemeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    val imageQuality by settingsManager.imageQuality.collectAsState(initial = 80)
    val copyMetadata by settingsManager.copyMetadata.collectAsState(initial = true)
    val videoPreset by settingsManager.videoPreset.collectAsState(initial = VideoPreset.MEDIUM.name)
    val customSizeMb by settingsManager.customSizeMb.collectAsState(initial = "")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            SettingsSection(title = "Default Compression") {
                SettingsSlider(
                    icon = Icons.Outlined.Image,
                    title = "Image Quality",
                    value = imageQuality.toFloat(),
                    onValueChange = {
                        coroutineScope.launch {
                            settingsManager.setImageQuality(it.roundToInt())
                        }
                    },
                    valueRange = 10f..100f,
                    steps = 8,
                    valueLabel = "${imageQuality}%"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                val presetOptions = VideoPreset.values().map {
                    it.name to it.name.replace("_", " ").split(' ').joinToString(" ") { word -> word.lowercase().replaceFirstChar(Char::titlecase) }
                }
                SettingsPicker(
                    icon = Icons.Outlined.HighQuality,
                    title = "Video Quality Preset",
                    currentValue = videoPreset,
                    options = presetOptions,
                    onValueSelected = {
                        coroutineScope.launch {
                            settingsManager.setVideoPreset(it)
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                SettingsTextField(
                    icon = Icons.Outlined.FolderZip,
                    title = "Custom Video Size",
                    value = customSizeMb,
                    onValueChange = {
                        coroutineScope.launch {
                            if (it.all { char -> char.isDigit() }) {
                                settingsManager.setCustomSizeMb(it)
                            }
                        }
                    },
                    label = "Custom Size (MB)",
                    placeholder = "Leave empty for auto"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                SettingsSwitch(
                    icon = Icons.Outlined.FileCopy,
                    title = "Copy Metadata",
                    description = "Copy EXIF data from original file",
                    checked = copyMetadata,
                    onCheckedChange = {
                        coroutineScope.launch {
                            settingsManager.setCopyMetadata(it)
                        }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))

            SettingsSection(title = "Appearance") {
                ThemeSelector(
                    selectedTheme = currentTheme,
                    onThemeSelected = onThemeChange
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsSlider(
    icon: ImageVector,
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    valueLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SettingsSwitch(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelector(selectedTheme: String, onThemeSelected: (String) -> Unit) {
    val themes = listOf(
        "Light" to Icons.Outlined.WbSunny,
        "Dark" to Icons.Outlined.Brightness3,
        "System" to Icons.Outlined.SettingsBrightness
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.ColorLens,
                contentDescription = "Theme",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text("Theme", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
        ) {
            themes.forEachIndexed { index, (themeName, icon) ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = themes.size),
                    onClick = { onThemeSelected(themeName) },
                    selected = selectedTheme == themeName,
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = themeName,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                    },
                    label = { Text(themeName) }
                )
            }
        }
    }
}

@Composable
fun SettingsPicker(
    icon: ImageVector,
    title: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    onValueSelected: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Select $title") },
            text = {
                Column(Modifier.selectableGroup()) {
                    options.forEach { (optionValue, optionDisplay) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = (currentValue == optionValue),
                                    onClick = {
                                        onValueSelected(optionValue)
                                        showDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (currentValue == optionValue),
                                onClick = null
                            )
                            Text(
                                text = optionDisplay,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                options.find { it.first == currentValue }?.second ?: currentValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select")
    }
}

@Composable
fun SettingsTextField(
    icon: ImageVector,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    label: String,
    placeholder: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            keyboardOptions = keyboardOptions,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
    }
}