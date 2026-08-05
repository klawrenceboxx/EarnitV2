package com.kaleel.earnitv2

import java.net.IDN
import java.net.URI

/** A validated, ASCII, privacy-safe hostname stored on a Rule. */
@JvmInline
value class BlockedDomain private constructor(val value: String) {
    companion object {
        fun parse(input: String?): BlockedDomain? = DomainNormalizer.normalize(input)?.let(::BlockedDomain)
    }

    override fun toString(): String = value
}

object DomainNormalizer {
    private val label = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
    private val forbiddenScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    fun normalize(input: String?): String? {
        val raw = input?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (forbiddenScheme.containsMatchIn(raw) &&
            !raw.startsWith("http://", true) && !raw.startsWith("https://", true)
        ) return null
        if (raw.any { it.isWhitespace() || it.isISOControl() }) return null

        val uri = runCatching { URI(if (raw.contains("://")) raw else "https://$raw") }.getOrNull()
            ?: return null
        if (uri.scheme !in setOf("http", "https") || uri.userInfo != null) return null
        var host = (uri.host ?: uri.rawAuthority?.substringBeforeLast(':')?.takeIf { ':' !in it })
            ?.trimEnd('.') ?: return null
        host = runCatching { IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES) }.getOrNull()
            ?.lowercase() ?: return null
        while (host.startsWith("www.") || host.startsWith("m.")) host = host.substringAfter('.')
        if (host.length !in 1..253 || host.contains("..") || '.' !in host) return null
        val labels = host.split('.')
        if (labels.any { !label.matches(it) } || labels.last().length < 2 || labels.last().all(Char::isDigit)) return null
        return host
    }
}

object DomainMatcher {
    fun matches(blockedDomain: String, currentHost: String): Boolean {
        val blocked = DomainNormalizer.normalize(blockedDomain) ?: return false
        val current = normalizeObservedHost(currentHost) ?: return false
        return current == blocked || current.endsWith(".$blocked")
    }

    fun normalizeObservedHost(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val withScheme = if (raw.contains("://")) raw else "https://$raw"
        val host = runCatching { URI(withScheme).host }.getOrNull() ?: return null
        return runCatching { IDN.toASCII(host.trimEnd('.'), IDN.USE_STD3_ASCII_RULES).lowercase() }.getOrNull()
    }
}
