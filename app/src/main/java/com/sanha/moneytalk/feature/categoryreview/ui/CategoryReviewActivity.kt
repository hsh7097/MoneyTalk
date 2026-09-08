package com.sanha.moneytalk.feature.categoryreview.ui

import android.content.Context
import android.content.Intent
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

/** 읽기 전용 미분류 목록. 거래 변경은 기존 TransactionEditActivity가 담당한다. */
@AndroidEntryPoint
class CategoryReviewActivity : ComponentActivity() {
    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, CategoryReviewActivity::class.java))
        }
    }

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeName by settingsDataStore.themeModeFlow.collectAsStateWithLifecycle("SYSTEM")
            val themeMode = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.SYSTEM
            MoneyTalkTheme(themeMode = themeMode) {
                CategoryReviewScreen(onBack = { finish() })
            }
        }
    }
}
