package com.example.roombooking.ui.booking

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.CaptureActivity

/**
 * The library's scanner opens in landscape; the orientation is overridden in the manifest.
 * Its own prompt didn't show on Android 15, so the prompt is drawn here instead.
 */
class PortraitCaptureActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<View>(com.google.zxing.client.android.R.id.zxing_status_view)?.visibility = View.GONE
        val density = resources.displayMetrics.density
        val prompt = TextView(this).apply {
            text = intent.getStringExtra(Intents.Scan.PROMPT_MESSAGE)
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding((24 * density).toInt(), (72 * density).toInt(), (24 * density).toInt(), 0)
        }
        addContentView(
            prompt,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP),
        )
    }
}
