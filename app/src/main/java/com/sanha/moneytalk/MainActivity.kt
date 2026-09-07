package com.sanha.moneytalk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.AnalyticsHelper
import com.sanha.moneytalk.core.firebase.ForceUpdateChecker
import com.sanha.moneytalk.core.firebase.ForceUpdateState
import com.sanha.moneytalk.core.notification.SmsNotificationManager
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.ui.ForceUpdateDialog
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var snackbarBus: AppSnackbarBus
    @Inject
    lateinit var settingsDataStore: SettingsDataStore
    @Inject
    lateinit var forceUpdateChecker: ForceUpdateChecker
    @Inject
    lateinit var analyticsHelper: AnalyticsHelper
    @Inject
    lateinit var smsNotificationManager: SmsNotificationManager

    /** Activity-scoped MainViewModel (동기화/권한/광고 통합 관리) */
    private val mainViewModel: MainViewModel by viewModels()

    /** SMS 권한 요청 후 콜백 (권한 획득 시 호출) */
    private var pendingPermissionCallback: (() -> Unit)? = null

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, getString(R.string.permission_sms_granted), Toast.LENGTH_SHORT).show()
            pendingPermissionCallback?.invoke()
        } else {
            Toast.makeText(this, getString(R.string.permission_sms_denied), Toast.LENGTH_LONG).show()
        }
        pendingPermissionCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeModeStr by settingsDataStore.themeModeFlow.collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val themeMode = try {
                ThemeMode.valueOf(themeModeStr)
            } catch (_: Exception) {
                ThemeMode.SYSTEM
            }

            MoneyTalkTheme(themeMode = themeMode) {
                // 강제 업데이트 체크 (앱 사용 중 RTDB 변경 시 실시간 대응)
                val forceUpdateState by forceUpdateChecker.forceUpdateRequired
                    .collectAsStateWithLifecycle(initialValue = ForceUpdateState.NotRequired)

                if (forceUpdateState is ForceUpdateState.Required) {
                    ForceUpdateDialog(
                        state = forceUpdateState as ForceUpdateState.Required,
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

                MoneyTalkApp(
                    onRequestSmsPermission = { onGranted ->
                        checkAndRequestSmsPermission(onGranted)
                    },
                    onExitApp = { finish() },
                    snackbarBus = snackbarBus,
                    analyticsHelper = analyticsHelper
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        smsNotificationManager.clearTransactionNotifications()
        mainViewModel.onAppResume()
    }

    private fun checkAndRequestSmsPermission(onGranted: () -> Unit) {
        val allGranted = SMS_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            onGranted()
        } else {
            pendingPermissionCallback = onGranted
            smsPermissionLauncher.launch(SMS_PERMISSIONS)
        }
    }

    companion object {
        private val SMS_PERMISSIONS = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
    }
}
