package com.example.earnitv2

import android.content.pm.ApplicationInfo

enum class AppPickerCategory(val label: String) {
    All("All"),
    Social("Social"),
    Games("Games"),
    Entertainment("Entertainment"),
    Productivity("Productivity"),
    Education("Education"),
    Reading("Reading"),
    Health("Health"),
    Finance("Finance"),
    Communication("Communication"),
    Shopping("Shopping"),
    Utilities("Utilities")
}

fun selectedAppCountLabel(count: Int): String {
    return "$count ${if (count == 1) "app" else "apps"} selected"
}

fun selectedAppPreviewLabel(names: List<String>, previewLimit: Int = 3): String {
    if (names.isEmpty()) return "Select one or more apps"
    val preview = names.take(previewLimit).joinToString(", ")
    val remaining = names.size - previewLimit
    return if (remaining > 0) "$preview +$remaining more" else preview
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
    return if (category == AppPickerCategory.All) {
        "No apps found."
    } else {
        "No ${category.label} apps found."
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
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppPickerCategory.Productivity
        ApplicationInfo.CATEGORY_NEWS -> AppPickerCategory.Reading
        ApplicationInfo.CATEGORY_MAPS,
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> AppPickerCategory.Utilities
        else -> null
    }
}

private fun categoryFromKnownPackage(packageName: String, appName: String): AppPickerCategory? {
    val value = "$packageName $appName".lowercase()
    return when {
        SOCIAL_KEYWORDS.any { it in value } -> AppPickerCategory.Social
        GAME_KEYWORDS.any { it in value } -> AppPickerCategory.Games
        ENTERTAINMENT_KEYWORDS.any { it in value } -> AppPickerCategory.Entertainment
        PRODUCTIVITY_KEYWORDS.any { it in value } -> AppPickerCategory.Productivity
        EDUCATION_KEYWORDS.any { it in value } -> AppPickerCategory.Education
        READING_KEYWORDS.any { it in value } -> AppPickerCategory.Reading
        HEALTH_KEYWORDS.any { it in value } -> AppPickerCategory.Health
        FINANCE_KEYWORDS.any { it in value } -> AppPickerCategory.Finance
        COMMUNICATION_KEYWORDS.any { it in value } -> AppPickerCategory.Communication
        SHOPPING_KEYWORDS.any { it in value } -> AppPickerCategory.Shopping
        UTILITIES_KEYWORDS.any { it in value } -> AppPickerCategory.Utilities
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

private val PRODUCTIVITY_KEYWORDS = listOf(
    "notion", "todo", "tasks", "trello", "asana", "office", "word", "excel",
    "docs", "sheets", "drive", "calendar", "dropbox", "evernote", "onenote"
)

private val EDUCATION_KEYWORDS = listOf(
    "duolingo", "khan", "coursera", "quizlet", "udemy", "classroom", "moodle",
    "school", "learn", "study"
)

private val READING_KEYWORDS = listOf(
    "kindle", "books", "ebook", "reader", "readwise", "pocket", "medium", "news"
)

private val HEALTH_KEYWORDS = listOf(
    "health", "fitness", "workout", "fitbit", "strava", "peloton", "myfitnesspal",
    "meditation", "headspace"
)

private val FINANCE_KEYWORDS = listOf(
    "bank", "banking", "finance", "wallet", "invest", "trading", "paypal", "venmo",
    "cashapp", "coinbase", "wealthsimple"
)

private val COMMUNICATION_KEYWORDS = listOf(
    "gmail", "email", "outlook", "messages", "signal", "zoom", "meet", "slack",
    "teams", "phone", "contacts"
)

private val SHOPPING_KEYWORDS = listOf(
    "amazon", "shopping", "shop", "ebay", "etsy", "walmart", "target", "bestbuy"
)

private val UTILITIES_KEYWORDS = listOf(
    "calculator", "clock", "weather", "maps", "files", "settings", "authenticator",
    "scanner", "vpn", "password"
)
