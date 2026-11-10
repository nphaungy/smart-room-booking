package com.example.roombooking.ui.rooms

import androidx.compose.runtime.saveable.rememberSaveable
import com.example.roombooking.ui.common.appCardColors
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.example.roombooking.domain.isCheckInOpen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import com.example.roombooking.ui.common.BottomActionBar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roombooking.data.SessionManager
import com.example.roombooking.data.repository.BookingRepository
import com.example.roombooking.data.repository.RoomRepository
import com.example.roombooking.domain.Booking
import com.example.roombooking.domain.Room
import com.example.roombooking.domain.RoomSearch
import com.example.roombooking.domain.RoomType
import com.example.roombooking.ui.common.BounceButton
import com.example.roombooking.ui.common.ChoiceDropdown
import com.example.roombooking.ui.common.DateChips
import com.example.roombooking.ui.common.EmptyView
import com.example.roombooking.ui.common.ErrorView
import com.example.roombooking.ui.common.Fmt
import com.example.roombooking.ui.common.GradientIcon
import com.example.roombooking.ui.common.GradientTitle
import com.example.roombooking.ui.common.SkeletonCard
import com.example.roombooking.ui.common.enterOnce
import com.example.roombooking.ui.common.floatingOrbs
import com.example.roombooking.ui.common.flowingGradient
import com.example.roombooking.ui.common.popIn
import com.example.roombooking.ui.common.NumberField
import com.example.roombooking.ui.common.ScreenScaffold
import com.example.roombooking.ui.common.StatusChip
import com.example.roombooking.ui.common.TimeDropdown
import com.example.roombooking.ui.common.UiState
import com.example.roombooking.ui.common.appViewModelFactory
import com.example.roombooking.ui.common.pressScale
import com.example.roombooking.ui.common.toListState
import com.example.roombooking.ui.theme.Brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

data class NextBooking(val booking: Booking, val canCheckIn: Boolean)

class RoomListViewModel(
    private val repo: RoomRepository,
    private val bookings: BookingRepository,
    session: SessionManager,
) : ViewModel() {
    val userName: String = session.currentUser?.fullName.orEmpty()

    var filter by mutableStateOf(RoomSearch())
    var filterError by mutableStateOf<String?>(null)
        private set
    var types by mutableStateOf<List<RoomType>>(emptyList())
        private set
    /** Shown as a banner when we fall back to the cached list (offline). */
    var offlineNotice by mutableStateOf<String?>(null)
        private set

    private val refreshState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    private val searchResult = MutableStateFlow<UiState<List<Room>>?>(null) // null = show the cached list

    val state: StateFlow<UiState<List<Room>>> =
        combine(repo.cachedRooms, searchResult, refreshState) { cached, search, refresh ->
            search ?: when {
                cached.isNotEmpty() -> UiState.Success(cached)
                refresh is UiState.Error -> UiState.Error(refresh.message)
                refresh is UiState.Loading -> UiState.Loading
                else -> UiState.Empty
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    /** Re-evaluates the greeting card as time passes, so check-in appears while the screen stays open. */
    private val clock = flow {
        while (true) {
            emit(Unit)
            delay(30_000)
        }
    }

    /** The booking to check in to now, otherwise the next upcoming one, for the greeting card. */
    val nextBooking: StateFlow<NextBooking?> = combine(bookings.myBookings, clock) { list, _ ->
        val now = OffsetDateTime.now()
        val open = list.filter { it.isCheckInOpen(now) }.minByOrNull { it.start }
        when {
            open != null -> NextBooking(open, canCheckIn = true)
            else -> list.filter { it.status.isActive && it.end.isAfter(now) }.minByOrNull { it.start }
                ?.let { NextBooking(it, canCheckIn = false) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isSearching: Boolean get() = searchResult.value != null

    init {
        refresh()
        viewModelScope.launch { repo.roomTypes().onSuccess { types = it } }
        viewModelScope.launch { bookings.refreshMine() }
    }

    fun refresh() {
        if (searchResult.value != null) return search()
        refreshState.value = UiState.Loading
        viewModelScope.launch {
            val r = repo.refresh()
            offlineNotice = r.exceptionOrNull()?.message
            refreshState.value = r.fold({ UiState.Success(Unit) }, { UiState.Error(it.message ?: "โหลดข้อมูลไม่สำเร็จ") })
        }
    }

    fun search() {
        val f = filter
        filterError = when {
            f.date != null && (f.startMinute == null || f.endMinute == null) -> "กรุณาเลือกเวลาเริ่มและสิ้นสุด"
            f.startMinute != null && f.endMinute != null && f.startMinute >= f.endMinute -> "เวลาเริ่มต้องน้อยกว่าเวลาสิ้นสุด"
            (f.startMinute != null || f.endMinute != null) && f.date == null -> "กรุณาเลือกวันที่"
            else -> null
        }
        if (filterError != null) return
        searchResult.value = UiState.Loading
        viewModelScope.launch { searchResult.value = repo.search(f).toListState() }
    }

    fun clear() {
        filter = RoomSearch()
        filterError = null
        searchResult.value = null
    }

    companion object {
        val Factory = appViewModelFactory { c, _ ->
            RoomListViewModel(c.roomRepository, c.bookingRepository, c.session)
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "สวัสดีตอนเช้า"
    in 12..16 -> "สวัสดีตอนบ่าย"
    else -> "สวัสดีตอนเย็น"
}

@Composable
fun RoomListScreen(
    onOpenRoom: (roomId: Long, date: LocalDate?) -> Unit,
    onOpenBooking: (Long) -> Unit,
    topActions: @Composable () -> Unit,
    vm: RoomListViewModel = viewModel(factory = RoomListViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val next by vm.nextBooking.collectAsStateWithLifecycle()
    var showFilter by rememberSaveable { mutableStateOf(false) }
    val seen = remember { mutableSetOf<Any>() }
    val listState = rememberLazyListState()
    // Brings every filter on screen when the panel opens; index 1 is the panel.
    LaunchedEffect(showFilter) { if (showFilter) listState.animateScrollToItem(1) }

    ScreenScaffold(
        title = "ระบบจองห้อง",
        subtitle = "Smart Room Booking",
        actions = {
            IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "รีเฟรช") }
            topActions()
        },
        bottomBar = {
            if (showFilter) {
                BottomActionBar {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BounceButton(
                            onClick = {
                                vm.search()
                                // Results are below the panel, so it closes to reveal them.
                                if (vm.filterError == null) showFilter = false
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("ค้นหาห้องว่าง") }
                        OutlinedButton(onClick = vm::clear, modifier = Modifier.weight(1f)) { Text("ล้างตัวกรอง") }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Box(Modifier.enterOnce(seen, "greeting", 0)) { GreetingCard(vm.userName, next, onOpenBooking) } }
            item { Box(Modifier.enterOnce(seen, "search", 1)) { SearchPanel(vm, expanded = showFilter, onToggle = { showFilter = !showFilter }) } }

            vm.offlineNotice?.takeIf { state is UiState.Success && !vm.isSearching }?.let { msg ->
                item {
                    Text("แสดงข้อมูลที่บันทึกไว้ในเครื่อง ($msg)", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }

            item {
                SectionTitle(if (vm.isSearching) "ห้องที่ว่างตามเงื่อนไข" else "ห้องทั้งหมด",
                    (state as? UiState.Success)?.data?.size,
                    onClear = if (vm.isSearching) vm::clear else null)
            }

            when (val s = state) {
                UiState.Loading -> items(3) { SkeletonCard() }
                UiState.Empty -> item {
                    EmptyView(if (vm.isSearching) "ไม่มีห้องว่างตามเงื่อนไข ลองเปลี่ยนเวลาหรือจำนวนคน" else "ยังไม่มีห้องในระบบ")
                }
                is UiState.Error -> item { ErrorView(s.message, vm::refresh) }
                is UiState.Success -> itemsIndexed(s.data, key = { _, r -> r.id }) { index, room ->
                    // animateItem: rooms slide into place when a search filters the list
                    RoomCard(room, Modifier.animateItem().enterOnce(seen, room.id, index)) { onOpenRoom(room.id, vm.filter.date) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, count: Int?, onClear: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        GradientTitle(text, Modifier.weight(1f))
        if (onClear != null) TextButton(onClick = onClear) { Text("ล้าง") }
        if (count != null) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                Text(
                    "$count ห้อง", Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** Animated gradient card at the top of the home screen: greeting + the user's next booking. */
@Composable
private fun GreetingCard(name: String, upcoming: NextBooking?, onOpenBooking: (Long) -> Unit) {
    val next = upcoming?.booking
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .flowingGradient()
                .floatingOrbs()
                .padding(20.dp),
        ) {
            Text(greeting(), color = Brand.OnHeroMuted, style = MaterialTheme.typography.labelLarge)
            Text(name.ifBlank { "ผู้ใช้" }, color = Brand.OnHero, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Surface(
                color = Color.White.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().let { m ->
                    if (next != null) m.clickable { onOpenBooking(next.id) } else m
                },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).popIn(200).background(Brand.Highlight, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Event, null, tint = Brand.Ink) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (next == null) {
                            Text("ยังไม่มีการจองที่กำลังจะถึง", color = Brand.OnHero, fontWeight = FontWeight.SemiBold)
                            Text("ค้นหาห้องว่างด้านล่างเพื่อเริ่มจอง", color = Brand.OnHeroMuted,
                                style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("การจองถัดไป · ${next.roomCode}", color = Brand.OnHero, fontWeight = FontWeight.SemiBold)
                            Text(Fmt.bookingWhen(next.start, next.end), color = Brand.OnHeroMuted,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (next != null) StatusChip(next.status, onGradient = true)
                }
            }
            if (next != null && upcoming.canCheckIn) {
                Spacer(Modifier.height(12.dp))
                // The light button stands out on the orange card, because missing check-in is recorded as a no-show.
                Button(
                    onClick = { onOpenBooking(next.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.Highlight, contentColor = Brand.Ink),
                ) { Text("แสดง QR เช็กอิน" + (next.checkinDeadline?.let { " · ภายใน ${Fmt.time(it)} น." } ?: "")) }
            }
        }
    }
}

/** RB03 search filters in a collapsible card. */
@Composable
private fun SearchPanel(vm: RoomListViewModel, expanded: Boolean, onToggle: () -> Unit) {
    val arrowAngle by animateFloatAsState(
        if (expanded) 180f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "searchArrow",
    )
    Card(Modifier.fillMaxWidth(), colors = appCardColors(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (expanded) "ซ่อนตัวกรอง" else "แสดงตัวกรอง", onClick = onToggle)
                .semantics { stateDescription = if (expanded) "เปิดอยู่" else "ปิดอยู่" }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientIcon(Icons.Outlined.Search, Brand.Accent, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("ค้นหาห้องว่าง", style = MaterialTheme.typography.titleMedium)
                Text("เลือกวัน เวลา และจำนวนผู้เข้าร่วม", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ExpandMore, null, Modifier.rotate(arrowAngle))
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DateChips(vm.filter.date, { vm.filter = vm.filter.copy(date = it) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeDropdown("เริ่ม", vm.filter.startMinute, { vm.filter = vm.filter.copy(startMinute = it) },
                        Modifier.weight(1f))
                    TimeDropdown("ถึง", vm.filter.endMinute, { vm.filter = vm.filter.copy(endMinute = it) },
                        Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField("จำนวนคน", vm.filter.attendees?.toString() ?: "",
                        { vm.filter = vm.filter.copy(attendees = it.toIntOrNull()?.takeIf { n -> n > 0 }) },
                        Modifier.weight(1f))
                    ChoiceDropdown("ประเภท", vm.types, vm.types.firstOrNull { it.id == vm.filter.typeId },
                        { it.name }, { vm.filter = vm.filter.copy(typeId = it?.id) },
                        Modifier.weight(1.4f), allowNone = true)
                }
                OutlinedTextField(
                    value = vm.filter.query ?: "", onValueChange = { vm.filter = vm.filter.copy(query = it) },
                    label = { Text("ชื่อห้อง / รหัส / อาคาร") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                vm.filterError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private fun typeIcon(typeName: String): ImageVector = when {
    "ประชุม" in typeName -> Icons.Outlined.Groups
    "คอมพิวเตอร์" in typeName -> Icons.Outlined.Computer
    "เรียน" in typeName -> Icons.Outlined.School
    else -> Icons.Outlined.MeetingRoom
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomCard(room: Room, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = Brand.Accent
    val source = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().pressScale(source),
        colors = appCardColors(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 3.dp),
        interactionSource = source,
    ) {
        Column(
            Modifier
                // thin strip down the left edge
                .drawBehind { drawRect(Brush.verticalGradient(accent), size = Size(5.dp.toPx(), size.height)) }
                .padding(start = 21.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).popIn(120).background(Brush.linearGradient(accent), MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) { Icon(typeIcon(room.typeName), null, tint = Brand.OnHero) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(room.code, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(room.name, style = MaterialTheme.typography.titleMedium)
                    Text(room.typeName, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${room.capacity}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text("ที่นั่ง", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Place, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(" ${room.building} ชั้น ${room.floor}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!room.active) {
                    Spacer(Modifier.weight(1f))
                    Text("ปิดใช้งาน", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (room.equipment.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    room.equipment.forEach { e ->
                        Surface(color = accent.first().copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
                            Text(
                                if (e.quantity > 1) "${e.name} ×${e.quantity}" else e.name,
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text("ดูตารางและจอง", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
