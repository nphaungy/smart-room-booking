package com.example.roombooking.ui.admin

import androidx.compose.runtime.saveable.rememberSaveable
import com.example.roombooking.ui.common.appCardColors
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roombooking.data.remote.AppException
import com.example.roombooking.data.repository.AdminRepository
import com.example.roombooking.data.repository.RoomForm
import com.example.roombooking.data.repository.RoomRepository
import com.example.roombooking.domain.Closure
import com.example.roombooking.domain.Equipment
import com.example.roombooking.domain.Role
import com.example.roombooking.domain.Room
import com.example.roombooking.domain.RoomType
import com.example.roombooking.domain.User
import com.example.roombooking.ui.common.ChoiceDropdown
import com.example.roombooking.ui.common.DateChips
import com.example.roombooking.ui.common.Fmt
import com.example.roombooking.ui.common.LoadingView
import com.example.roombooking.ui.common.NumberField
import com.example.roombooking.ui.common.ScreenScaffold
import com.example.roombooking.ui.common.StateContent
import com.example.roombooking.ui.common.TimeDropdown
import com.example.roombooking.ui.common.UiState
import com.example.roombooking.ui.common.appViewModelFactory
import com.example.roombooking.ui.common.toListState
import com.example.roombooking.ui.rooms.RoomCard
import kotlinx.coroutines.launch
import java.time.LocalDate

// =====================================================================  admin home (rooms + users)
class AdminViewModel(private val repo: AdminRepository) : ViewModel() {
    var rooms by mutableStateOf<UiState<List<Room>>>(UiState.Loading)
        private set
    var users by mutableStateOf<UiState<List<User>>>(UiState.Loading)
        private set
    var message by mutableStateOf<String?>(null)

    fun loadRooms() {
        viewModelScope.launch { rooms = UiState.Loading; rooms = repo.rooms().toListState() }
    }

    fun loadUsers() {
        viewModelScope.launch { users = UiState.Loading; users = repo.users().toListState() }
    }

    fun setRole(user: User, role: Role) {
        viewModelScope.launch {
            repo.setRole(user.id, role)
                .onSuccess { message = "เปลี่ยนสิทธิ์ ${it.fullName} เป็น ${it.role}"; loadUsers() }
                .onFailure { message = it.message }
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, _ -> AdminViewModel(c.adminRepository) }
    }
}

@Composable
fun AdminScreen(
    onEditRoom: (Long?) -> Unit,
    onClosures: (Long) -> Unit,
    onCheckInQr: (Long) -> Unit,
    topActions: @Composable () -> Unit,
    vm: AdminViewModel = viewModel(factory = AdminViewModel.Factory),
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }
    // reload whenever this screen is shown again (e.g. after saving a room)
    LaunchedEffect(Unit) { vm.loadRooms(); vm.loadUsers() }

    ScreenScaffold(
        title = "ผู้ดูแลระบบ",
        actions = topActions,
        snackbarHostState = snackbar,
        floatingActionButton = {
            if (tab == 0) FloatingActionButton(onClick = { onEditRoom(null) }) { Icon(Icons.Filled.Add, "เพิ่มห้อง") }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("ห้องและอุปกรณ์") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("ผู้ใช้และสิทธิ์") })
            }
            if (tab == 0) {
                StateContent(vm.rooms, vm::loadRooms, "ยังไม่มีห้อง") { rooms ->
                    LazyColumn(contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 88.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(rooms, key = { it.id }) { room ->
                            Column {
                                RoomCard(room) { onEditRoom(room.id) }
                                Row {
                                    TextButton(onClick = { onClosures(room.id) }) { Text("จัดการช่วงปิดปรับปรุง") }
                                    TextButton(onClick = { onCheckInQr(room.id) }) { Text("QR เช็กอิน") }
                                }
                            }
                        }
                    }
                }
            } else {
                StateContent(vm.users, vm::loadUsers, "ไม่มีผู้ใช้") { users ->
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(users, key = { it.id }) { u ->
                            Card(Modifier.fillMaxWidth(), colors = appCardColors()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(u.fullName, style = MaterialTheme.typography.titleSmall)
                                        Text(u.email, style = MaterialTheme.typography.bodySmall)
                                    }
                                    ChoiceDropdown("สิทธิ์", Role.entries.toList(), u.role, { it.name },
                                        { r -> if (r != null && r != u.role) vm.setRole(u, r) }, Modifier.width(170.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================  room create / edit form
class RoomFormViewModel(
    handle: SavedStateHandle,
    private val admin: AdminRepository,
    private val rooms: RoomRepository,
) : ViewModel() {
    private val roomId: Long? = handle.get<Long>("roomId")?.takeIf { it > 0 }
    val isEdit = roomId != null

    var loading by mutableStateOf(true)
        private set
    var types by mutableStateOf<List<RoomType>>(emptyList())
        private set
    var allEquipment by mutableStateOf<List<Equipment>>(emptyList())
        private set

    var code by mutableStateOf("")
    var name by mutableStateOf("")
    var building by mutableStateOf("")
    var floor by mutableStateOf("1")
    var capacity by mutableStateOf("")
    var typeId by mutableStateOf<Int?>(null)
    var active by mutableStateOf(true)
    var description by mutableStateOf("")
    val equipment = mutableStateMapOf<Int, Int>() // id -> quantity
    var newEquipment by mutableStateOf("")

    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            rooms.roomTypes().onSuccess { types = it }
            rooms.equipment().onSuccess { allEquipment = it }
            if (roomId != null) {
                admin.rooms().onSuccess { list ->
                    list.firstOrNull { it.id == roomId }?.let { r ->
                        code = r.code; name = r.name; building = r.building; floor = r.floor.toString()
                        capacity = r.capacity.toString(); typeId = r.typeId; active = r.active
                        description = r.description.orEmpty()
                        r.equipment.forEach { equipment[it.id] = it.quantity }
                    }
                }.onFailure { error = it.message }
            }
            loading = false
        }
    }

    fun toggle(e: Equipment, checked: Boolean) {
        if (checked) equipment[e.id] = equipment[e.id] ?: 1 else equipment.remove(e.id)
    }

    fun addEquipment() {
        val n = newEquipment.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            admin.addEquipment(n).onSuccess { allEquipment = allEquipment + it; equipment[it.id] = 1; newEquipment = "" }
                .onFailure { error = it.message }
        }
    }

    fun save() {
        val cap = capacity.toIntOrNull()
        val fl = floor.toIntOrNull()
        val t = typeId
        error = when {
            code.isBlank() || name.isBlank() || building.isBlank() -> "กรุณากรอกรหัส ชื่อห้อง และอาคาร"
            cap == null || cap <= 0 -> "ความจุต้องมากกว่า 0"
            fl == null -> "กรุณาระบุชั้น"
            t == null -> "กรุณาเลือกประเภทห้อง"
            else -> null
        }
        if (error != null || saving) return
        saving = true
        viewModelScope.launch {
            admin.saveRoom(roomId, RoomForm(code, name, building, fl!!, cap!!, t!!, active, description, equipment.toMap()))
                .onSuccess { saved = true; rooms.refresh() }
                .onFailure { error = it.message }
            saving = false
        }
    }

    companion object {
        val Factory = appViewModelFactory { c, h -> RoomFormViewModel(h, c.adminRepository, c.roomRepository) }
    }
}

@Composable
fun RoomFormScreen(onBack: () -> Unit, vm: RoomFormViewModel = viewModel(factory = RoomFormViewModel.Factory)) {
    LaunchedEffect(vm.saved) { if (vm.saved) onBack() }
    ScreenScaffold(title = if (vm.isEdit) "แก้ไขห้อง" else "เพิ่มห้อง", onBack = onBack) { padding ->
        if (vm.loading) LoadingView(Modifier.padding(padding)) else Column(
            Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().imePadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(vm.code, { vm.code = it }, label = { Text("รหัสห้อง เช่น SC-301") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(vm.name, { vm.name = it }, label = { Text("ชื่อห้อง") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(vm.building, { vm.building = it }, label = { Text("อาคาร") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("ชั้น", vm.floor, { vm.floor = it }, Modifier.weight(1f))
                NumberField("ความจุ (คน)", vm.capacity, { vm.capacity = it }, Modifier.weight(1f))
            }
            ChoiceDropdown("ประเภทห้อง", vm.types, vm.types.firstOrNull { it.id == vm.typeId }, { it.name },
                { vm.typeId = it?.id }, Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("เปิดให้จอง", Modifier.weight(1f))
                Switch(checked = vm.active, onCheckedChange = { vm.active = it })
            }
            OutlinedTextField(vm.description, { vm.description = it }, label = { Text("รายละเอียด (ไม่บังคับ)") },
                minLines = 2, modifier = Modifier.fillMaxWidth())

            Text("อุปกรณ์ในห้อง", style = MaterialTheme.typography.titleMedium)
            vm.allEquipment.forEach { e ->
                val checked = vm.equipment.containsKey(e.id)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { vm.toggle(e, it) })
                    Text(e.name, Modifier.weight(1f))
                    if (checked) NumberField("จำนวน", (vm.equipment[e.id] ?: 1).toString(),
                        { v -> vm.equipment[e.id] = v.toIntOrNull()?.coerceAtLeast(1) ?: 1 }, Modifier.width(110.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(vm.newEquipment, { vm.newEquipment = it }, label = { Text("เพิ่มอุปกรณ์ใหม่") },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = vm::addEquipment) { Text("เพิ่ม") }
            }

            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = vm::save, enabled = !vm.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (vm.saving) "กำลังบันทึก..." else "บันทึก")
            }
        }
    }
}

// =====================================================================  closures (BR03)
class ClosuresViewModel(handle: SavedStateHandle, private val repo: AdminRepository) : ViewModel() {
    val roomId: Long = checkNotNull(handle.get<Long>("roomId"))
    var state by mutableStateOf<UiState<List<Closure>>>(UiState.Loading)
        private set
    var date by mutableStateOf<LocalDate?>(null)
    var start by mutableStateOf<Int?>(null)
    var end by mutableStateOf<Int?>(null)
    var reason by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
        private set
    /** Number of bookings that would be cancelled; non-null shows a confirmation dialog. */
    var conflictCount by mutableStateOf<Int?>(null)
        private set

    init { load() }

    fun load() {
        viewModelScope.launch { state = UiState.Loading; state = repo.closures(roomId).toListState() }
    }

    fun add(cancelConflicts: Boolean = false) {
        val d = date; val s = start; val e = end
        error = when {
            d == null || s == null || e == null -> "กรุณาเลือกวันและเวลา"
            s >= e -> "เวลาเริ่มต้องน้อยกว่าเวลาสิ้นสุด"
            reason.isBlank() -> "กรุณาระบุเหตุผล"
            else -> null
        }
        if (error != null) return
        conflictCount = null
        viewModelScope.launch {
            repo.addClosure(roomId, d!!, s!!, e!!, reason, cancelConflicts)
                .onSuccess { reason = ""; start = null; end = null; load() }
                .onFailure { ex ->
                    val app = ex as? AppException
                    if (app?.code == "CLOSURE_CONFLICT") {
                        conflictCount = (app.details?.get("booking_ids") as? List<*>)?.size ?: 1
                    } else error = ex.message
                }
        }
    }

    fun dismissConflict() { conflictCount = null }

    fun delete(c: Closure) {
        viewModelScope.launch { repo.deleteClosure(c.id).onSuccess { load() }.onFailure { error = it.message } }
    }

    companion object {
        val Factory = appViewModelFactory { c, h -> ClosuresViewModel(h, c.adminRepository) }
    }
}

@Composable
fun ClosuresScreen(onBack: () -> Unit, vm: ClosuresViewModel = viewModel(factory = ClosuresViewModel.Factory)) {
    ScreenScaffold(title = "ช่วงปิดปรับปรุง", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = appCardColors()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("เพิ่มช่วงปิดปรับปรุง", style = MaterialTheme.typography.titleSmall)
                    DateChips(vm.date, { vm.date = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeDropdown("เริ่ม", vm.start, { vm.start = it }, Modifier.weight(1f))
                        TimeDropdown("ถึง", vm.end, { vm.end = it }, Modifier.weight(1f))
                    }
                    OutlinedTextField(vm.reason, { vm.reason = it }, label = { Text("เหตุผล เช่น ซ่อมแอร์") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { vm.add() }, modifier = Modifier.fillMaxWidth()) { Text("บันทึกช่วงปิด") }
                }
            }
            StateContent(vm.state, vm::load, "ไม่มีช่วงปิดปรับปรุงที่กำลังจะมาถึง") { list ->
                LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(list, key = { it.id }) { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Fmt.bookingWhen(c.start, c.end))
                                Text(c.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.delete(c) }) { Icon(Icons.Filled.Delete, "ลบ") }
                        }
                    }
                }
            }
        }
    }

    vm.conflictCount?.let { n ->
        AlertDialog(
            onDismissRequest = vm::dismissConflict,
            title = { Text("มีการจองในช่วงนี้ $n รายการ") },
            text = { Text("หากยืนยัน ระบบจะยกเลิกการจองเหล่านี้และแจ้งเตือนผู้จองโดยอัตโนมัติ") },
            confirmButton = { TextButton(onClick = { vm.add(cancelConflicts = true) }) { Text("ยืนยันและยกเลิกการจอง") } },
            dismissButton = { TextButton(onClick = vm::dismissConflict) { Text("ไม่ใช่ตอนนี้") } },
        )
    }
}
