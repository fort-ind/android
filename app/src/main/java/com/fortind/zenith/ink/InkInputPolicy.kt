package com.fortind.zenith.ink

enum class InkTool { Pen, Marker, Highlighter, Eraser }

enum class InkPointer { Touch, Stylus, StylusButton, StylusEraser, Mouse, MouseOtherButton }

enum class InkAction { Draw, Erase, Ignore }

data class InkInputPolicy(val drawWithMouse: Boolean = true, val touchStopsAfterPen: Boolean = true) {
    fun actionFor(pointer: InkPointer, tool: InkTool, penSeen: Boolean): InkAction = when (pointer) {
        InkPointer.StylusEraser, InkPointer.StylusButton -> InkAction.Erase
        InkPointer.Stylus -> tool.action()
        InkPointer.Touch -> if (touchStopsAfterPen && penSeen) InkAction.Ignore else tool.action()
        InkPointer.Mouse -> if (drawWithMouse) tool.action() else InkAction.Ignore
        InkPointer.MouseOtherButton -> InkAction.Ignore
    }

    fun isPen(pointer: InkPointer): Boolean =
        pointer == InkPointer.Stylus || pointer == InkPointer.StylusButton || pointer == InkPointer.StylusEraser

    private fun InkTool.action(): InkAction = if (this == InkTool.Eraser) InkAction.Erase else InkAction.Draw
}
