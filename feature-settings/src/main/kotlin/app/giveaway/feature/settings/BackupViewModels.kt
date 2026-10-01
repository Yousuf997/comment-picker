package app.giveaway.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giveaway.core.data.backup.BackupManager
import app.giveaway.core.data.backup.BackupSummary
import app.giveaway.core.security.backup.BackupFormatException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** How strong a backup password looks: length and variety, with trivial repeats marked weak (spec: S5). */
internal enum class PasswordStrength {
    NONE, WEAK, FAIR, GOOD, STRONG;

    /** Fair or better is required to make a backup. */
    val acceptable: Boolean get() = this >= FAIR

    companion object {
        private const val FAIR_LENGTH = 8
        private const val GOOD_LENGTH = 12
        private const val STRONG_LENGTH = 16
        private const val VARIED_CLASSES = 3
        private const val TRIVIAL_DISTINCT = 3

        fun of(password: String): PasswordStrength {
            if (password.isEmpty()) return NONE
            if (password.length < FAIR_LENGTH || password.toSet().size <= TRIVIAL_DISTINCT) return WEAK
            val classes = listOf<(Char) -> Boolean>(
                Char::isLowerCase,
                Char::isUpperCase,
                Char::isDigit,
                // Symbols, spaces and letters without case, such as Arabic.
                { c: Char -> !c.isLowerCase() && !c.isUpperCase() && !c.isDigit() },
            )
            val varied = classes.count { test -> password.any(test) } >= VARIED_CLASSES
            val byLength = when {
                password.length >= STRONG_LENGTH -> 2
                password.length >= GOOD_LENGTH -> 1
                else -> 0
            }
            return entries[FAIR.ordinal + minOf(byLength + if (varied) 1 else 0, 2)]
        }
    }
}

/** Backup export (plan M-13): writes the encrypted file to the document the user picked. */
@HiltViewModel
internal class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backups: BackupManager,
) : ViewModel() {
    enum class Status { IDLE, WORKING, SAVED, FAILED }

    private val statusFlow = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = statusFlow.asStateFlow()

    fun export(uri: Uri, password: CharArray) {
        if (statusFlow.value == Status.WORKING) return
        statusFlow.value = Status.WORKING
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "No stream" }
                        .use { backups.export(it, password) }
                }.onFailure {
                    // Don't leave a half-written file behind.
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                }.isSuccess
            }
            password.fill(' ')
            statusFlow.value = if (saved) Status.SAVED else Status.FAILED
        }
    }
}

/** Why a backup couldn't be read. A wrong password and a damaged file look the same, by design of the cipher. */
internal enum class RestoreError { WRONG_PASSWORD_OR_DAMAGED, NOT_A_BACKUP, NEWER_VERSION, FAILED }

internal data class RestoreState(
    val file: Uri? = null,
    val working: Boolean = false,
    val summary: BackupSummary? = null,
    val error: RestoreError? = null,
    val restored: Boolean = false,
)

/** Restore (plan M-13): pick the file, check it with the password, then confirm replacing everything. */
@HiltViewModel
internal class RestoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backups: BackupManager,
) : ViewModel() {
    private val stateFlow = MutableStateFlow(RestoreState())
    val state: StateFlow<RestoreState> = stateFlow.asStateFlow()

    fun onFile(uri: Uri) = stateFlow.update { RestoreState(file = uri) }

    /** Reads the whole backup without changing anything, so the user sees what they would get back. */
    fun check(password: CharArray) = run(password) { uri ->
        val summary = backups.inspect(open(uri), password)
        stateFlow.update { it.copy(summary = summary) }
    }

    fun restore(password: CharArray) = run(password) { uri ->
        backups.restore(open(uri), password)
        stateFlow.update { it.copy(restored = true) }
    }

    private fun run(password: CharArray, block: suspend (Uri) -> Unit) {
        val uri = stateFlow.value.file ?: return
        if (stateFlow.value.working) return
        stateFlow.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            val failure = withContext(Dispatchers.IO) { runCatching { block(uri) } }.exceptionOrNull()
            val error = failure?.let(::errorFor)
            password.fill(' ')
            stateFlow.update { it.copy(working = false, error = error, summary = it.summary.takeIf { error == null }) }
        }
    }

    private fun open(uri: Uri) = checkNotNull(context.contentResolver.openInputStream(uri)) { "No stream" }

    private fun errorFor(error: Throwable) = when {
        error is BackupFormatException && error.reason == BackupFormatException.Reason.NEWER_VERSION ->
            RestoreError.NEWER_VERSION
        error is BackupFormatException -> RestoreError.NOT_A_BACKUP
        error is IOException -> RestoreError.WRONG_PASSWORD_OR_DAMAGED
        else -> RestoreError.FAILED
    }
}
