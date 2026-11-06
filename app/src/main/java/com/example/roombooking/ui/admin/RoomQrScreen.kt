package com.example.roombooking.ui.admin

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roombooking.data.repository.AdminRepository
import com.example.roombooking.domain.CheckInCode
import com.example.roombooking.domain.RoomCheckInCode
import com.example.roombooking.ui.common.ScreenScaffold
import com.example.roombooking.ui.common.StateContent
import com.example.roombooking.ui.common.UiState
import com.example.roombooking.ui.common.appViewModelFactory
import com.example.roombooking.ui.common.toState
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.launch

class RoomQrViewModel(handle: SavedStateHandle, private val repo: AdminRepository) : ViewModel() {
    private val roomId: Long = checkNotNull(handle.get<Long>("roomId"))
    var state by mutableStateOf<UiState<RoomCheckInCode>>(UiState.Loading)
        private set
    var message by mutableStateOf<String?>(null)

    init { load() }

    fun load() {
        viewModelScope.launch {
            state = UiState.Loading
            state = repo.roomCheckInCode(roomId).toState()
        }
    }

    fun rotate() {
        viewModelScope.launch {
            repo.rotateRoomCheckInCode(roomId)
                .onSuccess { state = UiState.Success(it); message = "สร้างรหัสใหม่แล้ว อย่าลืมเปลี่ยน QR หน้าห้อง" }
                .onFailure { message = it.message }
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, h -> RoomQrViewModel(h, c.adminRepository) }
    }
}

@Composable
fun RoomQrScreen(onBack: () -> Unit, vm: RoomQrViewModel = viewModel(factory = RoomQrViewModel.Factory)) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }
    var confirming by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(title = "QR เช็กอิน", onBack = onBack, snackbarHostState = snackbar) { padding ->
        StateContent(vm.state, vm::load, "ไม่พบห้อง", Modifier.padding(padding)) { room ->
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("${room.roomCode} ${room.roomName}", style = MaterialTheme.typography.titleLarge)
                val qr = remember(room.code) {
                    BarcodeEncoder().encodeBitmap(CheckInCode.qrContent(room.code), BarcodeFormat.QR_CODE, 720, 720).asImageBitmap()
                }
                // Always black on white: scanners struggle with a QR recoloured for dark mode.
                Surface(color = Color.White, shape = MaterialTheme.shapes.medium) {
                    Image(qr, "QR เช็กอินห้อง ${room.roomCode}", Modifier.size(260.dp))
                }
                Text(room.code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("ติด QR และรหัสนี้ไว้หน้าห้อง ผู้จองสแกนเพื่อเช็กอิน หรือกรอกรหัสถ้ากล้องใช้ไม่ได้",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { confirming = true }) { Text("สร้างรหัสใหม่") }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("สร้างรหัสใหม่?") },
            text = { Text("QR ที่ติดอยู่หน้าห้องจะใช้ไม่ได้ทันที ต้องนำ QR ใหม่ไปติดแทน") },
            confirmButton = { TextButton(onClick = { confirming = false; vm.rotate() }) { Text("สร้างใหม่") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("ปิด") } },
        )
    }
}
