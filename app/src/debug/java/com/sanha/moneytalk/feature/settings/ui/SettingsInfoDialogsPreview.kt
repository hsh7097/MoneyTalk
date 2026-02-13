package com.sanha.moneytalk.feature.settings.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// ========== AppInfoDialog Preview ==========

@Preview(showBackground = true, name = "앱 정보 다이얼로그")
@Composable
private fun AppInfoDialogPreview() {
    MaterialTheme {
        AppInfoDialog(onDismiss = {})
    }
}

// ========== PrivacyPolicyDialog Preview ==========

@Preview(showBackground = true, name = "개인정보 처리방침 다이얼로그")
@Composable
private fun PrivacyPolicyDialogPreview() {
    MaterialTheme {
        PrivacyPolicyDialog(onDismiss = {})
    }
}
