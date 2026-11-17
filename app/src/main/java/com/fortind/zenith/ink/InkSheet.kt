package com.fortind.zenith.ink

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class SheetShape(val widthRatio: Int, val heightRatio: Int) {
    Landscape(4, 3),
    Wide(16, 9),
    Square(1, 1),
    Portrait(3, 4),
}

enum class SheetBackground {
    White,
    Black,
    Theme,
    Transparent;

    fun argb(themeArgb: Int): Int? = when (this) {
        White -> 0xFFFFFFFF.toInt()
        Black -> 0xFF000000.toInt()
        Theme -> themeArgb
        Transparent -> null
    }
}

data class SheetSize(val width: Float, val height: Float) {
    val aspectRatio: Float get() = width / height
}

data class PixelSize(val width: Int, val height: Int)

object SheetGeometry {
    const val DRAWING_LONG_SIDE = 800f
    const val MARKUP_LONG_SIDE = 1000f
    const val DRAWING_EXPORT_SCALE = 2f
    const val MARKUP_MAX_PIXELS = 2560

    fun drawing(shape: SheetShape): SheetSize {
        val longRatio = max(shape.widthRatio, shape.heightRatio).toFloat()
        val unit = DRAWING_LONG_SIDE / longRatio
        return SheetSize(shape.widthRatio * unit, shape.heightRatio * unit)
    }

    fun markup(photoWidth: Int, photoHeight: Int): SheetSize {
        val longSide = max(photoWidth, photoHeight).coerceAtLeast(1).toFloat()
        val unit = MARKUP_LONG_SIDE / longSide
        return SheetSize(photoWidth.coerceAtLeast(1) * unit, photoHeight.coerceAtLeast(1) * unit)
    }

    fun drawingExport(sheet: SheetSize): PixelSize =
        PixelSize((sheet.width * DRAWING_EXPORT_SCALE).roundToInt(), (sheet.height * DRAWING_EXPORT_SCALE).roundToInt())

    fun fitScale(sheet: SheetSize, availableWidth: Float, availableHeight: Float): Float =
        min(availableWidth / sheet.width, availableHeight / sheet.height).coerceAtLeast(0f)

    fun limitPixels(width: Int, height: Int, maxSide: Int = MARKUP_MAX_PIXELS): PixelSize {
        val longSide = max(width, height)
        if (longSide <= maxSide) return PixelSize(width, height)
        val scale = maxSide.toFloat() / longSide
        return PixelSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
    }
}
