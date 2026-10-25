package com.example.chat.core.customization

/** Port of customization_history.dart (capacity 10, identity-equality no-op guard as in Dart). */
class CustomizationHistoryItem(
    val timestampMillis: Long,
    val scope: ComponentScope,
    val previous: CustomizationSnapshot,
    val next: CustomizationSnapshot,
    val description: String = "",
)

class CustomizationHistory {
    private val _items = mutableListOf<CustomizationHistoryItem>()
    val items: List<CustomizationHistoryItem> get() = _items.toList()
    val canUndo: Boolean get() = _items.isNotEmpty()

    fun record(scope: ComponentScope, previous: CustomizationSnapshot, next: CustomizationSnapshot, description: String = "") {
        if (previous === next) return
        if (_items.size >= maxCapacity) _items.removeAt(0)
        _items.add(CustomizationHistoryItem(System.currentTimeMillis(), scope, previous, next, description))
    }

    fun popUndo(): CustomizationHistoryItem? = if (_items.isEmpty()) null else _items.removeAt(_items.size - 1)
    fun clear() = _items.clear()

    companion object { const val maxCapacity = 10 }
}
