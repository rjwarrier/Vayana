package com.vayana.feature.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.homelibrary.HomeLibraryStatus
import com.vayana.core.homelibrary.HomeLibrarySync
import com.vayana.core.resources.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeLibraryCardViewModel @Inject constructor(
    private val sync: HomeLibrarySync,
) : ViewModel() {
    val status: StateFlow<HomeLibraryStatus> = sync.status
    val lastSyncedAt: StateFlow<Long?> = sync.lastSyncedAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun syncNow() {
        viewModelScope.launch { sync.syncNow(force = true) }
    }
}

/**
 * Where the Home Library mirror stands: connected, not installed, sharing off, and when it last ran. The on/off switch
 * is the ordinary "Sync with Home Library" setting below it; this card only reports and offers a manual run.
 */
@Composable
internal fun HomeLibraryCard(modifier: Modifier = Modifier, viewModel: HomeLibraryCardViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Elevations.none,
    ) {
        Row(
            modifier = Modifier.padding(Paddings.card),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = stringResource(R.string.home_library_card_title),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = status.headline(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                status.detail()?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (status != HomeLibraryStatus.Disabled) {
                    Text(
                        text = lastSyncedAt?.let { millis ->
                            val formatted = DateUtils.formatDateTime(
                                context,
                                millis,
                                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
                            )
                            stringResource(R.string.home_library_last_synced, formatted)
                        } ?: stringResource(R.string.home_library_never_synced),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (status != HomeLibraryStatus.Disabled && status != HomeLibraryStatus.Syncing) {
                TextButton(onClick = viewModel::syncNow) {
                    Text(stringResource(R.string.home_library_sync_now))
                }
            }
        }
    }
}

@Composable
private fun HomeLibraryStatus.headline(): String = stringResource(
    when (this) {
        HomeLibraryStatus.Disabled -> R.string.home_library_status_disabled
        HomeLibraryStatus.Syncing -> R.string.home_library_status_syncing
        HomeLibraryStatus.NotConnected -> R.string.home_library_status_not_connected
        HomeLibraryStatus.SharingOff -> R.string.home_library_status_sharing_off
        HomeLibraryStatus.UnsupportedSchema -> R.string.home_library_status_unsupported
        HomeLibraryStatus.Failed -> R.string.home_library_status_failed
        // Idle is "not tried yet": reads as connected rather than flashing a warning at launch.
        HomeLibraryStatus.Connected, HomeLibraryStatus.Idle -> R.string.home_library_status_connected
    },
)

@Composable
private fun HomeLibraryStatus.detail(): String? =
    if (this == HomeLibraryStatus.NotConnected) stringResource(R.string.home_library_status_not_connected_detail) else null
