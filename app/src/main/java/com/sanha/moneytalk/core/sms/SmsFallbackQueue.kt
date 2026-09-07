package com.sanha.moneytalk.core.sms

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** App-private, backup-excluded pending financial SMS. Attempts survive process death. */
@Singleton
class SmsFallbackQueue internal constructor(
    private val stateFile: File,
    private val gson: Gson
) {
    @Inject
    constructor(@ApplicationContext context: Context, gson: Gson) : this(
        File(context.noBackupFilesDir, "sms_fallback_queue.json"),
        gson
    )

    data class Entry(
        @SerializedName("id") val id: String,
        @SerializedName("body") val body: String,
        @SerializedName("address") val address: String,
        @SerializedName("receivedAt") val receivedAt: Long,
        @SerializedName("queuedAt") val queuedAt: Long,
        @SerializedName("generation") val generation: Long,
        @SerializedName("attempts") val attempts: Int = 0
    ) {
        fun toInput() = SmsInput(id, body, address, receivedAt)
    }

    private data class State(
        @SerializedName("generation") val generation: Long = 0,
        @SerializedName("entries") val entries: List<Entry> = emptyList(),
        @SerializedName("exhaustedKeys") val exhaustedKeys: Set<String> = emptySet()
    )

    companion object {
        const val MAX_ATTEMPTS = 3
    }

    private var state = if (stateFile.exists()) {
        gson.fromJson(stateFile.readText(), State::class.java) ?: State()
    } else {
        State()
    }

    @Synchronized
    fun generation(): Long = state.generation

    @Synchronized
    fun enqueue(input: SmsInput, expectedGeneration: Long, now: Long): Boolean {
        if (expectedGeneration != state.generation || exhaustedKey(input.id) in state.exhaustedKeys ||
            state.entries.any { it.id == input.id }
        ) return false
        save(state.copy(entries = state.entries + Entry(
            id = input.id,
            body = input.body,
            address = input.address,
            receivedAt = input.date,
            queuedAt = now,
            generation = expectedGeneration
        )))
        return true
    }

    @Synchronized
    fun pending(): List<Entry> = state.entries.filter { it.attempts < MAX_ATTEMPTS }

    @Synchronized
    fun beginAttempt(id: String, expectedGeneration: Long): Entry? {
        val entry = state.entries.firstOrNull { it.id == id && it.generation == expectedGeneration }
            ?.takeIf { it.attempts < MAX_ATTEMPTS } ?: return null
        val updated = entry.copy(attempts = entry.attempts + 1)
        if (updated.attempts == MAX_ATTEMPTS) {
            // The last attempt owns its payload in memory. Retain only its retry budget on disk.
            save(state.copy(
                entries = state.entries.filterNot { it.id == id },
                exhaustedKeys = state.exhaustedKeys + exhaustedKey(id)
            ))
        } else {
            save(state.copy(entries = state.entries.map { if (it.id == id) updated else it }))
        }
        return updated
    }

    @Synchronized
    fun complete(id: String, expectedGeneration: Long) {
        if (state.generation != expectedGeneration) return
        save(state.copy(
            entries = state.entries.filterNot { it.id == id },
            exhaustedKeys = state.exhaustedKeys - exhaustedKey(id)
        ))
    }

    @Synchronized
    fun clear() {
        save(State(generation = state.generation + 1))
    }

    // smsId itself includes the sender; only its digest is needed to preserve the retry limit.
    private fun exhaustedKey(id: String): String =
        MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun save(updated: State) {
        stateFile.parentFile?.mkdirs()
        val temporary = File(stateFile.path + ".tmp")
        FileOutputStream(temporary).use { output ->
            output.write(gson.toJson(updated).toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        Files.move(
            temporary.toPath(),
            stateFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
        state = updated
    }
}
