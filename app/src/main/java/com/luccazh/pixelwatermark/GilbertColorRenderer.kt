package com.luccazh.pixelwatermark

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.caverock.androidsvg.SVG
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/** Renders Gilbert's OpenType-SVG glyphs, which Android's system text stack treats as monochrome. */
class GilbertColorRenderer private constructor(context: Context) {
    private val fontBytes = context.assets.open("fonts/gilbert.otf").use { it.readBytes() }
    private val typeface = Typeface.createFromAsset(context.assets, "fonts/gilbert.otf")
    private val glyphByCodePoint = parseCmap(fontBytes)
    private val svgXmlByGlyph = parseSvgTable(fontBytes)
    private val parsedSvg = ConcurrentHashMap<Int, SVG>()

    fun measureText(text: String, textSize: Float): Float = measurePaint(textSize).measureText(text)

    fun drawText(canvas: Canvas, text: String, x: Float, baseline: Float, textSize: Float, alpha: Int) {
        val paint = measurePaint(textSize)
        var cursor = x
        val layerBounds = RectF(
            x,
            baseline - textSize * 0.85f,
            x + paint.measureText(text) + textSize * 0.15f,
            baseline + textSize * 0.25f
        )
        val checkpoint = if (alpha < 255) {
            canvas.saveLayerAlpha(layerBounds, alpha.coerceIn(0, 255))
        } else {
            canvas.save()
        }
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            val charCount = Character.charCount(codePoint)
            val glyph = glyphByCodePoint[codePoint]
            val svgXml = glyph?.let(svgXmlByGlyph::get)
            if (glyph != null && svgXml != null) {
                val svg = parsedSvg.getOrPut(glyph) {
                    SVG.getFromString(svgXml).apply {
                        setDocumentViewBox(0f, -800f, 1000f, 1000f)
                    }
                }
                svg.renderToCanvas(
                    canvas,
                    RectF(cursor, baseline - textSize * 0.8f, cursor + textSize, baseline + textSize * 0.2f)
                )
            }
            val next = offset + charCount
            cursor += paint.measureText(text, offset, next)
            offset = next
        }
        canvas.restoreToCount(checkpoint)
    }

    private fun measurePaint(textSize: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        this.typeface = this@GilbertColorRenderer.typeface
    }

    companion object {
        @Volatile private var instance: GilbertColorRenderer? = null

        fun get(context: Context): GilbertColorRenderer = instance ?: synchronized(this) {
            instance ?: GilbertColorRenderer(context.applicationContext).also { instance = it }
        }

        private fun parseCmap(bytes: ByteArray): Map<Int, Int> {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val cmap = findTable(buffer, "cmap")
            val count = ushort(buffer, cmap + 2)
            var format4 = -1
            repeat(count) { index ->
                val record = cmap + 4 + index * 8
                val platform = ushort(buffer, record)
                val encoding = ushort(buffer, record + 2)
                val subtable = cmap + buffer.getInt(record + 4)
                if (ushort(buffer, subtable) == 4 && platform == 3 && encoding == 1) format4 = subtable
            }
            check(format4 >= 0) { "Gilbert cmap format 4 not found" }
            val segCount = ushort(buffer, format4 + 6) / 2
            val endCodes = format4 + 14
            val startCodes = endCodes + segCount * 2 + 2
            val deltas = startCodes + segCount * 2
            val ranges = deltas + segCount * 2
            val result = mutableMapOf<Int, Int>()
            repeat(segCount) { segment ->
                val start = ushort(buffer, startCodes + segment * 2)
                val end = ushort(buffer, endCodes + segment * 2)
                val delta = buffer.getShort(deltas + segment * 2).toInt()
                val rangeOffsetPosition = ranges + segment * 2
                val rangeOffset = ushort(buffer, rangeOffsetPosition)
                if (start != 0xffff) {
                    for (code in start..end) {
                        val glyph = if (rangeOffset == 0) {
                            (code + delta) and 0xffff
                        } else {
                            val glyphPosition = rangeOffsetPosition + rangeOffset + (code - start) * 2
                            val raw = ushort(buffer, glyphPosition)
                            if (raw == 0) 0 else (raw + delta) and 0xffff
                        }
                        if (glyph != 0) result[code] = glyph
                    }
                }
            }
            return result
        }

        private fun parseSvgTable(bytes: ByteArray): Map<Int, String> {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val table = findTable(buffer, "SVG ")
            val index = table + buffer.getInt(table + 2)
            val count = ushort(buffer, index)
            val result = mutableMapOf<Int, String>()
            repeat(count) { entry ->
                val record = index + 2 + entry * 12
                val first = ushort(buffer, record)
                val last = ushort(buffer, record + 2)
                val documentOffset = buffer.getInt(record + 4)
                val documentLength = buffer.getInt(record + 8)
                val start = index + documentOffset
                val xml = bytes.copyOfRange(start, start + documentLength).toString(Charsets.UTF_8)
                for (glyph in first..last) result[glyph] = xml
            }
            return result
        }

        private fun findTable(buffer: ByteBuffer, tag: String): Int {
            val count = ushort(buffer, 4)
            repeat(count) { index ->
                val record = 12 + index * 16
                val found = ByteArray(4).also { bytes ->
                    repeat(4) { bytes[it] = buffer.get(record + it) }
                }.toString(Charsets.ISO_8859_1)
                if (found == tag) return buffer.getInt(record + 8)
            }
            error("Font table $tag not found")
        }

        private fun ushort(buffer: ByteBuffer, position: Int): Int = buffer.getShort(position).toInt() and 0xffff
    }
}
