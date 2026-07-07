package com.example.earnitv2

object AppPackages {
    const val PRODUCTIVE_APP = "com.duolingo"
    const val INSTAGRAM_APP = "com.instagram.android"
    const val LICHESS_APP = "org.lichess.mobileV2"

    val BLOCKED_APPS = mapOf(
        INSTAGRAM_APP to "Instagram",
        LICHESS_APP to "Lichess"
    )

    fun getBlockedAppName(packageName: String): String? = BLOCKED_APPS[packageName]
}
