package com.example.roombooking.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.roombooking.AppContainer
import com.example.roombooking.data.SessionState
import com.example.roombooking.domain.Role
import com.example.roombooking.domain.User
import com.example.roombooking.ui.admin.AdminScreen
import com.example.roombooking.ui.admin.ClosuresScreen
import com.example.roombooking.ui.admin.RoomFormScreen
import com.example.roombooking.ui.approval.ApprovalsScreen
import com.example.roombooking.ui.auth.LoginScreen
import com.example.roombooking.ui.auth.RegisterScreen
import com.example.roombooking.ui.booking.BookingDetailScreen
import com.example.roombooking.ui.booking.BookingFormScreen
import com.example.roombooking.ui.booking.MyBookingsScreen
import com.example.roombooking.ui.common.LoadingView
import com.example.roombooking.ui.common.Motion
import com.example.roombooking.ui.dashboard.DashboardScreen
import com.example.roombooking.ui.notifications.NotificationsScreen
import com.example.roombooking.ui.rooms.RoomDetailScreen
import com.example.roombooking.ui.rooms.RoomListScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"

    const val ROOMS = "rooms"
    const val MY_BOOKINGS = "my_bookings"
    const val APPROVALS = "approvals"
    const val DASHBOARD = "dashboard"
    const val ADMIN = "admin"

    const val ROOM = "room/{roomId}?date={date}"
    fun room(id: Long, date: LocalDate?) = "room/$id?date=${date ?: ""}"

    const val BOOK_NEW = "book/new/{roomId}?date={date}&start={start}"
    fun bookNew(roomId: Long, date: LocalDate, start: Int?) = "book/new/$roomId?date=$date&start=${start ?: -1}"

    const val BOOK_EDIT = "book/edit/{bookingId}"
    fun bookEdit(id: Long) = "book/edit/$id"

    const val BOOKING = "booking/{bookingId}"
    fun booking(id: Long) = "booking/$id"

    const val NOTIFICATIONS = "notifications"

    const val ADMIN_ROOM = "admin/room/{roomId}"
    fun adminRoom(id: Long?) = "admin/room/${id ?: -1}"

    const val CLOSURES = "admin/closures/{roomId}"
    fun closures(id: Long) = "admin/closures/$id"
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private fun tabsFor(role: Role): List<TabItem> = buildList {
    add(TabItem(Routes.ROOMS, "ค้นหาห้อง", Icons.Filled.Search))
    add(TabItem(Routes.MY_BOOKINGS, "การจองของฉัน", Icons.Filled.Event))
    if (role.isStaff) {
        add(TabItem(Routes.APPROVALS, "พิจารณา", Icons.Filled.TaskAlt))
        add(TabItem(Routes.DASHBOARD, "สถิติ", Icons.Filled.BarChart))
    }
    if (role == Role.ADMIN) add(TabItem(Routes.ADMIN, "ผู้ดูแล", Icons.Filled.AdminPanelSettings))
}

private val tabRoutes = setOf(Routes.ROOMS, Routes.MY_BOOKINGS, Routes.APPROVALS, Routes.DASHBOARD, Routes.ADMIN)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

// Detail screens slide in from the right (and back out to the right on "back");
// switching between bottom tabs just cross-fades, like most apps.
private val enterAnim: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isTabSwitch()) fadeIn(tween(Motion.SHORT + 60))
    else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(Motion.MEDIUM)) +
        fadeIn(tween(Motion.MEDIUM))
}
private val exitAnim: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isTabSwitch()) fadeOut(tween(Motion.SHORT))
    else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(Motion.MEDIUM)) { it / 4 } +
        fadeOut(tween(Motion.MEDIUM))
}
private val popEnterAnim: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isTabSwitch()) fadeIn(tween(Motion.SHORT + 60))
    else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(Motion.MEDIUM)) { it / 4 } +
        fadeIn(tween(Motion.MEDIUM))
}
private val popExitAnim: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isTabSwitch()) fadeOut(tween(Motion.SHORT))
    else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(Motion.MEDIUM)) +
        fadeOut(tween(Motion.MEDIUM))
}

@Composable
fun AppRoot(container: AppContainer, pendingBookingId: Long?, onDeepLinkHandled: () -> Unit) {
    val session by container.session.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (container.session.state.value is SessionState.Loading) container.session.restore()
    }
    when (val s = session) {
        SessionState.Loading -> LoadingView()
        SessionState.LoggedOut -> AuthNav()
        // key(): a different account gets a fresh navigation stack
        is SessionState.LoggedIn -> key(s.user.id) {
            MainNav(container, s.user, pendingBookingId, onDeepLinkHandled)
        }
    }
}

@Composable
private fun AuthNav() {
    val nav = rememberNavController()
    NavHost(
        nav, startDestination = Routes.LOGIN,
        enterTransition = enterAnim, exitTransition = exitAnim,
        popEnterTransition = popEnterAnim, popExitTransition = popExitAnim,
    ) {
        composable(Routes.LOGIN) { LoginScreen(onRegister = { nav.navigate(Routes.REGISTER) }) }
        composable(Routes.REGISTER) { RegisterScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun MainNav(container: AppContainer, user: User, pendingBookingId: Long?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val tabs = remember(user.role) { tabsFor(user.role) }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val unread by container.notificationRepository.unreadCount.collectAsStateWithLifecycle(initialValue = 0)
    val scope = rememberCoroutineScope()

    // Tapped a system notification -> open that booking (RB08)
    LaunchedEffect(pendingBookingId) {
        pendingBookingId?.let {
            nav.navigate(Routes.booking(it))
            onDeepLinkHandled()
        }
    }

    // The bell rings only when a new notification arrives, not on every screen change.
    val bell = remember { Animatable(0f) }
    var lastUnread by remember { mutableIntStateOf(0) }
    LaunchedEffect(unread) {
        val isNew = unread > lastUnread
        lastUnread = unread
        if (isNew) listOf(-20f, 18f, -14f, 10f, -6f, 0f).forEach { bell.animateTo(it, tween(80)) }
    }

    val topActions: @Composable () -> Unit = {
        IconButton(onClick = { nav.navigate(Routes.NOTIFICATIONS) }) {
            BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                Icon(
                    Icons.Filled.Notifications, "การแจ้งเตือน",
                    Modifier.graphicsLayer {
                        rotationZ = bell.value
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    },
                )
            }
        }
        AccountMenu(user) { scope.launch { container.authRepository.logout() } }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = tabs.any { it.route == currentRoute },
                enter = slideInVertically(tween(Motion.MEDIUM)) { it } + fadeIn(tween(Motion.MEDIUM)),
                exit = slideOutVertically(tween(Motion.SHORT)) { it } + fadeOut(tween(Motion.SHORT)),
            ) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.navigateToTab(tab.route) },
                            icon = { TabIcon(tab.icon, currentRoute == tab.route) },
                            label = { Text(tab.label, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            nav, startDestination = Routes.ROOMS,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
            enterTransition = enterAnim, exitTransition = exitAnim,
            popEnterTransition = popEnterAnim, popExitTransition = popExitAnim,
        ) {
            // ---------------- tabs
            composable(Routes.ROOMS) {
                RoomListScreen(
                    onOpenRoom = { id, date -> nav.navigate(Routes.room(id, date)) },
                    onOpenBooking = { nav.navigate(Routes.booking(it)) },
                    topActions = topActions,
                )
            }
            composable(Routes.MY_BOOKINGS) {
                MyBookingsScreen(onOpen = { nav.navigate(Routes.booking(it)) }, topActions = topActions)
            }
            composable(Routes.APPROVALS) {
                ApprovalsScreen(onOpen = { nav.navigate(Routes.booking(it)) }, topActions = topActions)
            }
            composable(Routes.DASHBOARD) { DashboardScreen(topActions = topActions) }
            composable(Routes.ADMIN) {
                AdminScreen(
                    onEditRoom = { nav.navigate(Routes.adminRoom(it)) },
                    onClosures = { nav.navigate(Routes.closures(it)) },
                    topActions = topActions,
                )
            }

            // ---------------- detail screens
            composable(
                Routes.ROOM,
                arguments = listOf(
                    navArgument("roomId") { type = NavType.LongType },
                    navArgument("date") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                RoomDetailScreen(
                    onBack = { nav.popBackStack() },
                    onBook = { roomId, date, start -> nav.navigate(Routes.bookNew(roomId, date, start)) },
                    onOpenBooking = { nav.navigate(Routes.booking(it)) },
                )
            }
            composable(
                Routes.BOOK_NEW,
                arguments = listOf(
                    navArgument("roomId") { type = NavType.LongType },
                    navArgument("date") { type = NavType.StringType; defaultValue = "" },
                    navArgument("start") { type = NavType.IntType; defaultValue = -1 },
                ),
            ) {
                BookingFormScreen(
                    onBack = { nav.popBackStack() },
                    onSaved = { id ->
                        nav.navigate(Routes.booking(id)) { popUpTo(Routes.BOOK_NEW) { inclusive = true } }
                    },
                )
            }
            composable(Routes.BOOK_EDIT, arguments = listOf(navArgument("bookingId") { type = NavType.LongType })) {
                BookingFormScreen(
                    onBack = { nav.popBackStack() },
                    // go back to the (refreshed) detail screen instead of stacking a second one
                    onSaved = { nav.popBackStack() },
                )
            }
            composable(Routes.BOOKING, arguments = listOf(navArgument("bookingId") { type = NavType.LongType })) {
                BookingDetailScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.bookEdit(it)) })
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(onBack = { nav.popBackStack() }, onOpenBooking = { nav.navigate(Routes.booking(it)) })
            }
            composable(Routes.ADMIN_ROOM, arguments = listOf(navArgument("roomId") { type = NavType.LongType })) {
                RoomFormScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.CLOSURES, arguments = listOf(navArgument("roomId") { type = NavType.LongType })) {
                ClosuresScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}

/** Bottom-tab icon that springs a little larger when its tab is selected. */
@Composable
private fun TabIcon(icon: ImageVector, selected: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tabIcon",
    )
    Icon(
        icon, null,
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    )
}

/** Standard bottom-tab navigation: one copy of each tab, state restored when switching back. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AccountMenu(user: User, onLogout: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.AccountCircle, "บัญชี") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("${user.fullName}\n${user.email} · ${user.role}") }, onClick = {}, enabled = false)
            HorizontalDivider()
            DropdownMenuItem(text = { Text("ออกจากระบบ") }, onClick = { open = false; onLogout() })
        }
    }
}
