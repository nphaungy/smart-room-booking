package com.example.roombooking.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.YearMonth

/** Previous / next month. The month slides in from the side it was navigated towards. */
@Composable
fun MonthSwitcher(month: YearMonth, onShift: (Long) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "เดือนก่อน") }
        AnimatedContent(
            targetState = month,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(Motion.MEDIUM)) { it * direction } + fadeIn(tween(Motion.MEDIUM))) togetherWith
                    (slideOutHorizontally(tween(Motion.MEDIUM)) { -it * direction } + fadeOut(tween(Motion.SHORT)))
            },
            label = "month",
        ) { m ->
            Text(Fmt.monthYear(m.atDay(1)), style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = { onShift(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "เดือนถัดไป") }
    }
}
