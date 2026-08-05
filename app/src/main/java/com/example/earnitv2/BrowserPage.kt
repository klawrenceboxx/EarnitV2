package com.kaleel.earnitv2

import android.os.Bundle
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

data class BrowserPage(val browserPackage: String, val normalizedHost: String)

interface BrowserAdapter {
    fun supports(packageName: String): Boolean
    fun currentPage(root: AccessibilityNodeInfo?): BrowserPage?
    fun redirectCurrentPageToPlaceholder(root: AccessibilityNodeInfo?): Boolean
    fun isPlaceholderVisible(root: AccessibilityNodeInfo?): Boolean
}

class ChromeBrowserAdapter : BrowserAdapter {
    override fun supports(packageName: String): Boolean = packageName == CHROME_PACKAGE

    override fun currentPage(root: AccessibilityNodeInfo?): BrowserPage? {
        root ?: return null
        addressBarIds.forEach { id ->
            root.findAccessibilityNodeInfosByViewId(id).firstNotNullOfOrNull { node ->
                parseAddressBarText(node.text?.toString() ?: node.contentDescription?.toString())
            }?.let { return BrowserPage(CHROME_PACKAGE, it) }
        }
        return boundedBreadthFirst(root)?.let { BrowserPage(CHROME_PACKAGE, it) }
    }

    override fun redirectCurrentPageToPlaceholder(root: AccessibilityNodeInfo?): Boolean {
        root ?: return false
        val urlBar = addressBarIds.asSequence()
            .flatMap { root.findAccessibilityNodeInfosByViewId(it).asSequence() }
            .firstOrNull { it.viewIdResourceName?.endsWith(":id/url_bar") == true }
            ?: return false
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                LOCAL_PLACEHOLDER
            )
        }
        urlBar.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        urlBar.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val replaced = urlBar.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        val submitted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            urlBar.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        } else {
            false
        }
        return replaced && submitted
    }

    override fun isPlaceholderVisible(root: AccessibilityNodeInfo?): Boolean {
        root ?: return false
        return addressBarIds.asSequence()
            .flatMap { root.findAccessibilityNodeInfosByViewId(it).asSequence() }
            .any { it.viewIdResourceName?.endsWith(":id/url_bar") == true && it.text?.toString() == LOCAL_PLACEHOLDER }
    }

    private fun boundedBreadthFirst(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_FALLBACK_NODES) {
            val node = queue.removeFirst()
            val likelyAddressBar = node.isEditable || node.viewIdResourceName?.contains("url_bar") == true
            if (likelyAddressBar) {
                parseAddressBarText(node.text?.toString() ?: node.contentDescription?.toString())?.let { return it }
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let(queue::addLast) }
        }
        return null
    }

    companion object {
        const val CHROME_PACKAGE = "com.android.chrome"
        private const val MAX_FALLBACK_NODES = 80
        const val LOCAL_PLACEHOLDER = "about:blank"
        private val addressBarIds = listOf(
            "$CHROME_PACKAGE:id/url_bar",
            "$CHROME_PACKAGE:id/location_bar_status"
        )

        fun parseAddressBarText(text: String?): String? = DomainNormalizer.normalize(text)
    }
}

/** Holds only a configured-domain match and always clears it when Chrome is left. */
class CurrentBrowserPageObserver(
    private val adapters: List<BrowserAdapter> = listOf(ChromeBrowserAdapter())
) {
    private var page: BrowserPage? = null

    fun observe(packageName: String, root: AccessibilityNodeInfo?): BrowserPage? {
        val adapter = adapters.firstOrNull { it.supports(packageName) }
        page = adapter?.currentPage(root)
        return page
    }

    fun clear() { page = null }
    fun current(): BrowserPage? = page
    fun isSupportedBrowser(packageName: String): Boolean = adapters.any { it.supports(packageName) }
    fun redirectCurrentPageToPlaceholder(packageName: String, root: AccessibilityNodeInfo?): Boolean =
        adapters.firstOrNull { it.supports(packageName) }
            ?.redirectCurrentPageToPlaceholder(root) == true
    fun isPlaceholderVisible(packageName: String, root: AccessibilityNodeInfo?): Boolean =
        adapters.firstOrNull { it.supports(packageName) }
            ?.isPlaceholderVisible(root) == true
}

class WebsiteRedirectGate {
    private var pendingKey: String? = null

    fun begin(ruleId: String, domain: String): Boolean {
        val key = "$ruleId|$domain"
        if (pendingKey == key) return false
        pendingKey = key
        return true
    }

    fun complete() { pendingKey = null }
    fun clear() { pendingKey = null }
    fun isPending(): Boolean = pendingKey != null
}
