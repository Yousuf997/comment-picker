package app.giveaway.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme

/**
 * Bottom sheet from the spec: 28 dp top corners, drag handle, near-black scrim at 62%.
 * With [dismissible] false (the S13 save-video choice), Back, tapping outside and dragging down all do nothing,
 * so the user must pick an action inside the sheet; [onDismissRequest] is then never called.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiveawayBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = GiveawayTheme.colors
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> dismissible || target != SheetValue.Hidden },
    )
    ModalBottomSheet(
        onDismissRequest = { if (dismissible) onDismissRequest() },
        modifier = modifier,
        sheetState = sheetState,
        sheetGesturesEnabled = dismissible,
        shape = GiveawayTheme.shapes.sheet,
        containerColor = colors.surface,
        contentColor = colors.onBackground,
        scrimColor = colors.drawBackground.copy(alpha = GiveawayDimens.SHEET_SCRIM_ALPHA),
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = dismissible,
            shouldDismissOnClickOutside = dismissible,
        ),
        content = content,
    )
}
