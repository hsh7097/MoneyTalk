package com.sanha.moneytalk.feature.transactionlist.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.theme.MoneyTalkTheme
import com.sanha.moneytalk.core.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 날짜별 거래 상세 목록 Activity.
 *
 * 달력에서 특정 날짜 클릭 시 해당 날짜의 지출+수입 목록을 표시.
 * - EXTRA_DATE: "yyyy-MM-dd" 형식의 날짜 문자열
 */
@AndroidEntryPoint
class TransactionDetailListActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DATE = "extra_date"
    }

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

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
                TransactionDetailListScreen(onBack = { finish() })
            }
        }
    }
}
