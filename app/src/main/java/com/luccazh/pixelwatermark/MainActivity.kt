package com.luccazh.pixelwatermark

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PixelWatermarkApp() }
    }
}

private enum class AppPage { HOME, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PixelWatermarkApp() {
    val context = LocalContext.current
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (android.os.Build.VERSION.SDK_INT >= 31) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = colors) {
        val store = remember { SettingsStore(context) }
        var settings by remember { mutableStateOf(store.load()) }
        var page by rememberSaveable { mutableStateOf(AppPage.HOME) }

        Scaffold(
            topBar = {
                TopAppBar(title = { Text(if (page == AppPage.HOME) "Pixel Watermark" else "水印设置") })
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = page == AppPage.HOME,
                        onClick = { page = AppPage.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("首页") }
                    )
                    NavigationBarItem(
                        selected = page == AppPage.SETTINGS,
                        onClick = { page = AppPage.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("设置") }
                    )
                }
            }
        ) { padding ->
            when (page) {
                AppPage.HOME -> HomeScreen(
                    settings = settings,
                    onOpenSettings = { page = AppPage.SETTINGS },
                    modifier = Modifier.padding(padding)
                )
                AppPage.SETTINGS -> SettingsScreen(
                    settings = settings,
                    onSettingsChange = { updated ->
                        settings = updated
                        store.save(updated)
                    },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    settings: WatermarkSettings,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var lastResult by remember { mutableStateOf<ProcessingResult?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            progress = 0
            total = uris.size
            lastResult = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    WatermarkProcessor.processAll(context, uris, settings) { done, _ ->
                        scope.launch { progress = done }
                    }
                }
                isProcessing = false
                lastResult = result
                Toast.makeText(
                    context,
                    if (result.failed == 0) "已保存 ${result.succeeded} 张图片" else "成功 ${result.succeeded} 张，失败 ${result.failed} 张",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, "未授予媒体位置权限，处理照片时无法保留 GPS", Toast.LENGTH_LONG).show()
        }
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text("为照片添加水印", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "选择照片后会立即处理并保存到系统相册。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
        Text("当前样式", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSettings),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(16.dp)
        ) {
            ListItem(
                headlineContent = { Text(settings.style.label) },
                supportingContent = { Text(settings.style.description) },
                leadingContent = { StyleSample(settings.style, compact = true) },
                trailingContent = { Text("更改", color = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }

        Spacer(Modifier.weight(1f))
        lastResult?.let { result ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (result.failed == 0) "${result.succeeded} 张图片已保存" else "成功 ${result.succeeded} 张，失败 ${result.failed} 张",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (isProcessing) {
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else progress.toFloat() / total },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            Text(
                "正在处理 $progress / $total",
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Button(
            onClick = {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_MEDIA_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else {
                    locationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                }
            },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("选择照片")
            }
        }
        Text(
            "最多 50 张 · 保存到 Pictures / Pixel Watermark",
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsScreen(
    settings: WatermarkSettings,
    onSettingsChange: (WatermarkSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLogoStyle = settings.style == WatermarkStyle.PIXEL_LOGO ||
        settings.style == WatermarkStyle.GOOGLE_LOGO
    val logoVariants = if (settings.style == WatermarkStyle.GOOGLE_LOGO) {
        LogoVariant.entries
    } else {
        LogoVariant.entries.filter { it != LogoVariant.COLOR }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp)
    ) {
        item { SectionTitle("水印样式", "选择 Logo 或一种字体，二者不会叠加") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                WatermarkStyle.entries.forEachIndexed { index, style ->
                    ListItem(
                        headlineContent = { Text(style.label) },
                        supportingContent = { Text(style.description, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { StyleSample(style) },
                        trailingContent = {
                            RadioButton(
                                selected = settings.style == style,
                                onClick = {
                                    onSettingsChange(
                                        settings.copy(
                                            style = style,
                                            logoVariant = if (
                                                style == WatermarkStyle.PIXEL_LOGO && settings.logoVariant == LogoVariant.COLOR
                                            ) LogoVariant.AUTO else settings.logoVariant,
                                            bottomBarLogoEnabled = if (
                                                style == WatermarkStyle.GOOGLE_LOGO
                                            ) false else settings.bottomBarLogoEnabled
                                        )
                                    )
                                }
                            )
                        },
                        modifier = Modifier.clickable {
                            onSettingsChange(
                                settings.copy(
                                    style = style,
                                    logoVariant = if (
                                        style == WatermarkStyle.PIXEL_LOGO && settings.logoVariant == LogoVariant.COLOR
                                    ) LogoVariant.AUTO else settings.logoVariant,
                                    bottomBarLogoEnabled = if (
                                        style == WatermarkStyle.GOOGLE_LOGO
                                    ) false else settings.bottomBarLogoEnabled
                                )
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (index != WatermarkStyle.entries.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 88.dp))
                }
            }
        }

        if (!isLogoStyle) {
            item { Spacer(Modifier.height(28.dp)); SectionTitle("水印内容", "支持自动替换照片和设备信息") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = settings.titleTemplate,
                        onValueChange = { onSettingsChange(settings.copy(titleTemplate = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("主标题") },
                        supportingText = { Text("{device} 会替换为当前设备型号") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = settings.subtitleTemplate,
                        onValueChange = { onSettingsChange(settings.copy(subtitleTemplate = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("拍摄信息") },
                        supportingText = { Text("{image_info} 会替换为照片 EXIF 信息") },
                        singleLine = true
                    )
                }
            }
        }

        if (isLogoStyle) {
            item { Spacer(Modifier.height(28.dp)); SectionTitle("Logo 版本", "自动仅在黑色与白色之间选择") }
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    logoVariants.forEachIndexed { index, variant ->
                        SegmentedButton(
                            selected = settings.logoVariant == variant,
                            onClick = { onSettingsChange(settings.copy(logoVariant = variant)) },
                            shape = SegmentedButtonDefaults.itemShape(index, logoVariants.size),
                            label = { Text(variant.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } else if (settings.style != WatermarkStyle.GILBERT) {
            item { Spacer(Modifier.height(28.dp)); SectionTitle("文字颜色") }
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    WatermarkTone.entries.forEachIndexed { index, tone ->
                        SegmentedButton(
                            selected = settings.tone == tone,
                            onClick = { onSettingsChange(settings.copy(tone = tone)) },
                            shape = SegmentedButtonDefaults.itemShape(index, WatermarkTone.entries.size),
                            label = { Text(tone.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(28.dp)); SectionTitle("版式") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                ListItem(
                    headlineContent = { Text("底部信息栏") },
                    supportingContent = { Text("在照片下方扩展长方形区域，水印不会覆盖照片") },
                    trailingContent = {
                        Switch(
                            checked = settings.bottomBarEnabled,
                            onCheckedChange = { onSettingsChange(settings.copy(bottomBarEnabled = it)) }
                        )
                    },
                    modifier = Modifier.clickable {
                        onSettingsChange(settings.copy(bottomBarEnabled = !settings.bottomBarEnabled))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                if (settings.bottomBarEnabled && settings.style != WatermarkStyle.GOOGLE_LOGO) {
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    ListItem(
                        headlineContent = { Text("右侧 Logo") },
                        supportingContent = { Text("在底部信息栏右侧添加 Google Logo") },
                        trailingContent = {
                            Switch(
                                checked = settings.bottomBarLogoEnabled,
                                onCheckedChange = { onSettingsChange(settings.copy(bottomBarLogoEnabled = it)) }
                            )
                        },
                        modifier = Modifier.clickable {
                            onSettingsChange(settings.copy(bottomBarLogoEnabled = !settings.bottomBarLogoEnabled))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (settings.bottomBarLogoEnabled) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        ) {
                            BottomBarLogoStyle.entries.forEachIndexed { index, logoStyle ->
                                SegmentedButton(
                                    selected = settings.bottomBarLogoStyle == logoStyle,
                                    onClick = { onSettingsChange(settings.copy(bottomBarLogoStyle = logoStyle)) },
                                    shape = SegmentedButtonDefaults.itemShape(index, BottomBarLogoStyle.entries.size),
                                    label = { Text(logoStyle.label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(28.dp)); SectionTitle("位置与尺寸") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("位置", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        WatermarkCorner.entries.forEachIndexed { index, corner ->
                            SegmentedButton(
                                selected = settings.corner == corner,
                                onClick = { onSettingsChange(settings.copy(corner = corner)) },
                                shape = SegmentedButtonDefaults.itemShape(index, WatermarkCorner.entries.size),
                                label = { Text(corner.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    SliderSetting("不透明度", "${(settings.opacity * 100).toInt()}%", settings.opacity, 0.2f..1f) {
                        onSettingsChange(settings.copy(opacity = it))
                    }
                    SliderSetting("边距", "${settings.marginPercent.toInt()}%", settings.marginPercent, 1f..12f) {
                        onSettingsChange(settings.copy(marginPercent = it))
                    }
                    SliderSetting("大小", "${settings.scalePercent.toInt()}%", settings.scalePercent, 12f..38f) {
                        onSettingsChange(settings.copy(scalePercent = it))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, supporting: String? = null) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    supporting?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun StyleSample(style: WatermarkStyle, compact: Boolean = false) {
    val context = LocalContext.current
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val width = if (compact) 56.dp else 64.dp
    val height = if (compact) 40.dp else 44.dp
    Box(modifier = Modifier.width(width).height(height), contentAlignment = Alignment.CenterStart) {
        when (style) {
            WatermarkStyle.PIXEL_LOGO -> Image(
                painter = painterResource(
                    if (darkTheme) R.drawable.pixel_logo_white else R.drawable.pixel_logo_black
                ),
                contentDescription = null,
                modifier = Modifier.size(if (compact) 38.dp else 42.dp)
            )
            WatermarkStyle.GOOGLE_LOGO -> Image(
                painter = painterResource(R.drawable.google_logo_color),
                contentDescription = null,
                modifier = Modifier.size(if (compact) 20.dp else 24.dp)
            )
            WatermarkStyle.GILBERT -> {
                val renderer = remember { GilbertColorRenderer.get(context) }
                Canvas(Modifier.fillMaxSize()) {
                    renderer.drawText(drawContext.canvas.nativeCanvas, "Aa", 0f, size.height * 0.74f, size.height * 0.72f, 255)
                }
            }
            else -> {
                val family = remember(style) {
                    FontFamily(Typeface.createFromAsset(context.assets, "fonts/${style.fileName}"))
                }
                Text("Aa", fontFamily = family, fontSize = if (compact) 24.sp else 28.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = range)
}
