package com.example.core.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView

class FloatingRecorderOverlay(
    private val context: Context,
    private val onPauseResumeClick: () -> Unit,
    private val onSaveClick: () -> Unit,
    private val onCancelClick: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val view: View
    private val params: WindowManager.LayoutParams

    private val collapsedView: View
    private val expandedView: View
    private val timerTextCollapsed: TextView
    private val statusTextExpanded: TextView
    private val pauseResumeBtn: Button
    private val saveBtn: Button
    private val cancelBtn: Button

    private var isExpanded = false
    private var isAdded = false

    init {
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC000000.toInt())
            setPadding(24, 24, 24, 24)
            elevation = 16f
        }

        // Collapsed Container
        val collapsedLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 8, 8, 8)
        }
        val redDot = TextView(context).apply {
            text = "🔴 "
            textSize = 14f
        }
        timerTextCollapsed = TextView(context).apply {
            text = "00:00"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
        }
        collapsedLayout.addView(redDot)
        collapsedLayout.addView(timerTextCollapsed)

        // Expanded Container
        val expandedLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(8, 8, 8, 8)
        }
        statusTextExpanded = TextView(context).apply {
            text = "Recording • 00:00"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
        }
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 0)
        }
        pauseResumeBtn = Button(context).apply {
            text = "Pause"
            textSize = 11f
            setOnClickListener { onPauseResumeClick() }
        }
        saveBtn = Button(context).apply {
            text = "Save"
            textSize = 11f
            setOnClickListener {
                Log.d("PixelRoxRecorder", "SAVE_01 overlay clicked")
                onSaveClick()
            }
        }
        cancelBtn = Button(context).apply {
            text = "Cancel"
            textSize = 11f
            setOnClickListener { onCancelClick() }
        }
        val collapseActionBtn = Button(context).apply {
            text = "_"
            textSize = 11f
            setOnClickListener { toggleExpand() }
        }
        btnRow.addView(pauseResumeBtn)
        btnRow.addView(Space(context).apply { layoutParams = LinearLayout.LayoutParams(6, 0) })
        btnRow.addView(saveBtn)
        btnRow.addView(Space(context).apply { layoutParams = LinearLayout.LayoutParams(6, 0) })
        btnRow.addView(cancelBtn)
        btnRow.addView(Space(context).apply { layoutParams = LinearLayout.LayoutParams(6, 0) })
        btnRow.addView(collapseActionBtn)

        expandedLayout.addView(statusTextExpanded)
        expandedLayout.addView(btnRow)

        rootLayout.addView(collapsedLayout)
        rootLayout.addView(expandedLayout)

        collapsedView = collapsedLayout
        expandedView = expandedLayout
        view = rootLayout

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener(object : View.OnTouchListener {
            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                            isDragging = true
                        }
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (_: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            toggleExpand()
                        }
                        return true
                    }
                }
                return false
            }
        })

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded
        if (isExpanded) {
            collapsedView.visibility = View.GONE
            expandedView.visibility = View.VISIBLE
        } else {
            collapsedView.visibility = View.VISIBLE
            expandedView.visibility = View.GONE
        }
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }

    fun show() {
        if (!isAdded) {
            try {
                windowManager.addView(view, params)
                isAdded = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hide() {
        if (isAdded) {
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {}
            isAdded = false
        }
    }

    fun updateState(isPaused: Boolean, timeFormatted: String) {
        timerTextCollapsed.text = timeFormatted
        if (isPaused) {
            statusTextExpanded.text = "Paused • $timeFormatted"
            pauseResumeBtn.text = "Resume"
        } else {
            statusTextExpanded.text = "Recording • $timeFormatted"
            pauseResumeBtn.text = "Pause"
        }
    }
}
