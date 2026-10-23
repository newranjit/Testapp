package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingAgentOverlayService : Service() {

    companion object {
        var isOverlayRunning: Boolean = false
            private set

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Toast.makeText(context, "Please allow 'Display over other apps' for Floating Agent", Toast.LENGTH_LONG).show()
                return
            }
            val intent = Intent(context, FloatingAgentOverlayService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingAgentOverlayService::class.java)
            context.stopService(intent)
        }

        fun toggle(context: Context) {
            if (isOverlayRunning) {
                stop(context)
            } else {
                start(context)
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private var overlayRootView: FrameLayout? = null
    private var collapsedBubbleView: View? = null
    private var expandedCardView: View? = null

    private lateinit var windowParams: WindowManager.LayoutParams
    private var isExpanded = false

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        isOverlayRunning = true

        AgentNotificationController.updateOverlayState(this, isOverlayActive = true)

        createOverlayWindow()
        observeBackgroundExecution()
    }

    private fun observeBackgroundExecution() {
        CoroutineScope(Dispatchers.Main).launch {
            BackgroundAgentExecutor.lastExecutionResult.collect { result ->
                statusTextView?.text = result ?: "Ready for commands"
            }
        }
    }

    private var statusTextView: TextView? = null

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayWindow() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        overlayRootView = FrameLayout(this)

        // Build Collapsed Floating Bubble
        collapsedBubbleView = buildCollapsedBubble()

        // Build Expanded Command Card
        expandedCardView = buildExpandedCard()
        expandedCardView?.visibility = View.GONE

        overlayRootView?.addView(collapsedBubbleView)
        overlayRootView?.addView(expandedCardView)

        try {
            windowManager.addView(overlayRootView, windowParams)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not show overlay: ${e.message}", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun buildCollapsedBubble(): View {
        val sizeDp = dpToPx(62)
        val bubble = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizeDp, sizeDp)

            // Modern glowing cyan & indigo gradient circle
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(
                    Color.parseColor("#38BDF8"), // Cyan
                    Color.parseColor("#4F46E5")  // Indigo
                )
                gradientType = GradientDrawable.SWEEP_GRADIENT
                setStroke(dpToPx(2), Color.parseColor("#E0F2FE"))
            }
            elevation = dpToPx(12).toFloat()
        }

        val icon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(R.drawable.ic_launcher_foreground)
        }
        bubble.addView(icon)

        // Touch & Drag Handling
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = windowParams.x
                    initialY = windowParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                        isClick = false
                    }
                    windowParams.x = initialX + deltaX
                    windowParams.y = initialY + deltaY
                    windowManager.updateViewLayout(overlayRootView, windowParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        expandCommandBox()
                    }
                    true
                }
                else -> false
            }
        }

        return bubble
    }

    private fun buildExpandedCard(): View {
        val widthDp = dpToPx(320)
        val cardLayout = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(widthDp, FrameLayout.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(18).toFloat()
                setColor(Color.parseColor("#0F172A")) // Deep dark slate
                setStroke(dpToPx(1), Color.parseColor("#38BDF8"))
            }
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(16))
            elevation = dpToPx(16).toFloat()
        }

        // Header: Title, Minimize button, Deactivate button
        val headerRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = "AgentFlow Assistant"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        headerRow.addView(titleView)

        // Minimize button
        val minimizeBtn = TextView(this).apply {
            text = "−"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener { collapseCommandBox() }
        }
        headerRow.addView(minimizeBtn)

        // Close Overlay Button
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            setPadding(dpToPx(8), dpToPx(4), dpToPx(4), dpToPx(4))
            setOnClickListener { stopSelf() }
        }
        headerRow.addView(closeBtn)

        cardLayout.addView(headerRow)

        // Status text
        statusTextView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
                bottomMargin = dpToPx(10)
            }
            text = "Ready to execute phone commands"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 12f
        }
        cardLayout.addView(statusTextView)

        // Input Row (EditText + Mic + Send)
        val inputRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val editText = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(44), 1f).apply {
                marginEnd = dpToPx(6)
            }
            hint = "Ask anything to automate…"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 13f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(22).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dpToPx(1), Color.parseColor("#334155"))
            }
            setPadding(dpToPx(14), 0, dpToPx(14), 0)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        inputRow.addView(editText)

        // Mic Button (opens voice listening in app)
        val micBtn = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(42), dpToPx(42)).apply {
                marginEnd = dpToPx(6)
            }
            text = "🎤"
            textSize = 14f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1E293B"))
                setStroke(dpToPx(1), Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                collapseCommandBox()
                val voiceIntent = Intent(this@FloatingAgentOverlayService, MainActivity::class.java).apply {
                    action = AgentNotificationController.ACTION_NOTIFICATION_VOICE
                    putExtra("START_VOICE_LISTENING", true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(voiceIntent)
            }
        }
        inputRow.addView(micBtn)

        // Send Button
        val sendBtn = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(42), dpToPx(42))
            text = "➔"
            textSize = 14f
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                val command = editText.text.toString().trim()
                if (command.isNotBlank()) {
                    editText.text.clear()
                    statusTextView?.text = "Executing: \"$command\"…"
                    hideKeyboard(editText)
                    BackgroundAgentExecutor.executePrompt(this@FloatingAgentOverlayService, command)
                }
            }
        }
        inputRow.addView(sendBtn)

        cardLayout.addView(inputRow)

        // Quick Command Shortcut Chips
        val chipScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(10)
            }
            isHorizontalScrollBarEnabled = false
        }

        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val quickChips = listOf(
            "Flashlight" to "Toggle the flashlight",
            "10m Timer" to "Set a 10 minute timer",
            "Maps" to "Open Google Maps",
            "Wi-Fi" to "Open Wi-Fi Settings",
            "YouTube" to "Open YouTube"
        )

        quickChips.forEach { (label, command) ->
            val chip = TextView(this).apply {
                text = label
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 11f
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(12).toFloat()
                    setColor(Color.parseColor("#1E293B"))
                    setStroke(dpToPx(1), Color.parseColor("#38BDF8"))
                }
                setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dpToPx(6)
                }
                setOnClickListener {
                    statusTextView?.text = "Executing: $label…"
                    BackgroundAgentExecutor.executePrompt(this@FloatingAgentOverlayService, command)
                }
            }
            chipRow.addView(chip)
        }

        chipScroll.addView(chipRow)
        cardLayout.addView(chipScroll)

        return cardLayout
    }

    private fun expandCommandBox() {
        isExpanded = true
        collapsedBubbleView?.visibility = View.GONE
        expandedCardView?.visibility = View.VISIBLE

        // Change window flags so EditText can receive keyboard input
        windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        windowManager.updateViewLayout(overlayRootView, windowParams)
    }

    private fun collapseCommandBox() {
        isExpanded = false
        expandedCardView?.visibility = View.GONE
        collapsedBubbleView?.visibility = View.VISIBLE

        // Revert to non-focusable so clicks pass through to other apps
        windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        windowManager.updateViewLayout(overlayRootView, windowParams)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        isOverlayRunning = false
        AgentNotificationController.updateOverlayState(this, isOverlayActive = false)
        if (overlayRootView != null) {
            try {
                windowManager.removeView(overlayRootView)
            } catch (e: Exception) {
                // ignore
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
