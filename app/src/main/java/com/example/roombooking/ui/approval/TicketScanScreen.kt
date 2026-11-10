package com.example.roombooking.ui.approval

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roombooking.data.repository.BookingRepository
import com.example.roombooking.domain.Booking
import com.example.roombooking.domain.CheckInCode
import com.example.roombooking.ui.booking.PortraitCaptureActivity
import com.example.roombooking.ui.common.BounceButton
import com.example.roombooking.ui.common.Fmt
import com.example.roombooking.ui.common.LabelValue
import com.example.roombooking.ui.common.ScreenScaffold
import com.example.roombooking.ui.common.appCardColors
import com.example.roombooking.ui.common.appViewModelFactory
import com.example.roombooking.ui.theme.StatusColors
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

/** What the last scan did, shown under the scan button. */
sealed interface ScanResult {
    data class CheckedIn(val booking: Booking) : ScanResult
    data class Failed(val message: String) : ScanResult
}

/**
 * Staff check-in: an approver or admin scans the QR e-ticket on the booker's phone
 * (like a ticket checker at a cinema or concert). The server checks the ticket, the status and the time window.
 */
class TicketScanViewModel(
    private val handle: SavedStateHandle,
    private val repo: BookingRepository,
    private val isStaff: Boolean,
) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var result by mutableStateOf<ScanResult?>(null)
        private set

    init {
        // Opened from a QR scanned with the phone's own camera: check it in straight away, but only once,
        // even if Android later re-creates this screen.
        val fromCamera = handle.get<String>("code")
        if (fromCamera != null && handle.get<Boolean>(KEY_DONE) != true) {
            handle[KEY_DONE] = true
            submit(fromCamera)
        }
    }

    /** [raw] is what the camera read or what staff typed. */
    fun submit(raw: String) {
        if (busy) return
        if (!isStaff) {
            result = ScanResult.Failed("สแกนเช็กอินได้เฉพาะบัญชีผู้อนุมัติหรือผู้ดูแลระบบ กรุณาเข้าสู่ระบบด้วยบัญชีเจ้าหน้าที่")
            return
        }
        val code = CheckInCode.parse(raw)
        if (code == null) {
            result = ScanResult.Failed("QR นี้ไม่ใช่ตั๋วเช็กอินของระบบจองห้อง")
            return
        }
        busy = true
        viewModelScope.launch {
            result = repo.scanTicket(code).fold(
                onSuccess = { ScanResult.CheckedIn(it) },
                onFailure = { ScanResult.Failed(it.message ?: "เช็กอินไม่สำเร็จ") },
            )
            busy = false
        }
    }

    companion object {
        private const val KEY_DONE = "camera_code_done"

        val Factory = appViewModelFactory { c, h ->
            TicketScanViewModel(h, c.bookingRepository, isStaff = c.session.currentUser?.role?.isStaff == true)
        }
    }
}

@Composable
fun TicketScanScreen(
    onBack: () -> Unit,
    onOpenBooking: (Long) -> Unit,
    vm: TicketScanViewModel = viewModel(factory = TicketScanViewModel.Factory),
) {
    var typing by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { r ->
        r.contents?.let(vm::submit) // null when the user backed out of the camera
    }
    val openScanner = {
        scanner.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("เล็งกล้องไปที่ QR ในแอปของผู้จอง")
                .setBeepEnabled(false)
                .setCaptureActivity(PortraitCaptureActivity::class.java),
        )
    }

    ScreenScaffold(title = "สแกนเช็กอิน", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Text("สแกน QR ของผู้จองเพื่อเช็กอิน", style = MaterialTheme.typography.titleMedium)
            Text(
                "ผู้จองเปิดรายการจองที่อนุมัติแล้วในแอป แล้วแสดง QR ให้สแกนเมื่อมาถึงห้อง",
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BounceButton(onClick = openScanner, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                Text("เปิดกล้องสแกน")
            }
            TextButton(onClick = { text = ""; typing = true }, enabled = !vm.busy) { Text("กรอกรหัสแทน") }

            if (vm.busy) CircularProgressIndicator()
            when (val r = vm.result) {
                is ScanResult.CheckedIn -> CheckedInCard(r.booking, onOpenBooking)
                is ScanResult.Failed -> FailedCard(r.message)
                null -> Unit
            }
        }
    }

    if (typing) {
        AlertDialog(
            onDismissRequest = { typing = false },
            title = { Text("กรอกรหัสเช็กอิน") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(40) },
                    label = { Text("รหัสใต้ QR ของผู้จอง (12 ตัว)") }, singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { typing = false; vm.submit(text) }, enabled = text.isNotBlank()) { Text("เช็กอิน") }
            },
            dismissButton = { TextButton(onClick = { typing = false }) { Text("ปิด") } },
        )
    }
}

@Composable
private fun CheckedInCard(b: Booking, onOpenBooking: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = appCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.CheckCircle, null, Modifier.size(40.dp), tint = StatusColors.free)
            Text("เช็กอินสำเร็จ", color = StatusColors.free, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            LabelValue("ห้อง", "${b.roomCode} ${b.roomName}")
            LabelValue("ผู้จอง", b.bookerName)
            LabelValue("เวลา", Fmt.bookingWhen(b.start, b.end))
            LabelValue("จำนวนคน", "${b.attendees} คน")
            LabelValue("วัตถุประสงค์", b.purpose)
            TextButton(onClick = { onOpenBooking(b.id) }) { Text("ดูรายละเอียดการจอง") }
        }
    }
}

@Composable
private fun FailedCard(message: String) {
    Card(Modifier.fillMaxWidth(), colors = appCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.ErrorOutline, null, Modifier.size(40.dp), tint = StatusColors.rejected)
            Text("เช็กอินไม่สำเร็จ", color = StatusColors.rejected, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            Text(message)
        }
    }
}
