package com.fortind.zenith.ink

enum class InkColor(val argb: Long) {
    Black(0xFF000000),
    White(0xFFFFFFFE),
    Gray(0xFF757575),
    Red(0xFFE53935),
    Orange(0xFFFB8C00),
    Yellow(0xFFFDD835),
    Green(0xFF43A047),
    Blue(0xFF1E88E5),
    Purple(0xFF8E24AA),
    Pink(0xFFD81B60),
}

enum class InkSize { Small, Medium, Large }

data class ToolStyle(val color: InkColor, val size: InkSize)

object InkStyles {
    const val HIGHLIGHTER_ALPHA = 0.4f
    const val EPSILON = 0.1f

    val defaults: Map<InkTool, ToolStyle> = mapOf(
        InkTool.Pen to ToolStyle(InkColor.Black, InkSize.Medium),
        InkTool.Marker to ToolStyle(InkColor.Black, InkSize.Medium),
        InkTool.Highlighter to ToolStyle(InkColor.Yellow, InkSize.Medium),
        InkTool.Eraser to ToolStyle(InkColor.Black, InkSize.Medium),
    )

    fun width(tool: InkTool, size: InkSize): Float = when (tool) {
        InkTool.Pen -> size.pick(2f, 4f, 8f)
        InkTool.Marker -> size.pick(5f, 10f, 20f)
        InkTool.Highlighter -> size.pick(14f, 24f, 40f)
        InkTool.Eraser -> size.pick(6f, 16f, 36f)
    }

    fun forBackground(styles: Map<InkTool, ToolStyle>, background: SheetBackground): Map<InkTool, ToolStyle> {
        val from = when (background) {
            SheetBackground.Black, SheetBackground.Theme -> InkColor.Black
            SheetBackground.White -> InkColor.White
            SheetBackground.Transparent -> return styles
        }
        val to = if (from == InkColor.Black) InkColor.White else InkColor.Black
        return styles.mapValues { (tool, style) ->
            if ((tool == InkTool.Pen || tool == InkTool.Marker) && style.color == from) style.copy(color = to) else style
        }
    }

    fun highlighterMultiplies(backdropArgb: Int?): Boolean {
        if (backdropArgb == null) return true
        val red = (backdropArgb shr 16) and 0xFF
        val green = (backdropArgb shr 8) and 0xFF
        val blue = backdropArgb and 0xFF
        return (LUMA_RED * red + LUMA_GREEN * green + LUMA_BLUE * blue) / 255f >= LIGHT_BACKDROP_LUMA
    }

    private const val LUMA_RED = 0.2126f
    private const val LUMA_GREEN = 0.7152f
    private const val LUMA_BLUE = 0.0722f
    private const val LIGHT_BACKDROP_LUMA = 0.5f

    private fun InkSize.pick(small: Float, medium: Float, large: Float): Float = when (this) {
        InkSize.Small -> small
        InkSize.Medium -> medium
        InkSize.Large -> large
    }
}
