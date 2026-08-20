package com.luccazh.pixelwatermark

import android.content.Context
import androidx.core.content.edit

enum class WatermarkStyle(
    val label: String,
    val description: String,
    val fileName: String? = null
) {
    PIXEL_LOGO("Pixel Logo", "仅使用 Pixel Logo"),
    GOOGLE_LOGO("Google Logo", "Google G 标志"),
    GOOGLE_SANS("Google Sans", "简洁、接近 Pixel 系统字体", "google_sans.ttf"),
    BITCOUNT("Bitcount", "像素感可变字体", "bitcount.ttf"),
    GILBERT("Gilbert Color", "原生彩色字形", "gilbert.otf"),
    INTER("Inter", "清晰的现代无衬线字体", "inter.ttf"),
    LIBERTINUS("Libertinus Serif", "经典衬线字体", "libertinus.ttf")
}

enum class WatermarkTone(val label: String) {
    AUTO("自动"),
    LIGHT("白色"),
    DARK("黑色")
}

enum class LogoVariant(val label: String) {
    AUTO("自动"),
    LIGHT("白色"),
    DARK("黑色"),
    COLOR("彩色")
}

enum class BottomBarLogoStyle(val label: String) {
    MONOCHROME("单色"),
    COLOR("彩色")
}

enum class WatermarkCorner(val label: String) {
    TOP_START("左上"),
    TOP_END("右上"),
    BOTTOM_START("左下"),
    BOTTOM_END("右下")
}

data class WatermarkSettings(
    val style: WatermarkStyle = WatermarkStyle.GOOGLE_SANS,
    val titleTemplate: String = "{device}",
    val subtitleTemplate: String = "{image_info}",
    val tone: WatermarkTone = WatermarkTone.AUTO,
    val logoVariant: LogoVariant = LogoVariant.AUTO,
    val bottomBarEnabled: Boolean = false,
    val bottomBarLogoEnabled: Boolean = false,
    val bottomBarLogoStyle: BottomBarLogoStyle = BottomBarLogoStyle.MONOCHROME,
    val corner: WatermarkCorner = WatermarkCorner.BOTTOM_START,
    val opacity: Float = 0.92f,
    val marginPercent: Float = 4f,
    val scalePercent: Float = 22f
)

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("watermark_settings", Context.MODE_PRIVATE)

    fun load(): WatermarkSettings {
        val savedStyle = preferences.getString("style", null)
        val migratedStyle = preferences.getString("font", null)
        val savedTone = preferences.getString("tone", null) ?: preferences.getString("logo", null)
        val migratedLogoVariant = when (enumValueOrDefault(savedTone, WatermarkTone.AUTO)) {
            WatermarkTone.AUTO -> LogoVariant.AUTO
            WatermarkTone.LIGHT -> LogoVariant.LIGHT
            WatermarkTone.DARK -> LogoVariant.DARK
        }
        return WatermarkSettings(
            style = enumValueOrDefault(savedStyle ?: migratedStyle, WatermarkStyle.GOOGLE_SANS),
            titleTemplate = preferences.getString("title", "{device}") ?: "{device}",
            subtitleTemplate = preferences.getString("subtitle", "{image_info}") ?: "{image_info}",
            tone = enumValueOrDefault(savedTone, WatermarkTone.AUTO),
            logoVariant = enumValueOrDefault(preferences.getString("logo_variant", null), migratedLogoVariant),
            bottomBarEnabled = preferences.getBoolean("bottom_bar", false),
            bottomBarLogoEnabled = preferences.getBoolean("bottom_bar_logo", false),
            bottomBarLogoStyle = enumValueOrDefault(
                preferences.getString("bottom_bar_logo_style", null),
                BottomBarLogoStyle.MONOCHROME
            ),
            corner = enumValueOrDefault(preferences.getString("corner", null), WatermarkCorner.BOTTOM_START),
            opacity = preferences.getFloat("opacity", 0.92f),
            marginPercent = preferences.getFloat("margin", 4f),
            scalePercent = preferences.getFloat("scale", 22f)
        )
    }

    fun save(settings: WatermarkSettings) {
        preferences.edit {
            putString("style", settings.style.name)
            putString("title", settings.titleTemplate)
            putString("subtitle", settings.subtitleTemplate)
            putString("tone", settings.tone.name)
            putString("logo_variant", settings.logoVariant.name)
            putBoolean("bottom_bar", settings.bottomBarEnabled)
            putBoolean("bottom_bar_logo", settings.bottomBarLogoEnabled)
            putString("bottom_bar_logo_style", settings.bottomBarLogoStyle.name)
            putString("corner", settings.corner.name)
            putFloat("opacity", settings.opacity)
            putFloat("margin", settings.marginPercent)
            putFloat("scale", settings.scalePercent)
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, fallback: T): T =
        runCatching { enumValueOf<T>(value.orEmpty()) }.getOrDefault(fallback)
}
