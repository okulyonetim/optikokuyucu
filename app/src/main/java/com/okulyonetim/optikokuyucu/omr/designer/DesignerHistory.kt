package com.okulyonetim.optikokuyucu.omr.designer

/** Small UI-agnostic undo/redo engine for immutable designer documents. */
class DesignerHistory<T>(
    initial: T,
    private val maxStates: Int = 100
) {
    init {
        require(maxStates >= 2)
    }

    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()
    private var currentValue: T = initial

    fun current(): T = currentValue
    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /** Commits a new immutable state. Equal states are ignored. */
    fun commit(next: T): T {
        if (next == currentValue) return currentValue

        undoStack.addLast(currentValue)
        while (undoStack.size > maxStates - 1) {
            undoStack.removeFirst()
        }
        currentValue = next
        redoStack.clear()
        return currentValue
    }

    fun undo(): T {
        val previous = undoStack.removeLastOrNull() ?: return currentValue
        redoStack.addLast(currentValue)
        currentValue = previous
        return currentValue
    }

    fun redo(): T {
        val next = redoStack.removeLastOrNull() ?: return currentValue
        undoStack.addLast(currentValue)
        currentValue = next
        return currentValue
    }

    fun reset(value: T): T {
        undoStack.clear()
        redoStack.clear()
        currentValue = value
        return currentValue
    }
}
