package com.example.roombooking.ui.rooms

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import com.example.roombooking.ui.theme.StatusColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roombooking.data.repository.RoomRepository
import com.example.roombooking.domain.BookingRules
import com.example.roombooking.domain.Room
import com.example.roombooking.domain.RoomSchedule
import com.example.roombooking.domain.ScheduleSlot
import com.example.roombooking.domain.SlotKind
import com.example.roombooking.ui.common.DateChips
import com.example.roombooking.ui.common.ErrorView
import com.example.roombooking.ui.common.Fmt
import com.example.roombooking.ui.common.LoadingView
import com.example.roombooking.ui.common.Motion
import com.example.roombooking.ui.common.enterAnimation
import com.example.roombooking.ui.common.ScreenScaffold
import com.example.roombooking.ui.common.UiState
import com.example.roombooking.ui.common.appViewModelFactory
import com.example.roombooking.ui.common.toState
import com.example.roombooking.ui.common.floatingOrbs
import com.example.roombooking.ui.common.flowingGradient
import com.example.roombooking.ui.theme.Brand
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime

class RoomDetailViewModel(handle: SavedStateHandle, private val repo: RoomRepository) : ViewModel() {
    val roomId: Long = checkNotNull(handle.get<Long>("roomId"))
    var date by mutableStateOf(
        handle.get<String>("date")?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) } ?: BookingRules.firstBookableDate(),
    )
        private set
    var room by mutableStateOf<UiState<Room>>(UiState.Loading)
        private set
    var schedule by mutableStateOf<UiState<RoomSchedule>>(UiState.Loading)
        private set

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            room = UiState.Loading
            room = repo.room(roomId).toState()
        }
        loadSchedule()
    }

    fun selectDate(d: LocalDate) {
        date = d
        loadSchedule()
    }

    private fun loadSchedule() {
        val d = date
        viewModelScope.launch {
            schedule = UiState.Loading
            schedule = repo.schedule(roomId, d).toState()
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, h -> RoomDetailViewModel(h, c.roomRepository) }
    }
}

/** Free is empty, booked is solid, pending is gold-tinted and closed is a muted stone. */
@Composable
private fun slotFill(kind: SlotKind): Color = when (kind) {
    SlotKind.FREE -> Color.Transparent
    SlotKind.PENDING -> MaterialTheme.colorScheme.secondaryContainer
    SlotKind.APPROVED -> MaterialTheme.colorScheme.primary
    SlotKind.CLOSURE -> StatusColors.closure
}

@Composable
private fun slotTextColor(kind: SlotKind): Color = when (kind) {
    SlotKind.FREE, SlotKind.CLOSURE -> MaterialTheme.colorScheme.onSurface
    SlotKind.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
    SlotKind.APPROVED -> MaterialTheme.colorScheme.onPrimary
}

private fun slotText(slot: ScheduleSlot): String = when (slot.kind) {
    SlotKind.FREE -> "ว่าง — แตะเพื่อจอง"
    SlotKind.PENDING -> "รออนุมัติ" + (slot.label?.let { " · $it" } ?: "")
    SlotKind.APPROVED -> "จองแล้ว" + (slot.label?.let { " · $it" } ?: "")
    SlotKind.CLOSURE -> slot.label ?: "ปิดปรับปรุง"
}

@Composable
fun RoomDetailScreen(
    onBack: () -> Unit,
    onBook: (roomId: Long, date: LocalDate, startMinute: Int?) -> Unit,
    onOpenBooking: (Long) -> Unit,
    vm: RoomDetailViewModel = viewModel(factory = RoomDetailViewModel.Factory),
) {
    val room = (vm.room as? UiState.Success)?.data
    ScreenScaffold(
        title = room?.let { "${it.code} ${it.name}" } ?: "รายละเอียดห้อง",
        onBack = onBack,
        floatingActionButton = {
            AnimatedVisibility(
                visible = room?.active == true,
                enter = scaleIn(tween(Motion.MEDIUM, delayMillis = 150)) + fadeIn(tween(Motion.MEDIUM, delayMillis = 150)),
                exit = scaleOut() + fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    onClick = { onBook(vm.roomId, vm.date, null) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("จองห้องนี้") },
                )
            }
        },
    ) { padding ->
        when (val r = vm.room) {
            UiState.Loading, UiState.Empty -> LoadingView(Modifier.padding(padding))
            is UiState.Error -> ErrorView(r.message, vm::reload, Modifier.padding(padding))
            is UiState.Success -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { Box(Modifier.enterAnimation(0)) { RoomHeader(r.data) } }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("ปฏิทินช่วงเวลา (RB04)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    DateChips(vm.date, vm::selectDate)
                    Spacer(Modifier.height(8.dp))
                    Text(Fmt.date(vm.date), style = MaterialTheme.typography.titleSmall)
                    Legend()
                }
                when (val s = vm.schedule) {
                    UiState.Loading, UiState.Empty -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> item {
                        Column {
                            Text(s.message, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { vm.selectDate(vm.date) }) { Text("ลองใหม่") }
                        }
                    }
                    is UiState.Success -> items(s.data.slots, key = { it.start.toString() }) { slot ->
                        val bookable = slot.kind == SlotKind.FREE && r.data.active && slot.start.isAfter(OffsetDateTime.now())
                        val bookingId = slot.bookingId
                        // Someone else's slot: the form reports the conflict and offers the queue.
                        val queueable = slot.kind in listOf(SlotKind.PENDING, SlotKind.APPROVED) && bookingId == null &&
                            r.data.active && slot.start.isAfter(OffsetDateTime.now())
                        val click: (() -> Unit)? = when {
                            bookable || queueable -> fun() { onBook(vm.roomId, vm.date, BookingRules.minuteOf(slot.start)) }
                            bookingId != null -> fun() { onOpenBooking(bookingId) }
                            else -> null
                        }
                        SlotRow(slot = slot, onClick = click, queueable = queueable)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomHeader(room: Room) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .flowingGradient()
                .floatingOrbs()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(room.code, color = Brand.Highlight, style = MaterialTheme.typography.labelLarge)
            Text(room.name, color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text("${room.building} ชั้น ${room.floor} · ${room.typeName}", color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderPill("ความจุ ${room.capacity} คน")
                room.equipment.take(2).forEach { HeaderPill(if (it.quantity > 1) "${it.name} ×${it.quantity}" else it.name) }
            }
            room.description?.let { Text(it, color = Color.White.copy(alpha = 0.8f)) }
            if (!room.active) Text("ห้องนี้ปิดใช้งาน ไม่สามารถจองได้", color = Brand.Highlight, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HeaderPill(text: String) {
    Surface(color = Color.White.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White,
            style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(SlotKind.FREE to "ว่าง", SlotKind.PENDING to "รออนุมัติ", SlotKind.APPROVED to "จองแล้ว",
            SlotKind.CLOSURE to "ปิด").forEach { (k, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp).background(slotFill(k), MaterialTheme.shapes.extraSmall)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall),
                )
                Text(" $label", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SlotRow(slot: ScheduleSlot, onClick: (() -> Unit)?, queueable: Boolean) {
    val past = !slot.end.isAfter(OffsetDateTime.now())
    // Grows with the text, and scaled so the times stay in one column on one line.
    var mod = Modifier.fillMaxWidth().heightIn(min = 48.dp).height(IntrinsicSize.Min)
    mod = if (onClick != null) mod.clickable(role = Role.Button, onClick = onClick) else mod.semantics(mergeDescendants = true) {}
    Row(mod, verticalAlignment = Alignment.CenterVertically) {
        Text(Fmt.time(slot.start), Modifier.width(56.dp * LocalDensity.current.fontScale), softWrap = false,
            style = MaterialTheme.typography.labelLarge)
        Box(
            Modifier.weight(1f).fillMaxHeight().padding(vertical = 2.dp)
                .alpha(if (past) 0.4f else 1f)
                .background(slotFill(slot.kind), MaterialTheme.shapes.small)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                when {
                    past && slot.kind == SlotKind.FREE -> "ผ่านไปแล้ว"
                    queueable -> (if (slot.kind == SlotKind.PENDING) "รออนุมัติ" else "จองแล้ว") + " — แตะเพื่อเข้าคิว"
                    else -> slotText(slot) + if (slot.isMine) " (ของฉัน)" else ""
                },
                color = slotTextColor(slot.kind),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
