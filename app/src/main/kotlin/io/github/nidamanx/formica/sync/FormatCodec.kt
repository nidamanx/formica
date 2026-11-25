// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.nidamanx.formica.sync

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The outcome of reading a file: only [Valid] may change any data. */
sealed interface ReadResult {
    data class Valid(val file: ExportFile) : ReadResult

    /** Not JSON, or not written by Formica. */
    data object NotFormica : ReadResult

    /** Written by a newer Formica: the user must update the app. */
    data class NewerVersion(val formatVersion: Int) : ReadResult

    /** A Formica file with missing or damaged content. */
    data object Unreadable : ReadResult
}

object FormatCodec {
    // Every field is written, nulls included; unknown fields (newer minor versions) are skipped
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun write(file: ExportFile): String = json.encodeToString(ExportFile.serializer(), file)

    fun read(text: String): ReadResult {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return ReadResult.NotFormica
        val format = root["format"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        if (format != FORMAT_NAME) return ReadResult.NotFormica
        val version = root["formatVersion"]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
            ?: return ReadResult.Unreadable
        if (version > FORMAT_VERSION) return ReadResult.NewerVersion(version)
        if (version < 1) return ReadResult.Unreadable
        return try {
            ReadResult.Valid(json.decodeFromJsonElement(ExportFile.serializer(), root))
        } catch (_: SerializationException) {
            ReadResult.Unreadable
        } catch (_: IllegalArgumentException) {
            ReadResult.Unreadable
        }
    }
}
