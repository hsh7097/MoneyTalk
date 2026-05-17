package com.sanha.moneytalk.core.notification

/**
 * 앱 알림을 거래 후보로 신뢰할 금융/결제 앱 패키지 목록.
 *
 * AndroidManifest.xml의 <queries>는 설치 여부 확인 범위이고, 이 목록은 실제 알림 처리 대상이다.
 */
object FinancialAppPackageRegistry {

    private val appNotificationPackages = setOf(
        // 인터넷은행/은행
        "com.kakaobank.channel",
        "com.kbstar.liivbank",
        "com.kbstar.reboot",
        "com.kbstar.kbbank",
        "com.wooribank.smart.npib",
        "kr.co.citibank.citimobile",
        "kr.co.kfcc.mobilebank",
        "com.knb.psb",

        // 카드
        "com.kbcard.cxh.appcard",
        "com.lcacApp",
        "com.wooricard.smartapp",
        "nh.smart.nhallonepay",
        "com.hyundaicard.appcard",
        "kr.co.samsungcard.mpocket",
        "net.ib.android.smcard",
        "com.hanaskcard.paycla",
        "kr.co.hanamembers.hmscustomer",
        "com.shinhancard.smartshinhan",
        "com.shcard.smartpay",
        "com.shinhan.smartcaremgr",

        // 간편결제/지갑
        "com.ssg.serviceapp.android.egiftcertificate",
        "kvp.jjy.MispAndroid320",
        "viva.republica.toss",
        "com.nhnent.payapp",
        "com.mysmilepay.app",
        "com.samsung.android.spay",
        "com.samsung.android.spaylite",
        "com.sec.android.wallet",
        "com.lge.lgpay",
        "com.lottemembers.android",

        // 계좌이체/휴대폰/교통 결제
        "com.kftc.bankpay.android",
        "com.nh.cashcardapp",
        "kr.danal.app.damoum",
        "com.tmoney.inapp",
        "com.tmoney.nfc_pay",
        "uplus.membership"
    )

    fun isSupportedAppNotificationPackage(packageName: String): Boolean =
        packageName in appNotificationPackages
}
