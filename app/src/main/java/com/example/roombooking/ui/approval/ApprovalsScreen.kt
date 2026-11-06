package com.example.roombooking.ui.approval

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roombooking.data.repository.BookingRepository
import com.example.roombooking.domain.Booking
import com.example.roombooking.domain.BookingStatus
import com.example.roombooking.ui.booking.BookingList
import com.example.roombooking.ui.common.ScreenScaffold
import com.example.roombooking.ui.common.StateContent
import com.example.roombooking.ui.common.UiState
import com.example.roombooking.ui.common.appViewModelFactory
import com.example.roombooking.ui.common.toListState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** RB07: approver's queue. "รออนุมัติ" is sorted oldest-first by the server (first come, first served). */
class ApprovalsViewModel(private val repo: BookingRepository) : ViewModel() {
    var status by mutableStateOf<BookingStatus?>(BookingStatus.PENDING)
        private set
    var state by mutableStateOf<UiState<List<Booking>>>(UiState.Loading)
        private set
    private var job: Job? = null

    init { load() }

    fun select(s: BookingStatus?) {
        status = s
        load()
    }

    fun load() {
        job?.cancel()
        val s = status
        job = viewModelScope.launch {
            state = UiState.Loading
            state = repo.allBookings(s).toListState()
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> ApprovalsViewModel(c.bookingRepository) }
    }
}

@Composable
fun ApprovalsScreen(
    onOpen: (Long) -> Unit,
    topActions: @Composable () -> Unit,
    vm: ApprovalsViewModel = viewModel(factory = ApprovalsViewModel.Factory),
) {
    val tabs: List<BookingStatus?> = listOf(BookingStatus.PENDING, BookingStatus.APPROVED, BookingStatus.REJECTED,
        BookingStatus.CANCELLED, BookingStatus.NO_SHOW, null)
    ScreenScaffold(
        title = "พิจารณาการจอง",
        actions = {
            IconButton(onClick = vm::load) { Icon(Icons.Filled.Refresh, "รีเฟรช") }
            topActions()
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tabs.indexOf(vm.status), edgePadding = 8.dp) {
                tabs.forEach { s ->
                    Tab(selected = vm.status == s, onClick = { vm.select(s) }, text = { Text(s?.thai ?: "ทั้งหมด") })
                }
            }
            StateContent(
                vm.state, vm::load,
                emptyMessage = if (vm.status == BookingStatus.PENDING) "ไม่มีคำขอที่รออนุมัติ" else "ไม่มีรายการ",
            ) { list -> BookingList(list, onOpen, showBooker = true) }
        }
    }
}
