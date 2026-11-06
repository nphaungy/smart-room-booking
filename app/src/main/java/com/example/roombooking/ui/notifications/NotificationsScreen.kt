package com.example.roombooking.ui.notifications

import com.example.roombooking.ui.common.appCardColors
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roombooking.data.repository.NotificationRepository
import com.example.roombooking.domain.AppNotification
import com.example.roombooking.ui.common.Fmt
import com.example.roombooking.ui.common.GradientIcon
import com.example.roombooking.ui.common.PulsingDot
import com.example.roombooking.ui.common.enterOnce
import com.example.roombooking.ui.common.pressScale
import com.example.roombooking.ui.common.ScreenScaffold
import com.example.roombooking.ui.common.StateContent
import com.example.roombooking.ui.common.UiState
import com.example.roombooking.ui.common.appViewModelFactory
import com.example.roombooking.ui.theme.Brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationsViewModel(private val repo: NotificationRepository) : ViewModel() {
    private val refreshState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    var error by mutableStateOf<String?>(null)
        private set

    val state: StateFlow<UiState<List<AppNotification>>> =
        combine(repo.notifications, refreshState) { list, refresh ->
            when {
                list.isNotEmpty() -> UiState.Success(list)
                refresh is UiState.Loading -> UiState.Loading
                refresh is UiState.Error -> UiState.Error(refresh.message)
                else -> UiState.Empty
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init { refresh() }

    fun refresh() {
        refreshState.value = UiState.Loading
        viewModelScope.launch {
            val r = repo.refresh()
            error = r.exceptionOrNull()?.message
            refreshState.value = r.fold({ UiState.Success(Unit) }, { UiState.Error(it.message ?: "โหลดไม่สำเร็จ") })
        }
    }

    fun open(n: AppNotification) {
        if (!n.isRead) viewModelScope.launch { repo.markRead(n.id) }
    }

    fun markAll() {
        viewModelScope.launch { repo.markAllRead().onFailure { error = it.message } }
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> NotificationsViewModel(c.notificationRepository) }
    }
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenBooking: (Long) -> Unit,
    vm: NotificationsViewModel = viewModel(factory = NotificationsViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(
        title = "การแจ้งเตือน",
        onBack = onBack,
        actions = {
            IconButton(onClick = vm::markAll) { Icon(Icons.Filled.DoneAll, "อ่านทั้งหมด") }
            IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "รีเฟรช") }
        },
    ) { padding ->
        StateContent(state, vm::refresh, "ยังไม่มีการแจ้งเตือน", Modifier.padding(padding)) { list ->
            val seen = remember { mutableSetOf<Any>() }
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(list, key = { _, n -> n.id }) { index, n ->
                    NotificationCard(n, Modifier.animateItem().enterOnce(seen, n.id, index)) {
                        vm.open(n)
                        n.bookingId?.let(onOpenBooking)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(n: AppNotification, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().pressScale(source),
        colors = appCardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (n.isRead) 0.dp else 1.dp),
        interactionSource = source,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (n.isRead) {
                GradientIcon(
                    Icons.Outlined.NotificationsNone,
                    listOf(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.outline),
                    size = 40.dp,
                )
            } else {
                GradientIcon(Icons.Filled.Notifications, Brand.Emerald, size = 40.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(n.title, fontWeight = if (n.isRead) FontWeight.Normal else FontWeight.Bold,
                    color = if (n.isRead) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                Text(n.message, style = MaterialTheme.typography.bodyMedium)
                Text(Fmt.dateTime(n.createdAt), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!n.isRead) {
                Spacer(Modifier.width(8.dp))
                PulsingDot(MaterialTheme.colorScheme.primary)
            }
        }
    }
}
