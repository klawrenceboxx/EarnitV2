package com.example.earnitv2

import android.content.pm.ApplicationInfo

enum class AppPickerCategory(val label: String) {
    All("All"),
    Social("Social"),
    Games("Games"),
    Entertainment("Entertainment")
}

fun selectedAppCountLabel(count: Int): String {
    return "$count ${if (count == 1) "app" else "apps"} selected"
}

fun filterLaunchableApps(
    apps: List<EarnItRuleStore.LaunchableApp>,
    category: AppPickerCategory,
    query: String
): List<EarnItRuleStore.LaunchableApp> {
    val categoryFiltered = if (category == AppPickerCategory.All) {
        apps
    } else {
        apps.filter { classifyLaunchableApp(it) == category }
    }
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return categoryFiltered
    return categoryFiltered.filter { app ->
        app.name.contains(trimmedQuery, ignoreCase = true) ||
            app.packageName.contains(trimmedQuery, ignoreCase = true)
    }
}

fun appPickerEmptyText(category: AppPickerCategory, query: String): String {
    if (query.isNotBlank()) return "No apps match your search."
    return when (category) {
        AppPickerCategory.All -> "No apps found."
        AppPickerCategory.Social -> "No Social apps found."
        AppPickerCategory.Games -> "No Games apps found."
        AppPickerCategory.Entertainment -> "No Entertainment apps found."
    }
}

fun classifyLaunchableApp(app: EarnItRuleStore.LaunchableApp): AppPickerCategory? {
    return categoryFromApplicationInfo(app.applicationCategory)
        ?: categoryFromKnownPackage(app.packageName, app.name)
}

private fun categoryFromApplicationInfo(category: Int?): AppPickerCategory? {
    return when (category) {
        ApplicationInfo.CATEGORY_SOCIAL -> AppPickerCategory.Social
        ApplicationInfo.CATEGORY_GAME -> AppPickerCategory.Games
        ApplicationInfo.CATEGORY_AUDIO,
        ApplicationInfo.CATEGORY_VIDEO,
        ApplicationInfo.CATEGORY_IMAGE -> AppPickerCategory.Entertainment
        else -> null
    }
}

private fun categoryFromKnownPackage(packageName: String, appName: String): AppPickerCategory? {
    val value = "$packageName $appName".lowercase()
    return when {
        SOCIAL_KEYWORDS.any { it in value } -> AppPickerCategory.Social
        GAME_KEYWORDS.any { it in value } -> AppPickerCategory.Games
        ENTERTAINMENT_KEYWORDS.any { it in value } -> AppPickerCategory.Entertainment
        else -> null
    }
}

private val SOCIAL_KEYWORDS = listOf(
    "instagram",
    "facebook",
    "snapchat",
    "snap.",
    "tiktok",
    "twitter",
    "reddit",
    "pinterest",
    "whatsapp",
    "telegram",
    "discord",
    "threads"
)

private val GAME_KEYWORDS = listOf(
    "game",
    "games",
    "gaming",
    "roblox",
    "minecraft",
    "fortnite",
    "candycrush",
    "clash",
    "pokemon"
)

private val ENTERTAINMENT_KEYWORDS = listOf(
    "youtube",
    "netflix",
    "spotify",
    "hulu",
    "disney",
    "primevideo",
    "twitch",
    "music",
    "video",
    "podcast",
    "stream"
)
