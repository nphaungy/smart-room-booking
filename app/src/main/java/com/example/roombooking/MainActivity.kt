package com.example.roombooking

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import com.example.roombooking.domain.CheckInCode
import com.example.roombooking.notification.NotificationHelper
import com.example.roombooking.notification.WorkScheduler
import com.example.roombooking.ui.navigation.AppRoot
import com.example.roombooking.ui.theme.RoomBookingTheme

class MainActivity : ComponentActivity() {

    /** Booking id from a tapped notification; AppRoot navigates to it and then clears it. */
    private val pendingBookingId = mutableStateOf<Long?>(null)

    /** Ticket code from a QR scanned with the phone's camera; AppRoot opens the scanner with it, then clears it. */
    private val pendingTicket = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // dark status-bar icons on the bright orange top bar (it is orange in both light and dark mode)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        readDeepLink(intent)
        // Only a fresh launch carries a new scan; a re-created activity must not check in a second time.
        if (savedInstanceState == null) readTicket(intent)
        val container = (application as RoomBookingApp).container

        setContent {
            RoomBookingTheme {
                // Android 13+: ask once for permission to show notifications (RB08)
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !NotificationHelper.canNotify(this@MainActivity)
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                AppRoot(
                    container = container,
                    pendingBookingId = pendingBookingId.value,
                    onDeepLinkHandled = { pendingBookingId.value = null },
                    pendingTicket = pendingTicket.value,
                    onTicketHandled = { pendingTicket.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readDeepLink(intent)
        readTicket(intent)
    }

    override fun onResume() {
        super.onResume()
        WorkScheduler.pollNow(this) // check for new notifications whenever the app is opened
    }

    private fun readDeepLink(intent: Intent?) {
        val id = intent?.getLongExtra(NotificationHelper.EXTRA_BOOKING_ID, -1L) ?: -1L
        if (id > 0) pendingBookingId.value = id
    }

    private fun readTicket(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return
        // Reopened from "recent apps": the old scan is replayed, so ignore it.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val code = intent.data?.toString()?.let(CheckInCode::parse) ?: return
        pendingTicket.value = code
    }
}
