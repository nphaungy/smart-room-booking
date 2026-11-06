package com.example.roombooking.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roombooking.data.repository.AdminRepository
import com.example.roombooking.domain.BookingRules
import com.example.roombooking.domain.BookingStatus
import com.example.roombooking.domain.Dashboard
import com.example.roombooking.ui.common.Fmt
import com.example.roombooking.ui.common.GradientTitle
import com.example.roombooking.ui.common.Motion
import com.example.roombooking.ui.common.animatedCount
import com.example.roombooking.ui.common.ScreenScaffold
import com.example.roombooking.ui.common.StateContent
import com.example.roombooking.ui.common.UiState
import com.example.roombooking.ui.common.animatedProgress
import com.example.roombooking.ui.common.appViewModelFactory
import com.example.roombooking.ui.common.enterAnimation
import com.example.roombooking.ui.common.statusColor
import com.example.roombooking.ui.common.toState
import com.example.roombooking.ui.theme.Brand
import kotlinx.coroutines.launch
import java.time.YearMonth

/** RB09: monthly usage dashboard for approvers and admins. */
class DashboardViewModel(private val repo: AdminRepository) : ViewModel() {
    var month by mutableStateOf(YearMonth.from(BookingRules.today()))
        private set
    var state by mutableStateOf<UiState<Dashboard>>(UiState.Loading)
        private set

    init { load() }

    fun shift(months: Long) {
        month = month.plusMonths(months)
        load()
    }

    fun load() {
        val m = month
        viewModelScope.launch {
            state = UiState.Loading
            state = repo.dashboard(m.atDay(1), m.atEndOfMonth()).toState()
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> DashboardViewModel(c.adminRepository) }
    }
}

@Composable
fun DashboardScreen(
    topActions: @Composable () -> Unit,
    vm: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    ScreenScaffold(title = "Dashboard การใช้ห้อง", actions = topActions) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "เดือนก่อน") }
                // the month slides in from the side it was navigated towards
                AnimatedContent(
                    targetState = vm.month,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(Motion.MEDIUM)) { it * direction } + fadeIn(tween(Motion.MEDIUM))) togetherWith
                            (slideOutHorizontally(tween(Motion.MEDIUM)) { -it * direction } + fadeOut(tween(Motion.SHORT)))
                    },
                    label = "dashboardMonth",
                ) { m ->
                    Text(Fmt.monthYear(m.atDay(1)), style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = { vm.shift(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "เดือนถัดไป") }
            }
            StateContent(vm.state, vm::load, "ไม่มีข้อมูล") { d -> DashboardContent(d) }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, colors: List<Color>, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Column(Modifier.fillMaxSize().background(Brush.linearGradient(colors)).padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White)
        }
    }
}

/** Rounded bar on a track, coloured by how busy the room is. */
@Composable
private fun UsageBar(fraction: Float, modifier: Modifier = Modifier) {
    val colors = when {
        fraction < 0.4f -> Brand.Ocean
        fraction < 0.75f -> listOf(Brand.Amber, Brand.Coral)
        else -> Brand.Sunset
    }
    Box(
        modifier.fillMaxWidth().height(10.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
    ) {
        Box(
            Modifier.fillMaxWidth(fraction).fillMaxHeight()
                .background(Brush.horizontalGradient(colors), MaterialTheme.shapes.small),
        )
    }
}

/** Hour chart colour: calm teal for quiet hours through amber to pink at the peak. */
private fun heatColor(level: Float): Color =
    if (level < 0.5f) lerp(Brand.Teal, Brand.Amber, level * 2) else lerp(Brand.Amber, Brand.Pink, (level - 0.5f) * 2)

@Composable
private fun DashboardContent(d: Dashboard) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("คำขอทั้งหมด", animatedCount(d.totalBookings).toString(), Brand.Grape, Modifier.weight(1f).enterAnimation(0))
            StatCard("รออนุมัติตอนนี้", animatedCount(d.pendingNow, 90).toString(), Brand.Sunset, Modifier.weight(1f).enterAnimation(1))
            StatCard(
                "อัตราอนุมัติ",
                d.approvalRatePercent?.let { "${animatedCount(it.toInt(), 180)}%" } ?: "-",
                Brand.Ocean,
                Modifier.weight(1f).enterAnimation(2),
            )
        }

        GradientTitle("แยกตามสถานะ")
        val maxStatus = (d.byStatus.values.maxOrNull() ?: 0).coerceAtLeast(1)
        BookingStatus.entries.forEachIndexed { i, s ->
            val n = d.byStatus[s] ?: 0
            val fraction = animatedProgress(n / maxStatus.toFloat(), delayMillis = i * 90)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.thai, Modifier.width(96.dp))
                Box(Modifier.weight(1f).height(18.dp)) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight()
                        .background(statusColor(s), MaterialTheme.shapes.small))
                }
                Text("  $n")
            }
        }

        GradientTitle("อัตราการใช้ห้อง (ชั่วโมงที่อนุมัติ ÷ ชั่วโมงเปิดให้บริการ)")
        d.rooms.forEachIndexed { i, r ->
            val usage = animatedProgress((r.utilizationPercent / 100.0).toFloat().coerceIn(0f, 1f), delayMillis = 200 + i * 60)
            Column {
                Row {
                    Text("${r.roomCode} ${r.roomName}", Modifier.weight(1f))
                    Text("%.1f ชม. · %.1f%%".format(r.approvedHours, r.utilizationPercent))
                }
                UsageBar(usage, Modifier.padding(top = 4.dp))
            }
        }

        GradientTitle("ช่วงเวลาที่มีการจองมาก")
        val maxHour = (d.byHour.values.maxOrNull() ?: 0).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (BookingRules.OPEN_HOUR until BookingRules.CLOSE_HOUR).forEach { h ->
                val n = d.byHour[h] ?: 0
                val grow = animatedProgress(n / maxHour.toFloat(), delayMillis = 300 + (h - BookingRules.OPEN_HOUR) * 40)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (n > 0) Text("$n", style = MaterialTheme.typography.labelSmall)
                    Box(Modifier.fillMaxWidth().height((100f * grow).dp)
                        .background(heatColor(n / maxHour.toFloat()), MaterialTheme.shapes.extraSmall))
                    Text("$h", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
