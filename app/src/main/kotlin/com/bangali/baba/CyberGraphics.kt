package com.bangali.baba

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CarbonPanel
import com.example.ui.theme.CrimsonCore
import com.example.ui.theme.CrimsonDark
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.MetallicGrey
import com.example.ui.theme.MetallicHighlight
import com.example.ui.theme.NeonBloodRed
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.VoidBlack

enum class DashboardTab {
    VAULT,
    WEAPON,
    PRESET,
    LAB
}

enum class LedStatus {
    ACTIVE_GREEN,
    WARNING_YELLOW,
    ERROR_RED
}

/**
 * Placeholder UI-to-Engine callback contract.
 * External engine modules implement this interface without coupling backend logic to UI classes.
 */
interface PhantomEngineCallbacks {
    fun onVerifyLicense(licenseKey: String, onResult: (success: Boolean, statusText: String) -> Unit)
    fun onLaunchSession()
    fun onBypassToggle(enabled: Boolean)
    fun onInjectClick(enabled: Boolean)
    fun onTabSelected(tab: DashboardTab)
    fun onTargetGameSelected(packageName: String)
}

object PhantomEngineBridge {
    @Volatile
    var callbacks: PhantomEngineCallbacks = DefaultPlaceholderCallbacks()

    private class DefaultPlaceholderCallbacks : PhantomEngineCallbacks {
        override fun onVerifyLicense(
            licenseKey: String,
            onResult: (success: Boolean, statusText: String) -> Unit
        ) {
            if (licenseKey.isBlank()) {
                onResult(false, "Failed: License key required")
            } else {
                onResult(true, "Success")
            }
        }

        override fun onLaunchSession() = Unit
        override fun onBypassToggle(enabled: Boolean) = Unit
        override fun onInjectClick(enabled: Boolean) = Unit
        override fun onTabSelected(tab: DashboardTab) = Unit
        override fun onTargetGameSelected(packageName: String) = Unit
    }
}

@Composable
fun CyberGridBackground(
    modifier: Modifier = Modifier,
    pulseGlow: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "cyber_bg_pulse")
    val glowAlpha by if (pulseGlow) {
        transition.animateFloat(
            initialValue = 0.14f,
            targetValue = 0.28f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bg_glow_alpha"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.18f) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidBlack)
            .drawWithCache {
                val gridStep = 28.dp.toPx()
                val accentLineColor = MetallicGrey.copy(alpha = 0.28f)
                val horizonColor = NeonBloodRed.copy(alpha = 0.22f)
                onDrawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                NeonBloodRed.copy(alpha = glowAlpha),
                                CrimsonDark.copy(alpha = 0.16f),
                                VoidBlack
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.24f),
                            radius = size.maxDimension * 0.72f
                        )
                    )

                    var x = 0f
                    while (x <= size.width) {
                        drawLine(
                            color = accentLineColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1f
                        )
                        x += gridStep
                    }

                    var y = 0f
                    while (y <= size.height) {
                        drawLine(
                            color = accentLineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                        y += gridStep
                    }

                    drawLine(
                        color = horizonColor,
                        start = Offset(0f, size.height * 0.18f),
                        end = Offset(size.width, size.height * 0.18f),
                        strokeWidth = 1.5f
                    )
                }
            }
    )
}

@Composable
fun AnimatedDragonEmblem(
    modifier: Modifier = Modifier,
    accentColor: Color = NeonBloodRed,
    animate: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "dragon_emblem")
    val pulse by if (animate) {
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dragon_pulse"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.85f) }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.34f * pulse),
                    CrimsonDark.copy(alpha = 0.16f * pulse),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = size.minDimension * 0.52f
            ),
            radius = size.minDimension * 0.52f,
            center = Offset(cx, cy)
        )

        drawCircle(
            color = MetallicGrey.copy(alpha = 0.7f),
            radius = size.minDimension * 0.44f,
            center = Offset(cx, cy),
            style = Stroke(width = size.minDimension * 0.018f)
        )

        // Outer head silhouette
        val dragonSilhouette = Path().apply {
            moveTo(w * 0.24f, h * 0.78f)
            lineTo(w * 0.18f, h * 0.62f)
            lineTo(w * 0.27f, h * 0.54f)
            lineTo(w * 0.16f, h * 0.44f)
            lineTo(w * 0.29f, h * 0.38f)
            lineTo(w * 0.23f, h * 0.16f) // rear horn
            lineTo(w * 0.41f, h * 0.28f)
            lineTo(w * 0.44f, h * 0.14f) // crown spike
            lineTo(w * 0.56f, h * 0.29f)
            lineTo(w * 0.72f, h * 0.35f) // brow
            lineTo(w * 0.84f, h * 0.46f) // snout tip
            lineTo(w * 0.77f, h * 0.54f)
            lineTo(w * 0.64f, h * 0.51f) // upper jaw teeth ridge
            lineTo(w * 0.57f, h * 0.56f)
            lineTo(w * 0.74f, h * 0.61f) // lower jaw
            lineTo(w * 0.68f, h * 0.70f)
            lineTo(w * 0.55f, h * 0.65f)
            lineTo(w * 0.49f, h * 0.84f) // neck armor base
            close()
        }

        drawPath(
            path = dragonSilhouette,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF23232A), Color(0xFF0E0E12), Color(0xFF1A070C)),
                start = Offset(w * 0.2f, h * 0.15f),
                end = Offset(w * 0.8f, h * 0.85f)
            )
        )

        drawPath(
            path = dragonSilhouette,
            color = accentColor.copy(alpha = 0.75f + 0.25f * pulse),
            style = Stroke(
                width = size.minDimension * 0.026f,
                join = StrokeJoin.Miter,
                cap = StrokeCap.Square
            )
        )

        // Armor plate fractures
        val plateLines = Path().apply {
            moveTo(w * 0.32f, h * 0.42f)
            lineTo(w * 0.51f, h * 0.44f)
            lineTo(w * 0.46f, h * 0.63f)
            lineTo(w * 0.34f, h * 0.70f)

            moveTo(w * 0.51f, h * 0.44f)
            lineTo(w * 0.68f, h * 0.42f)
        }
        drawPath(
            path = plateLines,
            color = MetallicHighlight,
            style = Stroke(width = size.minDimension * 0.014f, join = StrokeJoin.Bevel)
        )

        // Glowing dragon eye slit + horizontal cyber flare
        val eyePath = Path().apply {
            moveTo(w * 0.54f, h * 0.39f)
            lineTo(w * 0.67f, h * 0.41f)
            lineTo(w * 0.61f, h * 0.45f)
            close()
        }
        drawPath(path = eyePath, color = Color.White.copy(alpha = 0.95f))
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, accentColor, Color.White, accentColor, Color.Transparent)
            ),
            start = Offset(w * 0.44f, h * 0.415f),
            end = Offset(w * 0.88f, h * 0.415f),
            strokeWidth = size.minDimension * 0.024f * (0.75f + 0.25f * pulse),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun CyberChamferCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MetallicGrey,
    accentColor: Color = NeonBloodRed,
    cornerCut: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = CutCornerShape(topStart = cornerCut, bottomEnd = cornerCut)
    Box(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = accentColor.copy(alpha = 0.28f),
                spotColor = accentColor.copy(alpha = 0.38f)
            )
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(CarbonPanel, ObsidianSurface)
                )
            )
            .drawWithCache {
                val strokePx = 1.25.dp.toPx()
                val cutPx = cornerCut.toPx()
                val bracketLen = 10.dp.toPx()
                val outlinePath = Path().apply {
                    moveTo(cutPx, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height - cutPx)
                    lineTo(size.width - cutPx, size.height)
                    lineTo(0f, size.height)
                    lineTo(0f, cutPx)
                    close()
                }
                onDrawWithContent {
                    drawContent()
                    drawPath(
                        path = outlinePath,
                        color = borderColor,
                        style = Stroke(width = strokePx)
                    )
                    // Top-right red corner accent
                    drawLine(
                        color = accentColor,
                        start = Offset(size.width - bracketLen, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = strokePx * 1.8f
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, bracketLen),
                        strokeWidth = strokePx * 1.8f
                    )
                    // Bottom-left red corner accent
                    drawLine(
                        color = accentColor,
                        start = Offset(0f, size.height - bracketLen),
                        end = Offset(0f, size.height),
                        strokeWidth = strokePx * 1.8f
                    )
                    drawLine(
                        color = accentColor,
                        start = Offset(0f, size.height),
                        end = Offset(bracketLen, size.height),
                        strokeWidth = strokePx * 1.8f
                    )
                }
            }
            .padding(14.dp),
        content = content
    )
}

@Composable
fun LedStatusIndicator(
    status: LedStatus,
    modifier: Modifier = Modifier
) {
    val ledColor = when (status) {
        LedStatus.ACTIVE_GREEN -> MatrixGreen
        LedStatus.WARNING_YELLOW -> AmberWarning
        LedStatus.ERROR_RED -> NeonBloodRed
    }
    val transition = rememberInfiniteTransition(label = "led_pulse")
    val haloAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "led_halo"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val radius = size.minDimension * 0.28f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ledColor.copy(alpha = haloAlpha), Color.Transparent),
                center = center,
                radius = size.minDimension * 0.5f
            ),
            radius = size.minDimension * 0.5f,
            center = center
        )
        drawCircle(color = ledColor, radius = radius, center = center)
        drawCircle(
            color = Color.White.copy(alpha = 0.7f),
            radius = radius * 0.35f,
            center = Offset(center.x - radius * 0.22f, center.y - radius * 0.22f)
        )
    }
}

@Composable
fun TargetGameCanvasIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val r = size.minDimension * 0.36f
        val stroke = size.minDimension * 0.085f

        drawCircle(color = tint, radius = r, center = Offset(cx, cy), style = Stroke(stroke))
        drawCircle(color = tint, radius = r * 0.28f, center = Offset(cx, cy))
        drawLine(tint, Offset(cx, 0f), Offset(cx, cy - r * 0.55f), strokeWidth = stroke)
        drawLine(tint, Offset(cx, cy + r * 0.55f), Offset(cx, size.height), strokeWidth = stroke)
        drawLine(tint, Offset(0f, cy), Offset(cx - r * 0.55f, cy), strokeWidth = stroke)
        drawLine(tint, Offset(cx + r * 0.55f, cy), Offset(size.width, cy), strokeWidth = stroke)
    }
}

@Composable
fun DashboardTabCanvasIcon(
    tab: DashboardTab,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = size.minDimension * 0.085f

        when (tab) {
            DashboardTab.VAULT -> {
                val shield = Path().apply {
                    moveTo(w * 0.5f, h * 0.1f)
                    lineTo(w * 0.85f, h * 0.24f)
                    lineTo(w * 0.82f, h * 0.58f)
                    lineTo(w * 0.5f, h * 0.9f)
                    lineTo(w * 0.18f, h * 0.58f)
                    lineTo(w * 0.15f, h * 0.24f)
                    close()
                }
                drawPath(shield, color = tint, style = Stroke(stroke, join = StrokeJoin.Miter))
                drawCircle(color = tint, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.46f))
            }

            DashboardTab.WEAPON -> {
                val r = size.minDimension * 0.34f
                val cx = w * 0.5f
                val cy = h * 0.5f
                drawCircle(tint, radius = r, center = Offset(cx, cy), style = Stroke(stroke))
                drawLine(tint, Offset(cx - r * 1.35f, cy), Offset(cx - r * 0.45f, cy), stroke)
                drawLine(tint, Offset(cx + r * 0.45f, cy), Offset(cx + r * 1.35f, cy), stroke)
                drawLine(tint, Offset(cx, cy - r * 1.35f), Offset(cx, cy - r * 0.45f), stroke)
                drawLine(tint, Offset(cx, cy + r * 0.45f), Offset(cx, cy + r * 1.35f), stroke)
                drawCircle(tint, radius = r * 0.2f, center = Offset(cx, cy))
            }

            DashboardTab.PRESET -> {
                val x1 = w * 0.25f
                val x2 = w * 0.5f
                val x3 = w * 0.75f
                drawLine(tint, Offset(x1, h * 0.15f), Offset(x1, h * 0.85f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(x2, h * 0.15f), Offset(x2, h * 0.85f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(x3, h * 0.15f), Offset(x3, h * 0.85f), stroke, StrokeCap.Round)
                drawCircle(tint, radius = w * 0.09f, center = Offset(x1, h * 0.34f))
                drawCircle(tint, radius = w * 0.09f, center = Offset(x2, h * 0.64f))
                drawCircle(tint, radius = w * 0.09f, center = Offset(x3, h * 0.42f))
            }

            DashboardTab.LAB -> {
                val hex = Path().apply {
                    moveTo(w * 0.5f, h * 0.1f)
                    lineTo(w * 0.84f, h * 0.3f)
                    lineTo(w * 0.84f, h * 0.7f)
                    lineTo(w * 0.5f, h * 0.9f)
                    lineTo(w * 0.16f, h * 0.7f)
                    lineTo(w * 0.16f, h * 0.3f)
                    close()
                }
                drawPath(hex, color = tint, style = Stroke(stroke, join = StrokeJoin.Miter))
                drawLine(tint, Offset(w * 0.34f, h * 0.5f), Offset(w * 0.66f, h * 0.5f), stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.34f), Offset(w * 0.5f, h * 0.66f), stroke)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Hardware-Accelerated Custom Android Views / Drawables for OverlayService
// ---------------------------------------------------------------------------

class CrtScanlineOverlayView(context: Context) : View(context) {
    private val scanlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(36, 0, 0, 0)
        strokeWidth = 1.5f
    }
    private val edgeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(42, 255, 0, 51)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val stepPx = context.resources.displayMetrics.density * 3f

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        var y = 0f
        while (y < h) {
            canvas.drawLine(0f, y, w, y, scanlinePaint)
            y += stepPx
        }
        canvas.drawRect(2f, 2f, w - 2f, h - 2f, edgeGlowPaint)
    }
}

class CyberSwitchView(context: Context) : View(context) {
    var isChecked: Boolean = false
        private set

    var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    private var animProgress: Float = 0f
    private var animator: ValueAnimator? = null
    private val density = context.resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AndroidColor.rgb(34, 34, 40)
    }
    private val trackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
        color = AndroidColor.rgb(66, 66, 76)
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thumbGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val trackRect = RectF()

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            setChecked(!isChecked, animate = true)
            onCheckedChangeListener?.invoke(isChecked)
        }
    }

    fun setChecked(checked: Boolean, animate: Boolean = false) {
        if (isChecked == checked && !animate) return
        isChecked = checked
        animator?.cancel()
        val target = if (checked) 1f else 0f
        if (!animate) {
            animProgress = target
            invalidate()
        } else {
            animator = ValueAnimator.ofFloat(animProgress, target).apply {
                duration = 170L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    animProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredW = (52f * density).toInt()
        val desiredH = (48f * density).toInt()
        setMeasuredDimension(
            resolveSize(desiredW, widthMeasureSpec),
            resolveSize(desiredH, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val trackH = 22f * density
        val trackW = 44f * density
        val left = (w - trackW) / 2f
        val top = (h - trackH) / 2f
        trackRect.set(left, top, left + trackW, top + trackH)

        trackPaint.color = if (isChecked) AndroidColor.rgb(12, 34, 20) else AndroidColor.rgb(32, 32, 38)
        trackStrokePaint.color = if (isChecked) AndroidColor.rgb(0, 255, 102) else AndroidColor.rgb(74, 74, 82)
        val radius = trackH / 2f
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)
        canvas.drawRoundRect(trackRect, radius, radius, trackStrokePaint)

        val thumbRadius = 8f * density
        val startX = left + thumbRadius + 3f * density
        val endX = left + trackW - thumbRadius - 3f * density
        val cx = startX + (endX - startX) * animProgress
        val cy = h / 2f

        val thumbColor = if (isChecked) {
            AndroidColor.rgb(0, 255, 102)
        } else {
            AndroidColor.rgb(255, 0, 51)
        }
        thumbPaint.color = thumbColor
        thumbGlowPaint.color = AndroidColor.argb(95, AndroidColor.red(thumbColor), AndroidColor.green(thumbColor), AndroidColor.blue(thumbColor))
        canvas.drawCircle(cx, cy, thumbRadius + 2f * density, thumbGlowPaint)
        canvas.drawCircle(cx, cy, thumbRadius, thumbPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}

enum class HeaderGlyphKind {
    TERMINAL_PROMPT,
    MINIMIZE,
    CLOSE
}

class CyberHeaderGlyphView(
    context: Context,
    private val kind: HeaderGlyphKind,
    private val glyphColor: Int
) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = glyphColor
        style = Paint.Style.STROKE
        strokeWidth = 2.1f * density
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.MITER
    }

    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val p = w * 0.28f
        when (kind) {
            HeaderGlyphKind.MINIMIZE -> {
                canvas.drawLine(p, h * 0.52f, w - p, h * 0.52f, paint)
            }
            HeaderGlyphKind.CLOSE -> {
                canvas.drawLine(p, p, w - p, h - p, paint)
                canvas.drawLine(w - p, p, p, h - p, paint)
            }
            HeaderGlyphKind.TERMINAL_PROMPT -> {
                canvas.drawLine(w * 0.16f, h * 0.28f, w * 0.45f, h * 0.5f, paint)
                canvas.drawLine(w * 0.45f, h * 0.5f, w * 0.16f, h * 0.72f, paint)
                canvas.drawLine(w * 0.52f, h * 0.72f, w * 0.84f, h * 0.72f, paint)
            }
        }
    }
}

class CyberChipDrawable(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(232, 8, 8, 12)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(255, 0, 51)
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }
    private val rect = RectF()

    override fun draw(canvas: AndroidCanvas) {
        val inset = 1.5f * density
        rect.set(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - inset
        )
        val r = rect.height() / 2f
        canvas.drawRoundRect(rect, r, r, fillPaint)
        canvas.drawRoundRect(rect, r, r, borderPaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        borderPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        borderPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class CyberPanelDrawable(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val metallicBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(42, 42, 48)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val redAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(255, 0, 51)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val path = AndroidPath()

    override fun draw(canvas: AndroidCanvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val inset = 1.5f * density
        val cut = 10f * density

        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            AndroidColor.argb(244, 14, 14, 19),
            AndroidColor.argb(246, 5, 5, 8),
            Shader.TileMode.CLAMP
        )

        path.reset()
        path.moveTo(inset + cut, inset)
        path.lineTo(w - inset, inset)
        path.lineTo(w - inset, h - inset - cut)
        path.lineTo(w - inset - cut, h - inset)
        path.lineTo(inset, h - inset)
        path.lineTo(inset, inset + cut)
        path.close()

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, metallicBorderPaint)

        val bracket = 14f * density
        canvas.drawLine(w - inset - bracket, inset, w - inset, inset, redAccentPaint)
        canvas.drawLine(w - inset, inset, w - inset, inset + bracket, redAccentPaint)
        canvas.drawLine(inset, h - inset - bracket, inset, h - inset, redAccentPaint)
        canvas.drawLine(inset, h - inset, inset + bracket, h - inset, redAccentPaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        metallicBorderPaint.alpha = alpha
        redAccentPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        metallicBorderPaint.colorFilter = colorFilter
        redAccentPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
