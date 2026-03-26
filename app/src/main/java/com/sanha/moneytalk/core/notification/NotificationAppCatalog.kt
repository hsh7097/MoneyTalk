package com.sanha.moneytalk.core.notification

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class NotificationTargetApp(
    val packageName: String,
    val appName: String,
    val isRecommended: Boolean
)

object NotificationAppCatalog {

    private data class RecommendedAppSpec(
        val packageName: String,
        val fallbackName: String
    )

    private val recommendedApps = listOf(
        RecommendedAppSpec("com.kakaobank.channel", "카카오뱅크"),
        RecommendedAppSpec("viva.republica.toss", "토스"),
        RecommendedAppSpec("com.kbstar.kbbank", "KB스타뱅킹"),
        RecommendedAppSpec("com.kbcard.cxh.appcard", "KB Pay"),
        RecommendedAppSpec("com.shinhan.sbanking", "신한 SOL뱅크"),
        RecommendedAppSpec("com.shcard.smartpay", "신한카드"),
        RecommendedAppSpec("com.wooricard.wpay", "우리카드"),
        RecommendedAppSpec("com.wooribank.smart.npib", "우리WON뱅킹"),
        RecommendedAppSpec("com.kakao.talk", "카카오톡"),
        RecommendedAppSpec("com.kakaopay.app", "카카오페이"),
        RecommendedAppSpec("com.samsung.android.spay", "삼성 월렛"),
        RecommendedAppSpec("com.samsung.android.rajaampat", "삼성카드"),
        RecommendedAppSpec("com.hyundaicard.appcard", "현대카드"),
        RecommendedAppSpec("com.lotte.lottesmartpay", "롯데카드"),
        RecommendedAppSpec("nh.smart", "NH올원뱅크"),
        RecommendedAppSpec("com.nhcard.nhyappcard", "NH pay"),
        RecommendedAppSpec("com.hanaskcard.paycla", "하나Pay"),
        RecommendedAppSpec("com.hanabank.ebk.channel.android.hananbank", "하나원큐")
    )

    fun getInstalledLaunchableApps(context: Context): List<NotificationTargetApp> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val selfPackageName = context.packageName

        return packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == selfPackageName) return@mapNotNull null

                val appName = runCatching {
                    resolveInfo.loadLabel(packageManager).toString()
                }.getOrNull().orEmpty().ifBlank {
                    packageName.substringAfterLast('.')
                }

                NotificationTargetApp(
                    packageName = packageName,
                    appName = appName,
                    isRecommended = recommendedApps.any { it.packageName == packageName }
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareByDescending<NotificationTargetApp> { it.isRecommended }
                    .thenBy { it.appName.lowercase() }
            )
    }

    fun getDefaultSelectedPackages(installedApps: List<NotificationTargetApp>): Set<String> {
        val installedByPackage = installedApps.associateBy { it.packageName }
        return recommendedApps.mapNotNull { spec ->
            installedByPackage[spec.packageName]?.packageName
        }.toSet()
    }

    fun resolveDisplayName(
        packageName: String,
        installedApps: List<NotificationTargetApp>
    ): String {
        installedApps.firstOrNull { it.packageName == packageName }?.let { return it.appName }
        return recommendedApps.firstOrNull { it.packageName == packageName }?.fallbackName
            ?: packageName
    }
}
