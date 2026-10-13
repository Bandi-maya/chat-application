package com.example.chat.island

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Gesture arbiter handling tap, long press, vertical pull-down, fling-to-dismiss,
 * and nested scroll disambiguation (fixing K-4).
 */
class IslandGestureArbiter(
    context: Context,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onTap()
        fun onLongPress()
        fun onSwipeDown()
        fun onSwipeUp()
        fun onSwipeHorizontal(directionRight: Boolean)
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity

    private var startX = 0f
    private var startY = 0f
    private var isDragging = false

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            callbacks.onTap()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            callbacks.onLongPress()
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y

            if (abs(dx) > abs(dy) && abs(dx) > touchSlop && abs(velocityX) > minFlingVelocity) {
                callbacks.onSwipeHorizontal(dx > 0)
                return true
            } else if (abs(dy) > abs(dx) && abs(dy) > touchSlop && abs(velocityY) > minFlingVelocity) {
                if (dy > 0) {
                    callbacks.onSwipeDown()
                } else {
                    callbacks.onSwipeUp()
                }
                return true
            }
            return false
        }
    })

    fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                val dy = event.y - startY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    isDragging = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return true
    }
}
