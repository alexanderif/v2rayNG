package com.v2ray.ang.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.handler.MmkvManager
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MILLIS_PER_DAY = 86_400_000L
private const val BINARY_STEP = 1024.0

/** Common subscription lengths in days; the real length is not published by panels, so it is guessed. */
private val STANDARD_PERIOD_DAYS = intArrayOf(7, 15, 30, 60, 90, 120, 180, 365)

private val ColorStatusOk = Color(0xFF2FD14A)
private val ColorStatusBad = Color(0xFFE53935)
private val ColorTraffic = Color(0xFFFF7F0E)
private val ColorTime = Color(0xFFFFD60A)

// The card is 25% lower than before: every vertical size is 0.75x, horizontal sizes are unchanged.

/**
 * One card per subscription: name + status + share/edit/delete on top, then the remaining traffic
 * and days on one side and the total traffic / total period bars on the other.
 *
 * The bars are always on the left and the figures on the right, in every language.
 * [info] is null for a subscription whose panel publishes no account information; the card then
 * only has its header. The action callbacks are null when the card has no config to act on.
 */
@Composable
internal fun SubscriptionServerCard(
    title: String,
    groupId: String,
    info: SubscriptionUsageInfo?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onShare: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val now = remember(info) { System.currentTimeMillis() }
    val model = remember(info) { info?.let { buildSubscriptionInfoUiModel(it, now) } }

    val unlimited = model == null || model.totalBytes <= 0L
    val remainingBytes = if (model == null) 0L else (model.totalBytes - model.usedBytes).coerceAtLeast(0L)
    // The bars fill up as the account is used: empty when new, full when traffic / time has ended.
    val trafficFraction = if (unlimited || model == null) 0f
    else (1f - remainingBytes.toFloat() / model.totalBytes.toFloat()).coerceIn(0f, 1f)

    val expired = model?.expired == true
    val daysLeft: Long? = model?.expireMillis?.let { ((it - now) + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY }
    val periodDays: Int? = daysLeft?.let { left ->
        remember(groupId, left) { resolveSubscriptionPeriodDays(groupId, left.toInt()) }
    }
    val timeFraction = when {
        expired -> 1f
        daysLeft != null && periodDays != null && periodDays > 0 ->
            (1f - daysLeft.toFloat() / periodDays.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }

    // A bar that has reached its end turns red, and so does the status circle.
    val trafficEnded = !unlimited && remainingBytes == 0L
    val timeEnded = expired || (daysLeft != null && daysLeft <= 0L)
    val healthy = !trafficEnded && !timeEnded
    val trafficColor = if (trafficEnded) ColorStatusBad else ColorTraffic
    val timeColor = if (timeEnded) ColorStatusBad else ColorTime

    // Why the account is cut off; written in front of the name and scrolled because it is long.
    val endedCause: String? = when {
        trafficEnded && timeEnded -> stringResource(R.string.sub_card_ended_both)
        trafficEnded -> stringResource(R.string.sub_card_ended_traffic)
        timeEnded -> stringResource(R.string.sub_card_ended_time)
        else -> null
    }

    val selectedDescription = if (isSelected) stringResource(R.string.acc_selected_server) else null
    val naturalDirection = LocalLayoutDirection.current

    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .semantics {
                if (selectedDescription != null) stateDescription = selectedDescription
            },
        shape = RoundedCornerShape(21.dp),
        color = scheme.surface,
        shadowElevation = 4.dp,
        border = if (isSelected) BorderStroke(2.dp, scheme.primary) else null,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            // Header: status circle, name, actions
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PulseDot(
                    color = if (healthy) ColorStatusOk else ColorStatusBad,
                    diameter = 22.dp,
                )
                Spacer(Modifier.width(12.dp))
                if (endedCause != null) {
                    MarqueeLtrText(
                        text = "$endedCause - $title",
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    FitOneLineText(
                        text = title,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (onShare != null) {
                    Spacer(Modifier.width(8.dp))
                    RoundActionButton(
                        iconRes = R.drawable.ic_share_24dp,
                        contentDescription = stringResource(R.string.title_configuration_share),
                        onClick = onShare,
                    )
                }
                if (onEdit != null) {
                    Spacer(Modifier.width(8.dp))
                    RoundActionButton(
                        iconRes = R.drawable.ic_edit_24dp,
                        contentDescription = stringResource(R.string.acc_edit),
                        onClick = onEdit,
                    )
                }
                if (onDelete != null) {
                    Spacer(Modifier.width(8.dp))
                    RoundActionButton(
                        iconRes = R.drawable.ic_delete_24dp,
                        contentDescription = stringResource(R.string.acc_delete),
                        onClick = onDelete,
                    )
                }
            }

            if (model != null) {
                Spacer(Modifier.height(9.dp))
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(Modifier.height(9.dp))

                // The body is laid out right-to-left in every language: figures first (right), bars
                // second (left). Each half goes back to the language's own direction inside.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides naturalDirection) {
                                if (unlimited) {
                                    StatLine(
                                        iconRes = R.drawable.ic_storage_24dp,
                                        number = stringResource(R.string.sub_info_unlimited),
                                        suffix = "",
                                        color = trafficColor,
                                    )
                                } else {
                                    val (num, unit) = splitTraffic(remainingBytes)
                                    StatLine(
                                        iconRes = R.drawable.ic_storage_24dp,
                                        number = num,
                                        suffix = stringResource(R.string.sub_card_left_suffix, trafficUnitLabel(unit)),
                                        color = trafficColor,
                                    )
                                }
                                when {
                                    expired -> StatLine(
                                        iconRes = R.drawable.ic_calendar_24dp,
                                        number = stringResource(R.string.sub_card_expired),
                                        suffix = "",
                                        color = ColorStatusBad,
                                    )
                                    daysLeft != null -> StatLine(
                                        iconRes = R.drawable.ic_calendar_24dp,
                                        number = daysLeft.coerceAtLeast(0L).toString(),
                                        suffix = stringResource(R.string.sub_card_days_left_suffix),
                                        color = timeColor,
                                    )
                                    else -> StatLine(
                                        iconRes = R.drawable.ic_calendar_24dp,
                                        number = stringResource(R.string.sub_card_no_expiry),
                                        suffix = "",
                                        color = timeColor,
                                    )
                                }
                            }
                        }

                        Box(
                            Modifier
                                .padding(horizontal = 12.dp)
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(scheme.outlineVariant.copy(alpha = 0.8f))
                        )

                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides naturalDirection) {
                                val totalLabel = if (unlimited) {
                                    stringResource(R.string.sub_info_unlimited)
                                } else {
                                    val (num, unit) = splitTraffic(model.totalBytes)
                                    "$num ${trafficUnitLabel(unit)}"
                                }
                                BarBlock(
                                    iconRes = R.drawable.ic_bar_chart_24dp,
                                    label = totalLabel,
                                    fraction = if (trafficEnded) 1f else trafficFraction,
                                    fillColor = trafficColor,
                                    trackColor = trafficColor.copy(alpha = 0.2f),
                                )
                                BarBlock(
                                    iconRes = R.drawable.ic_calendar_24dp,
                                    label = if (periodDays != null) periodLabel(periodDays) else stringResource(R.string.sub_card_no_expiry),
                                    fraction = if (timeEnded) 1f else timeFraction,
                                    fillColor = timeColor,
                                    trackColor = if (timeEnded) timeColor.copy(alpha = 0.2f) else scheme.onSurface.copy(alpha = 0.1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A config of a subscription with several configs, listed under the subscription's card. */
@Composable
internal fun SubscriptionChildRow(
    row: ServerRowUiModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val selectedDescription = if (isSelected) stringResource(R.string.acc_selected_server) else null
    val delayText = if (row.testDelayMillis == 0L) "" else stringResource(R.string.server_test_delay_value, row.testDelayMillis)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) scheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .semantics {
                if (selectedDescription != null) stateDescription = selectedDescription
            }
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.remarks,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) scheme.primary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (delayText.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = delayText,
                style = MaterialTheme.typography.bodySmall,
                color = if (row.testDelayMillis < 0L) ColorStatusBad else scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Single-line text that shrinks its font until the whole name fits (down to a minimum size). */
@Composable
private fun FitOneLineText(text: String, modifier: Modifier = Modifier) {
    var fontSize by remember(text) { mutableStateOf(16.sp) }
    Text(
        text = text,
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize > 10.sp) {
                fontSize = (fontSize.value - 0.5f).sp
            }
        },
    )
}

/**
 * Single-line text that moves from left to right and starts again, so a long text can be read in
 * one line. The direction does not depend on the language. A text that fits stays still.
 */
@Composable
private fun MarqueeLtrText(text: String, modifier: Modifier = Modifier) {
    key(text) {
        val transition = rememberInfiniteTransition(label = "titleMarquee")
        val durationMillis = (4000 + text.length * 160).coerceAtMost(20000)
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "titleMarqueeProgress",
        )
        Layout(
            content = {
                Text(
                    text = text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
            },
            modifier = modifier.clipToBounds(),
        ) { measurables, constraints ->
            val placeable = measurables.first().measure(
                Constraints(
                    minWidth = 0,
                    maxWidth = Constraints.Infinity,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight,
                )
            )
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
            val height = placeable.height.coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                // From just outside the left edge to just outside the right edge, then again.
                val x = if (placeable.width <= width) {
                    0
                } else {
                    (-placeable.width + (width + placeable.width) * progress).roundToInt()
                }
                placeable.place(x, 0)
            }
        }
    }
}

/** Green/red circle that pulses (a soft ring spreads out of it and fades). */
@Composable
private fun PulseDot(color: Color, diameter: Dp) {
    val transition = rememberInfiniteTransition(label = "statusPulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "statusPulsePhase",
    )
    Canvas(Modifier.size(diameter)) {
        val maxRadius = this.size.minDimension / 2f
        val coreRadius = maxRadius * (0.52f + 0.06f * sin(phase * 2f * PI.toFloat()))
        listOf(phase, (phase + 0.5f) % 1f).forEach { p ->
            drawCircle(
                color = color.copy(alpha = (1f - p) * 0.45f),
                radius = coreRadius + (maxRadius - coreRadius) * p,
            )
        }
        drawCircle(color = color, radius = coreRadius)
    }
}

@Composable
private fun RoundActionButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(iconRes),
                contentDescription,
                Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Grey round icon followed by a big colored number and a small suffix ("7.5" + "GB left"). */
@Composable
private fun StatLine(iconRes: Int, number: String, suffix: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = number,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
        if (suffix.isNotEmpty()) {
            Text(
                text = " $suffix",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Label with a small grey icon above a rounded progress bar. */
@Composable
private fun BarBlock(iconRes: Int, label: String, fraction: Float, fillColor: Color, trackColor: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        // The bar always fills from the left edge, whatever the language direction.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(trackColor)
            ) {
                if (fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                            .clip(RoundedCornerShape(5.dp))
                            .background(fillColor)
                    )
                }
            }
        }
    }
}

/** Splits a byte count into a short number ("7.5", "10") and the unit index (0 = B ... 4 = TB). */
private fun splitTraffic(bytes: Long): Pair<String, Int> {
    var size = bytes.toDouble()
    var unit = 0
    while (size >= BINARY_STEP && unit < 4) {
        size /= BINARY_STEP
        unit++
    }
    val text = if (size >= 100 || size == Math.floor(size)) {
        String.format(Locale.US, "%.0f", size)
    } else {
        String.format(Locale.US, "%.1f", size)
    }
    return text to unit
}

@Composable
private fun trafficUnitLabel(unit: Int): String = stringResource(
    when (unit) {
        0 -> R.string.sub_unit_b
        1 -> R.string.sub_unit_kb
        2 -> R.string.sub_unit_mb
        3 -> R.string.sub_unit_gb
        else -> R.string.sub_unit_tb
    }
)

@Composable
private fun periodLabel(days: Int): String = when (days) {
    30 -> stringResource(R.string.sub_period_month_1)
    60 -> stringResource(R.string.sub_period_month_2)
    90 -> stringResource(R.string.sub_period_month_3)
    120 -> stringResource(R.string.sub_period_month_4)
    180 -> stringResource(R.string.sub_period_month_6)
    365 -> stringResource(R.string.sub_period_year_1)
    else -> stringResource(R.string.sub_period_days, days)
}

/**
 * Panels do not publish the length of a subscription, only the expiry date. The longest number of
 * days left ever seen for this subscription is remembered (a renewal starts a new measurement) and
 * rounded up to the nearest common period (30, 60, 90 ... days).
 */
internal fun resolveSubscriptionPeriodDays(groupId: String, daysLeft: Int): Int {
    val maxKey = "pref_sub_period_max_$groupId"
    val lastKey = "pref_sub_period_last_$groupId"
    var max = MmkvManager.decodeSettingsInt(maxKey, 0)
    val last = MmkvManager.decodeSettingsInt(lastKey, 0)
    if (max <= 0 || daysLeft > last + 1 || daysLeft > max) {
        max = daysLeft
    }
    MmkvManager.encodeSettings(maxKey, max)
    MmkvManager.encodeSettings(lastKey, daysLeft)
    return STANDARD_PERIOD_DAYS.firstOrNull { it >= max } ?: max
}
