package com.example.roombooking.ui.booking

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roombooking.data.repository.BookingRepository
import com.example.roombooking.domain.Booking
import com.example.roombooking.domain.BookingStatus
import com.example.roombooking.ui.common.Fmt
import com.example.roombooking.ui.common.Motion
import com.example.roombooking.ui.common.enterOnce
import com.example.roombooking.ui.common.pressScale
import com.example.roombooking.ui.common.ScreenScaffold
import com.example.roombooking.ui.common.StateContent
import com.example.roombooking.ui.common.StatusChip
import com.example.roombooking.ui.common.UiState
import com.example.roombooking.ui.common.appViewModelFactory
import com.example.roombooking.ui.common.statusColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MyBookingsViewModel(private val repo: BookingRepository) : ViewModel() {
    private val refreshState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    private val tab = MutableStateFlow<BookingStatus?>(null) // null = all
    var selectedTab by mutableStateOf<BookingStatus?>(null)
        private set
    var refreshError by mutableStateOf<String?>(null)
        private set

    val state: StateFlow<UiState<List<Booking>>> =
        combine(repo.myBookings, refreshState, tab) { list, refresh, status ->
            val filtered = if (status == null) list else list.filter { it.status == status }
            when {
                filtered.isNotEmpty() -> UiState.Success(filtered)
                refresh is UiState.Loading && list.isEmpty() -> UiState.Loading
                refresh is UiState.Error && list.isEmpty() -> UiState.Error(refresh.message)
                else -> UiState.Empty
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init { refresh() }

    fun refresh() {
        refreshState.value = UiState.Loading
        viewModelScope.launch {
            val r = repo.refreshMine()
            refreshError = r.exceptionOrNull()?.message
            refreshState.value = r.fold({ UiState.Success(Unit) }, { UiState.Error(it.message ?: "โหลดไม่สำเร็จ") })
        }
    }

    fun selectTab(status: BookingStatus?) {
        selectedTab = status
        tab.value = status
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> MyBookingsViewModel(c.bookingRepository) }
    }
}

@Composable
fun MyBookingsScreen(
    onOpen: (Long) -> Unit,
    topActions: @Composable () -> Unit,
    vm: MyBookingsViewModel = viewModel(factory = MyBookingsViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val tabs: List<BookingStatus?> = listOf(null) + BookingStatus.entries
    ScreenScaffold(
        title = "การจองของฉัน",
        actions = {
            IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "รีเฟรช") }
            topActions()
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val selectedIndex = tabs.indexOf(vm.selectedTab)
            val primary = MaterialTheme.colorScheme.primary
            val indicator by animateColorAsState(
                vm.selectedTab?.let(::statusColor) ?: primary, tween(Motion.MEDIUM), label = "tabIndicator",
            )
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 8.dp,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[selectedIndex]), color = indicator)
                },
            ) {
                tabs.forEach { s ->
                    // each tab takes the colour of the status it filters by
                    Tab(selected = vm.selectedTab == s, onClick = { vm.selectTab(s) },
                        selectedContentColor = s?.let(::statusColor) ?: primary,
                        text = { Text(s?.thai ?: "ทั้งหมด") })
                }
            }
            vm.refreshError?.takeIf { state is UiState.Success }?.let {
                Text("แสดงข้อมูลที่บันทึกไว้ในเครื่อง ($it)", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            StateContent(state, vm::refresh, emptyMessage = "ยังไม่มีรายการจอง") { list ->
                BookingList(list, onOpen)
            }
        }
    }
}

@Composable
fun BookingList(list: List<Booking>, onOpen: (Long) -> Unit, showBooker: Boolean = false) {
    val seen = remember { mutableSetOf<Any>() }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(list, key = { _, b -> b.id }) { index, b ->
            BookingCard(b, showBooker, Modifier.animateItem().enterOnce(seen, b.id, index)) { onOpen(b.id) }
        }
    }
}

@Composable
fun BookingCard(b: Booking, showBooker: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val strip by animateColorAsState(statusColor(b.status), tween(Motion.MEDIUM), label = "strip")
    val source = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().pressScale(source),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 6.dp),
        interactionSource = source,
    ) {
        Column(
            Modifier
                // left strip in the status colour: pending amber, approved green, rejected red, cancelled grey
                .drawBehind { drawRect(strip, size = Size(5.dp.toPx(), size.height)) }
                .padding(start = 21.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${b.roomCode} ${b.roomName}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                StatusChip(b.status)
            }
            Text(Fmt.bookingWhen(b.start, b.end))
            if (showBooker) Text("ผู้จอง: ${b.bookerName} · ${b.attendees} คน", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(b.purpose, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
