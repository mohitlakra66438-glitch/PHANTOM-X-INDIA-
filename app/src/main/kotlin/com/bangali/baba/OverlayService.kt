package com.bangali.baba

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayService : Service() {

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        private const val CHANNEL_ID = "phantom_x_overlay_channel"
        private const val NOTIFICATION_ID = 1042
        private const val FPS_UPDATE_INTERVAL_MS = 500L
        private const val MAX_LOG_LINES = 80
    }

    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fpsReaderJob: Job? = null

    private var fpsChipView: FrameLayout? = null
    private var fpsChipText: TextView? = null
    private var fpsChipParams: WindowManager.LayoutParams? = null

    private var terminalRootView: FrameLayout? = null
    private var terminalParams: WindowManager.LayoutParams? = null
    private var terminalFpsText: TextView? = null
    private var logScrollView: ScrollView? = null
    private var logConsoleText: TextView? = null
    private var moreDropdownContainer: LinearLayout? = null

    private var bypassSwitch: CyberSwitchView? = null
    private var injectSwitch: CyberSwitchView? = null

    private var monoTypeface: Typeface = Typeface.MONOSPACE
    private var boldTypeface: Typeface = Typeface.DEFAULT_BOLD

    private val currentFps = AtomicInteger(0)
    private val logLines = ArrayDeque<String>(MAX_LOG_LINES)
    private var cachedLayerName: String? = null
    private var lastLayerScanMs: Long = 0L
    private var isTerminalVisible: Boolean = false
    private var isUsingChoreographerFallback: Boolean = false

    @Volatile
    private var choreographerLastFrameNs: Long = 0L
    @Volatile
    private var choreographerEmaFps: Double = 60.0

    private val choreographerCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return
            val prev = choreographerLastFrameNs
            if (prev > 0L && frameTimeNanos > prev) {
                val deltaNs = (frameTimeNanos - prev).toDouble()
                val instantFps = (1_000_000_000.0 / deltaNs).coerceIn(1.0, 240.0)
                choreographerEmaFps = choreographerEmaFps * 0.8 + instantFps * 0.2
            }
            choreographerLastFrameNs = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val uiRefreshRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val fpsValue = currentFps.get()
            val formatted = if (fpsValue > 0) "FPS: $fpsValue" else "FPS: --"
            fpsChipText?.text = formatted
            terminalFpsText?.text = formatted
            mainHandler.postDelayed(this, FPS_UPDATE_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        monoTypeface = ResourcesCompat.getFont(this, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
        boldTypeface = ResourcesCompat.getFont(this, R.font.space_grotesk) ?: Typeface.DEFAULT_BOLD

        startOverlayForeground()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createFpsChipOverlay()
        createTerminalMenuOverlay()

        appendLog("PHANTOM X HUD INITIALIZED")
        appendLog("FPS ENGINE: SurfaceFlinger latency probe ready")

        mainHandler.post(uiRefreshRunnable)
        startSurfaceFlingerMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startOverlayForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        ).roundToInt()
    }

    // -----------------------------------------------------------------------
    // Component A: Persistent Draggable FPS Chip
    // -----------------------------------------------------------------------
    private fun createFpsChipOverlay() {
        val chipContainer = FrameLayout(this).apply {
            background = CyberChipDrawable(this@OverlayService)
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            minimumHeight = dp(40f)
        }

        val chipLabel = TextView(this).apply {
            text = "FPS: --"
            typeface = monoTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.rgb(0, 255, 102)) // Matrix Green
            letterSpacing = 0.08f
            includeFontPadding = false
        }
        chipContainer.addView(
            chipLabel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16f)
            y = dp(48f)
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var downRawX = 0f
        var downRawY = 0f
        var isDragging = false

        chipContainer.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = (initialX + dx.roundToInt()).coerceAtLeast(0)
                        params.y = (initialY + dy.roundToInt()).coerceAtLeast(0)
                        windowManager?.updateViewLayout(v, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        toggleTerminalMenu()
                    }
                    true
                }
                else -> false
            }
        }

        fpsChipView = chipContainer
        fpsChipText = chipLabel
        fpsChipParams = params

        try {
            windowManager?.addView(chipContainer, params)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    // -----------------------------------------------------------------------
    // Component B: Modal Terminal Menu with CRT Scanlines & Split View
    // -----------------------------------------------------------------------
    private fun createTerminalMenuOverlay() {
        val panelWidth = (resources.displayMetrics.widthPixels * 0.88f)
            .roundToInt()
            .coerceAtMost(dp(440f))

        val rootContainer = FrameLayout(this).apply {
            background = CyberPanelDrawable(this@OverlayService)
            visibility = View.GONE
        }

        val contentColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), dp(10f), dp(12f), dp(12f))
        }

        // Header Row (Draggable handle)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2f), 0, 0, dp(8f))
        }

        val terminalGlyph = CyberHeaderGlyphView(
            this,
            HeaderGlyphKind.TERMINAL_PROMPT,
            Color.rgb(255, 0, 51)
        )
        headerRow.addView(terminalGlyph, LinearLayout.LayoutParams(dp(18f), dp(18f)))

        val titleView = TextView(this).apply {
            text = " TERMINAL"
            typeface = monoTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.rgb(244, 244, 246))
            letterSpacing = 0.1f
        }
        headerRow.addView(
            titleView,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val liveFpsView = TextView(this).apply {
            text = "FPS: --"
            typeface = monoTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.rgb(0, 255, 102))
            setPadding(dp(6f), 0, dp(8f), 0)
        }
        headerRow.addView(liveFpsView)

        val minimizeBtn = FrameLayout(this).apply {
            contentDescription = getString(R.string.cd_minimize_overlay)
            background = createCyberButtonDrawable(
                fillColor = Color.rgb(24, 24, 30),
                strokeColor = Color.rgb(58, 58, 66),
                pressedColor = Color.rgb(48, 48, 58)
            )
            setOnClickListener { setTerminalVisible(false) }
        }
        minimizeBtn.addView(
            CyberHeaderGlyphView(this, HeaderGlyphKind.MINIMIZE, Color.rgb(220, 220, 228)),
            FrameLayout.LayoutParams(dp(22f), dp(22f), Gravity.CENTER)
        )
        headerRow.addView(minimizeBtn, LinearLayout.LayoutParams(dp(36f), dp(36f)).apply {
            marginEnd = dp(6f)
        })

        val closeBtn = FrameLayout(this).apply {
            contentDescription = getString(R.string.cd_close_overlay)
            background = createCyberButtonDrawable(
                fillColor = Color.rgb(58, 0, 13),
                strokeColor = Color.rgb(255, 0, 51),
                pressedColor = Color.rgb(110, 0, 26)
            )
            setOnClickListener {
                appendLog("SHUTTING DOWN OVERLAY SERVICE")
                stopSelf()
            }
        }
        closeBtn.addView(
            CyberHeaderGlyphView(this, HeaderGlyphKind.CLOSE, Color.rgb(255, 255, 255)),
            FrameLayout.LayoutParams(dp(22f), dp(22f), Gravity.CENTER)
        )
        headerRow.addView(closeBtn, LinearLayout.LayoutParams(dp(36f), dp(36f)))

        contentColumn.addView(
            headerRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        // Header Neon Divider
        val divider = View(this).apply {
            setBackgroundColor(Color.rgb(52, 52, 60))
        }
        contentColumn.addView(
            divider,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)).apply {
                bottomMargin = dp(10f)
            }
        )

        // Body Split View: Left Log Console | Right Control Panel
        val splitBody = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
        }

        // Left Pane: Log Console (maxHeight 200dp)
        val maxConsoleHeight = dp(200f)
        val consoleScroll = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val maxSpec = MeasureSpec.makeMeasureSpec(maxConsoleHeight, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, maxSpec)
            }
        }.apply {
            background = GradientDrawable().apply {
                setColor(Color.rgb(5, 5, 5))
                setStroke(dp(1f), Color.rgb(42, 42, 48))
                cornerRadius = dp(4f).toFloat()
            }
            setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
            isVerticalScrollBarEnabled = true
        }

        val consoleText = TextView(this).apply {
            typeface = monoTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTextColor(Color.rgb(0, 255, 102))
            setLineSpacing(dp(2f).toFloat(), 1.0f)
        }
        consoleScroll.addView(
            consoleText,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        splitBody.addView(
            consoleScroll,
            LinearLayout.LayoutParams(0, dp(196f), 1.25f).apply {
                marginEnd = dp(10f)
            }
        )

        // Right Pane: Control Panel (Switches + MORE dropdown)
        val controlPane = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val bypassToggle = CyberSwitchView(this).apply {
            onCheckedChangeListener = { enabled ->
                PhantomEngineBridge.callbacks.onBypassToggle(enabled)
                appendLog("BYPASS -> ${if (enabled) "ENABLED" else "DISABLED"}")
            }
        }
        controlPane.addView(
            createSwitchRow(getString(R.string.overlay_bypass), bypassToggle),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8f)
            }
        )

        val injectToggle = CyberSwitchView(this).apply {
            onCheckedChangeListener = { enabled ->
                PhantomEngineBridge.callbacks.onInjectClick(enabled)
                appendLog("INJECT -> ${if (enabled) "ACTIVE" else "STANDBY"}")
            }
        }
        controlPane.addView(
            createSwitchRow(getString(R.string.overlay_inject), injectToggle),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10f)
            }
        )

        val dropdownContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = GradientDrawable().apply {
                setColor(Color.rgb(12, 12, 16))
                setStroke(dp(1f), Color.rgb(64, 64, 72))
                cornerRadius = dp(4f).toFloat()
            }
        }

        val moreBtn = TextView(this).apply {
            text = "${getString(R.string.overlay_more)} v"
            typeface = monoTypeface
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.rgb(244, 244, 246))
            gravity = Gravity.CENTER
            minimumHeight = dp(44f)
            background = createCyberButtonDrawable(
                fillColor = Color.rgb(34, 8, 14),
                strokeColor = Color.rgb(255, 0, 51),
                pressedColor = Color.rgb(86, 0, 20)
            )
            setOnClickListener {
                dropdownContainer.visibility =
                    if (dropdownContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        controlPane.addView(
            moreBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val dropdownItems = listOf(
            getString(R.string.overlay_more_copy) to {
                val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clip.setPrimaryClip(ClipData.newPlainText("PhantomXLog", consoleText.text))
                appendLog("LOG COPIED TO CLIPBOARD")
                Toast.makeText(this, "Terminal log copied", Toast.LENGTH_SHORT).show()
                dropdownContainer.visibility = View.GONE
            },
            getString(R.string.overlay_more_about) to {
                appendLog("PHANTOM X INDIA v1.0 // UI HUD")
                dropdownContainer.visibility = View.GONE
            },
            getString(R.string.overlay_more_settings) to {
                dropdownContainer.visibility = View.GONE
                setTerminalVisible(false)
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(mainIntent)
            },
            getString(R.string.overlay_more_close) to {
                stopSelf()
            }
        )

        dropdownItems.forEachIndexed { index, (label, action) ->
            val itemView = TextView(this).apply {
                text = label
                typeface = monoTypeface
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(if (index == dropdownItems.lastIndex) Color.rgb(255, 75, 102) else Color.rgb(225, 225, 232))
                setPadding(dp(10f), dp(9f), dp(10f), dp(9f))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                background = createListHoverDrawable()
                setOnClickListener { action() }
            }
            dropdownContainer.addView(
                itemView,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }

        controlPane.addView(
            dropdownContainer,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4f)
            }
        )

        splitBody.addView(
            controlPane,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.95f)
        )

        contentColumn.addView(
            splitBody,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        rootContainer.addView(
            contentColumn,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        // Authentic CRT Scanline Overlay across the entire modal window
        val scanlineView = CrtScanlineOverlayView(this)
        rootContainer.addView(
            scanlineView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val params = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18f)
            y = dp(96f)
        }

        // Drag modal by header row
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        headerRow.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downX = event.rawX
                    downY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        params.x = (startX + dx.roundToInt()).coerceAtLeast(0)
                        params.y = (startY + dy.roundToInt()).coerceAtLeast(0)
                        windowManager?.updateViewLayout(rootContainer, params)
                    }
                    true
                }
                else -> false
            }
        }

        terminalRootView = rootContainer
        terminalParams = params
        terminalFpsText = liveFpsView
        logScrollView = consoleScroll
        logConsoleText = consoleText
        moreDropdownContainer = dropdownContainer
        bypassSwitch = bypassToggle
        injectSwitch = injectToggle

        try {
            windowManager?.addView(rootContainer, params)
        } catch (_: Exception) {
            // Keep FPS chip running if modal attach fails
        }
    }

    private fun createSwitchRow(label: String, switchView: CyberSwitchView): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.rgb(16, 16, 22))
                setStroke(dp(1f), Color.rgb(42, 42, 48))
                cornerRadius = dp(4f).toFloat()
            }
            setPadding(dp(8f), dp(2f), dp(4f), dp(2f))

            val labelTv = TextView(this@OverlayService).apply {
                text = label
                typeface = boldTypeface
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(Color.rgb(235, 235, 240))
                letterSpacing = 0.08f
                maxLines = 1
            }
            addView(labelTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(switchView)
        }
    }

    private fun createCyberButtonDrawable(
        fillColor: Int,
        strokeColor: Int,
        pressedColor: Int
    ): StateListDrawable {
        val normal = GradientDrawable().apply {
            setColor(fillColor)
            setStroke(dp(1f), strokeColor)
            cornerRadius = dp(4f).toFloat()
        }
        val pressed = GradientDrawable().apply {
            setColor(pressedColor)
            setStroke(dp(1.5f), strokeColor)
            cornerRadius = dp(4f).toFloat()
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private fun createListHoverDrawable(): StateListDrawable {
        val pressed = ColorDrawable(Color.argb(95, 255, 0, 51))
        val hovered = ColorDrawable(Color.argb(48, 255, 0, 51))
        val normal = ColorDrawable(Color.TRANSPARENT)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(android.R.attr.state_hovered), hovered)
            addState(intArrayOf(), normal)
        }
    }

    private fun toggleTerminalMenu() {
        setTerminalVisible(!isTerminalVisible)
    }

    private fun setTerminalVisible(visible: Boolean) {
        isTerminalVisible = visible
        terminalRootView?.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) {
            moreDropdownContainer?.visibility = View.GONE
        }
    }

    private fun appendLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = "[$timestamp] $message"
        mainHandler.post {
            if (!isRunning) return@post
            if (logLines.size >= MAX_LOG_LINES) {
                logLines.removeFirst()
            }
            logLines.addLast(line)
            logConsoleText?.text = logLines.joinToString("\n")
            logScrollView?.post {
                logScrollView?.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Real-time Read-Only SurfaceFlinger FPS Reader (Background IO Thread)
    // -----------------------------------------------------------------------
    private fun startSurfaceFlingerMonitor() {
        fpsReaderJob?.cancel()
        fpsReaderJob = serviceScope.launch {
            while (isActive && isRunning) {
                val measuredFps = readSurfaceFlingerFps()
                if (measuredFps != null) {
                    if (isUsingChoreographerFallback) {
                        isUsingChoreographerFallback = false
                        mainHandler.post {
                            Choreographer.getInstance().removeFrameCallback(choreographerCallback)
                        }
                        appendLog("FPS SOURCE: SurfaceFlinger latency")
                    }
                    val prev = currentFps.get()
                    val smoothed = if (prev <= 0) {
                        measuredFps
                    } else {
                        (prev * 0.45 + measuredFps * 0.55).roundToInt()
                    }
                    currentFps.set(smoothed.coerceIn(1, 240))
                } else {
                    if (!isUsingChoreographerFallback) {
                        isUsingChoreographerFallback = true
                        choreographerLastFrameNs = 0L
                        mainHandler.post {
                            if (isRunning) {
                                Choreographer.getInstance().postFrameCallback(choreographerCallback)
                            }
                        }
                        appendLog("FPS SOURCE: Display Choreographer fallback")
                    }
                    currentFps.set(choreographerEmaFps.roundToInt().coerceIn(1, 240))
                }
                delay(FPS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun readSurfaceFlingerFps(): Int? {
        val now = System.currentTimeMillis()
        if (cachedLayerName == null || now - lastLayerScanMs > 5_000L) {
            cachedLayerName = resolveActiveSurfaceLayer()
            lastLayerScanMs = now
        }

        val layer = cachedLayerName
        val latencyLines = if (layer != null) {
            executeReadOnlyCommand("dumpsys SurfaceFlinger --latency \"$layer\"")
        } else {
            executeReadOnlyCommand("dumpsys SurfaceFlinger --latency")
        }

        if (latencyLines.isEmpty()) {
            cachedLayerName = null
            return null
        }

        return parseSurfaceFlingerLatency(latencyLines)
    }

    private fun resolveActiveSurfaceLayer(): String? {
        val layers = executeReadOnlyCommand("dumpsys SurfaceFlinger --list")
        if (layers.isEmpty()) return null

        // Prefer active SurfaceView / BlastBufferQueue game layer, otherwise top SurfaceView/BufferLayer
        val prioritized = layers.lastOrNull { line ->
            line.contains("SurfaceView", ignoreCase = true) &&
                !line.contains("Background", ignoreCase = true)
        } ?: layers.lastOrNull { line ->
            line.contains("BlastBufferQueue", ignoreCase = true) ||
                line.contains("MainActivity", ignoreCase = true)
        }
        return prioritized?.trim()?.takeIf { it.isNotEmpty() }
    }

    internal fun parseSurfaceFlingerLatency(lines: List<String>): Int? {
        if (lines.size < 3) return null

        val refreshPeriodNs = lines.firstOrNull()?.trim()?.toLongOrNull() ?: return null
        if (refreshPeriodNs <= 0L) return null

        val presentationTimestamps = ArrayList<Long>(lines.size)
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val parts = line.split('\t', ' ')
            if (parts.size >= 3) {
                val actualPresentTime = parts[1].toLongOrNull() ?: continue
                if (actualPresentTime > 0L && actualPresentTime != Long.MAX_VALUE) {
                    presentationTimestamps.add(actualPresentTime)
                }
            }
        }

        if (presentationTimestamps.size < 2) return null

        // Measure only the most recent ~1 second window of frames for snappy, accurate FPS
        val latestTs = presentationTimestamps.last()
        val windowStartTs = latestTs - 1_000_000_000L
        val recentFrames = presentationTimestamps.filter { it >= windowStartTs }

        val targetList = if (recentFrames.size >= 2) recentFrames else presentationTimestamps.takeLast(8)
        var deltaSum = 0.0
        var deltaCount = 0
        for (idx in 1 until targetList.size) {
            val diff = targetList[idx] - targetList[idx - 1]
            if (diff in 1_000_000L..500_000_000L) {
                deltaSum += diff.toDouble()
                deltaCount++
            }
        }

        if (deltaCount == 0 || deltaSum <= 0.0) return null
        val avgFrameNs = deltaSum / deltaCount
        return (1_000_000_000.0 / avgFrameNs).roundToInt().coerceIn(1, 240)
    }

    private fun executeReadOnlyCommand(command: String): List<String> {
        // Attempt root-assisted SurfaceFlinger read first, then direct shell read fallback
        val directResult = runCommandLines(arrayOf("sh", "-c", command))
        if (directResult.size >= 3) return directResult
        return runCommandLines(arrayOf("su", "-c", command))
    }

    private fun runCommandLines(cmdArray: Array<String>): List<String> {
        var process: Process? = null
        return try {
            process = ProcessBuilder(*cmdArray)
                .redirectErrorStream(false)
                .start()
            val output = ArrayList<String>(140)
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line = reader.readLine()
                while (line != null && output.size < 200) {
                    output.add(line)
                    line = reader.readLine()
                }
            }
            process.waitFor()
            output
        } catch (_: Exception) {
            emptyList()
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        fpsReaderJob?.cancel()
        fpsReaderJob = null
        serviceScope.cancel()

        mainHandler.removeCallbacksAndMessages(null)
        try {
            Choreographer.getInstance().removeFrameCallback(choreographerCallback)
        } catch (_: Exception) {
        }

        try {
            fpsChipView?.let { windowManager?.removeViewImmediate(it) }
        } catch (_: Exception) {
        }
        try {
            terminalRootView?.let { windowManager?.removeViewImmediate(it) }
        } catch (_: Exception) {
        }

        fpsChipView = null
        fpsChipText = null
        fpsChipParams = null
        terminalRootView = null
        terminalParams = null
        terminalFpsText = null
        logScrollView = null
        logConsoleText = null
        moreDropdownContainer = null
        bypassSwitch = null
        injectSwitch = null
        windowManager = null

        super.onDestroy()
    }
}
