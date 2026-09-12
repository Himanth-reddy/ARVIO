package com.arflix.tv.ui.screens.tv.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arflix.tv.R

/** A separate focus window keeps the fullscreen player's key handlers behind the selector. */
@Composable
internal fun FullscreenSourcesDialog(
    channel: EnrichedChannel,
    variants: List<EnrichedChannel>,
    loading: Boolean,
    failed: Boolean,
    onDismiss: () -> Unit,
    onPick: (EnrichedChannel) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val firstFocus = remember { FocusRequester() }
        val hasAlternatives = !loading && !failed && variants.size > 1
        LaunchedEffect(hasAlternatives) {
            firstFocus.requestFocus()
        }
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .background(LiveColors.PanelRaised).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.live_label_choose_source), color = Color.White)
            Text(channel.name, color = LiveColors.FgDim)
            when {
                loading -> CircularProgressIndicator()
                failed -> Text(stringResource(R.string.live_sources_failed), color = Color.White)
                !hasAlternatives -> Text(stringResource(R.string.live_sources_empty), color = Color.White)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(variants, key = { _, item -> item.id }) { index, variant ->
                        Button(
                            onClick = { onPick(variant) },
                            modifier = Modifier.fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                        ) {
                            Text(variant.name)
                        }
                    }
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = if (!hasAlternatives) Modifier.focusRequester(firstFocus) else Modifier,
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }
}
