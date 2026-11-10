package com.example.roombooking.ui.common

import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.HorizontalDivider
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
    bottomBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            // The bar has a fixed height: with large text the decorative subtitle gives way to the title.
            val showSubtitle = subtitle != null && LocalDensity.current.fontScale <= 1.15f
            CappedFontScale {
                Column(Modifier.background(Brush.horizontalGradient(Brand.Hero))) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(title, Modifier.semantics { heading() }, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                if (showSubtitle) {
                                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Brand.OnHeroMuted, maxLines = 1)
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
                            titleContentColor = Brand.OnHero,
                            navigationIconContentColor = Brand.OnHero,
                            actionIconContentColor = Brand.OnHero,
                        ),
                    )
                    // a light hairline; it also keeps the bar distinct from the page in dark mode
                    HorizontalDivider(color = Brand.Highlight.copy(alpha = 0.4f))
                }
            }
        },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
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
        GradientIcon(Icons.Outlined.ErrorOutline, Brand.Accent, Modifier.popIn())
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
        GradientIcon(Icons.Outlined.Inbox, Brand.Accent, Modifier.bob())
        Text(message, Modifier.padding(vertical = 12.dp), textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

/**
 * For bars with a fixed height, where text scaled past [max] would be cut off. Page content
 * stays outside this and scales fully with the user's setting.
 */
@Composable
fun CappedFontScale(max: Float = 1.3f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(max)),
        content = content,
    )
}

/** White on the warm off-white page in light mode; the raised tone in dark mode, where white isn't available. */
@Composable
fun appCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHighest
    else MaterialTheme.colorScheme.surfaceContainerLowest,
)

/**
 * Keeps a screen's main action in reach of the thumb. It rides above the keyboard, which also
 * shrinks the scrolling content above it, so a focused field is scrolled into view.
 */
@Composable
fun BottomActionBar(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            content = content,
        )
    }
}

/** Navy icon on a round orange badge. */
@Composable
fun GradientIcon(icon: ImageVector, colors: List<Color>, modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Box(
        modifier.size(size).background(Brush.linearGradient(colors), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(size * 0.55f), tint = Brand.OnHero) }
}

/** Section heading with a thin bar in front of it. */
@Composable
fun GradientTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(2.dp).height(18.dp)
                .background(MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.extraSmall),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary)
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

@Composable
fun statusColor(status: BookingStatus): Color = when (status) {
    BookingStatus.PENDING -> StatusColors.pending
    BookingStatus.APPROVED -> StatusColors.free
    BookingStatus.REJECTED -> StatusColors.rejected
    BookingStatus.CANCELLED -> StatusColors.cancelled
    BookingStatus.NO_SHOW -> StatusColors.noShow
}

@Composable
fun StatusChip(status: BookingStatus, modifier: Modifier = Modifier, onGradient: Boolean = false) {
    val color by animateColorAsState(statusColor(status), tween(Motion.MEDIUM), label = "statusChip")
    // On the orange hero a see-through tint would be unreadable, so the chip turns solid white.
    // Approved is the status people look for, so it alone gets a solid fill.
    val solid = status == BookingStatus.APPROVED
    val container = when {
        onGradient -> Color.White
        solid -> color
        else -> color.copy(alpha = 0.12f)
    }
    val textColor = when {
        onGradient -> Brand.Ink
        solid -> MaterialTheme.colorScheme.surface
        else -> color
    }
    Surface(color = container, shape = MaterialTheme.shapes.small, modifier = modifier) {
        AnimatedContent(targetState = status.thai, transitionSpec = {
            (fadeIn(tween(Motion.MEDIUM)) + scaleIn(tween(Motion.MEDIUM), initialScale = 0.8f)) togetherWith
                fadeOut(tween(Motion.SHORT))
        }, label = "statusText") { label ->
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // a booking still waiting for a decision keeps a live pulse
                if (status == BookingStatus.PENDING) {
                    PulsingDot(textColor, size = 6.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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

/** A read-only field that opens a menu, so TalkBack announces it as a choice with its current value. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectField(
    label: String,
    value: String,
    choices: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label, maxLines = 1) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { (text, select) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { select(); open = false })
            }
        }
    }
}

/** Picks a time in 30-minute steps (BR08). */
@Composable
fun TimeDropdown(
    label: String,
    selectedMinute: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    options: List<Int> = BookingRules.timeOptions,
) {
    SelectField(
        label = label,
        value = selectedMinute?.let(BookingRules::minuteLabel).orEmpty(),
        choices = options.map { m -> BookingRules.minuteLabel(m) to { onSelect(m) } },
        modifier = modifier,
    )
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
    val none: List<Pair<String, () -> Unit>> = if (allowNone) listOf("ทั้งหมด" to { onSelect(null) }) else emptyList()
    SelectField(
        label = label,
        value = selected?.let(optionLabel) ?: if (allowNone) "ทั้งหมด" else "",
        choices = none + options.map { o -> optionLabel(o) to { onSelect(o) } },
        modifier = modifier,
    )
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
    // merged so TalkBack reads the label and its value as one item
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {}) {
        Text(label, Modifier.weight(0.38f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.62f))
    }
}
