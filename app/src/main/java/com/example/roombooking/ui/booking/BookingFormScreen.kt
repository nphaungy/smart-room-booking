package com.example.roombooking.ui.booking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roombooking.data.remote.AppException
import com.example.roombooking.data.repository.BookingRepository
import com.example.roombooking.data.repository.RoomRepository
import com.example.roombooking.domain.Booking
import com.example.roombooking.domain.BookingRules
import com.example.roombooking.domain.Room
import com.example.roombooking.ui.common.BounceButton
import com.example.roombooking.ui.common.DateChips
import com.example.roombooking.ui.common.ErrorView
import com.example.roombooking.ui.common.Fmt
import com.example.roombooking.ui.common.LoadingView
import com.example.roombooking.ui.common.NumberField
import com.example.roombooking.ui.common.ScreenScaffold
import com.example.roombooking.ui.common.TimeDropdown
import com.example.roombooking.ui.common.appViewModelFactory
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Create (roomId given) or edit (bookingId given) a booking request. */
class BookingFormViewModel(
    handle: SavedStateHandle,
    private val rooms: RoomRepository,
    private val bookings: BookingRepository,
) : ViewModel() {
    private val editId: Long = handle.get<Long>("bookingId") ?: -1L
    private var roomId: Long = handle.get<Long>("roomId") ?: -1L
    val isEdit = editId > 0

    var room by mutableStateOf<Room?>(null)
        private set
    private var editing: Booking? = null

    var loadError by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(true)
        private set

    var date by mutableStateOf(handle.get<String>("date")?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) })
    var start by mutableStateOf(handle.get<Int>("start")?.takeIf { it >= 0 })
    var end by mutableStateOf(handle.get<Int>("start")?.takeIf { it >= 0 }?.let { minOf(it + 60, BookingRules.CLOSE_HOUR * 60) })
    var attendees by mutableStateOf("")
    var purpose by mutableStateOf("")

    var submitting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Set after a successful save; the screen then navigates to the booking detail. */
    var savedId by mutableStateOf<Long?>(null)
        private set

    init { load() }

    fun load() {
        loading = true
        loadError = null
        viewModelScope.launch {
            if (isEdit) {
                val b = bookings.detail(editId).getOrElse {
                    loadError = it.message; loading = false; return@launch
                }
                editing = b
                roomId = b.roomId
                date = b.start.atZoneSameInstant(BookingRules.ZONE).toLocalDate()
                start = BookingRules.minuteOf(b.start)
                end = BookingRules.minuteOf(b.end)
                attendees = b.attendees.toString()
                purpose = b.purpose
            }
            rooms.room(roomId).onSuccess { room = it }.onFailure { loadError = it.message }
            loading = false
        }
    }

    fun submit() {
        if (submitting) return
        val r = room ?: return
        val n = attendees.toIntOrNull()
        // Instant client-side check (BR01, BR07, BR08); the server re-checks everything.
        error = BookingRules.validate(date, start, end, n, r.capacity, purpose)
        if (error != null) return
        submitting = true
        viewModelScope.launch {
            val result = editing?.let { bookings.update(it, date!!, start!!, end!!, n!!, purpose) }
                ?: bookings.create(r.id, date!!, start!!, end!!, n!!, purpose)
            result
                .onSuccess { savedId = it.id }
                .onFailure { e ->
                    error = if ((e as? AppException)?.code == "BOOKING_CONFLICT") {
                        "ช่วงเวลานี้ถูกจองแล้ว (อาจมีผู้ใช้อื่นจองก่อนเพียงไม่กี่วินาที) กรุณาเลือกเวลาอื่น"
                    } else e.message
                }
            submitting = false
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, h -> BookingFormViewModel(h, c.roomRepository, c.bookingRepository) }
    }
}

@Composable
fun BookingFormScreen(
    onBack: () -> Unit,
    onSaved: (bookingId: Long) -> Unit,
    vm: BookingFormViewModel = viewModel(factory = BookingFormViewModel.Factory),
) {
    LaunchedEffect(vm.savedId) { vm.savedId?.let(onSaved) }

    ScreenScaffold(title = if (vm.isEdit) "แก้ไขคำขอจอง" else "สร้างคำขอจอง", onBack = onBack) { padding ->
        val room = vm.room
        when {
            vm.loading -> LoadingView(Modifier.padding(padding))
            room == null -> ErrorView(vm.loadError ?: "ไม่พบห้อง", vm::load, Modifier.padding(padding))
            else -> Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("${room.code} ${room.name}", style = MaterialTheme.typography.titleMedium)
                        Text("${room.building} ชั้น ${room.floor} · ความจุ ${room.capacity} คน")
                    }
                }
                Text("วันที่", style = MaterialTheme.typography.titleSmall)
                DateChips(vm.date, { vm.date = it })
                vm.date?.let { Text(Fmt.date(it), color = MaterialTheme.colorScheme.primary) }

                Text("เวลา (ช่วงละ ${BookingRules.SLOT_MINUTES} นาที, ${BookingRules.OPEN_HOUR}:00–${BookingRules.CLOSE_HOUR}:00)",
                    style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeDropdown("เริ่ม", vm.start, { vm.start = it; if (vm.end == null || vm.end!! <= it) vm.end = minOf(it + 60, BookingRules.CLOSE_HOUR * 60) },
                        Modifier.weight(1f), options = BookingRules.timeOptions.dropLast(1))
                    TimeDropdown("สิ้นสุด", vm.end, { vm.end = it }, Modifier.weight(1f),
                        options = BookingRules.timeOptions.drop(1))
                }

                NumberField("จำนวนผู้เข้าร่วม (ไม่เกิน ${room.capacity})", vm.attendees, { vm.attendees = it },
                    Modifier.fillMaxWidth(), isError = (vm.attendees.toIntOrNull() ?: 0) > room.capacity)
                OutlinedTextField(
                    value = vm.purpose, onValueChange = { vm.purpose = it.take(500) },
                    label = { Text("วัตถุประสงค์การใช้ห้อง") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                )

                AnimatedVisibility(vm.error != null) { Text(vm.error.orEmpty(), color = MaterialTheme.colorScheme.error) }
                Text("คำขอจะอยู่ในสถานะ \"รออนุมัติ\" จนกว่าเจ้าหน้าที่จะพิจารณา",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BounceButton(onClick = vm::submit, enabled = !vm.submitting, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Crossfade(targetState = vm.submitting, label = "submit") { busy ->
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(if (vm.isEdit) "บันทึกการแก้ไข" else "ส่งคำขอจอง")
                    }
                }
            }
        }
    }
}
