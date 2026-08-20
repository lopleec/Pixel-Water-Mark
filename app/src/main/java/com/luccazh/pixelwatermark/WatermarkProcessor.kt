package com.luccazh.pixelwatermark

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.exifinterface.media.ExifInterface
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class ProcessingResult(val succeeded: Int, val failed: Int)

object WatermarkProcessor {
    fun processAll(
        context: Context,
        uris: List<Uri>,
        settings: WatermarkSettings,
        onProgress: (done: Int, total: Int) -> Unit
    ): ProcessingResult {
        var succeeded = 0
        var failed = 0
        uris.forEachIndexed { index, uri ->
            runCatching { processOne(context, uri, settings, index) }
                .onSuccess { succeeded++ }
                .onFailure { error ->
                    failed++
                    Log.e("WatermarkProcessor", "Failed to process $uri", error)
                }
            onProgress(index + 1, uris.size)
        }
        return ProcessingResult(succeeded, failed)
    }

    private fun processOne(context: Context, uri: Uri, settings: WatermarkSettings, index: Int) {
        val sourceUri = unredactedUri(context, uri)
        val imageInfo = readImageInfo(context, sourceUri)
        val source = ImageDecoder.createSource(context.contentResolver, sourceUri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
        val bitmap = if (decoded.config == Bitmap.Config.ARGB_8888 && decoded.isMutable) {
            decoded
        } else {
            decoded.copy(Bitmap.Config.ARGB_8888, true).also { decoded.recycle() }
        }

        val output = if (settings.bottomBarEnabled) {
            createBottomBarImage(context, bitmap, settings, imageInfo)
        } else {
            drawWatermark(context, bitmap, settings, imageInfo)
            bitmap
        }
        try {
            saveToGallery(context, output, sourceUri, index)
        } finally {
            if (output !== bitmap) output.recycle()
            bitmap.recycle()
        }
    }

    private fun unredactedUri(context: Context, uri: Uri): Uri {
        val hasLocationPermission = context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) return uri

        val mediaStoreUri = if (uri.authority == MediaStore.AUTHORITY && "picker" in uri.pathSegments) {
            uri.lastPathSegment?.toLongOrNull()?.let { mediaId ->
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
            } ?: uri
        } else {
            uri
        }
        val requiredOriginalUri = MediaStore.setRequireOriginal(mediaStoreUri)
        return runCatching {
            context.contentResolver.openFileDescriptor(requiredOriginalUri, "r")?.use { }
                ?: error("无法打开未脱敏原图")
            requiredOriginalUri
        }.getOrElse { error ->
            Log.w("WatermarkProcessor", "Original media URI unavailable; using picker URI", error)
            uri
        }
    }

    private fun drawWatermark(
        context: Context,
        bitmap: Bitmap,
        settings: WatermarkSettings,
        imageInfo: String
    ) {
        val canvas = Canvas(bitmap)
        val shortSide = min(bitmap.width, bitmap.height).toFloat()
        val targetWidth = shortSide * (settings.scalePercent / 100f)
        val margin = shortSide * (settings.marginPercent / 100f)
        val alpha = (settings.opacity * 255).toInt().coerceIn(0, 255)
        if (isLogoStyle(settings.style)) {
            drawLogo(canvas, context, bitmap, settings, targetWidth, margin, alpha)
        } else {
            drawTextWatermark(canvas, context, bitmap, settings, imageInfo, targetWidth, margin, alpha)
        }
    }

    private fun createBottomBarImage(
        context: Context,
        source: Bitmap,
        settings: WatermarkSettings,
        imageInfo: String
    ): Bitmap {
        val shortSide = min(source.width, source.height).toFloat()
        val barHeight = (shortSide * 0.20f).toInt().coerceAtLeast(96)
        val output = createBitmap(source.width, source.height + barHeight)
        val canvas = Canvas(output)
        canvas.drawBitmap(source, 0f, 0f, null)

        val contentTone = when {
            isLogoStyle(settings.style) && settings.logoVariant == LogoVariant.LIGHT -> WatermarkTone.LIGHT
            isLogoStyle(settings.style) -> WatermarkTone.DARK
            settings.style == WatermarkStyle.GILBERT -> WatermarkTone.DARK
            settings.tone == WatermarkTone.AUTO -> WatermarkTone.DARK
            else -> settings.tone
        }
        val barIsDark = contentTone == WatermarkTone.LIGHT
        val barColor = if (barIsDark) Color.rgb(24, 24, 26) else Color.WHITE
        canvas.drawRect(0f, source.height.toFloat(), source.width.toFloat(), output.height.toFloat(), Paint().apply {
            color = barColor
        })

        val targetWidth = shortSide * (settings.scalePercent / 100f)
        val margin = shortSide * (settings.marginPercent / 100f)
        val alpha = (settings.opacity * 255).toInt().coerceIn(0, 255)
        val showSideLogo = settings.bottomBarLogoEnabled && settings.style != WatermarkStyle.GOOGLE_LOGO
        val extraLogoSize = if (showSideLogo) barHeight * 0.32f else 0f
        val extraLogoInset = if (showSideLogo) barHeight * 0.10f else 0f
        val reservedRightWidth = if (showSideLogo) extraLogoSize + margin + extraLogoInset else 0f
        if (isLogoStyle(settings.style)) {
            drawLogoInBottomBar(
                canvas, context, source, settings, barIsDark, targetWidth, margin,
                barHeight, reservedRightWidth, alpha
            )
        } else {
            drawTextInBottomBar(
                canvas, context, source, settings, imageInfo, contentTone, targetWidth,
                margin, barHeight, reservedRightWidth, alpha
            )
        }
        if (showSideLogo) {
            drawBottomBarSideLogo(canvas, context, source, settings, barIsDark, margin, barHeight, extraLogoSize, alpha)
        }
        return output
    }

    private fun drawLogoInBottomBar(
        canvas: Canvas,
        context: Context,
        source: Bitmap,
        settings: WatermarkSettings,
        barIsDark: Boolean,
        targetWidth: Float,
        margin: Float,
        barHeight: Int,
        reservedRightWidth: Float,
        alpha: Int
    ) {
        val automaticTone = if (barIsDark) WatermarkTone.LIGHT else WatermarkTone.DARK
        val logoRes = logoResource(settings, automaticTone)
        val logo = BitmapFactory.decodeResource(context.resources, logoRes)
        val availableWidth = (source.width - margin * 2f - reservedRightWidth).coerceAtLeast(1f)
        val logoScale = if (settings.style == WatermarkStyle.GOOGLE_LOGO) 0.48f else 1f
        val maxHeight = barHeight * if (settings.style == WatermarkStyle.GOOGLE_LOGO) 0.36f else 0.64f
        val width = min(targetWidth * logoScale, min(availableWidth, maxHeight * logo.width / logo.height.toFloat()))
        val height = width * logo.height / logo.width.toFloat()
        val left = when (settings.corner) {
            WatermarkCorner.TOP_START, WatermarkCorner.BOTTOM_START -> margin
            WatermarkCorner.TOP_END, WatermarkCorner.BOTTOM_END -> source.width - margin - reservedRightWidth - width
        }.coerceAtLeast(0f)
        val top = source.height + (barHeight - height) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }
        canvas.drawBitmap(logo, null, RectF(left, top, left + width, top + height), paint)
        logo.recycle()
    }

    private fun drawTextInBottomBar(
        canvas: Canvas,
        context: Context,
        source: Bitmap,
        settings: WatermarkSettings,
        imageInfo: String,
        tone: WatermarkTone,
        targetWidth: Float,
        margin: Float,
        barHeight: Int,
        reservedRightWidth: Float,
        alpha: Int
    ) {
        val title = applyVariables(settings.titleTemplate, imageInfo)
        val subtitle = applyVariables(settings.subtitleTemplate, imageInfo)
        val isGilbert = settings.style == WatermarkStyle.GILBERT
        val typeface = Typeface.createFromAsset(context.assets, "fonts/${settings.style.fileName}")
        var titleSize = max(18f, targetWidth * 0.22f)
        var subtitleSize = titleSize * 0.44f

        fun measure(text: String, size: Float): Float = if (isGilbert) {
            GilbertColorRenderer.get(context).measureText(text, size)
        } else {
            Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.typeface = typeface }.measureText(text)
        }

        val maxWidth = (source.width - margin * 2f - reservedRightWidth).coerceAtLeast(1f)
        val unscaledWidth = max(measure(title, titleSize), measure(subtitle, subtitleSize))
        if (unscaledWidth > maxWidth) {
            val scale = maxWidth / unscaledWidth
            titleSize *= scale
            subtitleSize *= scale
        }
        val titleWidth = measure(title, titleSize)
        val subtitleWidth = measure(subtitle, subtitleSize)
        val contentWidth = max(titleWidth, subtitleWidth)
        val gap = titleSize * 0.18f
        val contentHeight = titleSize + gap + subtitleSize
        val left = when (settings.corner) {
            WatermarkCorner.TOP_START, WatermarkCorner.BOTTOM_START -> margin
            WatermarkCorner.TOP_END, WatermarkCorner.BOTTOM_END -> source.width - margin - reservedRightWidth - contentWidth
        }.coerceAtLeast(0f)
        val top = source.height + (barHeight - contentHeight) / 2f
        val titleBaseline = top + titleSize * 0.82f
        val subtitleBaseline = top + titleSize + gap + subtitleSize * 0.82f

        if (isGilbert) {
            val renderer = GilbertColorRenderer.get(context)
            renderer.drawText(canvas, title, left, titleBaseline, titleSize, alpha)
            renderer.drawText(canvas, subtitle, left, subtitleBaseline, subtitleSize, alpha)
        } else {
            val color = if (tone == WatermarkTone.LIGHT) Color.WHITE else Color.rgb(30, 30, 32)
            canvas.drawText(title, left, titleBaseline, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; this.alpha = alpha; this.textSize = titleSize; this.typeface = typeface
            })
            canvas.drawText(subtitle, left, subtitleBaseline, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; this.alpha = alpha; this.textSize = subtitleSize; this.typeface = typeface
            })
        }
    }

    private fun drawBottomBarSideLogo(
        canvas: Canvas,
        context: Context,
        source: Bitmap,
        settings: WatermarkSettings,
        barIsDark: Boolean,
        margin: Float,
        barHeight: Int,
        size: Float,
        alpha: Int
    ) {
        val logoRes = when (settings.bottomBarLogoStyle) {
            BottomBarLogoStyle.COLOR -> R.drawable.google_logo_color
            BottomBarLogoStyle.MONOCHROME -> if (barIsDark) {
                R.drawable.google_logo_white
            } else {
                R.drawable.google_logo_black
            }
        }
        val logo = BitmapFactory.decodeResource(context.resources, logoRes)
        val left = (source.width - margin - barHeight * 0.10f - size).coerceAtLeast(0f)
        val top = source.height + (barHeight - size) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }
        canvas.drawBitmap(logo, null, RectF(left, top, left + size, top + size), paint)
        logo.recycle()
    }

    private fun drawLogo(
        canvas: Canvas,
        context: Context,
        bitmap: Bitmap,
        settings: WatermarkSettings,
        size: Float,
        margin: Float,
        alpha: Int
    ) {
        val probeRes = when (settings.style) {
            WatermarkStyle.GOOGLE_LOGO -> R.drawable.google_logo_black
            else -> R.drawable.pixel_logo_black
        }
        val probe = BitmapFactory.decodeResource(context.resources, probeRes)
        val width = if (settings.style == WatermarkStyle.GOOGLE_LOGO) size * 0.48f else size
        val height = width * probe.height / probe.width.toFloat()
        probe.recycle()
        val left = when (settings.corner) {
            WatermarkCorner.TOP_START, WatermarkCorner.BOTTOM_START -> margin
            WatermarkCorner.TOP_END, WatermarkCorner.BOTTOM_END -> bitmap.width - margin - width
        }.coerceAtLeast(0f)
        val top = when (settings.corner) {
            WatermarkCorner.TOP_START, WatermarkCorner.TOP_END -> margin
            WatermarkCorner.BOTTOM_START, WatermarkCorner.BOTTOM_END -> bitmap.height - margin - height
        }.coerceAtLeast(0f)
        val bounds = RectF(left, top, left + width, top + height)
        val automaticTone = resolveTone(bitmap, bounds, WatermarkTone.AUTO)
        val resolvedLogoRes = logoResource(settings, automaticTone)
        val logo = BitmapFactory.decodeResource(context.resources, resolvedLogoRes)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }
        canvas.drawBitmap(logo, null, bounds, paint)
        logo.recycle()
    }

    private fun isLogoStyle(style: WatermarkStyle): Boolean =
        style == WatermarkStyle.PIXEL_LOGO || style == WatermarkStyle.GOOGLE_LOGO

    private fun logoResource(settings: WatermarkSettings, automaticTone: WatermarkTone): Int {
        if (settings.style == WatermarkStyle.GOOGLE_LOGO && settings.logoVariant == LogoVariant.COLOR) {
            return R.drawable.google_logo_color
        }
        val tone = when (settings.logoVariant) {
            LogoVariant.LIGHT -> WatermarkTone.LIGHT
            LogoVariant.DARK -> WatermarkTone.DARK
            LogoVariant.AUTO, LogoVariant.COLOR -> automaticTone
        }
        return when (settings.style) {
            WatermarkStyle.GOOGLE_LOGO -> if (tone == WatermarkTone.LIGHT) {
                R.drawable.google_logo_white
            } else {
                R.drawable.google_logo_black
            }
            else -> if (tone == WatermarkTone.LIGHT) {
                R.drawable.pixel_logo_white
            } else {
                R.drawable.pixel_logo_black
            }
        }
    }

    private fun drawTextWatermark(
        canvas: Canvas,
        context: Context,
        bitmap: Bitmap,
        settings: WatermarkSettings,
        imageInfo: String,
        targetWidth: Float,
        margin: Float,
        alpha: Int
    ) {
        val title = applyVariables(settings.titleTemplate, imageInfo)
        val subtitle = applyVariables(settings.subtitleTemplate, imageInfo)
        val isGilbert = settings.style == WatermarkStyle.GILBERT
        val typeface = Typeface.createFromAsset(context.assets, "fonts/${settings.style.fileName}")
        var titleSize = max(18f, targetWidth * 0.22f)
        var subtitleSize = titleSize * 0.44f

        fun measure(text: String, size: Float): Float = if (isGilbert) {
            GilbertColorRenderer.get(context).measureText(text, size)
        } else {
            Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.typeface = typeface }.measureText(text)
        }

        val unscaledWidth = max(measure(title, titleSize), measure(subtitle, subtitleSize))
        if (unscaledWidth > targetWidth) {
            val scale = targetWidth / unscaledWidth
            titleSize *= scale
            subtitleSize *= scale
        }
        val titleWidth = measure(title, titleSize)
        val subtitleWidth = measure(subtitle, subtitleSize)
        val contentWidth = max(titleWidth, subtitleWidth)
        val gap = titleSize * 0.18f
        val contentHeight = titleSize + gap + subtitleSize
        val left = when (settings.corner) {
            WatermarkCorner.TOP_START, WatermarkCorner.BOTTOM_START -> margin
            WatermarkCorner.TOP_END, WatermarkCorner.BOTTOM_END -> bitmap.width - margin - contentWidth
        }.coerceAtLeast(0f)
        val top = when (settings.corner) {
            WatermarkCorner.TOP_START, WatermarkCorner.TOP_END -> margin
            WatermarkCorner.BOTTOM_START, WatermarkCorner.BOTTOM_END -> bitmap.height - margin - contentHeight
        }.coerceAtLeast(0f)
        val titleBaseline = top + titleSize * 0.82f
        val subtitleBaseline = top + titleSize + gap + subtitleSize * 0.82f

        if (isGilbert) {
            val renderer = GilbertColorRenderer.get(context)
            renderer.drawText(canvas, title, left, titleBaseline, titleSize, alpha)
            renderer.drawText(canvas, subtitle, left, subtitleBaseline, subtitleSize, alpha)
        } else {
            val bounds = RectF(left, top, left + contentWidth, top + contentHeight)
            val resolvedTone = resolveTone(bitmap, bounds, settings.tone)
            val color = if (resolvedTone == WatermarkTone.LIGHT) Color.WHITE else Color.rgb(30, 30, 32)
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; this.alpha = alpha; this.textSize = titleSize; this.typeface = typeface
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; this.alpha = alpha; this.textSize = subtitleSize; this.typeface = typeface
            }
            canvas.drawText(title, left, titleBaseline, titlePaint)
            canvas.drawText(subtitle, left, subtitleBaseline, subtitlePaint)
        }
    }

    private fun resolveTone(bitmap: Bitmap, bounds: RectF, requested: WatermarkTone): WatermarkTone {
        if (requested != WatermarkTone.AUTO) return requested
        val left = bounds.left.toInt().coerceIn(0, bitmap.width - 1)
        val right = bounds.right.toInt().coerceIn(left + 1, bitmap.width)
        val top = bounds.top.toInt().coerceIn(0, bitmap.height - 1)
        val bottom = bounds.bottom.toInt().coerceIn(top + 1, bitmap.height)
        val columns = 12
        val rows = 12
        var whiteContrast = 0.0
        var blackContrast = 0.0
        for (row in 0 until rows) {
            val y = top + ((row + 0.5f) / rows * (bottom - top)).toInt().coerceAtMost(bottom - top - 1)
            for (column in 0 until columns) {
                val x = left + ((column + 0.5f) / columns * (right - left)).toInt().coerceAtMost(right - left - 1)
                val pixel = bitmap[x, y]
                val luminance = relativeLuminance(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
                whiteContrast += 1.05 / (luminance + 0.05)
                blackContrast += (luminance + 0.05) / 0.05
            }
        }
        return if (whiteContrast >= blackContrast) WatermarkTone.LIGHT else WatermarkTone.DARK
    }

    private fun relativeLuminance(red: Int, green: Int, blue: Int): Double {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    }

    private fun applyVariables(template: String, imageInfo: String): String = template
        .replace("{device}", deviceName())
        .replace("{image_info}", imageInfo)

    private fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER.trim()
        val model = Build.MODEL.trim()
        return if (model.startsWith(manufacturer, ignoreCase = true)) model
        else "$manufacturer $model".trim().replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    private fun readImageInfo(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                val exif = ExifInterface(descriptor.fileDescriptor)
                val focal = exif.getAttribute("FocalLength")?.toDecimalRational()?.let { "${formatNumber(it)}mm" }
                val aperture = exif.getAttribute("FNumber")?.toDecimalRational()?.let { "f/${formatNumber(it)}" }
                    ?: exif.getAttribute("ApertureValue")?.toDecimalRational()?.let { "f/${formatNumber(it)}" }
                val exposure = exif.getAttribute("ExposureTime")?.toDecimalRational()?.let { value ->
                    if (value in 0.0..0.5 && value > 0) "1/${(1.0 / value).toInt()}s" else "${formatNumber(value)}s"
                }
                val iso = exif.getAttribute("PhotographicSensitivity")
                    ?: exif.getAttribute("ISOSpeedRatings")
                listOfNotNull(focal, aperture, exposure, iso?.let { "ISO $it" }).joinToString("  ·  ")
            }
        }.getOrNull().orEmpty().ifBlank { "Photo by ${deviceName()}" }
    }

    private fun String.toDecimalRational(): Double? {
        val parts = split('/')
        return if (parts.size == 2) {
            val numerator = parts[0].toDoubleOrNull() ?: return null
            val denominator = parts[1].toDoubleOrNull() ?: return null
            if (denominator == 0.0) null else numerator / denominator
        } else toDoubleOrNull()
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

    private fun saveToGallery(context: Context, bitmap: Bitmap, sourceUri: Uri, index: Int) {
        val name = "PixelWatermark_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}_${index + 1}.jpg"
        val dateTaken = context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { descriptor ->
            readDateTaken(ExifInterface(descriptor.fileDescriptor))
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Pixel Watermark")
            dateTaken?.let { put(MediaStore.Images.Media.DATE_TAKEN, it) }
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建输出图片")
        try {
            resolver.openOutputStream(outputUri)?.use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)) { "图片编码失败" }
            } ?: error("无法写入输出图片")
            copyExifMetadata(context, sourceUri, outputUri, bitmap.width, bitmap.height)
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(outputUri, values, null, null)
        } catch (error: Throwable) {
            resolver.delete(outputUri, null, null)
            throw error
        }
    }

    private fun readDateTaken(exif: ExifInterface): Long? {
        val value = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        val localDateTime = runCatching {
            LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
        }.getOrNull() ?: return null
        val offset = (exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME))
            ?.let { runCatching { ZoneOffset.of(it) }.getOrNull() }
        return if (offset != null) {
            localDateTime.toInstant(offset).toEpochMilli()
        } else {
            localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }

    private fun copyExifMetadata(
        context: Context,
        sourceUri: Uri,
        outputUri: Uri,
        outputWidth: Int,
        outputHeight: Int
    ) {
        context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { sourceDescriptor ->
            context.contentResolver.openFileDescriptor(outputUri, "rw")?.use { outputDescriptor ->
                val sourceExif = ExifInterface(sourceDescriptor.fileDescriptor)
                val outputExif = ExifInterface(outputDescriptor.fileDescriptor)
                EXIF_TAGS_TO_COPY.forEach { tag ->
                    sourceExif.getAttribute(tag)?.let { value ->
                        runCatching { outputExif.setAttribute(tag, value) }
                            .onFailure { Log.w("WatermarkProcessor", "Unable to copy EXIF tag $tag", it) }
                    }
                }
                sourceExif.latLong?.let { coordinates ->
                    outputExif.setLatLong(coordinates[0], coordinates[1])
                }
                sourceExif.getAltitude(Double.NaN).takeIf { it.isFinite() }?.let { altitude ->
                    outputExif.setAltitude(altitude)
                }
                outputExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                outputExif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, outputWidth.toString())
                outputExif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, outputHeight.toString())
                outputExif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, outputWidth.toString())
                outputExif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, outputHeight.toString())
                outputExif.saveAttributes()
            } ?: error("无法写入图片元数据")
        } ?: error("无法读取原图元数据")
    }

    private val EXIF_TAGS_TO_COPY = arrayOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_EXIF_VERSION,
        ExifInterface.TAG_FLASHPIX_VERSION,
        ExifInterface.TAG_COLOR_SPACE,
        ExifInterface.TAG_GAMMA,
        ExifInterface.TAG_COMPONENTS_CONFIGURATION,
        ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_RELATED_SOUND_FILE,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_PROGRAM,
        ExifInterface.TAG_SPECTRAL_SENSITIVITY,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_SENSITIVITY_TYPE,
        ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY,
        ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX,
        ExifInterface.TAG_ISO_SPEED,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_BRIGHTNESS_VALUE,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_MAX_APERTURE_VALUE,
        ExifInterface.TAG_SUBJECT_DISTANCE,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_LIGHT_SOURCE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_SUBJECT_AREA,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FLASH_ENERGY,
        ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION,
        ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION,
        ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT,
        ExifInterface.TAG_SUBJECT_LOCATION,
        ExifInterface.TAG_EXPOSURE_INDEX,
        ExifInterface.TAG_SENSING_METHOD,
        ExifInterface.TAG_FILE_SOURCE,
        ExifInterface.TAG_SCENE_TYPE,
        ExifInterface.TAG_CFA_PATTERN,
        ExifInterface.TAG_CUSTOM_RENDERED,
        ExifInterface.TAG_EXPOSURE_MODE,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_SCENE_CAPTURE_TYPE,
        ExifInterface.TAG_GAIN_CONTROL,
        ExifInterface.TAG_CONTRAST,
        ExifInterface.TAG_SATURATION,
        ExifInterface.TAG_SHARPNESS,
        ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION,
        ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
        ExifInterface.TAG_IMAGE_UNIQUE_ID,
        ExifInterface.TAG_CAMERA_OWNER_NAME,
        ExifInterface.TAG_BODY_SERIAL_NUMBER,
        ExifInterface.TAG_LENS_SPECIFICATION,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_LENS_SERIAL_NUMBER,
        ExifInterface.TAG_GPS_VERSION_ID,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_SATELLITES,
        ExifInterface.TAG_GPS_STATUS,
        ExifInterface.TAG_GPS_MEASURE_MODE,
        ExifInterface.TAG_GPS_DOP,
        ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_TRACK,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_MAP_DATUM,
        ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_DEST_BEARING_REF,
        ExifInterface.TAG_GPS_DEST_BEARING,
        ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
        ExifInterface.TAG_GPS_DEST_DISTANCE,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_DIFFERENTIAL,
        ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
        ExifInterface.TAG_XMP
    )
}
