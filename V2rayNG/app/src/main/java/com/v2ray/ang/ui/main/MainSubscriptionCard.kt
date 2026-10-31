package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.handler.MmkvManager
import java.util.Locale

private const val MILLIS_PER_DAY = 86_400_000L
private const val LOW_FRACTION = 0.1f
private const val LOW_DAYS = 3L
private const val BINARY_STEP = 1024.0

/** Common subscription lengths in days; the real length is not published by panels, so it is guessed. */
private val STANDARD_PERIOD_DAYS = intArrayOf(7, 15, 30, 60, 90, 120, 180, 365)

private val ColorStatusOk = Color(0xFF4CD11F)
private val ColorStatusBad = Color(0xFFE53935)
private val ColorTraffic = Color(0xFFFF7F0E)
private val ColorTime = Color(0xFFFFD60A)

/**
 * One card per subscription config: name + status + share/edit/delete on top, then the remaining
 * traffic and days on one side and the total traffic / total period bars on the other.
 */
@Composable
internal fun SubscriptionServerCard(
    row: ServerRowUiModel,
    groupId: String,
    info: SubscriptionUsageInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val now = remember(info) { System.currentTimeMillis() }
    val model = remember(info) { buildSubscriptionInfoUiModel(info, now) }

    val unlimited = model.totalBytes <= 0L
    val remainingBytes = (model.totalBytes - model.usedBytes).coerceAtLeast(0L)
    val trafficFraction = if (unlimited) 1f else remainingBytes.toFloat() / model.totalBytes.toFloat()

    val expired = model.expired
    val daysLeft: Long? = model.expireMillis?.let { ((it - now) + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY }
    val periodDays: Int? = daysLeft?.let { left ->
        remember(groupId, left) { resolveSubscriptionPeriodDays(groupId, left.toInt()) }
    }
    val timeFraction = when {
        expired -> 0f
        daysLeft != null && periodDays != null && periodDays > 0 ->
            (daysLeft.toFloat() / periodDays.toFloat()).coerceIn(0f, 1f)
        else -> 1f
    }

    val exhausted = !unlimited && remainingBytes == 0L
    val healthy = !expired && !exhausted
    val trafficColor = if (!unlimited && trafficFraction <= LOW_FRACTION) ColorStatusBad else ColorTraffic
    val timeColor = if (expired || (daysLeft != null && daysLeft <= LOW_DAYS)) ColorStatusBad else ColorTime

    val selectedDescription = if (isSelected) stringResource(R.string.acc_selected_server) else null

    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics {
                if (selectedDescription != null) stateDescription = selectedDescription
            },
        shape = RoundedCornerShape(28.dp),
        color = scheme.surface,
        shadowElevation = 4.dp,
        border = if (isSelected) BorderStroke(2.dp, scheme.primary) else null,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Header: status dot, name, actions
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (healthy) ColorStatusOk else ColorStatusBad)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = row.remarks,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                RoundActionButton(
                    iconRes = R.drawable.ic_share_24dp,
                    contentDescription = stringResource(R.string.title_configuration_share),
                    onClick = onShare,
                )
                Spacer(Modifier.width(8.dp))
                RoundActionButton(
                    iconRes = R.drawable.ic_edit_24dp,
                    contentDescription = stringResource(R.string.acc_edit),
                    onClick = onEdit,
                )
                Spacer(Modifier.width(8.dp))
                RoundActionButton(
                    iconRes = R.drawable.ic_delete_24dp,
                    contentDescription = stringResource(R.string.acc_delete),
                    onClick = onDelete,
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(12.dp))

            // Body: remaining figures | totals with bars
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Remaining traffic
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
                    // Remaining days
                    when {
                        expired -> StatLine(
                            iconRes = R.drawable.ic_calendar_24dp,
                            number = stringResource(R.string.sub_card_expired),
                            suffix = "",
                            color = ColorStatusBad,
                        )
                        daysLeft != null -> StatLine(
                            iconRes = R.drawable.ic_calendar_24dp,
                            number = daysLeft.toString(),
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

                Box(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(scheme.outlineVariant.copy(alpha = 0.8f))
                )

                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val totalLabel = if (unlimited) {
                        stringResource(R.string.sub_info_unlimited)
                    } else {
                        val (num, unit) = splitTraffic(model.totalBytes)
                        "$num ${trafficUnitLabel(unit)}"
                    }
                    BarBlock(
                        iconRes = R.drawable.ic_bar_chart_24dp,
                        label = totalLabel,
                        fraction = trafficFraction,
                        fillColor = trafficColor,
                        trackColor = trafficColor.copy(alpha = 0.2f),
                    )
                    BarBlock(
                        iconRes = R.drawable.ic_calendar_24dp,
                        label = if (periodDays != null) periodLabel(periodDays) else stringResource(R.string.sub_card_no_expiry),
                        fraction = timeFraction,
                        fillColor = timeColor,
                        trackColor = scheme.onSurface.copy(alpha = 0.1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundActionButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(iconRes),
                contentDescription,
                Modifier.size(20.dp),
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
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = number,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
        if (suffix.isNotEmpty()) {
            Text(
                text = " $suffix",
                style = MaterialTheme.typography.bodyMedium,
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        // The bar always fills from the left edge, like in the design, whatever the language direction.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(trackColor)
            ) {
                if (fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                            .clip(RoundedCornerShape(7.dp))
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
