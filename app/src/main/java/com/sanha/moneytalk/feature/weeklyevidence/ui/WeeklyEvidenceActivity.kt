package com.sanha.moneytalk.feature.weeklyevidence.ui

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
import com.sanha.moneytalk.feature.weeklyevidence.WeeklyEvidenceRequest
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WeeklyEvidenceActivity : ComponentActivity() {
    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeName by settingsDataStore.themeModeFlow.collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val theme = runCatching { ThemeMode.valueOf(themeName) }.getOrDefault(ThemeMode.SYSTEM)
            MoneyTalkTheme(themeMode = theme) {
                WeeklyEvidenceScreen(onBack = { finish() })
            }
        }
    }

    companion object {
        internal const val CATEGORY = "weekly_category"
        internal const val RECENT_START = "weekly_recent_start"
        internal const val RECENT_END = "weekly_recent_end"
        internal const val PREVIOUS_START = "weekly_previous_start"
        internal const val PREVIOUS_END = "weekly_previous_end"
        internal const val AS_OF = "weekly_as_of"
        internal const val TIME_ZONE = "weekly_time_zone"
        internal const val INITIALLY_RECENT = "weekly_initially_recent"

        fun open(context: Context, request: WeeklyEvidenceRequest) {
            context.startActivity(Intent(context, WeeklyEvidenceActivity::class.java).apply {
                putExtra(CATEGORY, request.category)
                putExtra(RECENT_START, request.recentStart.toString())
                putExtra(RECENT_END, request.recentEndInclusive.toString())
                putExtra(PREVIOUS_START, request.previousStart.toString())
                putExtra(PREVIOUS_END, request.previousEndInclusive.toString())
                putExtra(AS_OF, request.asOfMillis)
                putExtra(TIME_ZONE, request.timeZoneId)
                putExtra(INITIALLY_RECENT, request.initiallyRecent)
            })
        }
    }
}
