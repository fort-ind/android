package com.fortind.zenith.ui.nav

data class CaptionSpan(val start: Int, val end: Int) {
    val width: Int get() = end - start

    fun overlaps(other: CaptionSpan): Boolean = start < other.end && other.start < end
}

data class CaptionPlacement(val index: Int, val span: CaptionSpan)

fun placeCaptionClaims(
    panes: List<CaptionSpan?>,
    zoneLeft: Int,
    zoneRight: Int,
    minWidth: Int,
): List<CaptionPlacement> {
    if (zoneRight - zoneLeft < minWidth) return emptyList()
    val kept = mutableListOf<CaptionPlacement>()
    for (index in panes.indices.reversed()) {
        val span = panes[index] ?: CaptionSpan(zoneLeft, zoneRight)
        if (span.width < minWidth) continue
        if (kept.any { it.span.overlaps(span) }) continue
        kept += CaptionPlacement(index, span)
    }
    val ordered = kept.sortedBy { it.span.start }
    return ordered.mapIndexedNotNull { position, placement ->
        val start = if (position == 0) zoneLeft else maxOf(placement.span.start, zoneLeft)
        val end = if (position == ordered.lastIndex) zoneRight else minOf(placement.span.end, zoneRight)
        CaptionSpan(start, end).takeIf { it.width >= minWidth }?.let { placement.copy(span = it) }
    }
}
