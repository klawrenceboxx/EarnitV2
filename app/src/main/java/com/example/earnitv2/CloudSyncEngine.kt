package com.kaleel.earnitv2

import android.content.Context

/** A signed-in cloud session. Tokens stay on this device and are excluded from Android backup. */
internal data class CloudSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long
)

internal sealed interface CloudResult<out T> {
    data class Ok<T>(val value: T) : CloudResult<T>
    /** The session is no longer valid; the user has to sign in again. */
    object Unauthorized : CloudResult<Nothing>
    /** Network or server trouble; safe to retry later. */
    data class Retryable(val message: String) : CloudResult<Nothing>
    /** The request itself was rejected (bad code, bad email, missing backend setup). */
    data class Rejected(val message: String) : CloudResult<Nothing>
}

/** Everything sync needs from the server. Faked in tests, Supabase in production. */
internal interface CloudSyncBackend {
    val isConfigured: Boolean
    fun requestEmailCode(email: String): CloudResult<Unit>
    fun verifyEmailCode(email: String, code: String, nowMillis: Long): CloudResult<CloudSession>
    fun refresh(session: CloudSession, nowMillis: Long): CloudResult<CloudSession>
    fun signOut(session: CloudSession)
    fun fetch(session: CloudSession, collection: String): CloudResult<List<SyncRecord>>
    fun push(session: CloudSession, records: List<SyncRecord>): CloudResult<Unit>
}

internal enum class CloudSyncOutcome { Synced, NotConfigured, SignedOut, SessionExpired, Offline, Failed }

internal data class CloudSyncReport(
    val outcome: CloudSyncOutcome,
    val pulled: Int = 0,
    val pushed: Int = 0,
    val heldBackByStrictMode: Int = 0,
    val message: String? = null
)

/** Persistence for the session, the per-record journal and the last sync result. */
internal class CloudSyncStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun session(): CloudSession? {
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val userId = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        return CloudSession(
            userId = userId,
            email = prefs.getString(KEY_EMAIL, null).orEmpty(),
            accessToken = prefs.getString(KEY_ACCESS_TOKEN, null).orEmpty(),
            refreshToken = refreshToken,
            expiresAtMillis = prefs.getLong(KEY_EXPIRES_AT, 0L)
        )
    }

    fun saveSession(session: CloudSession) {
        val previousUserId = prefs.getString(KEY_USER_ID, null)
        val editor = prefs.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.expiresAtMillis)
        // The journal describes one account's server state. Never carry it to another account.
        if (previousUserId != null && previousUserId != session.userId) editor.remove(KEY_RULE_JOURNAL)
        editor.commit()
    }

    /** Forgets the account. Local Rules are left exactly as they are. */
    fun clearSession() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_RULE_JOURNAL)
            .remove(KEY_LAST_SYNC_AT)
            .remove(KEY_LAST_OUTCOME)
            .commit()
    }

    /** Drops only the tokens after the server rejected them; keeps the journal for re-sign-in. */
    fun invalidateTokens() {
        prefs.edit().remove(KEY_ACCESS_TOKEN).remove(KEY_REFRESH_TOKEN).remove(KEY_EXPIRES_AT).commit()
    }

    fun signedInEmail(): String? = session()?.email

    fun ruleJournal(): Map<String, SyncJournalEntry> = SyncJournalCodec.decode(prefs.getString(KEY_RULE_JOURNAL, null))

    fun saveRuleJournal(journal: Map<String, SyncJournalEntry>) {
        prefs.edit().putString(KEY_RULE_JOURNAL, SyncJournalCodec.encode(journal)).commit()
    }

    fun deviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        return java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).commit()
        }
    }

    fun lastSyncAtMillis(): Long = prefs.getLong(KEY_LAST_SYNC_AT, 0L)

    fun lastOutcome(): CloudSyncOutcome? = prefs.getString(KEY_LAST_OUTCOME, null)
        ?.let { name -> CloudSyncOutcome.values().firstOrNull { it.name == name } }

    fun recordOutcome(report: CloudSyncReport, nowMillis: Long) {
        val editor = prefs.edit().putString(KEY_LAST_OUTCOME, report.outcome.name)
        if (report.outcome == CloudSyncOutcome.Synced) editor.putLong(KEY_LAST_SYNC_AT, nowMillis)
        editor.commit()
    }

    companion object {
        /** Excluded from Android backup in res/xml/backup_rules.xml and data_extraction_rules.xml. */
        const val PREFS_NAME = "earnit_cloud_sync"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_RULE_JOURNAL = "rule_journal"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_LAST_OUTCOME = "last_outcome"
    }
}

/**
 * Runs one sync pass for Rules: detect local edits, merge with the server last-writer-wins,
 * apply remote changes locally, push local changes. Blocking; call it off the main thread.
 */
internal class CloudSyncEngine(
    private val context: Context,
    private val backend: CloudSyncBackend,
    private val store: CloudSyncStore = CloudSyncStore(context),
    /** True while Strict Mode forbids weakening Rules; remote edits and deletes are held back. */
    private val strictModeProtecting: () -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun sync(): CloudSyncReport {
        val report = synchronized(LOCK) { runSync() }
        store.recordOutcome(report, nowMillis())
        return report
    }

    private fun runSync(): CloudSyncReport {
        if (!backend.isConfigured) return CloudSyncReport(CloudSyncOutcome.NotConfigured)
        var session = store.session() ?: return CloudSyncReport(CloudSyncOutcome.SignedOut)

        if (session.accessToken.isBlank() || session.expiresAtMillis - nowMillis() < TOKEN_REFRESH_MARGIN_MILLIS) {
            when (val refreshed = backend.refresh(session, nowMillis())) {
                is CloudResult.Ok -> {
                    session = refreshed.value
                    store.saveSession(session)
                }
                CloudResult.Unauthorized -> return expired()
                is CloudResult.Rejected -> return expired()
                is CloudResult.Retryable -> return CloudSyncReport(CloudSyncOutcome.Offline, message = refreshed.message)
            }
        }

        val remote = when (val fetched = backend.fetch(session, SyncCollections.RULES)) {
            is CloudResult.Ok -> fetched.value
            CloudResult.Unauthorized -> return expired()
            is CloudResult.Retryable -> return CloudSyncReport(CloudSyncOutcome.Offline, message = fetched.message)
            is CloudResult.Rejected -> return CloudSyncReport(CloudSyncOutcome.Failed, message = fetched.message)
        }
        // Only records this client can faithfully round-trip take part in the merge. Anything
        // else (another platform, a newer payload version) is left untouched on the server.
        val understood = remote.mapNotNull { record ->
            if (record.platform != SyncCollections.PLATFORM_ANDROID) return@mapNotNull null
            if (record.deleted) return@mapNotNull record
            val rule = RuleSyncCodec.decode(record.recordId, record.payloadJson) ?: return@mapNotNull null
            record.copy(payloadJson = RuleSyncCodec.encode(rule))
        }
        val unreadableIds = remote.map { it.recordId }.toSet() - understood.map { it.recordId }.toSet()

        val localRules = EarnItRuleStore.getRules(context)
        val localPayloads = localRules
            .filterNot { it.id in unreadableIds }
            .associate { it.id to RuleSyncCodec.encode(it) }
        val protectedIds = if (strictModeProtecting()) localRules.map { it.id }.toSet() else emptySet()
        val plan = SyncPlanner.plan(
            collection = SyncCollections.RULES,
            localPayloads = localPayloads,
            journal = store.ruleJournal().filterKeys { it !in unreadableIds },
            remote = understood,
            nowMillis = nowMillis(),
            deviceId = store.deviceId(),
            platform = SyncCollections.PLATFORM_ANDROID,
            protectedIds = protectedIds
        )

        if (plan.pushes.isNotEmpty()) {
            when (val pushed = backend.push(session, plan.pushes)) {
                is CloudResult.Ok -> Unit
                CloudResult.Unauthorized -> return expired()
                is CloudResult.Retryable -> return CloudSyncReport(CloudSyncOutcome.Offline, message = pushed.message)
                is CloudResult.Rejected -> return CloudSyncReport(CloudSyncOutcome.Failed, message = pushed.message)
            }
        }

        val journal = LinkedHashMap(plan.journal)
        if (plan.applyUpserts.isNotEmpty() || plan.applyDeletes.isNotEmpty()) {
            applyLocally(plan, journal)
        }
        store.saveRuleJournal(journal)
        return CloudSyncReport(
            outcome = CloudSyncOutcome.Synced,
            pulled = plan.applyUpserts.size + plan.applyDeletes.size,
            pushed = plan.pushes.size,
            heldBackByStrictMode = plan.heldBackIds.size
        )
    }

    private fun applyLocally(plan: SyncPlan, journal: MutableMap<String, SyncJournalEntry>) {
        val current = EarnItRuleStore.getRules(context)
        val byId = LinkedHashMap(current.associateBy { it.id })
        plan.applyUpserts.forEach { record ->
            val remoteRule = RuleSyncCodec.decode(record.recordId, record.payloadJson) ?: return@forEach
            byId[record.recordId] = RuleSyncCodec.mergeIntoLocal(byId[record.recordId], remoteRule)
        }
        plan.applyDeletes.forEach { byId.remove(it) }
        EarnItRuleStore.saveRules(context, byId.values.toList())
        plan.applyDeletes.forEach { id ->
            RewardLedger.deleteRuleState(context, id)
            EarnItPauseStore.clearPause(context, id)
        }
        // New Rules arrive enabled; the plan limit still applies on this device.
        val stored = EarnItRuleStore.reconcileForEntitlement(context, FeatureAccessPolicy.current(context))
            .associateBy { it.id }
        // Journal what is actually stored, so local normalisation is not mistaken for an edit.
        plan.applyUpserts.forEach { record ->
            val storedRule = stored[record.recordId] ?: return@forEach
            journal[record.recordId] = SyncJournalEntry(
                contentHash = SyncPlanner.contentHash(RuleSyncCodec.encode(storedRule)),
                updatedAtMillis = record.updatedAtMillis,
                deleted = false
            )
        }
    }

    private fun expired(): CloudSyncReport {
        store.invalidateTokens()
        return CloudSyncReport(CloudSyncOutcome.SessionExpired)
    }

    private companion object {
        val LOCK = Any()
        const val TOKEN_REFRESH_MARGIN_MILLIS = 60_000L
    }
}
