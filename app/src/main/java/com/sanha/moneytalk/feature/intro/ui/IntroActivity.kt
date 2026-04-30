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
import com.sanha.moneytalk.core.firebase.ForceUpdateChecker
import com.sanha.moneytalk.core.firebase.ForceUpdateState
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
import javax.inject.Inject

/**
 * 앱 초기 진입 Activity (LAUNCHER).
 *
 * 3가지 초기화를 처리한 뒤 MainActivity로 전환:
 * 1. 권한 설정 — 커스텀 설명 화면 + 시스템 권한 요청
 * 2. 서버 설정 — PremiumManager가 캐시를 복원하고 RTDB는 비동기로 갱신
 * 3. 강제 업데이트 체크 — 현재 보유한 min_version_name 기준 즉시 비교
 */
@AndroidEntryPoint
class IntroActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var premiumManager: PremiumManager

    /** 상태 머신 */
    private enum class IntroState { SPLASH, ONBOARDING, PERMISSION, NAVIGATING, FORCE_UPDATE }

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

                    IntroState.ONBOARDING -> {
                        OnboardingScreen(
                            onOnboardingFinished = { introState = IntroState.PERMISSION }
                        )
                    }

                    IntroState.PERMISSION -> {
                        PermissionScreen(
                            onAgree = { requestSmsPermission() },
                            onDisagree = { onPermissionFlowDone() }
                        )
                    }

                    IntroState.NAVIGATING -> {
                        // MainActivity 전환 직전 — 스플래시 배경만 유지 (애니메이션 재생 없이)
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
                checkConfigAndNavigate()
            } else {
                introState = IntroState.ONBOARDING
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
            checkConfigAndNavigate()
        }
    }

    /**
     * RTDB 응답을 기다리지 않고 현재 보유 설정만 확인한 뒤 MainActivity로 전환.
     * 서버 설정은 PremiumManager에서 캐시 복원 후 비동기로 갱신된다.
     */
    private fun checkConfigAndNavigate() {
        introState = IntroState.NAVIGATING
        val config = premiumManager.premiumConfig.value
        // 강제 업데이트 체크 (버전명 비교)
        if (ForceUpdateChecker.compareVersionNames(BuildConfig.VERSION_NAME, config.minVersionName) < 0) {
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
