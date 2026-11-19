package com.v2ray.ang.ui.main

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.enums.Language
import com.v2ray.ang.extension.concatUrl
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.HotaruCommunityCounter
import com.v2ray.ang.handler.HotaruDirectDpiManager
import com.v2ray.ang.handler.HotaruSmartEngine
import com.v2ray.ang.handler.HotaruWarpManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.UpdateCheckerManager
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val VoidGalaxyBg = Color(0xFF0D0E15)
private val DeepCyberSurface = Color(0xFF151828)
private val NeonCyan = Color(0xFF00E5FF)
private val HotaruMint = Color(0xFF00F5D4)
private val CyberMagenta = Color(0xFFFF2A85)
private val ConnectingViolet = Color(0xFFA855F7)
private val WarmAmber = Color(0xFFFFB703)
private val MutedText = Color(0xFF8B93B3)
private val TelegramBlue = Color(0xFF38BDF8)

private data class FireflySeed(
    val xFraction: Float,
    val startYFraction: Float,
    val radiusDp: Float,
    val speedFactor: Float,
    val phase: Float,
    val color: Color,
)

private val DefaultFireflies = listOf(
    FireflySeed(0.12f, 0.85f, 3.5f, 0.7f, 0.0f, HotaruMint),
    FireflySeed(0.24f, 0.62f, 2.2f, 1.1f, 1.1f, NeonCyan),
    FireflySeed(0.38f, 0.90f, 4.0f, 0.6f, 2.3f, HotaruMint),
    FireflySeed(0.52f, 0.78f, 2.6f, 0.9f, 0.7f, CyberMagenta),
    FireflySeed(0.68f, 0.88f, 3.2f, 0.8f, 3.1f, NeonCyan),
    FireflySeed(0.84f, 0.72f, 2.8f, 1.0f, 1.8f, HotaruMint),
    FireflySeed(0.91f, 0.50f, 2.4f, 0.75f, 4.2f, CyberMagenta),
    FireflySeed(0.18f, 0.35f, 2.5f, 0.95f, 2.8f, NeonCyan),
    FireflySeed(0.76f, 0.28f, 3.0f, 0.65f, 5.0f, HotaruMint),
    FireflySeed(0.45f, 0.22f, 2.0f, 1.2f, 3.6f, NeonCyan),
    FireflySeed(0.60f, 0.42f, 3.4f, 0.55f, 1.4f, HotaruMint),
    FireflySeed(0.08f, 0.58f, 2.3f, 0.85f, 4.7f, CyberMagenta),
)

@Composable
fun MinimalVpnHome(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    @Suppress("UNUSED_PARAMETER") onNavigate: (MainDestination) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()

    val isRunning = uiState.isRunning
    val isOptimizing = uiState.isOptimizing
    val livePing = uiState.livePing

    val hotaruServers by mainViewModel.serversForGroup(AppConfig.HOTARU_SUB_ID).collectAsStateWithLifecycle()
    val activeServersCount = remember(hotaruServers, uiState.selectedGuid) {
        val mmkvCount = MmkvManager.decodeServerList(AppConfig.HOTARU_SUB_ID).size
        if (mmkvCount > 0) mmkvCount else if (hotaruServers.isNotEmpty()) hotaruServers.size else MmkvManager.decodeAllServerList().size
    }

    val connectedUsersCount by HotaruCommunityCounter.countFlow.collectAsStateWithLifecycle()

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            HotaruCommunityCounter.syncUiState(false)
            delay(2500L)
        }
        while (true) {
            HotaruCommunityCounter.syncUiState(isRunning)
            delay(30_000L)
        }
    }

    val context = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        HotaruDirectDpiManager.ensureDirectDpiProfile()
        HotaruWarpManager.ensureDedicatedWarpProfile()
        val imported = HotaruSmartEngine.refreshSubscriptionSilently(context, force = false)
        if (imported > 0) {
            mainViewModel.reloadServerList()
        }
    }

    var isConnectingPending by remember { mutableStateOf(false) }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            isConnectingPending = false
        }
    }
    LaunchedEffect(isOptimizing, isConnectingPending) {
        if (isOptimizing) {
            isConnectingPending = true
        } else if (isConnectingPending) {
            delay(5500L)
            isConnectingPending = false
        }
    }

    val isBusy = !isRunning && (isOptimizing || isConnectingPending)

    var connectionDurationSeconds by remember { mutableLongStateOf(0L) }
    var sessionDownBytes by remember { mutableLongStateOf(0L) }
    var sessionUpBytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isRunning) {
        if (isRunning) {
            val startTime = System.currentTimeMillis()
            val myUid = android.os.Process.myUid()
            val initRx = android.net.TrafficStats.getUidRxBytes(myUid).takeIf { it != android.net.TrafficStats.UNSUPPORTED.toLong() } ?: 0L
            val initTx = android.net.TrafficStats.getUidTxBytes(myUid).takeIf { it != android.net.TrafficStats.UNSUPPORTED.toLong() } ?: 0L
            while (true) {
                connectionDurationSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime)
                val curRx = android.net.TrafficStats.getUidRxBytes(myUid).takeIf { it != android.net.TrafficStats.UNSUPPORTED.toLong() } ?: 0L
                val curTx = android.net.TrafficStats.getUidTxBytes(myUid).takeIf { it != android.net.TrafficStats.UNSUPPORTED.toLong() } ?: 0L
                sessionDownBytes = maxOf(0L, curRx - initRx)
                sessionUpBytes = maxOf(0L, curTx - initTx)
                delay(1000L)
            }
        } else {
            connectionDurationSeconds = 0L
            sessionDownBytes = 0L
            sessionUpBytes = 0L
        }
    }

    val config = LocalConfiguration.current
    val resolvedLang = remember(config) {
        val appLocale = AppCompatDelegate.getApplicationLocales().get(0)?.language
        val mmkvLang = MmkvManager.decodeSettingsString(AppConfig.PREF_LANGUAGE)
        val sysLang = config.locales.get(0)?.language
        when {
            appLocale == "fa" -> "fa"
            appLocale == "en" -> "en"
            mmkvLang == Language.PERSIAN.code -> "fa"
            mmkvLang == Language.ENGLISH.code -> "en"
            sysLang == "fa" -> "fa"
            else -> "en"
        }
    }
    val isPersian = resolvedLang == "fa"

    val networkSummary = remember(isRunning, isOptimizing, isPersian, uiState.selectedGuid) {
        HotaruSmartEngine.getLastNetworkProfileSummary(isPersian)
    }

    val onToggleConnection = remember {
        { onAction(MainAction.ToggleService) }
    }
    val onToggleLanguage = remember(isPersian) {
        {
            val targetCode = if (isPersian) Language.ENGLISH.code else Language.PERSIAN.code
            AppLocaleManager.setApplicationLanguage(targetCode)
        }
    }
    val onOpenTelegram = remember(onAction) {
        { onAction(MainAction.OpenSubscriptionLink(AppConfig.TG_CHANNEL_URL)) }
    }
    val onOpenUrl = remember(onAction) {
        { url: String -> onAction(MainAction.OpenSubscriptionLink(url)) }
    }
    val repoUrl = remember {
        AppConfig.APP_URL.ifBlank { "https://github.com/OneForAllHub/HotaruNet" }
    }

    var showLanguagePrompt by remember {
        mutableStateOf(!MmkvManager.decodeSettingsBool(AppConfig.PREF_FIRST_RUN_LANGUAGE_CHOSEN, false))
    }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<CheckUpdateResult?>(null) }
    var updateCheckCompleted by rememberSaveable { mutableStateOf(false) }
    var showFirstConnectDialog by rememberSaveable { mutableStateOf(false) }

    // Silent launch release check: only surfaces popup if a newer GitHub Release exists.
    LaunchedEffect(Unit) {
        val result = UpdateCheckerManager.checkForUpdateSilently()
        if (result.checkedOk) {
            updateCheckCompleted = true
        }
        if (result.hasUpdate) {
            availableUpdate = result
        }
    }

    // Retry silently once after VPN connects if pre-VPN direct check could not reach GitHub.
    LaunchedEffect(isRunning) {
        if (isRunning && !updateCheckCompleted && availableUpdate == null) {
            delay(1500L)
            val result = UpdateCheckerManager.checkForUpdateSilently()
            if (result.checkedOk) {
                updateCheckCompleted = true
            }
            if (result.hasUpdate) {
                availableUpdate = result
            }
        }
    }

    // One-time post-first-connect welcome & support dialog.
    LaunchedEffect(isRunning, showLanguagePrompt, availableUpdate) {
        if (isRunning &&
            !showLanguagePrompt &&
            availableUpdate == null &&
            !MmkvManager.decodeSettingsBool(AppConfig.PREF_FIRST_CONNECT_GUIDE_SHOWN, false)
        ) {
            MmkvManager.encodeSettings(AppConfig.PREF_FIRST_CONNECT_GUIDE_SHOWN, true)
            delay(750L)
            showFirstConnectDialog = true
        }
    }

    if (showLanguagePrompt) {
        FirstRunLanguageDialog(
            onConfirmLanguage = { chosenLang ->
                MmkvManager.encodeSettings(AppConfig.PREF_FIRST_RUN_LANGUAGE_CHOSEN, true)
                AppLocaleManager.setApplicationLanguage(chosenLang)
                showLanguagePrompt = false
            }
        )
    } else if (availableUpdate != null) {
        val update = availableUpdate!!
        val releasesUrl = update.releasePageUrl ?: repoUrl.concatUrl("releases/latest")
        HotaruUpdateDialog(
            isPersian = isPersian,
            currentVersion = BuildConfig.VERSION_NAME,
            latestVersion = update.latestVersion.orEmpty(),
            releaseNotes = update.releaseNotes.orEmpty(),
            onToggleLanguage = onToggleLanguage,
            onDownloadClick = {
                availableUpdate = null
                onOpenUrl(releasesUrl)
            },
            onDismiss = { availableUpdate = null },
        )
    } else if (showFirstConnectDialog) {
        HotaruFirstConnectDialog(
            isPersian = isPersian,
            onToggleLanguage = onToggleLanguage,
            onOpenGitHub = { onOpenUrl(repoUrl) },
            onOpenTelegram = onOpenTelegram,
            onDismiss = { showFirstConnectDialog = false },
        )
    }

    if (showAboutDialog) {
        HotaruAboutDialog(
            isPersian = isPersian,
            appVersion = BuildConfig.VERSION_NAME,
            onToggleLanguage = onToggleLanguage,
            onOpenGitHub = { onOpenUrl(repoUrl) },
            onOpenTelegram = onOpenTelegram,
            onDismiss = { showAboutDialog = false },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoidGalaxyBg)
    ) {
        CyberAnimeBackdrop(
            isRunning = isRunning,
            isConnecting = isBusy,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HotaruTopBar(
                isPersian = isPersian,
                onToggleLanguage = onToggleLanguage,
                onOpenTelegram = onOpenTelegram,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val statusLabel = when {
                    isRunning -> if (isPersian) "متصل" else "CONNECTED"
                    isOptimizing -> if (isPersian) "در حال انتخاب خودکار مسیر..." else "OPTIMIZING ROUTE..."
                    isBusy -> if (isPersian) "در حال اتصال..." else "CONNECTING..."
                    else -> if (isPersian) "آماده اتصال هوشمند" else "SMART AUTO-PILOT READY"
                }
                val statusColor by animateColorAsState(
                    targetValue = when {
                        isRunning -> HotaruMint
                        isBusy -> ConnectingViolet
                        else -> MutedText
                    },
                    animationSpec = tween(400),
                    label = "statusColor",
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = if (isPersian) 0.sp else 1.2.sp,
                            )
                        )
                    }
                }

                FireflyCommunityBadge(
                    isPersian = isPersian,
                    isRunning = isRunning,
                    connectedUsersCount = connectedUsersCount,
                )

                NeonConnectButton(
                    isRunning = isRunning,
                    isOptimizing = isOptimizing,
                    isConnecting = isBusy,
                    isPersian = isPersian,
                    onClick = onToggleConnection,
                )

                if (isRunning) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = formatDuration(connectionDurationSeconds),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.5.sp,
                                color = Color.White.copy(alpha = 0.92f),
                            )
                        )

                        // Real-time Session Traffic Counter
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = DeepCyberSurface.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "↓",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = HotaruMint,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                    )
                                    Text(
                                        text = formatTraffic(sessionDownBytes),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = Color.White.copy(alpha = 0.92f),
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(MutedText.copy(alpha = 0.6f))
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "↑",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = CyberMagenta,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                    )
                                    Text(
                                        text = formatTraffic(sessionUpBytes),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = Color.White.copy(alpha = 0.92f),
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (isOptimizing) {
                            if (isPersian) "تست موازی پایداری و سرعت..." else "Testing & selecting fastest tunnel..."
                        } else if (isPersian) {
                            "برای انتخاب خودکار سریع‌ترین سرور لمس کنید"
                        } else {
                            "Tap once for autonomous fastest connection"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MutedText.copy(alpha = 0.9f),
                        )
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HotaruSmartStatusCard(
                    isRunning = isRunning,
                    isOptimizing = isOptimizing,
                    isPersian = isPersian,
                    networkSummary = networkSummary,
                    activeServersCount = activeServersCount,
                    livePing = livePing,
                )

                HotaruFooterActionsBar(
                    isPersian = isPersian,
                    onOpenAbout = { showAboutDialog = true },
                    onOpenGitHub = { onOpenUrl(repoUrl) },
                )
            }
        }
    }
}

@Composable
private fun HotaruTopBar(
    isPersian: Boolean,
    onToggleLanguage: () -> Unit,
    onOpenTelegram: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "HotaruNet",
                maxLines = 1,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                    color = Color.White,
                )
            )
            Text(
                text = if (isPersian) "اتصال هوشمند خودکار" else "SMART AUTO-PILOT",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = if (isPersian) 0.sp else 0.5.sp,
                    color = HotaruMint.copy(alpha = 0.88f),
                    fontWeight = FontWeight.SemiBold,
                )
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = TelegramBlue.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, TelegramBlue.copy(alpha = 0.42f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenTelegram),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_telegram_24dp),
                        contentDescription = "@OneForAllHub",
                        tint = TelegramBlue,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = "@OneForAllHub",
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.94f),
                            fontWeight = FontWeight.SemiBold,
                        )
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = DeepCyberSurface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onToggleLanguage),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "FA",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isPersian) FontWeight.Bold else FontWeight.Normal,
                            color = if (isPersian) NeonCyan else MutedText,
                        )
                    )
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.labelSmall.copy(color = MutedText.copy(alpha = 0.5f))
                    )
                    Text(
                        text = "EN",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (!isPersian) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isPersian) NeonCyan else MutedText,
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CyberAnimeBackdrop(
    isRunning: Boolean,
    isConnecting: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hotaruBackdrop")
    val driftProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "fireflyDrift",
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "toriiPulse",
    )

    val accentColor by animateColorAsState(
        targetValue = when {
            isRunning -> HotaruMint
            isConnecting -> ConnectingViolet
            else -> CyberMagenta
        },
        animationSpec = tween(700),
        label = "backdropAccent",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h * 0.45f)

        // Deep galactic nebula glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.14f * pulse),
                    Color(0xFF1A1038).copy(alpha = 0.20f),
                    Color.Transparent,
                ),
                center = center,
                radius = w * 0.95f,
            ),
            radius = w * 0.95f,
            center = center,
        )

        // Glowing Cyber Torii Gate behind the center ring
        val gateCenterX = w / 2f
        val gateTopY = h * 0.24f
        val gateBottomY = h * 0.65f
        val gateWidth = w * 0.72f
        val pillarOffset = gateWidth * 0.30f
        val toriiGlow = accentColor.copy(alpha = 0.22f * pulse)
        val toriiStroke = accentColor.copy(alpha = 0.40f * pulse)

        val kasagiPath = Path().apply {
            moveTo(gateCenterX - gateWidth * 0.52f, gateTopY - 12f)
            quadraticTo(gateCenterX, gateTopY + 14f, gateCenterX + gateWidth * 0.52f, gateTopY - 12f)
        }
        drawPath(
            path = kasagiPath,
            color = toriiGlow,
            style = Stroke(width = 22f, cap = StrokeCap.Round),
        )
        drawPath(
            path = kasagiPath,
            color = toriiStroke,
            style = Stroke(width = 6f, cap = StrokeCap.Round),
        )

        val nukiY = gateTopY + 46f
        drawLine(
            color = toriiGlow,
            start = Offset(gateCenterX - gateWidth * 0.40f, nukiY),
            end = Offset(gateCenterX + gateWidth * 0.40f, nukiY),
            strokeWidth = 14f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = toriiStroke,
            start = Offset(gateCenterX - gateWidth * 0.40f, nukiY),
            end = Offset(gateCenterX + gateWidth * 0.40f, nukiY),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
        )

        val pillarBrush = Brush.verticalGradient(
            colors = listOf(
                toriiStroke,
                toriiGlow.copy(alpha = 0.08f),
                Color.Transparent,
            ),
            startY = gateTopY,
            endY = gateBottomY,
        )
        drawLine(
            brush = pillarBrush,
            start = Offset(gateCenterX - pillarOffset, gateTopY + 4f),
            end = Offset(gateCenterX - pillarOffset * 1.07f, gateBottomY),
            strokeWidth = 8f,
            cap = StrokeCap.Round,
        )
        drawLine(
            brush = pillarBrush,
            start = Offset(gateCenterX + pillarOffset, gateTopY + 4f),
            end = Offset(gateCenterX + pillarOffset * 1.07f, gateBottomY),
            strokeWidth = 8f,
            cap = StrokeCap.Round,
        )

        // Floating Hotaru (Firefly) bioluminescent particles
        DefaultFireflies.forEach { seed ->
            val cycle = (seed.startYFraction - driftProgress * seed.speedFactor)
            val wrappedY = ((cycle % 1f) + 1f) % 1f
            val sway = sin((driftProgress * 2f * PI + seed.phase).toDouble()).toFloat() * 18f
            val pos = Offset(
                x = (seed.xFraction * w + sway).coerceIn(0f, w),
                y = wrappedY * h,
            )
            val flicker = 0.45f + 0.55f * (0.5f + 0.5f * cos((driftProgress * 4f * PI + seed.phase).toDouble()).toFloat())
            val rPx = seed.radiusDp.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        seed.color.copy(alpha = 0.65f * flicker),
                        seed.color.copy(alpha = 0.15f * flicker),
                        Color.Transparent,
                    ),
                    center = pos,
                    radius = rPx * 4.5f,
                ),
                radius = rPx * 4.5f,
                center = pos,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f * flicker),
                radius = rPx * 0.7f,
                center = pos,
            )
        }
    }
}

@Composable
private fun NeonConnectButton(
    isRunning: Boolean,
    isOptimizing: Boolean,
    isConnecting: Boolean,
    isPersian: Boolean,
    onClick: () -> Unit,
) {
    val ringColor by animateColorAsState(
        targetValue = when {
            isRunning -> HotaruMint
            isConnecting -> ConnectingViolet
            else -> CyberMagenta
        },
        animationSpec = tween(500),
        label = "ringColor",
    )
    val secondaryGlow by animateColorAsState(
        targetValue = when {
            isRunning -> NeonCyan
            isConnecting -> NeonCyan
            else -> Color(0xFF7B2CBF)
        },
        animationSpec = tween(500),
        label = "secondaryGlow",
    )
    val glowIntensity by animateFloatAsState(
        targetValue = if (isRunning || isConnecting) 1f else 0.6f,
        animationSpec = tween(700),
        label = "glowIntensity",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "ringSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 1200 else 5000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spinAngle",
    )

    val actionLabel = when {
        isRunning -> if (isPersian) "قطع اتصال" else "CONNECTED"
        isOptimizing -> if (isPersian) "انتخاب خودکار" else "OPTIMIZING"
        isConnecting -> if (isPersian) "لطفاً صبر کنید" else "CONNECTING"
        else -> if (isPersian) "اتصال" else "CONNECT"
    }

    Box(
        modifier = Modifier
            .size(228.dp)
            .clip(CircleShape)
            .semantics { contentDescription = actionLabel }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val innerRingRadius = radius * 0.76f
            val outerArcRadius = radius * 0.88f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ringColor.copy(alpha = 0.34f * glowIntensity),
                        secondaryGlow.copy(alpha = 0.12f * glowIntensity),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A1E33),
                        VoidGalaxyBg,
                    ),
                    center = center,
                    radius = innerRingRadius,
                ),
                radius = innerRingRadius,
            )

            if (isRunning || isConnecting) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            secondaryGlow.copy(alpha = 0.85f),
                            ringColor,
                            Color.Transparent,
                        )
                    ),
                    startAngle = spinAngle,
                    sweepAngle = 210f,
                    useCenter = false,
                    topLeft = Offset(center.x - outerArcRadius, center.y - outerArcRadius),
                    size = Size(outerArcRadius * 2f, outerArcRadius * 2f),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
                )
            } else {
                drawCircle(
                    color = ringColor.copy(alpha = 0.25f),
                    radius = outerArcRadius,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }

            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(ringColor, secondaryGlow)
                ),
                radius = innerRingRadius,
                style = Stroke(width = 7.dp.toPx()),
            )

            val glyphCenter = Offset(center.x, center.y - 20.dp.toPx())
            val glyphRadius = 18.dp.toPx()
            drawArc(
                color = ringColor,
                startAngle = -52f,
                sweepAngle = 284f,
                useCenter = false,
                topLeft = Offset(glyphCenter.x - glyphRadius, glyphCenter.y - glyphRadius),
                size = Size(glyphRadius * 2f, glyphRadius * 2f),
                style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round),
            )
            drawLine(
                color = ringColor,
                start = Offset(glyphCenter.x, glyphCenter.y - glyphRadius - 3.dp.toPx()),
                end = Offset(glyphCenter.x, glyphCenter.y - 2.dp.toPx()),
                strokeWidth = 3.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 54.dp),
        ) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = if (isPersian) 0.sp else 1.8.sp,
                    color = Color.White,
                )
            )
        }
    }
}

@Composable
private fun FireflyCommunityBadge(
    isPersian: Boolean,
    isRunning: Boolean,
    connectedUsersCount: Long,
    modifier: Modifier = Modifier,
) {
    val formattedCount = remember(connectedUsersCount, isPersian) {
        val raw = "%,d".format(Locale.US, connectedUsersCount)
        if (isPersian) {
            raw.map { c ->
                when (c) {
                    '0' -> '۰'
                    '1' -> '۱'
                    '2' -> '۲'
                    '3' -> '۳'
                    '4' -> '۴'
                    '5' -> '۵'
                    '6' -> '۶'
                    '7' -> '۷'
                    '8' -> '۸'
                    '9' -> '۹'
                    ',' -> '،'
                    else -> c
                }
            }.joinToString("")
        } else raw
    }

    val infiniteTransition = rememberInfiniteTransition(label = "fireflyGlow")
    val fireflyPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val badgeText = when {
        isRunning -> {
            if (isPersian) {
                if (connectedUsersCount <= 1L) {
                    "۱ نفر (شما) هم‌اکنون به کرم‌های شب‌تاب نگاه می‌کنید"
                } else {
                    val others = "%,d".format(Locale.US, connectedUsersCount - 1L).map { c ->
                        when (c) {
                            '0' -> '۰'; '1' -> '۱'; '2' -> '۲'; '3' -> '۳'; '4' -> '۴'
                            '5' -> '۵'; '6' -> '۶'; '7' -> '۷'; '8' -> '۸'; '9' -> '۹'
                            ',' -> '،'; else -> c
                        }
                    }.joinToString("")
                    "شما و $others نفر دیگر هم‌اکنون به کرم‌های شب‌تاب نگاه می‌کنید"
                }
            } else {
                if (connectedUsersCount <= 1L) {
                    "1 soul (You) watching the fireflies tonight"
                } else {
                    "You and ${connectedUsersCount - 1L} others watching the fireflies tonight"
                }
            }
        }
        else -> {
            // Disconnected
            if (isPersian) {
                if (connectedUsersCount <= 0L) {
                    "۰ نفر هم‌اکنون به کرم‌های شب‌تاب نگاه می‌کنند"
                } else {
                    "$formattedCount نفر هم‌اکنون به کرم‌های شب‌تاب نگاه می‌کنند"
                }
            } else {
                if (connectedUsersCount <= 0L) {
                    "0 people watching the fireflies tonight"
                } else {
                    "$formattedCount people watching the fireflies tonight"
                }
            }
        }
    }

    val indicatorColor = if (isRunning) HotaruMint else WarmAmber

    Surface(
        shape = RoundedCornerShape(50),
        color = Color(0xFF0F1528).copy(alpha = 0.78f),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    NeonCyan.copy(alpha = 0.30f * fireflyPulse),
                    indicatorColor.copy(alpha = 0.60f * fireflyPulse),
                    NeonCyan.copy(alpha = 0.30f * fireflyPulse),
                )
            )
        ),
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(indicatorColor.copy(alpha = 0.30f * fireflyPulse))
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(indicatorColor.copy(alpha = fireflyPulse))
                )
            }

            Text(
                text = "✨ $badgeText",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.92f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = if (isPersian) 0.sp else 0.4.sp,
                ),
            )
        }
    }
}

@Composable
private fun HotaruSmartStatusCard(
    isRunning: Boolean,
    isOptimizing: Boolean,
    isPersian: Boolean,
    networkSummary: String,
    activeServersCount: Int = 0,
    livePing: Int,
) {
    val accentColor = when {
        isRunning -> HotaruMint
        isOptimizing -> ConnectingViolet
        else -> NeonCyan
    }
    val headlineText = when {
        isRunning ->
            if (isPersian) "مسیر هوشمند و ایمن فعال است" else "Optimal Secure Route Active"
        isOptimizing ->
            if (isPersian) "انتخاب بهترین سرور و تنظیمات..." else "Scanning Fastest Server & Settings..."
        else ->
            if (isPersian) "انتخاب تمام‌خودکار سرور بر اساس شبکه شما" else "Zero-Config Adaptive Tunnel"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = DeepCyberSurface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.36f)),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isRunning) HotaruMint else if (isOptimizing) WarmAmber else NeonCyan)
                    )
                    Text(
                        text = if (isPersian) "هوش مصنوعی مسیر" else "AUTO-PILOT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = if (isPersian) 0.sp else 1.2.sp,
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = HotaruMint.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, HotaruMint.copy(alpha = 0.32f)),
                ) {
                    Text(
                        text = if (isPersian) "شیلد ۵گانه فعال" else "5-RULE SHIELD",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = HotaruMint,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                        )
                    )
                }
            }

            Text(
                text = headlineText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            )

            val detailLine = if (activeServersCount > 0) {
                val formattedServers = if (isPersian) {
                    activeServersCount.toString().map { c ->
                        when (c) {
                            '0' -> '۰'; '1' -> '۱'; '2' -> '۲'; '3' -> '۳'; '4' -> '۴'
                            '5' -> '۵'; '6' -> '۶'; '7' -> '۷'; '8' -> '۸'; '9' -> '۹'
                            else -> c
                        }
                    }.joinToString("")
                } else activeServersCount.toString()
                if (isPersian) {
                    "$networkSummary (شامل $formattedServers گره فعال)"
                } else {
                    "$networkSummary • $formattedServers nodes"
                }
            } else {
                networkSummary
            }

            Text(
                text = detailLine,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MutedText,
                )
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = TimeUnit.SECONDS.toHours(seconds)
    val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
}

private fun formatTraffic(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

@Composable
private fun FirstRunLanguageDialog(
    onConfirmLanguage: (String) -> Unit,
) {
    var selectedLanguage by remember { mutableStateOf("en") }

    Dialog(
        onDismissRequest = {
            onConfirmLanguage("en")
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DeepCyberSurface,
            border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(NeonCyan, ConnectingViolet))),
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(VoidGalaxyBg)
                        .border(1.5.dp, NeonCyan, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "🌐",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "CHOOSE LANGUAGE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = NeonCyan,
                        )
                    )
                    Text(
                        text = "انتخاب زبان برنامه",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    )
                }

                Text(
                    text = "Please choose your preferred language\nلطفاً زبان مورد نظر خود را مشخص کنید",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MutedText,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                )

                // Option 1: English (Default)
                val isEnSelected = selectedLanguage == "en"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isEnSelected) NeonCyan.copy(alpha = 0.12f) else VoidGalaxyBg,
                    border = BorderStroke(
                        width = if (isEnSelected) 1.5.dp else 1.dp,
                        color = if (isEnSelected) NeonCyan else Color.White.copy(alpha = 0.12f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedLanguage = "en" },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(text = "🇬🇧", style = MaterialTheme.typography.titleMedium)
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "English",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isEnSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                        )
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = HotaruMint.copy(alpha = 0.2f),
                                        border = BorderStroke(0.8.dp, HotaruMint.copy(alpha = 0.6f)),
                                    ) {
                                        Text(
                                            text = "Default",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = HotaruMint,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        )
                                    }
                                }
                                Text(
                                    text = "Standard international interface",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MutedText),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    color = if (isEnSelected) NeonCyan else MutedText.copy(alpha = 0.4f),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isEnSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(NeonCyan)
                                )
                            }
                        }
                    }
                }

                // Option 2: Persian
                val isFaSelected = selectedLanguage == "fa"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isFaSelected) ConnectingViolet.copy(alpha = 0.15f) else VoidGalaxyBg,
                    border = BorderStroke(
                        width = if (isFaSelected) 1.5.dp else 1.dp,
                        color = if (isFaSelected) ConnectingViolet else Color.White.copy(alpha = 0.12f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedLanguage = "fa" },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(text = "🇮🇷", style = MaterialTheme.typography.titleMedium)
                            Column {
                                Text(
                                    text = "فارسی (Persian)",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFaSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                    )
                                )
                                Text(
                                    text = "رابط کاربری کاملاً فارسی",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MutedText),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    color = if (isFaSelected) ConnectingViolet else MutedText.copy(alpha = 0.4f),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isFaSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(ConnectingViolet)
                                )
                            }
                        }
                    }
                }

                // Confirm Button
                Button(
                    onClick = { onConfirmLanguage(selectedLanguage) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color(0xFF07080C),
                    ),
                ) {
                    Text(
                        text = if (selectedLanguage == "fa") "ادامه / CONTINUE" else "CONTINUE / ادامه",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun HotaruFooterActionsBar(
    isPersian: Boolean,
    onOpenAbout: () -> Unit,
    onOpenGitHub: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DeepCyberSurface.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.34f)),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenAbout),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_about_24dp),
                    contentDescription = if (isPersian) "درباره ما" else "About Us",
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isPersian) "درباره ما و حمایت" else "About & Support",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White.copy(alpha = 0.94f),
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = WarmAmber.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.42f)),
            modifier = Modifier
                .weight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenGitHub),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_github_24dp),
                    contentDescription = "GitHub Star",
                    tint = WarmAmber,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isPersian) "ستاره در GitHub ⭐" else "Star on GitHub ⭐",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = WarmAmber,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DialogLanguagePill(
    isPersian: Boolean,
    onToggleLanguage: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = VoidGalaxyBg,
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f)),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onToggleLanguage),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "FA",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isPersian) FontWeight.Bold else FontWeight.Normal,
                    color = if (isPersian) NeonCyan else MutedText,
                ),
            )
            Text(
                text = "/",
                style = MaterialTheme.typography.labelSmall.copy(color = MutedText.copy(alpha = 0.5f)),
            )
            Text(
                text = "EN",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (!isPersian) FontWeight.Bold else FontWeight.Normal,
                    color = if (!isPersian) NeonCyan else MutedText,
                ),
            )
        }
    }
}

@Composable
private fun HotaruUpdateDialog(
    isPersian: Boolean,
    currentVersion: String,
    latestVersion: String,
    releaseNotes: String,
    onToggleLanguage: () -> Unit,
    onDownloadClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DeepCyberSurface,
            border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(HotaruMint, NeonCyan))),
            shadowElevation = 18.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = HotaruMint.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, HotaruMint.copy(alpha = 0.5f)),
                    ) {
                        Text(
                            text = if (isPersian) "🚀 نسخه جدید" else "🚀 NEW RELEASE",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = HotaruMint,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                    DialogLanguagePill(isPersian = isPersian, onToggleLanguage = onToggleLanguage)
                }

                Text(
                    text = if (isPersian) "آپدیت جدید HotaruNet منتشر شد!" else "A New Version of HotaruNet Is Available!",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VoidGalaxyBg,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "v$currentVersion",
                            style = MaterialTheme.typography.labelLarge.copy(color = MutedText),
                        )
                        Text(
                            text = "➔",
                            style = MaterialTheme.typography.labelLarge.copy(color = NeonCyan),
                        )
                        Text(
                            text = "v$latestVersion",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = HotaruMint,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }

                val summaryText = releaseNotes.trim().ifEmpty {
                    if (isPersian) {
                        "بهبود سرعت انتخاب خودکار، پایداری بیشتر اتصال و ارتقای امنیت هسته در نسخه جدید آماده دانلود است."
                    } else {
                        "Improved auto-pilot speed, connection stability, and core updates are ready on the GitHub Releases page."
                    }
                }

                Text(
                    text = summaryText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 19.sp,
                    ),
                )

                Button(
                    onClick = onDownloadClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HotaruMint,
                        contentColor = Color(0xFF07080C),
                    ),
                ) {
                    Text(
                        text = if (isPersian) "⬇️ رفتن به صفحه دانلود (GitHub)" else "⬇️ Open Download Page (Releases)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = if (isPersian) "بعداً یادآوری کن" else "Later",
                        style = MaterialTheme.typography.labelMedium.copy(color = MutedText),
                    )
                }
            }
        }
    }
}

@Composable
private fun HotaruAboutDialog(
    isPersian: Boolean,
    appVersion: String,
    onToggleLanguage: () -> Unit,
    onOpenGitHub: () -> Unit,
    onOpenTelegram: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DeepCyberSurface,
            border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(NeonCyan, CyberMagenta))),
            shadowElevation = 18.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = if (isPersian) "درباره HotaruNet" else "About HotaruNet",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                        )
                        Text(
                            text = "v$appVersion • OneForAllHub",
                            style = MaterialTheme.typography.labelSmall.copy(color = NeonCyan),
                        )
                    }
                    DialogLanguagePill(isPersian = isPersian, onToggleLanguage = onToggleLanguage)
                }

                Text(
                    text = if (isPersian) {
                        "HotaruNet یک پروژهٔ رایگان و هوشمند برای دسترسی آزاد به اینترنت است. موتور خودکار برنامه متناسب با اپراتور شما، سریع‌ترین مسیر بین پروتکل‌های ضد‌فیلتر (REALITY، XHTTP، WS+TLS، Hysteria2 و WARP) را انتخاب می‌کند و هنگام افت سرعت یا قطعی به‌صورت خودکار جابه‌جا می‌شود."
                    } else {
                        "HotaruNet is a free, open-source autonomous anti-censorship client. Its Smart Auto-Pilot probes multiple censorship-resistant protocols (REALITY, XHTTP, WS+TLS, Hysteria2 & WARP) for your network and seamlessly fails over if a node degrades."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.88f),
                        lineHeight = 20.sp,
                    ),
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = WarmAmber.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, WarmAmber.copy(alpha = 0.38f)),
                ) {
                    Text(
                        text = if (isPersian) {
                            "⭐ حمایت شما باعث ادامهٔ پروژه می‌شود! برای تداوم و رایگان ماندن HotaruNet، لطفاً در صفحهٔ گیت‌هاب پروژه به ما ستاره (Star) بدهید و برای دریافت نسخه‌های جدید در کانال تلگرام ما عضو شوید."
                        } else {
                            "⭐ Your support keeps this project alive! Please give HotaruNet a Star on GitHub and join our Telegram channel for updates, news, and community support."
                        },
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = WarmAmber.copy(alpha = 0.96f),
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }

                Button(
                    onClick = onOpenGitHub,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarmAmber,
                        contentColor = Color(0xFF07080C),
                    ),
                ) {
                    Text(
                        text = if (isPersian) "⭐ حمایت و ستاره در گیت‌هاب" else "⭐ Give a Star on GitHub",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                    )
                }

                OutlinedButton(
                    onClick = onOpenTelegram,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, TelegramBlue.copy(alpha = 0.65f)),
                ) {
                    Text(
                        text = if (isPersian) "📢 عضویت در کانال تلگرام @OneForAllHub" else "📢 Join Telegram @OneForAllHub",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = TelegramBlue,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = if (isPersian) "بستن" else "Close",
                        style = MaterialTheme.typography.labelMedium.copy(color = MutedText),
                    )
                }
            }
        }
    }
}

@Composable
private fun HotaruFirstConnectDialog(
    isPersian: Boolean,
    onToggleLanguage: () -> Unit,
    onOpenGitHub: () -> Unit,
    onOpenTelegram: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DeepCyberSurface,
            border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(HotaruMint, ConnectingViolet))),
            shadowElevation = 18.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = HotaruMint.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, HotaruMint.copy(alpha = 0.45f)),
                    ) {
                        Text(
                            text = if (isPersian) "✨ اولین اتصال موفق" else "✨ FIRST CONNECTION",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = HotaruMint,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                    DialogLanguagePill(isPersian = isPersian, onToggleLanguage = onToggleLanguage)
                }

                Text(
                    text = if (isPersian) "به HotaruNet خوش آمدید!" else "Welcome to HotaruNet!",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                )

                Text(
                    text = if (isPersian) {
                        "اتصال شما با موفقیت برقرار شد. برنامه همیشه سرعت و پایداری مسیر را زیر نظر دارد و در صورت قطعی یا افت سرعت، خودکار شما را به سرور پرسرعت بعدی منتقل می‌کند.\n\n⭐ برای حمایت و ادامهٔ رایگان این پروژه، لطفاً در GitHub به ما ستاره (Star) بدهید و برای دریافت آخرین نسخه‌ها و اطلاعیه‌ها عضو کانال تلگرام ما شوید:"
                    } else {
                        "You're securely connected! HotaruNet continuously monitors route throughput and automatically switches to the next fastest verified node if your connection slows down or drops.\n\n⭐ To support keeping this project alive and free, please star our GitHub repository and join our Telegram channel for updates & releases:"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.88f),
                        lineHeight = 20.sp,
                    ),
                )

                Button(
                    onClick = onOpenGitHub,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarmAmber,
                        contentColor = Color(0xFF07080C),
                    ),
                ) {
                    Text(
                        text = if (isPersian) "⭐ حمایت با ستاره در گیت‌هاب" else "⭐ Star Project on GitHub",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                    )
                }

                OutlinedButton(
                    onClick = onOpenTelegram,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, TelegramBlue.copy(alpha = 0.65f)),
                ) {
                    Text(
                        text = if (isPersian) "📢 عضویت در کانال تلگرام @OneForAllHub" else "📢 Join @OneForAllHub Telegram",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = TelegramBlue,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text(
                        text = if (isPersian) "عالیه، متوجه شدم" else "Got It",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = HotaruMint,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
    }
}
