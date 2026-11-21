package com.fortind.zenith.media

enum class CaptureMode { Off, Auto, Hdr, Night, Portrait, FaceRetouch }

fun offeredCaptureModes(isAvailable: (CaptureMode) -> Boolean): List<CaptureMode> {
    val available = CaptureMode.entries.filter { it != CaptureMode.Off && isAvailable(it) }
    return if (available.isEmpty()) emptyList() else listOf(CaptureMode.Off) + available
}

fun effectiveCaptureMode(preferred: CaptureMode, offered: List<CaptureMode>): CaptureMode =
    if (preferred in offered) preferred else CaptureMode.Off
