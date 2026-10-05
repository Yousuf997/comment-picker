package app.giveaway.feature.draw

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giveaway.core.data.draw.DrawReset
import app.giveaway.core.designsystem.GiveawayTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Replaces a drawn result with a new draw (plan A30): the winners and certificate go, and S11 opens again. */
@HiltViewModel
class RedrawViewModel @Inject constructor(private val reset: DrawReset) : ViewModel() {
    private var busy = false
    private val doneChannel = Channel<Long>(Channel.BUFFERED)

    /** The giveaway that is ready to draw again. */
    val done: Flow<Long> = doneChannel.receiveAsFlow()

    fun redraw(giveawayId: Long) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val redrawn = try {
                reset.redraw(giveawayId)
                true
            } catch (expected: IllegalStateException) {
                // Deleted, or its result already cleared elsewhere: nothing to redraw from this screen.
                false
            }
            busy = false
            if (redrawn) doneChannel.send(giveawayId)
        }
    }
}

/** Redraws [giveawayId] and calls [onRedrawn] once it can be drawn again. */
@Composable
internal fun rememberRedraw(giveawayId: Long, onRedrawn: (Long) -> Unit): () -> Unit {
    val viewModel = hiltViewModel<RedrawViewModel>()
    LaunchedEffect(viewModel) { viewModel.done.collect(onRedrawn) }
    return { viewModel.redraw(giveawayId) }
}

/** Asked before the current winners are replaced (plan A30). */
@Composable
internal fun RedrawDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.redraw_title)) },
        text = { Text(stringResource(R.string.redraw_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("redraw:confirm")) {
                Text(stringResource(R.string.redraw_confirm), color = GiveawayTheme.colors.danger)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.redraw_cancel)) } },
    )
}
