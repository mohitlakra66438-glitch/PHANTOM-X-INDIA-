package com.bangali.baba

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonDark
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MatrixGreen
import com.example.ui.theme.MetallicGrey
import com.example.ui.theme.NeonBloodRed
import com.example.ui.theme.PhantomTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class KeyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhantomTheme {
                KeyVerificationScreen(
                    onVerificationSuccess = { licenseKey ->
                        val intent = Intent(this, MainActivity::class.java).apply {
                            putExtra(EXTRA_LICENSE_KEY, licenseKey)
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_LICENSE_KEY = "extra_license_key"
    }
}

private enum class VerifyStage {
    AWAITING,
    VERIFYING,
    SUCCESS,
    FAILED
}

@Composable
fun KeyVerificationScreen(
    onVerificationSuccess: (String) -> Unit
) {
    var licenseInput by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf(VerifyStage.AWAITING) }
    var statusText by remember { mutableStateOf("Awaiting Connection...") }
    var isInputFocused by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val dragonDescription = stringResource(R.string.cd_dragon_logo)
    val brandTitle = stringResource(R.string.brand_title)
    val brandSubtitle = stringResource(R.string.brand_subtitle)
    val hintEnterLicense = stringResource(R.string.hint_enter_license)
    val verifyButtonLabel = stringResource(R.string.btn_verify_access)
    val verifyingLabel = stringResource(R.string.status_verifying)

    fun triggerVerify() {
        if (stage == VerifyStage.VERIFYING) return
        stage = VerifyStage.VERIFYING
        statusText = verifyingLabel

        PhantomEngineBridge.callbacks.onVerifyLicense(licenseInput.trim()) { success, message ->
            coroutineScope.launch {
                delay(280L)
                if (success) {
                    stage = VerifyStage.SUCCESS
                    statusText = message
                    delay(320L)
                    onVerificationSuccess(licenseInput.trim())
                } else {
                    stage = VerifyStage.FAILED
                    statusText = message
                }
            }
        }
    }

    val underlineColor by animateColorAsState(
        targetValue = when {
            stage == VerifyStage.FAILED -> NeonBloodRed
            isInputFocused -> NeonBloodRed
            else -> MetallicGrey
        },
        animationSpec = tween(durationMillis = 220),
        label = "underline_color"
    )

    val statusColor by animateColorAsState(
        targetValue = when (stage) {
            VerifyStage.AWAITING -> TextSecondary
            VerifyStage.VERIFYING -> AmberWarning
            VerifyStage.SUCCESS -> MatrixGreen
            VerifyStage.FAILED -> NeonBloodRed
        },
        animationSpec = tween(durationMillis = 220),
        label = "status_color"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        CyberGridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CyberChamferCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp),
                borderColor = MetallicGrey,
                accentColor = NeonBloodRed,
                cornerCut = 14.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NODE // IN-01",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        LedStatusIndicator(
                            status = when (stage) {
                                VerifyStage.SUCCESS -> LedStatus.ACTIVE_GREEN
                                VerifyStage.FAILED -> LedStatus.ERROR_RED
                                VerifyStage.VERIFYING -> LedStatus.WARNING_YELLOW
                                VerifyStage.AWAITING -> LedStatus.ERROR_RED
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AnimatedDragonEmblem(
                        modifier = Modifier
                            .size(116.dp)
                            .semantics { contentDescription = dragonDescription },
                        accentColor = if (stage == VerifyStage.SUCCESS) MatrixGreen else NeonBloodRed,
                        animate = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = brandTitle,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        ),
                        color = NeonBloodRed,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = brandSubtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Sleek underline-style license input with green cursor
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BasicTextField(
                            value = licenseInput,
                            onValueChange = {
                                licenseInput = it
                                if (stage == VerifyStage.FAILED) {
                                    stage = VerifyStage.AWAITING
                                    statusText = "Awaiting Connection..."
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = JetBrainsMonoFamily,
                                color = TextPrimary,
                                letterSpacing = 1.5.sp
                            ),
                            cursorBrush = SolidColor(MatrixGreen),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { triggerVerify() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("license_input")
                                .onFocusChanged { isInputFocused = it.isFocused }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (licenseInput.isEmpty()) {
                                        Text(
                                            text = hintEnterLicense,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontFamily = JetBrainsMonoFamily,
                                                letterSpacing = 1.5.sp
                                            ),
                                            color = TextMuted
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(underlineColor)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val btnShape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(btnShape)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(CrimsonDark, Color(0xFF5A0014), CrimsonDark)
                                )
                            )
                            .border(width = 1.5.dp, color = NeonBloodRed, shape = btnShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = ripple(color = NeonBloodRed),
                                enabled = stage != VerifyStage.VERIFYING,
                                onClick = { triggerVerify() }
                            )
                            .testTag("verify_access_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (stage == VerifyStage.VERIFYING) "AUTHENTICATING..." else verifyButtonLabel,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFamily,
                                letterSpacing = 2.sp
                            ),
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = JetBrainsMonoFamily
                        ),
                        color = statusColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("verify_status_text")
                    )
                }
            }
        }
    }
}
