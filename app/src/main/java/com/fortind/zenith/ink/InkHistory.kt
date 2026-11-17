package com.fortind.zenith.ink

class InkHistory<T>(initial: T, private val limit: Int = DEFAULT_HISTORY_LIMIT) {
    private val states = ArrayList<T>().apply { add(initial) }
    private var index = 0

    val current: T get() = states[index]

    val canUndo: Boolean get() = index > 0

    val canRedo: Boolean get() = index < states.lastIndex

    fun push(state: T) {
        while (states.lastIndex > index) states.removeAt(states.lastIndex)
        states += state
        index++
        while (states.size > limit) {
            states.removeAt(0)
            index--
        }
    }

    fun undo(): T {
        if (canUndo) index--
        return current
    }

    fun redo(): T {
        if (canRedo) index++
        return current
    }

    fun reset(state: T) {
        states.clear()
        states += state
        index = 0
    }
}

const val DEFAULT_HISTORY_LIMIT = 100
