package com.sanha.moneytalk.feature.categorydetail.ui

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
 * 카테고리 상세 화면 Activity.
 *
 * 홈 화면에서 카테고리 탭 시 Intent로 실행.
 * - EXTRA_CATEGORY: 카테고리 displayName (예: "식비")
 * - EXTRA_YEAR: 선택된 연도
 * - EXTRA_MONTH: 선택된 월
 */
@AndroidEntryPoint
class CategoryDetailActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_YEAR = "extra_year"
        const val EXTRA_MONTH = "extra_month"
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
                CategoryDetailScreen(onBack = { finish() })
            }
        }
    }
}
