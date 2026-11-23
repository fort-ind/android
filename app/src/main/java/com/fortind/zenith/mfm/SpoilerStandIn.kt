package com.fortind.zenith.mfm

private const val FILLER = ' '

fun spoilerStandIn(hidden: String, label: String): String {
    if (hidden.isBlank()) return hidden
    val out = StringBuilder(label)
    var covered = 0
    val labelLength = label.codePointCount(0, label.length)
    var index = 0
    while (index < hidden.length) {
        val codePoint = hidden.codePointAt(index)
        index += Character.charCount(codePoint)
        when {
            codePoint == '\n'.code -> out.append('\n')
            covered < labelLength -> covered++
            else -> out.append(FILLER)
        }
    }
    return out.toString()
}
