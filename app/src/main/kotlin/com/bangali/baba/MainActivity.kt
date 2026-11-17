package com.bangali.baba

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CarbonPanel
import com.example.ui.theme.CrimsonDark
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.MetallicGrey
import com.example.ui.theme.NeonBloodRed
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.PhantomTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhantomTheme {
                DashboardScreen(
                    onRequestStartOverlay = {
                        val serviceIntent = Intent(this, OverlayService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                    },
                    onRequestStopOverlay = {
                        stopService(Intent(this, OverlayService::class.java))
                    }
                )
            }
        }
    }
}

private val TARGET_PACKAGES = listOf(
    "com.pubg.imobile",
    "com.dts.freefireth",
    "com.activision.callofduty.shooter",
    "com.tencent.ig"
)

@Composable
fun DashboardScreen(
    onRequestStartOverlay: () -> Unit,
    onRequestStopOverlay: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedTab by remember { mutableStateOf(DashboardTab.VAULT) }
    var targetPackageIndex by remember { mutableIntStateOf(0) }
    var isSessionReady by remember { mutableStateOf(true) }
    var isOverlayRunning by remember { mutableStateOf(OverlayService.isRunning) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var remainingSeconds by remember { mutableLongStateOf(29L * 86400L + 23L * 3600L + 58L * 60L + 42L) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    val targetPackage = TARGET_PACKAGES[targetPackageIndex]

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed with overlay launch even if user declines post notifications on API 33+
        if (Settings.canDrawOverlays(context)) {
            onRequestStartOverlay()
            isOverlayRunning = true
            statusBanner = "Overlay HUD started"
        }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        if (hasOverlayPermission) {
            onRequestStartOverlay()
            isOverlayRunning = true
            statusBanner = "Overlay HUD started"
        } else {
            statusBanner = "Overlay permission required for floating HUD"
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                isOverlayRunning = OverlayService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            if (remainingSeconds > 0L) remainingSeconds--
            isOverlayRunning = OverlayService.isRunning
        }
    }

    val formattedTimer = remember(remainingSeconds) {
        val days = remainingSeconds / 86400L
        val hours = (remainingSeconds % 86400L) / 3600L
        val mins = (remainingSeconds % 3600L) / 60L
        val secs = remainingSeconds % 60L
        String.format("%02dD : %02dH : %02dM : %02dS", days, hours, mins, secs)
    }

    val deviceModel = remember {
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
        if (model.length > 14) model.take(14) else model
    }

    fun launchSessionAction() {
        if (!isSessionReady) return
        if (isOverlayRunning) {
            onRequestStopOverlay()
            isOverlayRunning = false
            statusBanner = "Session terminated"
            return
        }

        PhantomEngineBridge.callbacks.onLaunchSession()

        if (!Settings.canDrawOverlays(context)) {
            statusBanner = "Grant Display Over Other Apps permission"
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlaySettingsLauncher.launch(intent)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        onRequestStartOverlay()
        isOverlayRunning = true
        statusBanner = "Floating FPS HUD active"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CyberGridBackground()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                DashboardTopBar(
                    deviceModel = deviceModel,
                    overlayAuthorized = hasOverlayPermission
                )
            },
            bottomBar = {
                DashboardFooterNav(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        selectedTab = tab
                        PhantomEngineBridge.callbacks.onTabSelected(tab)
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 2x2 Status Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatusGridCard(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("card_root_status"),
                            title = stringResource(R.string.label_root_status),
                            value = if (hasOverlayPermission) "HUD READY" else "PERM REQ",
                            subValue = "API ${Build.VERSION.SDK_INT} // NODE",
                            ledStatus = if (hasOverlayPermission) LedStatus.ACTIVE_GREEN else LedStatus.WARNING_YELLOW
                        )

                        StatusGridCard(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("card_payload_status"),
                            title = stringResource(R.string.label_payload_status),
                            value = if (isOverlayRunning) "ACTIVE" else "STANDBY",
                            subValue = if (isOverlayRunning) "HUD STREAMING" else "ENGINE HOOK IDLE",
                            ledStatus = if (isOverlayRunning) LedStatus.ACTIVE_GREEN else LedStatus.ERROR_RED
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CyberChamferCard(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("card_license_timer"),
                            borderColor = MetallicGrey,
                            accentColor = NeonBloodRed
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = stringResource(R.string.label_license_timer),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formattedTimer,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MatrixGreen,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "TIER // PHANTOM PRO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    maxLines = 1
                                )
                            }
                        }

                        CyberChamferCard(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(color = NeonBloodRed)
                                ) {
                                    targetPackageIndex = (targetPackageIndex + 1) % TARGET_PACKAGES.size
                                    PhantomEngineBridge.callbacks.onTargetGameSelected(TARGET_PACKAGES[targetPackageIndex])
                                }
                                .testTag("card_target_game"),
                            borderColor = MetallicGrey,
                            accentColor = NeonBloodRed
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.label_target_game),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = 1
                                    )
                                    TargetGameCanvasIcon(
                                        tint = NeonBloodRed,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .semantics {
                                                contentDescription = "Target package selector"
                                            }
                                    )
                                }
                                Text(
                                    text = targetPackage,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "TAP TO CYCLE TARGET",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Active Tab Telemetry Panel
                    ActiveTabDetailPanel(
                        tab = selectedTab,
                        isSessionReady = isSessionReady,
                        onToggleReady = { isSessionReady = it }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Action Zone: Pulsating Launch Session Button
                    LaunchSessionButton(
                        isReady = isSessionReady,
                        isOverlayRunning = isOverlayRunning,
                        onClick = { launchSessionAction() }
                    )

                    if (statusBanner != null) {
                        Text(
                            text = statusBanner.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isOverlayRunning) MatrixGreen else AmberWarning,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTopBar(
    deviceModel: String,
    overlayAuthorized: Boolean
) {
    val dragonCd = stringResource(R.string.cd_dragon_logo)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoidBlack.copy(alpha = 0.92f))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedDragonEmblem(
                modifier = Modifier
                    .size(38.dp)
                    .semantics { contentDescription = dragonCd },
                accentColor = NeonBloodRed,
                animate = false
            )
            Column {
                Text(
                    text = stringResource(R.string.brand_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = NeonBloodRed
                )
                Text(
                    text = "INDIA // DASHBOARD",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }

        val badgeShape = CutCornerShape(topStart = 6.dp, bottomEnd = 6.dp)
        Row(
            modifier = Modifier
                .clip(badgeShape)
                .background(CarbonPanel)
                .border(1.dp, MetallicGrey, badgeShape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LedStatusIndicator(
                status = if (overlayAuthorized) LedStatus.ACTIVE_GREEN else LedStatus.WARNING_YELLOW,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = "$deviceModel | ${if (overlayAuthorized) "HUD OK" else "HUD LOCK"}",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StatusGridCard(
    title: String,
    value: String,
    subValue: String,
    ledStatus: LedStatus,
    modifier: Modifier = Modifier
) {
    CyberChamferCard(
        modifier = modifier,
        borderColor = MetallicGrey,
        accentColor = NeonBloodRed
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LedStatusIndicator(
                    status = ledStatus,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = when (ledStatus) {
                    LedStatus.ACTIVE_GREEN -> MatrixGreen
                    LedStatus.WARNING_YELLOW -> AmberWarning
                    LedStatus.ERROR_RED -> NeonBloodRed
                },
                maxLines = 1
            )
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActiveTabDetailPanel(
    tab: DashboardTab,
    isSessionReady: Boolean,
    onToggleReady: (Boolean) -> Unit
) {
    val (moduleCode, moduleTitle, moduleDescription) = when (tab) {
        DashboardTab.VAULT -> Triple(
            "MOD-01 // VAULT",
            "ENCRYPTED PROFILE STORAGE",
            "UI hook ready for key vault bindings and session profiles."
        )
        DashboardTab.WEAPON -> Triple(
            "MOD-02 // WEAPON",
            "BALLISTICS HUD SLOT",
            "Reticle telemetry and weapon profile slots routed to overlay callbacks."
        )
        DashboardTab.PRESET -> Triple(
            "MOD-03 // PRESET",
            "DISPLAY & HUD PRESETS",
            "SurfaceFlinger 500ms frame sampling with CRT scanline overlay enabled."
        )
        DashboardTab.LAB -> Triple(
            "MOD-04 // LAB",
            "SYSTEM TELEMETRY LAB",
            "Toggle standby state below to preview disabled vs armed launcher state."
        )
    }

    CyberChamferCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_tab_panel"),
        borderColor = MetallicGrey,
        accentColor = NeonBloodRed
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = moduleCode,
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonBloodRed
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSessionReady) Color(0xFF0A2414) else CarbonPanel)
                        .border(
                            1.dp,
                            if (isSessionReady) MatrixGreen else MetallicGrey,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onToggleReady(!isSessionReady) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("toggle_arm_state")
                ) {
                    Text(
                        text = if (isSessionReady) "ARMED" else "LOCKED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSessionReady) MatrixGreen else TextMuted
                    )
                }
            }

            Text(
                text = moduleTitle,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = moduleDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun LaunchSessionButton(
    isReady: Boolean,
    isOverlayRunning: Boolean,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "launch_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "launch_glow"
    )

    val shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
    val buttonText = when {
        !isReady -> "SESSION LOCKED"
        isOverlayRunning -> stringResource(R.string.btn_stop_overlay)
        else -> stringResource(R.string.btn_launch_session)
    }

    val borderColor = when {
        !isReady -> MetallicGrey
        isOverlayRunning -> MatrixGreen
        else -> NeonBloodRed
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .drawBehind {
                if (isReady) {
                    val glowColor = if (isOverlayRunning) MatrixGreen else NeonBloodRed
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.34f * pulse),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            radius = size.width * 0.55f
                        ),
                        radius = size.width * 0.55f,
                        center = Offset(size.width * 0.5f, size.height * 0.5f)
                    )
                }
            }
            .clip(shape)
            .background(
                brush = if (isReady) {
                    Brush.horizontalGradient(
                        colors = if (isOverlayRunning) {
                            listOf(Color(0xFF072514), Color(0xFF0D3B20), Color(0xFF072514))
                        } else {
                            listOf(CrimsonDark, Color(0xFF6B0018), CrimsonDark)
                        }
                    )
                } else {
                    Brush.horizontalGradient(listOf(CarbonPanel, ObsidianSurface))
                }
            )
            .border(width = 1.75.dp, color = borderColor, shape = shape)
            .clickable(
                enabled = isReady,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = if (isOverlayRunning) MatrixGreen else NeonBloodRed),
                onClick = onClick
            )
            .testTag("launch_session_button"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LedStatusIndicator(
                status = when {
                    !isReady -> LedStatus.WARNING_YELLOW
                    isOverlayRunning -> LedStatus.ACTIVE_GREEN
                    else -> LedStatus.ERROR_RED
                },
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = buttonText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.5.sp
                ),
                color = if (isReady) TextPrimary else TextMuted
            )
        }
    }
}

@Composable
private fun DashboardFooterNav(
    selectedTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit
) {
    val tabItems = listOf(
        DashboardTab.VAULT to stringResource(R.string.tab_vault),
        DashboardTab.WEAPON to stringResource(R.string.tab_weapon),
        DashboardTab.PRESET to stringResource(R.string.tab_preset),
        DashboardTab.LAB to stringResource(R.string.tab_lab)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface.copy(alpha = 0.96f))
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MetallicGrey)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabItems.forEach { (tab, label) ->
                val isSelected = selectedTab == tab
                val tint by animateColorAsState(
                    targetValue = if (isSelected) NeonBloodRed else TextMuted,
                    animationSpec = tween(180),
                    label = "tab_tint"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = NeonBloodRed)
                        ) { onTabSelected(tab) }
                        .padding(vertical = 8.dp)
                        .testTag("footer_tab_${tab.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(2.dp)
                            .background(if (isSelected) NeonBloodRed else Color.Transparent)
                    )
                    DashboardTabCanvasIcon(
                        tab = tab,
                        tint = tint,
                        modifier = Modifier
                            .size(20.dp)
                            .semantics { contentDescription = label }
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = tint,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
