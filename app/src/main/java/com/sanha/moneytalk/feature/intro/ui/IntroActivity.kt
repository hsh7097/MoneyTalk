package com.sanha.moneytalk.feature.intro.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.sanha.moneytalk.BuildConfig
import com.sanha.moneytalk.MainActivity
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.ForceUpdateState
import com.sanha.moneytalk.core.firebase.PremiumConfig
import com.sanha.moneytalk.core.firebase.PremiumManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.Primary
import com.sanha.moneytalk.core.theme.PrimaryDark
import com.sanha.moneytalk.core.theme.PrimaryLight
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.feature.splash.ui.SplashScreen
import com.sanha.moneytalk.core.ui.ForceUpdateDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * 앱 초기 진입 Activity (LAUNCHER).
 *
 * 3가지 초기화를 처리한 뒤 MainActivity로 전환:
 * 1. 권한 설정 — 커스텀 설명 화면 + 시스템 권한 요청
 * 2. RTDB 가져오기 — PremiumConfig 비동기 로딩 (splash 동안 병렬)
 * 3. 강제 업데이트 체크 — RTDB 로드 후 min_version_code 비교
 */
@AndroidEntryPoint
class IntroActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var premiumManager: PremiumManager

    /** 상태 머신 */
    private enum class IntroState { SPLASH, PERMISSION, NAVIGATING, FORCE_UPDATE }

    private var introState by mutableStateOf(IntroState.SPLASH)
    private var forceUpdateState by mutableStateOf<ForceUpdateState>(ForceUpdateState.NotRequired)

    /** SMS 권한 요청 런처 */
    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, getString(R.string.permission_sms_granted), Toast.LENGTH_SHORT).show()
        }
        // SMS 권한 결과와 무관하게 알림 권한 요청으로 진행
        requestNotificationPermission()
    }

    /** 알림 권한 요청 런처 (Android 13+) */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // 알림 권한 결과와 무관하게 진행
        onPermissionFlowDone()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeModeStr by settingsDataStore.themeModeFlow
                .collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val themeMode = try {
                ThemeMode.valueOf(themeModeStr)
            } catch (_: Exception) {
                ThemeMode.SYSTEM
            }

            MoneyTalkTheme(themeMode = themeMode) {
                when (introState) {
                    IntroState.SPLASH -> {
                        SplashScreen(onSplashFinished = { onSplashFinished() })
                    }

                    IntroState.PERMISSION -> {
                        PermissionScreen(
                            onAgree = { requestSmsPermission() },
                            onDisagree = { onPermissionFlowDone() }
                        )
                    }

                    IntroState.NAVIGATING -> {
                        // RTDB 대기 중 — 스플래시 배경만 유지 (애니메이션 재생 없이)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(PrimaryLight, Primary, PrimaryDark)
                                    )
                                )
                        )
                    }

                    IntroState.FORCE_UPDATE -> {
                        val state = forceUpdateState
                        if (state is ForceUpdateState.Required) {
                            ForceUpdateDialog(
                                state = state,
                                onUpdate = {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                                    )
                                    startActivity(intent)
                                },
                                onExit = { finish() }
                            )
                        }
                    }
                }
            }
        }
    }

    /** 스플래시 애니메이션 완료 후 분기 */
    private fun onSplashFinished() {
        lifecycleScope.launch {
            val completed = settingsDataStore.onboardingCompletedFlow.first()
            if (completed) {
                waitForConfigAndNavigate()
            } else {
                introState = IntroState.PERMISSION
            }
        }
    }

    /** SMS 권한 요청 — 이미 허용된 경우 시스템 팝업 없이 다음 단계로 진행 */
    private fun requestSmsPermission() {
        val smsPermissions = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        val allGranted = smsPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            // 기존 사용자 업데이트: 이미 권한 있으면 팝업 스킵 → 알림 권한으로 진행
            requestNotificationPermission()
        } else {
            smsPermissionLauncher.launch(smsPermissions)
        }
    }

    /** 알림 권한 요청 (Android 13+) — 이미 허용된 경우 스킵 */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                onPermissionFlowDone()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onPermissionFlowDone()
        }
    }

    /** 권한 흐름 완료 (동의함/동의안함 공통) */
    private fun onPermissionFlowDone() {
        lifecycleScope.launch {
            settingsDataStore.setOnboardingCompleted(true)
            waitForConfigAndNavigate()
        }
    }

    /**
     * RTDB 비동기 대기 → 강제 업데이트 체크 → MainActivity 전환.
     *
     * premiumConfig의 첫 유효 값을 최대 3초 대기.
     * 타임아웃 시 기본값으로 진행 (MainActivity의 ForceUpdateChecker가 이후 대응).
     */
    private suspend fun waitForConfigAndNavigate() {
        introState = IntroState.NAVIGATING
        val config = withTimeoutOrNull(3000L) {
            premiumManager.premiumConfig.first { it != PremiumConfig() }
        }
        // 강제 업데이트 체크
        if (config != null && BuildConfig.VERSION_CODE < config.minVersionCode) {
            forceUpdateState = ForceUpdateState.Required(
                currentVersion = BuildConfig.VERSION_NAME,
                requiredVersion = config.minVersionName,
                message = config.forceUpdateMessage
            )
            introState = IntroState.FORCE_UPDATE
            return
        }
        navigateToMain()
    }

    /** MainActivity로 전환 후 IntroActivity 종료 */
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
