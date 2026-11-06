package com.example.roombooking.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.roombooking.domain.BookingRules
import com.example.roombooking.domain.BookingStatus
import com.example.roombooking.ui.theme.Brand
import com.example.roombooking.ui.theme.StatusColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            Column(Modifier.background(Brush.horizontalGradient(Brand.Hero))) {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, fontWeight = FontWeight.Bold)
                            if (subtitle != null) {
                                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Brand.Highlight, maxLines = 1)
                            }
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ย้อนกลับ")
                            }
                        }
                    },
                    actions = { actions() },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                )
            }
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
        content = content,
    )
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BouncingDots() }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GradientIcon(Icons.Outlined.ErrorOutline, Brand.Sunset, Modifier.popIn())
        Text(message, Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center)
        BounceButton(onClick = onRetry) { Text("ลองใหม่") }
    }
}

@Composable
fun EmptyView(message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GradientIcon(Icons.Outlined.Inbox, Brand.Grape, Modifier.bob())
        Text(message, Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

/** White icon on a round gradient badge. */
@Composable
fun GradientIcon(icon: ImageVector, colors: List<Color>, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Box(
        modifier.size(size).background(Brush.linearGradient(colors), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(size * 0.55f), tint = Color.White) }
}

/** Section heading with a small gradient bar in front of it. */
@Composable
fun GradientTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(4.dp).height(20.dp)
                .background(Brush.verticalGradient(Brand.Berry), MaterialTheme.shapes.extraSmall),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Placeholder with the shape of a list card, shown while the list loads. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).shimmer(MaterialTheme.shapes.medium))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.4f).height(12.dp).shimmer())
                Box(Modifier.fillMaxWidth(0.8f).height(16.dp).shimmer())
                Box(Modifier.fillMaxWidth(0.55f).height(12.dp).shimmer())
            }
        }
    }
}

/** Renders any UiState with the three standard states and a success slot. */
@Composable
fun <T> StateContent(
    state: UiState<T>,
    onRetry: () -> Unit,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    // Cross-fade only when the KIND of state changes (Loading -> Success, Success -> Error ...);
    // new data inside Success updates in place without flashing.
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.SHORT)) },
        label = "stateContent",
    ) { s ->
        when (s) {
            UiState.Loading -> LoadingView(modifier)
            UiState.Empty -> EmptyView(emptyMessage, modifier)
            is UiState.Error -> ErrorView(s.message, onRetry, modifier)
            is UiState.Success -> content(s.data)
        }
    }
}

fun statusColor(status: BookingStatus): Color = when (status) {
    BookingStatus.PENDING -> StatusColors.pending
    BookingStatus.APPROVED -> StatusColors.free
    BookingStatus.REJECTED -> StatusColors.approved
    BookingStatus.CANCELLED -> StatusColors.cancelled
}

@Composable
fun StatusChip(status: BookingStatus, modifier: Modifier = Modifier, onGradient: Boolean = false) {
    val color by animateColorAsState(statusColor(status), tween(Motion.MEDIUM), label = "statusChip")
    // On a gradient a see-through tint would be unreadable, so the chip turns solid white.
    val container = if (onGradient) Color.White else color.copy(alpha = 0.15f)
    Surface(color = container, shape = MaterialTheme.shapes.small, modifier = modifier) {
        AnimatedContent(targetState = status.thai, transitionSpec = {
            (fadeIn(tween(Motion.MEDIUM)) + scaleIn(tween(Motion.MEDIUM), initialScale = 0.8f)) togetherWith
                fadeOut(tween(Motion.SHORT))
        }, label = "statusText") { label ->
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // a booking still waiting for a decision keeps a live pulse
                if (status == BookingStatus.PENDING) {
                    PulsingDot(color, size = 6.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Horizontal list of the next 31 days (BR08: at most 30 days ahead). */
@Composable
fun DateChips(selected: LocalDate?, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(BookingRules.bookableDates()) { d ->
            FilterChip(
                selected = d == selected,
                onClick = { onSelect(d) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Fmt.dayName(d), style = MaterialTheme.typography.labelSmall)
                        Text(Fmt.dateShort(d), style = MaterialTheme.typography.labelLarge)
                    }
                },
            )
        }
    }
}

/** Picks a time in 30-minute steps (BR08), shown as a button with a drop-down list. */
@Composable
fun TimeDropdown(
    label: String,
    selectedMinute: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    options: List<Int> = BookingRules.timeOptions,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: " + (selectedMinute?.let(BookingRules::minuteLabel) ?: "--:--"))
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { m ->
                DropdownMenuItem(text = { Text(BookingRules.minuteLabel(m)) }, onClick = { onSelect(m); open = false })
            }
        }
    }
}

/** Generic single-choice drop-down. */
@Composable
fun <T> ChoiceDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: " + (selected?.let(optionLabel) ?: "ทั้งหมด"))
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowNone) DropdownMenuItem(text = { Text("ทั้งหมด") }, onClick = { onSelect(null); open = false })
            options.forEach { o ->
                DropdownMenuItem(text = { Text(optionLabel(o)) }, onClick = { onSelect(o); open = false })
            }
        }
    }
}

@Composable
fun NumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
                isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(0.38f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.62f))
    }
}
