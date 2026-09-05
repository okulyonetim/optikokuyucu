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
    private var transactionBase: T = initial
    private var transactionActive = false

    fun current(): T = currentValue
    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    fun isTransactionActive(): Boolean = transactionActive

    /** Commits a new immutable state. Equal states are ignored. */
    fun commit(next: T): T {
        if (transactionActive) endTransaction()
        if (next == currentValue) return currentValue

        pushUndo(currentValue)
        currentValue = next
        redoStack.clear()
        return currentValue
    }

    /** Starts a live edit whose intermediate states should collapse into one undo step. */
    fun beginTransaction(): T {
        if (!transactionActive) {
            transactionBase = currentValue
            transactionActive = true
        }
        return currentValue
    }

    /** Updates the current preview state without pushing another undo entry. */
    fun updateTransaction(next: T): T {
        if (!transactionActive) beginTransaction()
        currentValue = next
        return currentValue
    }

    /** Finalizes all transaction preview updates as one undoable edit. */
    fun endTransaction(): T {
        if (!transactionActive) return currentValue
        val base = transactionBase
        transactionActive = false
        if (base != currentValue) {
            pushUndo(base)
            redoStack.clear()
        }
        transactionBase = currentValue
        return currentValue
    }

    /** Discards transaction preview updates and restores the state from gesture start. */
    fun cancelTransaction(): T {
        if (!transactionActive) return currentValue
        currentValue = transactionBase
        transactionActive = false
        transactionBase = currentValue
        return currentValue
    }

    fun undo(): T {
        if (transactionActive) endTransaction()
        val previous = undoStack.removeLastOrNull() ?: return currentValue
        redoStack.addLast(currentValue)
        currentValue = previous
        return currentValue
    }

    fun redo(): T {
        if (transactionActive) endTransaction()
        val next = redoStack.removeLastOrNull() ?: return currentValue
        pushUndo(currentValue)
        currentValue = next
        return currentValue
    }

    fun reset(value: T): T {
        undoStack.clear()
        redoStack.clear()
        currentValue = value
        transactionBase = value
        transactionActive = false
        return currentValue
    }

    private fun pushUndo(value: T) {
        undoStack.addLast(value)
        while (undoStack.size > maxStates - 1) {
            undoStack.removeFirst()
        }
    }
}
