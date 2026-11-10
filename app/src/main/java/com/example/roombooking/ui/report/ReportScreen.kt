package com.example.roombooking.ui.report

import com.example.roombooking.ui.common.appCardColors
import androidx.compose.ui.semantics.semantics
import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roombooking.data.repository.AdminRepository
import com.example.roombooking.domain.Booking
import com.example.roombooking.domain.BookingRules
import com.example.roombooking.domain.MonthlyReport
import com.example.roombooking.ui.booking.BookingCard
import com.example.roombooking.ui.common.GradientTitle
import com.example.roombooking.ui.common.LabelValue
import com.example.roombooking.ui.common.MonthSwitcher
import com.example.roombooking.ui.common.ScreenScaffold
import com.example.roombooking.ui.common.StateContent
import com.example.roombooking.ui.common.UiState
import com.example.roombooking.ui.common.appViewModelFactory
import com.example.roombooking.ui.common.enterAnimation
import com.example.roombooking.ui.dashboard.StatCard
import com.example.roombooking.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.YearMonth

data class ReportData(val report: MonthlyReport, val noShows: List<Booking>)

class ReportViewModel(private val repo: AdminRepository) : ViewModel() {
    var month by mutableStateOf(YearMonth.from(BookingRules.today()))
        private set
    var state by mutableStateOf<UiState<ReportData>>(UiState.Loading)
        private set
    var message by mutableStateOf<String?>(null)
    private var job: Job? = null

    init { load() }

    fun shift(months: Long) {
        month = month.plusMonths(months)
        load()
    }

    fun load() {
        job?.cancel() // quick month changes must not let an older response overwrite a newer one
        val m = month
        job = viewModelScope.launch {
            state = UiState.Loading
            val report = repo.monthlyReport(m)
            val noShows = repo.noShows(m)
            state = report.fold(
                onSuccess = { r ->
                    noShows.fold(
                        onSuccess = { UiState.Success(ReportData(r, it)) },
                        onFailure = { UiState.Error(it.message ?: "โหลดไม่สำเร็จ") },
                    )
                },
                onFailure = { UiState.Error(it.message ?: "โหลดไม่สำเร็จ") },
            )
        }
    }

    fun export(uri: Uri, resolver: ContentResolver) {
        val m = month
        viewModelScope.launch {
            repo.monthlyReportCsv(m)
                .onSuccess { bytes ->
                    message = try {
                        withContext(Dispatchers.IO) { resolver.openOutputStream(uri)?.use { it.write(bytes) } }
                        "บันทึกไฟล์รายงานแล้ว"
                    } catch (e: IOException) {
                        "บันทึกไฟล์ไม่สำเร็จ"
                    }
                }
                .onFailure { message = it.message }
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> ReportViewModel(c.adminRepository) }
    }
}

@Composable
fun ReportScreen(
    onBack: () -> Unit,
    onOpenBooking: (Long) -> Unit,
    vm: ReportViewModel = viewModel(factory = ReportViewModel.Factory),
) {
    val snackbar = remember { SnackbarHostState() }
    val resolver = LocalContext.current.contentResolver
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.export(it, resolver) }
    }

    ScreenScaffold(
        title = "รายงานรายเดือน",
        onBack = onBack,
        actions = {
            IconButton(onClick = { saveFile.launch("room-report-${vm.month}.csv") }) {
                Icon(Icons.Filled.FileDownload, "ส่งออกไฟล์ CSV")
            }
        },
        snackbarHostState = snackbar,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            MonthSwitcher(vm.month, vm::shift)
            StateContent(vm.state, vm::load, "ไม่มีข้อมูล") { data -> ReportContent(data, onOpenBooking) }
        }
    }
}

@Composable
private fun ReportContent(data: ReportData, onOpenBooking: (Long) -> Unit) {
    val r = data.report
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("เช็กอิน", r.checkedIn.toString(), Brand.Accent, Modifier.weight(1f).enterAnimation(0))
            StatCard("ไม่มาใช้ห้อง", r.noShows.toString(), Brand.Accent, Modifier.weight(1f).enterAnimation(1))
            StatCard("อัตราการมา", r.attendanceRatePercent?.let { "%.0f%%".format(it) } ?: "-", Brand.Accent,
                Modifier.weight(1f).enterAnimation(2))
        }

        GradientTitle("สรุปทั้งเดือน")
        Card(Modifier.fillMaxWidth(), colors = appCardColors()) {
            Column(Modifier.padding(16.dp)) {
                LabelValue("คำขอทั้งหมด", r.totalRequests.toString())
                LabelValue("อนุมัติ", r.approved.toString())
                LabelValue("รออนุมัติ", r.pending.toString())
                LabelValue("ปฏิเสธ", r.rejected.toString())
                LabelValue("ยกเลิก", r.cancelled.toString())
                LabelValue("ชั่วโมงที่จอง", "%.1f ชม.".format(r.bookedHours))
                LabelValue("ชั่วโมงที่ใช้จริง", "%.1f ชม.".format(r.usedHours))
            }
        }

        GradientTitle("แยกตามห้อง")
        r.rooms.forEach { room ->
            Card(Modifier.fillMaxWidth(), colors = appCardColors()) {
                Column(Modifier.padding(12.dp).semantics(mergeDescendants = true) {}) {
                    Text("${room.roomCode} ${room.roomName}", fontWeight = FontWeight.SemiBold)
                    Text("อนุมัติ ${room.approved} · เช็กอิน ${room.checkedIn} · ไม่มา ${room.noShows}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("จอง %.1f ชม. · ใช้จริง %.1f ชม. · อัตราการใช้ %.1f%%".format(room.bookedHours, room.usedHours, room.utilizationPercent),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        GradientTitle("คิวรอห้อง")
        Card(Modifier.fillMaxWidth(), colors = appCardColors()) {
            Column(Modifier.padding(16.dp)) {
                LabelValue("เข้าคิว", r.waitlist.joined.toString())
                LabelValue("กำลังรอ", r.waitlist.waiting.toString())
                LabelValue("ได้ห้อง", r.waitlist.promoted.toString())
                LabelValue("ออกจากคิว", r.waitlist.cancelled.toString())
                LabelValue("หมดเวลา", r.waitlist.expired.toString())
            }
        }

        if (r.noShowUsers.isNotEmpty()) {
            GradientTitle("ผู้ที่ไม่มาใช้ห้อง")
            Card(Modifier.fillMaxWidth(), colors = appCardColors()) {
                Column(Modifier.padding(16.dp)) {
                    r.noShowUsers.forEach { LabelValue(it.fullName, "${it.noShows} ครั้ง") }
                }
            }
        }

        GradientTitle("บันทึกการไม่มาใช้ห้อง")
        if (data.noShows.isEmpty()) {
            Text("ไม่มีการบันทึกในเดือนนี้", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            data.noShows.forEach { b -> BookingCard(b, showBooker = true) { onOpenBooking(b.id) } }
        }
    }
}
