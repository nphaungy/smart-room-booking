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
import com.example.roombooking.notification.NotificationHelper
import com.example.roombooking.notification.WorkScheduler
import com.example.roombooking.ui.navigation.AppRoot
import com.example.roombooking.ui.theme.RoomBookingTheme

class MainActivity : ComponentActivity() {

    /** Booking id from a tapped notification; AppRoot navigates to it and then clears it. */
    private val pendingBookingId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // white status-bar icons on the navy top bar
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        readDeepLink(intent)
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
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        WorkScheduler.pollNow(this) // check for new notifications whenever the app is opened
    }

    private fun readDeepLink(intent: Intent?) {
        val id = intent?.getLongExtra(NotificationHelper.EXTRA_BOOKING_ID, -1L) ?: -1L
        if (id > 0) pendingBookingId.value = id
    }
}
