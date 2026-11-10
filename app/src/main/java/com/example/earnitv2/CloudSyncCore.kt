package com.kaleel.earnitv2

import java.security.MessageDigest

/*
 * Cloud sync core. Pure Kotlin, no Android imports, so the merge rules are unit-testable.
 *
 * Model: every synced item is a record (collection, id, JSON payload, last-writer timestamp,
 * tombstone flag). Devices merge last-writer-wins per record. docs/CLOUD_SYNC.md is the contract
 * other clients (iOS) implement against.
 */

/** Minimal JSON reader/writer so the sync core has no Android or library dependency. */
internal object SyncJson {
    fun encode(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    fun decode(text: String): Any? {
        val parser = Parser(text)
        parser.skipWhitespace()
        val value = parser.readValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw IllegalArgumentException("Trailing JSON content")
        return value
    }

    /** Same value with object keys sorted, so equal content always produces equal text. */
    fun canonicalize(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries
            .sortedBy { it.key.toString() }
            .associateTo(LinkedHashMap<String, Any?>()) { it.key.toString() to canonicalize(it.value) }
        is Iterable<*> -> value.map(::canonicalize)
        else -> value
    }

    /** Canonical text for JSON that came from elsewhere (Postgres jsonb reorders keys). */
    fun canonicalText(text: String): String = encode(canonicalize(decode(text)))

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Boolean -> out.append(if (value) "true" else "false")
            is Int, is Long -> out.append(value.toString())
            is Number -> {
                val number = value.toDouble()
                if (number.isNaN() || number.isInfinite()) out.append("null")
                else if (number == Math.floor(number) && Math.abs(number) < 1e15) out.append(number.toLong().toString())
                else out.append(number.toString())
            }
            is Map<*, *> -> {
                out.append('{')
                var first = true
                value.forEach { (key, item) ->
                    if (!first) out.append(',')
                    first = false
                    writeString(out, key.toString())
                    out.append(':')
                    write(out, item)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                value.forEach { item ->
                    if (!first) out.append(',')
                    first = false
                    write(out, item)
                }
                out.append(']')
            }
            else -> writeString(out, value.toString())
        }
    }

    private fun writeString(out: StringBuilder, value: String) {
        out.append('"')
        value.forEach { char ->
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (char < ' ') {
                    out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(char)
                }
            }
        }
        out.append('"')
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun atEnd(): Boolean = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun readValue(): Any? {
            skipWhitespace()
            if (atEnd()) throw IllegalArgumentException("Unexpected end of JSON")
            return when (val char = text[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (char == '-' || char.isDigit()) readNumber()
                else throw IllegalArgumentException("Unexpected character '$char' at $index")
            }
        }

        private fun readLiteral(word: String, value: Any?): Any? {
            if (!text.startsWith(word, index)) throw IllegalArgumentException("Invalid literal at $index")
            index += word.length
            return value
        }

        private fun readNumber(): Any {
            val start = index
            if (text[index] == '-') index++
            while (index < text.length && (text[index].isDigit() || text[index] in ".eE+-")) index++
            val raw = text.substring(start, index)
            return raw.toLongOrNull() ?: raw.toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid number '$raw'")
        }

        private fun readString(): String {
            index++ // opening quote
            val out = StringBuilder()
            while (true) {
                if (atEnd()) throw IllegalArgumentException("Unterminated string")
                val char = text[index++]
                when (char) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd()) throw IllegalArgumentException("Unterminated escape")
                        when (val escaped = text[index++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'u' -> {
                                if (index + 4 > text.length) throw IllegalArgumentException("Bad unicode escape")
                                out.append(text.substring(index, index + 4).toInt(16).toChar())
                                index += 4
                            }
                            else -> throw IllegalArgumentException("Bad escape '\\$escaped'")
                        }
                    }
                    else -> out.append(char)
                }
            }
        }

        private fun readArray(): List<Any?> {
            index++ // [
            val items = ArrayList<Any?>()
            skipWhitespace()
            if (!atEnd() && text[index] == ']') {
                index++
                return items
            }
            while (true) {
                items += readValue()
                skipWhitespace()
                if (atEnd()) throw IllegalArgumentException("Unterminated array")
                when (text[index++]) {
                    ',' -> Unit
                    ']' -> return items
                    else -> throw IllegalArgumentException("Expected , or ] at ${index - 1}")
                }
            }
        }

        private fun readObject(): Map<String, Any?> {
            index++ // {
            val fields = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (!atEnd() && text[index] == '}') {
                index++
                return fields
            }
            while (true) {
                skipWhitespace()
                if (atEnd() || text[index] != '"') throw IllegalArgumentException("Expected object key at $index")
                val key = readString()
                skipWhitespace()
                if (atEnd() || text[index++] != ':') throw IllegalArgumentException("Expected : after key")
                fields[key] = readValue()
                skipWhitespace()
                if (atEnd()) throw IllegalArgumentException("Unterminated object")
                when (text[index++]) {
                    ',' -> Unit
                    '}' -> return fields
                    else -> throw IllegalArgumentException("Expected , or } at ${index - 1}")
                }
            }
        }
    }
}

internal data class SyncRecord(
    val collection: String,
    val recordId: String,
    /** Canonical JSON object text. Empty object for tombstones. */
    val payloadJson: String,
    val updatedAtMillis: Long,
    val deleted: Boolean,
    val deviceId: String,
    val platform: String
)

internal object SyncCollections {
    const val RULES = "rules"
    const val PLATFORM_ANDROID = "android"
}

/**
 * Rule <-> sync payload. Only the terms of a Rule are synced. `enabled`, `lastActivatedAtMillis`
 * and `inactiveReason` are device-local: pauses, entitlement downgrades and Strict Mode live per
 * device, and a remote device must never be able to switch a local Rule off.
 */
internal object RuleSyncCodec {
    const val PAYLOAD_VERSION = 1

    fun encode(rule: EarnItRuleStore.Rule): String {
        val payload = LinkedHashMap<String, Any?>()
        payload["v"] = PAYLOAD_VERSION
        payload["type"] = rule.type.name
        payload["earnApps"] = rule.earnApps.map(::appJson)
        payload["blockedApps"] = rule.blockedApps.map(::appJson)
        payload["blockedDomains"] = rule.normalizedBlockedDomains
        payload["ratio"] = rule.rewardSecondsPerProductiveSecond
        payload["activeDays"] = rule.activeDays.sorted()
        payload["windows"] = rule.effectiveTimeWindows.map { window ->
            linkedMapOf<String, Any?>("start" to window.startMinute, "end" to window.endMinute)
        }
        payload["requirements"] = rule.requirements.map { requirement ->
            linkedMapOf<String, Any?>(
                "id" to requirement.app.packageName,
                "name" to requirement.app.name,
                "seconds" to requirement.requiredSeconds
            )
        }
        payload["requiresDailyCommitment"] = rule.requiresDailyCommitment
        return SyncJson.encode(SyncJson.canonicalize(payload))
    }

    /** Returns null when the payload is not a Rule this client understands. */
    fun decode(ruleId: String, payloadJson: String): EarnItRuleStore.Rule? {
        val payload = runCatching { SyncJson.decode(payloadJson) as? Map<*, *> }.getOrNull() ?: return null
        val version = (payload["v"] as? Number)?.toInt() ?: return null
        if (version > PAYLOAD_VERSION) return null
        val type = (payload["type"] as? String)
            ?.let { name -> EarnItRuleStore.RuleType.values().firstOrNull { it.name == name } }
            ?: return null
        val earnApps = apps(payload["earnApps"])
        val blockedApps = apps(payload["blockedApps"])
        val blockedDomains = (payload["blockedDomains"] as? List<*>).orEmpty().filterIsInstance<String>()
        if (blockedApps.isEmpty() && blockedDomains.isEmpty()) return null
        val windows = (payload["windows"] as? List<*>).orEmpty().mapNotNull { item ->
            val window = item as? Map<*, *> ?: return@mapNotNull null
            val start = (window["start"] as? Number)?.toInt() ?: return@mapNotNull null
            val end = (window["end"] as? Number)?.toInt() ?: return@mapNotNull null
            EarnItRuleStore.TimeWindow(start.coerceIn(0, 1_439), end.coerceIn(1, 1_440))
        }.ifEmpty { listOf(EarnItRuleStore.TimeWindow(0, 1_440)) }
        val requirements = (payload["requirements"] as? List<*>).orEmpty().mapNotNull { item ->
            val requirement = item as? Map<*, *> ?: return@mapNotNull null
            val id = (requirement["id"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val seconds = (requirement["seconds"] as? Number)?.toLong() ?: return@mapNotNull null
            EarnItRuleStore.RuleRequirement(
                app = EarnItRuleStore.RuleApp(id, (requirement["name"] as? String) ?: id),
                requiredSeconds = seconds.coerceAtLeast(1L)
            )
        }
        val activeDays = (payload["activeDays"] as? List<*>).orEmpty()
            .mapNotNull { (it as? Number)?.toInt() }
            .filter { it in 1..7 }
            .toSet()
        val primaryEarnApp = earnApps.firstOrNull()
        return EarnItRuleStore.Rule(
            id = ruleId,
            productivePackage = primaryEarnApp?.packageName.orEmpty(),
            productiveName = primaryEarnApp?.name.orEmpty(),
            blockedApps = blockedApps,
            rewardSecondsPerProductiveSecond = ((payload["ratio"] as? Number)?.toInt() ?: 1).coerceAtLeast(1),
            activeDays = activeDays,
            startMinute = windows.first().startMinute,
            endMinute = windows.first().endMinute,
            enabled = true,
            type = type,
            productiveApps = earnApps,
            requirements = requirements,
            timeWindows = windows,
            blockedDomains = blockedDomains,
            requiresDailyCommitment = payload["requiresDailyCommitment"] == true &&
                type == EarnItRuleStore.RuleType.CompleteToUnlock
        )
    }

    /** Applies synced terms onto an existing local Rule, keeping its device-local state. */
    fun mergeIntoLocal(local: EarnItRuleStore.Rule?, remote: EarnItRuleStore.Rule): EarnItRuleStore.Rule {
        if (local == null) return remote
        return remote.copy(
            enabled = local.enabled,
            lastActivatedAtMillis = local.lastActivatedAtMillis,
            inactiveReason = local.inactiveReason
        )
    }

    private fun appJson(app: EarnItRuleStore.RuleApp): Map<String, Any?> =
        linkedMapOf("id" to app.packageName, "name" to app.name)

    private fun apps(raw: Any?): List<EarnItRuleStore.RuleApp> {
        return (raw as? List<*>).orEmpty().mapNotNull { item ->
            val app = item as? Map<*, *> ?: return@mapNotNull null
            val id = (app["id"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            EarnItRuleStore.RuleApp(id, (app["name"] as? String) ?: id)
        }.distinctBy { it.packageName }
    }
}

/** What this device last knew about one record. */
internal data class SyncJournalEntry(
    val contentHash: String,
    val updatedAtMillis: Long,
    val deleted: Boolean
)

internal data class SyncPlan(
    /** Remote records to write into local storage. */
    val applyUpserts: List<SyncRecord>,
    /** Record ids to delete locally. */
    val applyDeletes: List<String>,
    /** Local state to send to the server. */
    val pushes: List<SyncRecord>,
    /** Journal to persist once the plan has been carried out. */
    val journal: Map<String, SyncJournalEntry>,
    /** Remote changes held back because Strict Mode protects the local Rule. */
    val heldBackIds: List<String>
)

internal object SyncPlanner {
    fun contentHash(payloadJson: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(payloadJson.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
    }

    /**
     * Last-writer-wins merge of one collection.
     *
     * Local edits are detected by diffing [localPayloads] against [journal], so no save path has
     * to remember to record a change. [protectedIds] are local records a remote change may not
     * weaken (Strict Mode): the local version is kept and re-pushed as the newest write.
     */
    fun plan(
        collection: String,
        localPayloads: Map<String, String>,
        journal: Map<String, SyncJournalEntry>,
        remote: List<SyncRecord>,
        nowMillis: Long,
        deviceId: String,
        platform: String,
        protectedIds: Set<String> = emptySet()
    ): SyncPlan {
        val working = LinkedHashMap(journal)

        // 1. Fold local edits and deletions into the journal.
        localPayloads.forEach { (id, payload) ->
            val hash = contentHash(payload)
            val known = working[id]
            if (known == null || known.deleted || known.contentHash != hash) {
                working[id] = SyncJournalEntry(hash, nowMillis, deleted = false)
            }
        }
        journal.forEach { (id, known) ->
            if (!known.deleted && id !in localPayloads) {
                working[id] = SyncJournalEntry("", nowMillis, deleted = true)
            }
        }

        val applyUpserts = ArrayList<SyncRecord>()
        val applyDeletes = ArrayList<String>()
        val pushes = ArrayList<SyncRecord>()
        val heldBack = ArrayList<String>()
        val remoteById = remote
            .filter { it.collection == collection && it.platform == platform }
            .associateBy { it.recordId }

        fun localRecord(id: String, entry: SyncJournalEntry) = SyncRecord(
            collection = collection,
            recordId = id,
            payloadJson = if (entry.deleted) "{}" else localPayloads[id] ?: "{}",
            updatedAtMillis = entry.updatedAtMillis,
            deleted = entry.deleted,
            deviceId = deviceId,
            platform = platform
        )

        // 2. Compare each record with the server.
        (working.keys + remoteById.keys).toSet().forEach { id ->
            val local = working[id]
            val server = remoteById[id]
            when {
                server == null && local != null -> pushes += localRecord(id, local)
                server != null && local == null -> {
                    if (server.deleted) {
                        working[id] = SyncJournalEntry("", server.updatedAtMillis, deleted = true)
                    } else {
                        applyUpserts += server
                        working[id] = SyncJournalEntry(contentHash(server.payloadJson), server.updatedAtMillis, false)
                    }
                }
                server != null && local != null -> {
                    val sameContent = server.deleted == local.deleted &&
                        (local.deleted || contentHash(server.payloadJson) == local.contentHash)
                    val serverWins = server.updatedAtMillis > local.updatedAtMillis ||
                        (server.updatedAtMillis == local.updatedAtMillis && server.deviceId > deviceId)
                    when {
                        sameContent -> working[id] = local.copy(
                            updatedAtMillis = maxOf(local.updatedAtMillis, server.updatedAtMillis)
                        )
                        serverWins && id in protectedIds && !local.deleted -> {
                            val kept = local.copy(updatedAtMillis = maxOf(nowMillis, server.updatedAtMillis + 1))
                            working[id] = kept
                            pushes += localRecord(id, kept)
                            heldBack += id
                        }
                        serverWins && server.deleted -> {
                            if (!local.deleted) applyDeletes += id
                            working[id] = SyncJournalEntry("", server.updatedAtMillis, deleted = true)
                        }
                        serverWins -> {
                            applyUpserts += server
                            working[id] = SyncJournalEntry(contentHash(server.payloadJson), server.updatedAtMillis, false)
                        }
                        else -> pushes += localRecord(id, local)
                    }
                }
            }
        }
        return SyncPlan(applyUpserts, applyDeletes, pushes, working, heldBack)
    }
}

internal object SyncJournalCodec {
    fun encode(journal: Map<String, SyncJournalEntry>): String {
        return SyncJson.encode(journal.map { (id, entry) ->
            linkedMapOf<String, Any?>(
                "id" to id,
                "h" to entry.contentHash,
                "t" to entry.updatedAtMillis,
                "d" to entry.deleted
            )
        })
    }

    fun decode(raw: String?): Map<String, SyncJournalEntry> {
        if (raw.isNullOrBlank()) return emptyMap()
        val items = runCatching { SyncJson.decode(raw) as? List<*> }.getOrNull() ?: return emptyMap()
        val journal = LinkedHashMap<String, SyncJournalEntry>()
        items.forEach { item ->
            val entry = item as? Map<*, *> ?: return@forEach
            val id = entry["id"] as? String ?: return@forEach
            journal[id] = SyncJournalEntry(
                contentHash = entry["h"] as? String ?: "",
                updatedAtMillis = (entry["t"] as? Number)?.toLong() ?: 0L,
                deleted = entry["d"] == true
            )
        }
        return journal
    }
}
