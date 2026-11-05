package com.olaf.rereminder.service

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.olaf.rereminder.data.Backup
import com.olaf.rereminder.data.BackupContents
import com.olaf.rereminder.data.BackupError
import com.olaf.rereminder.data.BackupException
import com.olaf.rereminder.data.BackupSettings
import com.olaf.rereminder.data.Reminder
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.data.SoundChoice
import com.olaf.rereminder.data.SoundMode
import com.olaf.rereminder.data.Storage
import com.olaf.rereminder.utils.NotificationHelper
import com.olaf.rereminder.utils.PreferenceHelper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate

/**
 * Export and import of the reminder list (#21) against files the user picks through the system
 * file picker — so no storage permission, and the file can live anywhere: Downloads, a cloud
 * folder, a USB stick. The format and its checks live in [Backup].
 *
 * Does file IO: call it off the main thread.
 */
class ReminderBackup(context: Context) {

    private val context = context.applicationContext
    private val repository = ReminderRepository.get(this.context)
    private val preferences = PreferenceHelper(this.context)

    enum class Mode {
        /** Keep the current reminders and add the imported ones after them. */
        ADD,

        /** Delete the current reminders and put the imported ones in their place. */
        REPLACE,
    }

    data class ImportResult(val added: Int, val skipped: Int)

    /** Writes every reminder and the settings to [uri]; returns how many reminders went in. */
    @Throws(IOException::class)
    fun export(uri: Uri): Int {
        if (!repository.ensureLoaded()) throw IOException("Reminders are not available yet")
        val reminders = repository.reminders.value
        val text = encode(reminders)
        // "wt", not "w": some providers keep the old end of a longer file otherwise.
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Could not open $uri")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return reminders.size
    }

    /** Reads and checks [uri] without changing anything yet. */
    @Throws(BackupException::class)
    fun read(uri: Uri): BackupContents {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { readAtMost(it, Backup.MAX_BYTES) }
                ?: throw BackupException(BackupError.UNREADABLE)
        } catch (e: IOException) {
            throw BackupException(BackupError.UNREADABLE, e)
        } catch (e: SecurityException) {
            throw BackupException(BackupError.UNREADABLE, e)
        }
        val contents = Backup.decode(bytes.toString(Charsets.UTF_8), System.currentTimeMillis())
        return contents.copy(
            reminders = contents.reminders.map { it.withToneOnThisDevice() },
            settings = contents.settings?.withTonesOnThisDevice(),
        )
    }

    /** How many of [contents] an [Mode.ADD] import would leave out as already there. */
    fun duplicatesOf(contents: BackupContents): Int =
        Backup.merge(repository.reminders.value, contents.reminders).skipped

    /**
     * Applies an import and re-arms every alarm. Returns null when the new list could not be
     * saved — nothing has changed then.
     */
    fun import(contents: BackupContents, mode: Mode, withSettings: Boolean): ImportResult? {
        if (!repository.ensureLoaded()) return null
        val scheduler = ReminderScheduler(context)
        val result = when (mode) {
            Mode.ADD -> {
                var merged: ImportResult? = null
                val saved = repository.mutate { current ->
                    val merge = Backup.merge(current, contents.reminders)
                    merged = ImportResult(merge.added, merge.skipped)
                    merge.reminders
                }
                if (!saved) return null
                merged ?: return null
            }

            Mode.REPLACE -> {
                val previous = repository.reminders.value
                keepCopyOf(previous)
                val replacement = Backup.replace(contents.reminders)
                if (!repository.mutate { replacement }) return null
                // The old ids may be reused by the new list, so clear everything the old list
                // had armed or posted before arming the new one.
                previous.forEach {
                    scheduler.cancelAlarms(it.id)
                    NotificationHelper.cancelReminderNotification(context, it.id)
                }
                ImportResult(added = replacement.size, skipped = 0)
            }
        }
        if (withSettings) contents.settings?.let { preferences.applyBackupSettings(it) }
        scheduler.sync()
        return result
    }

    private fun encode(reminders: List<Reminder>): String = Backup.encode(
        reminders = reminders,
        settings = preferences.backupSettings(),
        exportedAt = System.currentTimeMillis(),
        appVersion = appVersion(),
    )

    /**
     * Before a replace wipes the list, the old one goes to a file on the device. The user can't
     * reach it, but a support request can — and it costs one small file.
     */
    private fun keepCopyOf(reminders: List<Reminder>) {
        if (reminders.isEmpty()) return
        runCatching {
            File(Storage.filesDir(context), BEFORE_IMPORT_FILE).writeText(encode(reminders))
        }.onFailure { Log.w(TAG, "Could not keep a copy before replacing the reminders", it) }
    }

    private fun appVersion(): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    /** A tone from another phone that doesn't exist here falls back to the default sound. */
    private fun Reminder.withToneOnThisDevice(): Reminder {
        val tone = sound.toneUri
        return if (sound.mode == SoundMode.TONE && tone != null && isGone(tone)) {
            copy(sound = SoundChoice(SoundMode.DEFAULT))
        } else {
            this
        }
    }

    private fun BackupSettings.withTonesOnThisDevice(): BackupSettings = copy(
        ringtone = ringtone?.let(::orDefaultIfGone),
        alarmTone = alarmTone?.let(::orDefaultIfGone),
    )

    /** An empty string means "system default" in [BackupSettings]. */
    private fun orDefaultIfGone(tone: String): String = if (tone.isNotBlank() && isGone(tone)) "" else tone

    /**
     * True only when the tone is known to be missing. A tone the app may not look at (a song in
     * someone's music folder) is kept: the system plays notification sounds itself, and the
     * alarm player falls back to the default tone on its own.
     */
    private fun isGone(tone: String): Boolean {
        val uri = tone.toUri()
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        return try {
            context.contentResolver.query(uri, null, null, null, null).use { it == null || !it.moveToFirst() }
        } catch (_: SecurityException) {
            false
        } catch (e: Exception) {
            Log.i(TAG, "Tone $tone is not available on this device", e)
            true
        }
    }

    private companion object {
        const val TAG = "ReminderBackup"
        const val BEFORE_IMPORT_FILE = "reminders-before-import.json"

        fun readAtMost(input: InputStream, limit: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > limit) throw BackupException(BackupError.TOO_LARGE)
            }
            return out.toByteArray()
        }
    }
}

/** A file name with the date in it, so several exports don't overwrite each other. */
fun suggestedBackupName(today: LocalDate = LocalDate.now()): String = "rereminder-$today.json"
