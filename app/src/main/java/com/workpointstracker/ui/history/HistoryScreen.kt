package com.workpointstracker.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workpointstracker.ui.theme.DayJobColor
import com.workpointstracker.ui.theme.SideWorkColor
import com.workpointstracker.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel()) {
    val totalPoints by viewModel.totalPoints.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val dailyGoal by viewModel.dailyGoal.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val statsData by viewModel.statsData.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Activity Record",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = FormatUtils.formatPoints(totalPoints ?: 0.0),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Period selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TimePeriod.values().forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { viewModel.selectPeriod(period) },
                    label = { Text(period.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Stats card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = when (selectedPeriod) {
                        TimePeriod.DAY -> "Today"
                        TimePeriod.WEEK -> "Past 7 days"
                        TimePeriod.MONTH -> "Past 30 days"
                        TimePeriod.YEAR -> "Past year"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                statsData?.let { data ->
                    // Day job progress
                    val dayJobGoal = dailyGoal?.dayJobHours ?: 7.5
                    ProgressRow(
                        label = "Day Job",
                        current = data.dayJobHours,
                        goal = if (selectedPeriod == TimePeriod.DAY) dayJobGoal else dayJobGoal * 7,
                        color = DayJobColor
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Side work progress
                    val sideWorkGoal = dailyGoal?.sideWorkHours ?: 4.0
                    ProgressRow(
                        label = "Side Work",
                        current = data.sideWorkHours,
                        goal = if (selectedPeriod == TimePeriod.DAY) sideWorkGoal else sideWorkGoal * 7,
                        color = SideWorkColor
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Total points earned
                    Text(
                        text = "Points Earned: ${FormatUtils.formatPoints(data.totalPoints)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Streak card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Current Streak",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${appSettings?.currentStreak ?: 0} days",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                val streakBonus = when {
                    (appSettings?.currentStreak ?: 0) >= 30 -> 20
                    (appSettings?.currentStreak ?: 0) >= 7 -> 15
                    (appSettings?.currentStreak ?: 0) >= 3 -> 10
                    else -> 0
                }
                if (streakBonus > 0) {
                    Text(
                        text = "+$streakBonus% bonus active",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun ProgressRow(
    label: String,
    current: Double,
    goal: Double,
    color: androidx.compose.ui.graphics.Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${String.format("%.1f", current)}h / ${String.format("%.1f", goal)}h",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = ((current / goal).coerceIn(0.0, 1.0)).toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}
