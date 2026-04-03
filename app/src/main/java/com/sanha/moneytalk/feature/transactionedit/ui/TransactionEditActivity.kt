package com.sanha.moneytalk.feature.transactionedit.ui

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

/**
 * 거래 편집/추가 Activity.
 *
 * - EXTRA_EXPENSE_ID: 기존 지출 편집 시 expense ID, -1L이면 무시
 * - EXTRA_INCOME_ID: 기존 수입 편집 시 income ID, -1L이면 무시
 * - EXTRA_INITIAL_DATE: 새 거래 추가 시 기본 날짜 (Long, optional)
 */
@AndroidEntryPoint
class TransactionEditActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_EXPENSE_ID = "extra_expense_id"
        private const val EXTRA_INCOME_ID = "extra_income_id"

        fun open(
            context: Context,
            expenseId: Long = -1L,
            incomeId: Long = -1L
        ) {
            context.startActivity(
                Intent(context, TransactionEditActivity::class.java).apply {
                    putExtra(EXTRA_EXPENSE_ID, expenseId)
                    putExtra(EXTRA_INCOME_ID, incomeId)
                }
            )
        }
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
                TransactionEditScreen(onBack = { finish() })
            }
        }
    }
}
