package com.sanha.moneytalk.feature.splash.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanha.moneytalk.core.theme.OnPrimary
import com.sanha.moneytalk.core.theme.Primary
import com.sanha.moneytalk.core.theme.PrimaryDark
import com.sanha.moneytalk.core.theme.PrimaryLight
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(key1 = true) {
        // 애니메이션 시작
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500)
        )
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500)
        )

        // 잠시 대기 후 홈 화면으로 이동
        delay(1500)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        PrimaryLight,
                        Primary,
                        PrimaryDark
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = "MoneyTalk Logo",
                modifier = Modifier.size(100.dp),
                tint = OnPrimary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "MoneyTalk",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = OnPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "스마트한 가계부",
                fontSize = 16.sp,
                color = OnPrimary.copy(alpha = 0.8f)
            )
        }
    }
}
