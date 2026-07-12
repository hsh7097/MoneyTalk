package com.sanha.moneytalk.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.sanha.moneytalk.core.util.BuildVariantPolicy

/**
 * AdMob 배너 광고 Composable.
 *
 * 화면 하단에 고정 배치하여 사용.
 * 호출부에서 배너 노출 정책을 확인 후 조건부 렌더링할 것.
 *
 * @param adUnitId 화면별 배너 광고 단위 ID
 */
@Composable
fun BannerAdCompose(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    if (!BuildVariantPolicy.isMonetizationEnabled) {
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val adView = remember(adUnitId) {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            this.adUnitId = adUnitId
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                else -> { /* no-op */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    AndroidView(
        factory = { adView },
        modifier = modifier.fillMaxWidth()
    )
}

/** 배너 광고 단위 ID (비릴리즈 빌드에서는 노출 정책에서 차단) */
object BannerAdIds {
    /** Google 공식 배너 테스트 광고 ID */
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"

    val HOME = if (BuildVariantPolicy.shouldUseProductionAdUnits) {
        "ca-app-pub-4707673176609005/8344902874"
    } else {
        TEST_BANNER
    }
    val HISTORY = if (BuildVariantPolicy.shouldUseProductionAdUnits) {
        "ca-app-pub-4707673176609005/5323629075"
    } else {
        TEST_BANNER
    }
    val CATEGORY_DETAIL = if (BuildVariantPolicy.shouldUseProductionAdUnits) {
        "ca-app-pub-4707673176609005/9633933815"
    } else {
        TEST_BANNER
    }
}
