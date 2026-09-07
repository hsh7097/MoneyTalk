package com.sanha.moneytalk.feature.smssettings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sanha.moneytalk.R

private object SmsSettingsRoute {
    const val MAIN = "main"
    const val BLOCKED_PHRASES = "blocked_phrases"
    const val BLOCKED_SENDERS = "blocked_senders"
    const val EXCLUDED_CARDS = "excluded_cards"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SmsSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: SmsSettingsRoute.MAIN

    BackHandler(enabled = currentRoute != SmsSettingsRoute.MAIN) {
        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentRoute) {
                            SmsSettingsRoute.BLOCKED_PHRASES -> stringResource(R.string.sms_settings_blocked_phrase_page_title)
                            SmsSettingsRoute.BLOCKED_SENDERS -> stringResource(R.string.sms_settings_blocked_sender_page_title)
                            SmsSettingsRoute.EXCLUDED_CARDS -> stringResource(R.string.sms_settings_excluded_card_page_title)
                            else -> stringResource(R.string.sms_settings_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentRoute == SmsSettingsRoute.MAIN) {
                                onBack()
                            } else {
                                navController.popBackStack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SmsSettingsRoute.MAIN,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(SmsSettingsRoute.MAIN) {
                SmsSettingsMainContent(
                    uiState = uiState,
                    onRequestSync = {
                        viewModel.requestSmsAnalysisUpdate()
                    },
                    onOpenBlockedPhrases = { navController.navigate(SmsSettingsRoute.BLOCKED_PHRASES) },
                    onOpenBlockedSenders = { navController.navigate(SmsSettingsRoute.BLOCKED_SENDERS) },
                    onOpenExcludedCards = { navController.navigate(SmsSettingsRoute.EXCLUDED_CARDS) }
                )
            }

            composable(SmsSettingsRoute.BLOCKED_PHRASES) {
                BlockedPhraseManageScreen(
                    keywords = uiState.exclusionKeywords,
                    onAdd = viewModel::addExclusionKeyword,
                    onRemove = viewModel::removeExclusionKeyword
                )
            }

            composable(SmsSettingsRoute.BLOCKED_SENDERS) {
                BlockedSenderManageScreen(
                    blockedSenders = uiState.blockedSenders,
                    onAdd = viewModel::addBlockedSender,
                    onRemove = viewModel::removeBlockedSender
                )
            }

            composable(SmsSettingsRoute.EXCLUDED_CARDS) {
                ExcludedCardManageScreen(
                    cards = uiState.ownedCards,
                    onAdd = viewModel::addExcludedCard,
                    onExcludedChange = viewModel::updateCardExclusion
                )
            }
        }
    }
}
