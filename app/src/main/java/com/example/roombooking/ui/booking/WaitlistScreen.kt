package com.example.roombooking.ui.booking

import com.example.roombooking.ui.common.appCardColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roombooking.data.repository.BookingRepository
import com.example.roombooking.domain.WaitlistEntry
import com.example.roombooking.domain.WaitlistStatus
import com.example.roombooking.ui.common.Fmt
import com.example.roombooking.ui.common.ScreenScaffold
import com.example.roombooking.ui.common.StateContent
import com.example.roombooking.ui.common.UiState
import com.example.roombooking.ui.common.appViewModelFactory
import com.example.roombooking.ui.common.toListState
import com.example.roombooking.ui.theme.StatusColors
import kotlinx.coroutines.launch

class WaitlistViewModel(private val repo: BookingRepository) : ViewModel() {
    var state by mutableStateOf<UiState<List<WaitlistEntry>>>(UiState.Loading)
        private set
    var message by mutableStateOf<String?>(null)

    fun load() {
        viewModelScope.launch {
            state = UiState.Loading
            state = repo.myWaitlist().toListState()
        }
    }

    fun leave(id: Long) {
        viewModelScope.launch {
            repo.leaveWaitlist(id)
                .onSuccess { message = "ออกจากคิวแล้ว"; load() }
                .onFailure { message = it.message }
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> WaitlistViewModel(c.bookingRepository) }
    }
}

@Composable
fun WaitlistScreen(
    onBack: () -> Unit,
    onOpenBooking: (Long) -> Unit,
    vm: WaitlistViewModel = viewModel(factory = WaitlistViewModel.Factory),
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }

    ScreenScaffold(
        title = "คิวรอห้อง",
        onBack = onBack,
        actions = { IconButton(onClick = vm::load) { Icon(Icons.Filled.Refresh, "รีเฟรช") } },
        snackbarHostState = snackbar,
    ) { padding ->
        StateContent(vm.state, vm::load, "ยังไม่มีคิวรอห้อง", Modifier.padding(padding)) { list ->
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { e -> WaitlistCard(e, onLeave = { vm.leave(e.id) }, onOpenBooking = onOpenBooking) }
            }
        }
    }
}

@Composable
private fun WaitlistCard(e: WaitlistEntry, onLeave: () -> Unit, onOpenBooking: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = appCardColors()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${e.roomCode} ${e.roomName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text(Fmt.bookingWhen(e.start, e.end))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val waiting = e.status == WaitlistStatus.WAITING
                Text(
                    if (waiting && e.position != null) "คิวที่ ${e.position}" else e.status.thai,
                    Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    color = when (e.status) {
                        WaitlistStatus.WAITING -> StatusColors.pending
                        WaitlistStatus.PROMOTED -> StatusColors.free
                        WaitlistStatus.CANCELLED, WaitlistStatus.EXPIRED -> StatusColors.cancelled
                    },
                )
                if (waiting) TextButton(onClick = onLeave) { Text("ออกจากคิว") }
                e.bookingId?.let { id -> TextButton(onClick = { onOpenBooking(id) }) { Text("ดูการจอง") } }
            }
        }
    }
}
