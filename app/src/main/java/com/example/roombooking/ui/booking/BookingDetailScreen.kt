package com.example.roombooking.ui.booking

import androidx.compose.runtime.saveable.rememberSaveable
import com.example.roombooking.ui.common.appCardColors
import com.example.roombooking.ui.common.BottomActionBar
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roombooking.data.repository.BookingRepository
import com.example.roombooking.domain.Booking
import com.example.roombooking.domain.BookingStatus
import com.example.roombooking.domain.CheckInCode
import com.example.roombooking.domain.isCheckInOpen
import com.example.roombooking.ui.common.BounceButton
import com.example.roombooking.ui.common.Fmt
import com.example.roombooking.ui.common.LabelValue
import com.example.roombooking.ui.common.ScreenScaffold
import com.example.roombooking.ui.common.StateContent
import com.example.roombooking.ui.common.StatusChip
import com.example.roombooking.ui.common.UiState
import com.example.roombooking.ui.common.appViewModelFactory
import com.example.roombooking.ui.common.statusColor
import com.example.roombooking.ui.common.toState
import com.example.roombooking.ui.theme.StatusColors
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

class BookingDetailViewModel(handle: SavedStateHandle, private val repo: BookingRepository) : ViewModel() {
    private val bookingId: Long = checkNotNull(handle.get<Long>("bookingId"))
    var state by mutableStateOf<UiState<Booking>>(UiState.Loading)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    // load() is called by the screen each time it is shown, so returning from "edit" shows fresh data

    fun load() {
        viewModelScope.launch {
            state = UiState.Loading
            state = repo.detail(bookingId).toState()
        }
    }

    private fun act(success: String, call: suspend () -> Result<Booking>) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            call()
                .onSuccess { message = success; state = repo.detail(bookingId).toState() }
                .onFailure { message = it.message }
            busy = false
        }
    }

    fun approve() = act("อนุมัติแล้ว") { repo.approve(bookingId) }
    fun reject(reason: String) = act("ปฏิเสธแล้ว") { repo.reject(bookingId, reason) }
    fun cancel(note: String) = act("ยกเลิกแล้ว") { repo.cancel(bookingId, note) }
    fun staffCheckIn(note: String) = act("ยืนยันการมาใช้ห้องแล้ว") { repo.staffCheckIn(bookingId, note) }

    companion object {
        val Factory = appViewModelFactory { c, h -> BookingDetailViewModel(h, c.bookingRepository) }
    }
}

@Composable
fun BookingDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    vm: BookingDetailViewModel = viewModel(factory = BookingDetailViewModel.Factory),
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) } // "reject" | "cancel" | "staffCheckIn"
    var text by rememberSaveable { mutableStateOf("") }

    ScreenScaffold(
        title = "รายละเอียดการจอง",
        onBack = onBack,
        actions = { IconButton(onClick = vm::load) { Icon(Icons.Filled.Refresh, "รีเฟรช") } },
        bottomBar = {
            val b = (vm.state as? UiState.Success)?.data
            when {
                b == null -> Unit
                b.canDecide -> BottomActionBar {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BounceButton(onClick = vm::approve, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("อนุมัติ") }
                        OutlinedButton(onClick = { text = ""; dialog = "reject" }, enabled = !vm.busy,
                            modifier = Modifier.weight(1f)) { Text("ปฏิเสธ") }
                    }
                }
            }
        },
        snackbarHostState = snackbar,
    ) { padding ->
        StateContent(vm.state, vm::load, "ไม่พบรายการ", Modifier.padding(padding)) { b ->
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row {
                    Text("${b.roomCode} ${b.roomName}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    StatusChip(b.status)
                }
                Card(Modifier.fillMaxWidth(), colors = appCardColors()) {
                    Column(Modifier.padding(16.dp)) {
                        LabelValue("วันที่", Fmt.date(b.start))
                        LabelValue("เวลา", Fmt.range(b.start, b.end))
                        LabelValue("อาคาร", b.building)
                        LabelValue("ผู้จอง", b.bookerName)
                        LabelValue("จำนวนคน", "${b.attendees} คน")
                        LabelValue("วัตถุประสงค์", b.purpose)
                        b.approverName?.let { LabelValue("ผู้พิจารณา", it) }
                        b.decidedAt?.let { LabelValue("พิจารณาเมื่อ", Fmt.dateTime(it)) }
                        b.rejectReason?.let { LabelValue("เหตุผลที่ปฏิเสธ", it) }
                    }
                }

                // The booker's e-ticket: shown from approval until it has been scanned
                val ticket = b.ticketCode
                if (ticket != null && b.status == BookingStatus.APPROVED && b.checkedInAt == null) {
                    TicketCard(b, ticket)
                }

                CheckInSection(b, busy = vm.busy, onStaffConfirm = { text = ""; dialog = "staffCheckIn" })

                // ------------------------------------------- actions (shown only if the server says allowed)
                if (b.canEdit || b.canCancel) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (b.canEdit) OutlinedButton(onClick = { onEdit(b.id) }, enabled = !vm.busy,
                            modifier = Modifier.weight(1f)) { Text("แก้ไข") }
                        if (b.canCancel) OutlinedButton(
                            onClick = { text = ""; dialog = "cancel" }, enabled = !vm.busy, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("ยกเลิกการจอง") }
                    }
                }

                // ------------------------------------------- status history
                Text("ประวัติสถานะ", style = MaterialTheme.typography.titleMedium)
                b.logs.forEach { log ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            (log.from?.takeIf { it != log.to }?.let { "${it.thai} → " } ?: "") + log.to.thai,
                            color = statusColor(log.to), fontWeight = FontWeight.SemiBold,
                        )
                        Text("${log.changedBy} · ${Fmt.dateTime(log.changedAt)}", style = MaterialTheme.typography.bodySmall)
                        log.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }

    when (dialog) {
        "reject" -> ReasonDialog(
            title = "ปฏิเสธการจอง", label = "เหตุผล (จำเป็น)", text = text, onText = { text = it },
            required = true, confirm = "ปฏิเสธ",
            onConfirm = { vm.reject(text); dialog = null }, onDismiss = { dialog = null },
        )
        "staffCheckIn" -> ReasonDialog(
            title = "ยืนยันว่ามาใช้ห้อง", label = "หมายเหตุ (จำเป็น)", text = text, onText = { text = it },
            required = true, confirm = "ยืนยัน",
            onConfirm = { vm.staffCheckIn(text); dialog = null }, onDismiss = { dialog = null },
        )
        "cancel" -> ReasonDialog(
            title = "ยืนยันการยกเลิก", label = "หมายเหตุ (ไม่บังคับ)", text = text, onText = { text = it },
            required = false, confirm = "ยกเลิกการจอง",
            onConfirm = { vm.cancel(text); dialog = null }, onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun TicketCard(b: Booking, code: String) {
    val qr = remember(code) {
        BarcodeEncoder().encodeBitmap(CheckInCode.qrContent(code), BarcodeFormat.QR_CODE, 720, 720).asImageBitmap()
    }
    val opens = b.checkinOpensAt
    val deadline = b.checkinDeadline
    Card(Modifier.fillMaxWidth(), colors = appCardColors()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("QR เช็กอิน", style = MaterialTheme.typography.titleMedium)
            // Always black on white: scanners struggle with a QR recoloured for dark mode.
            Surface(color = Color.White, shape = MaterialTheme.shapes.medium) {
                Image(qr, "QR เช็กอินของการจอง ${b.roomCode}", Modifier.padding(8.dp).size(220.dp))
            }
            Text(CheckInCode.display(code), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                if (b.isCheckInOpen()) "ถึงเวลาเช็กอินแล้ว แสดง QR นี้ให้เจ้าหน้าที่สแกน"
                else "เมื่อถึงห้อง แสดง QR นี้ให้เจ้าหน้าที่สแกนเพื่อเช็กอิน",
                textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold,
            )
            if (opens != null && deadline != null) {
                Text(
                    "เช็กอินได้ ${Fmt.range(opens, deadline)} ถ้าไม่เช็กอินจะถูกบันทึกว่าไม่มาใช้ห้อง",
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CheckInSection(b: Booking, busy: Boolean, onStaffConfirm: () -> Unit) {
    val checkedInAt = b.checkedInAt
    val opens = b.checkinOpensAt
    val deadline = b.checkinDeadline
    if (checkedInAt != null) {
        Text(
            "เช็กอินแล้วเมื่อ ${Fmt.dateTime(checkedInAt)}" + if (b.checkedInByStaff) " (เจ้าหน้าที่ยืนยัน)" else "",
            color = StatusColors.free, fontWeight = FontWeight.SemiBold,
        )
    }
    if (b.status == BookingStatus.NO_SHOW) {
        Text("ไม่มีการเช็กอินภายในเวลาที่กำหนด หากมาใช้ห้องจริง กรุณาติดต่อเจ้าหน้าที่",
            color = StatusColors.noShow, fontWeight = FontWeight.SemiBold)
    }
    // The owner sees the window on the ticket card; staff viewing the booking see it here.
    if (b.ticketCode == null && opens != null && deadline != null && opens.isAfter(OffsetDateTime.now())) {
        Text("ผู้จองเช็กอินได้ ${Fmt.range(opens, deadline)} โดยให้เจ้าหน้าที่สแกน QR ของผู้จอง",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (b.canStaffCheckIn) {
        OutlinedButton(onClick = onStaffConfirm, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("ยืนยันว่ามาใช้ห้อง")
        }
    }
}

@Composable
private fun ReasonDialog(
    title: String, label: String, text: String, onText: (String) -> Unit, required: Boolean, confirm: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { onText(it.take(500)) }, label = { Text(label) }, minLines = 2)
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !required || text.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ปิด") } },
    )
}
