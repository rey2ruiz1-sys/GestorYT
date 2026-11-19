package com.example.ui.components

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

class DragDropListState(
    val lazyListState: LazyListState,
    private val haptic: HapticFeedback,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onDragEnd: () -> Unit
) {
    var draggedItemKey by mutableStateOf<Any?>(null)
        private set

    var draggedDistance by mutableFloatStateOf(0f)
        private set

    private var initialDraggedElement by mutableStateOf<LazyListItemInfo?>(null)
    private var currentIndexOfDraggedItem by mutableStateOf<Int?>(null)

    fun onDragStart(itemKey: Any) {
        val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey }
            ?: return
        draggedItemKey = itemKey
        initialDraggedElement = itemInfo
        currentIndexOfDraggedItem = itemInfo.index
        draggedDistance = 0f
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onDrag(deltaY: Float, indexOffset: Int = 0) {
        val currentIdx = currentIndexOfDraggedItem ?: return
        val startElement = initialDraggedElement ?: return
        draggedDistance += deltaY

        val startOffset = startElement.offset + draggedDistance
        val endOffset = startOffset + startElement.size
        val hoveredMiddle = (startOffset + endOffset) / 2f

        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val targetItem = visibleItems.firstOrNull { item ->
            item.index != currentIdx &&
                item.index >= indexOffset &&
                hoveredMiddle >= item.offset &&
                hoveredMiddle <= item.offset + item.size
        }

        if (targetItem != null) {
            val fromDomain = currentIdx - indexOffset
            val toDomain = targetItem.index - indexOffset
            if (fromDomain >= 0 && toDomain >= 0) {
                onMove(fromDomain, toDomain)
                currentIndexOfDraggedItem = targetItem.index
                draggedDistance += (startElement.offset - targetItem.offset)
                initialDraggedElement = targetItem
            }
        }
    }

    fun onDragInterrupted() {
        val hadDrag = draggedItemKey != null
        draggedItemKey = null
        draggedDistance = 0f
        initialDraggedElement = null
        currentIndexOfDraggedItem = null
        if (hadDrag) {
            onDragEnd()
        }
    }
}

@Composable
fun rememberDragDropListState(
    lazyListState: LazyListState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit
): DragDropListState {
    val haptic = LocalHapticFeedback.current
    return remember(lazyListState, haptic) {
        DragDropListState(
            lazyListState = lazyListState,
            haptic = haptic,
            onMove = onMove,
            onDragEnd = onDragEnd
        )
    }
}
