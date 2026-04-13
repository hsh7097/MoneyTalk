package com.sanha.moneytalk.feature.intro.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.OnPrimary
import com.sanha.moneytalk.core.theme.Primary
import com.sanha.moneytalk.core.theme.PrimaryDark
import com.sanha.moneytalk.core.theme.PrimaryLight
import com.sanha.moneytalk.core.util.toDpTextUnit

/**
 * 온보딩 인트로 화면.
 *
 * 3페이지 HorizontalPager(스와이프)로 앱의 핵심 가치를 전달.
 * 스플래시 이후, 권한 요청 이전에 표시.
 * "시작하기" 버튼은 마지막 페이지에서만 노출.
 *
 * 레이아웃 구조: 루트 Box 안에 4개 레이어
 *  1) HorizontalPager (fillMaxSize) — 전체 화면 스와이프
 *  2) 건너뛰기 (TopEnd)
 *  3) 페이지 인디케이터 (BottomCenter, 고정 위치)
 *  4) 시작하기 버튼 (BottomCenter, 고정 위치, 마지막 페이지만)
 *
 * @param onOnboardingFinished 온보딩 완료(시작하기/건너뛰기) 콜백
 */
@Composable
fun OnboardingScreen(
    onOnboardingFinished: () -> Unit
) {
    val pages = listOf(
        OnboardingPage(
            emoji = "\uD83D\uDCB3", // 💳
            titleRes = R.string.onboarding_title_1,
            descRes = R.string.onboarding_desc_1,
            featureRes = listOf(
                R.string.onboarding_feature_1_1,
                R.string.onboarding_feature_1_2,
                R.string.onboarding_feature_1_3
            )
        ),
        OnboardingPage(
            emoji = "\uD83E\uDD16", // 🤖
            titleRes = R.string.onboarding_title_2,
            descRes = R.string.onboarding_desc_2,
            featureRes = listOf(
                R.string.onboarding_feature_2_1,
                R.string.onboarding_feature_2_2,
                R.string.onboarding_feature_2_3
            )
        ),
        OnboardingPage(
            emoji = "\uD83D\uDD12", // 🔒
            titleRes = R.string.onboarding_title_3,
            descRes = R.string.onboarding_desc_3,
            featureRes = listOf(
                R.string.onboarding_feature_3_1,
                R.string.onboarding_feature_3_2,
                R.string.onboarding_feature_3_3
            )
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(PrimaryLight, Primary, PrimaryDark)
                )
            )
    ) {
        // 1) 전체 화면 스와이프 페이저
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                OnboardingPageContent(pages[page])
            }
        }

        // 2) 건너뛰기 버튼 (상단 우측) — 마지막 페이지에서는 숨김
        AnimatedVisibility(
            visible = !isLastPage,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 8.dp, top = 8.dp)
        ) {
            TextButton(onClick = onOnboardingFinished) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    color = OnPrimary.copy(alpha = 0.7f),
                    fontSize = 14.toDpTextUnit
                )
            }
        }

        // 3) 페이지 인디케이터 (하단 고정)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 100.dp)
        ) {
            pages.indices.forEach { index ->
                PageIndicatorDot(isActive = pagerState.currentPage == index)
            }
        }

        // 4) "시작하기" 버튼 (하단 고정) — 마지막 페이지에서만 노출
        AnimatedVisibility(
            visible = isLastPage,
            enter = fadeIn(tween(300)) + slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(300)
            ),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            Button(
                onClick = onOnboardingFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OnPrimary,
                    contentColor = Primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_start),
                    fontSize = 16.toDpTextUnit,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** 온보딩 페이지 데이터 */
private data class OnboardingPage(
    val emoji: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val featureRes: List<Int>
)

/** 온보딩 페이지 콘텐츠 (이모지 + 제목 + 설명 + feature bullets) */
@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = page.emoji,
            fontSize = 72.toDpTextUnit
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(page.titleRes),
            fontSize = 26.toDpTextUnit,
            fontWeight = FontWeight.Bold,
            color = OnPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(page.descRes),
            fontSize = 15.toDpTextUnit,
            color = OnPrimary.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            lineHeight = 22.toDpTextUnit
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Feature bullets
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = OnPrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            page.featureRes.forEach { featureStringRes ->
                Text(
                    text = stringResource(featureStringRes),
                    fontSize = 14.toDpTextUnit,
                    color = OnPrimary.copy(alpha = 0.9f),
                    lineHeight = 20.toDpTextUnit
                )
            }
        }
    }
}

/** 페이지 인디케이터 점 */
@Composable
private fun PageIndicatorDot(isActive: Boolean) {
    val color by animateColorAsState(
        targetValue = if (isActive) OnPrimary else OnPrimary.copy(alpha = 0.3f),
        animationSpec = tween(durationMillis = 300),
        label = "indicator_color"
    )
    Box(
        modifier = Modifier
            .size(if (isActive) 10.dp else 8.dp)
            .clip(CircleShape)
            .background(color)
    )
}
