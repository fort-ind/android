package com.fortind.zenith.media

enum class CaptureTimer(val seconds: Int) { Off(0), Three(3), Ten(10) }

enum class FlashChoice { Off, Auto, On }

fun flashChoices(hasFlashUnit: Boolean, screenFlash: Boolean): List<FlashChoice> = when {
    hasFlashUnit -> FlashChoice.entries
    screenFlash -> listOf(FlashChoice.Off, FlashChoice.On)
    else -> emptyList()
}

fun effectiveFlash(saved: FlashChoice, choices: List<FlashChoice>): FlashChoice =
    if (saved in choices) saved else FlashChoice.Off

fun nextFlash(current: FlashChoice, choices: List<FlashChoice>): FlashChoice {
    if (choices.isEmpty()) return current
    return choices[(choices.indexOf(current) + 1) % choices.size]
}

enum class VideoOption { Fps60, Stabilize, LowLight }

enum class Stabilization { Preview, Recording }

data class VideoSupport(
    val fps60: Boolean = false,
    val stabilization: Stabilization? = null,
    val fps60WithStabilization: Boolean = false,
    val lowLight: Boolean = false,
) {
    fun offers(option: VideoOption): Boolean = when (option) {
        VideoOption.Fps60 -> fps60
        VideoOption.Stabilize -> stabilization != null
        VideoOption.LowLight -> lowLight
    }
}

data class VideoOptions(
    val fps60: Boolean = false,
    val stabilize: Boolean = false,
    val lowLight: Boolean = false,
) {
    fun isOn(option: VideoOption): Boolean = when (option) {
        VideoOption.Fps60 -> fps60
        VideoOption.Stabilize -> stabilize
        VideoOption.LowLight -> lowLight
    }

    fun with(option: VideoOption, on: Boolean, support: VideoSupport): VideoOptions = when (option) {
        VideoOption.Fps60 -> copy(
            fps60 = on,
            lowLight = lowLight && !on,
            stabilize = stabilize && !(on && !support.fps60WithStabilization),
        )
        VideoOption.Stabilize -> copy(
            stabilize = on,
            fps60 = fps60 && !(on && !support.fps60WithStabilization),
        )
        VideoOption.LowLight -> copy(lowLight = on, fps60 = fps60 && !on)
    }

    fun turnedOffBy(option: VideoOption, support: VideoSupport): List<VideoOption> {
        val after = with(option, true, support)
        return VideoOption.entries.filter { it != option && isOn(it) && !after.isOn(it) }
    }

    fun effectiveFor(support: VideoSupport): VideoOptions {
        val stabilized = stabilize && support.stabilization != null
        val smooth = fps60 && support.fps60 && (!stabilized || support.fps60WithStabilization)
        return VideoOptions(
            fps60 = smooth,
            stabilize = stabilized,
            lowLight = lowLight && support.lowLight && !smooth,
        )
    }
}
