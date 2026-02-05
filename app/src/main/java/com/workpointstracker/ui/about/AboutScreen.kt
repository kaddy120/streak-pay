package com.workpointstracker.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workpointstracker.domain.usecase.Badge

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Work Points Tracker",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Version 1.0.0",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Gamify your work sessions by converting hours into points. Track your progress, build streaks, and unlock your wishlist items!",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                Divider()

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Point System",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                InfoRow("Day Job (9am-4pm)", "0.25 pts/hr")
                InfoRow("Side Work", "1.0 pts/hr")
                InfoRow("Early Morning (5-8am)", "1.5 pts/hr")
                InfoRow("First hour bonus", "+0.5 pts")

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Streak Bonuses",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                InfoRow("3-day streak", "+10%")
                InfoRow("7-day streak", "+15%")
                InfoRow("30-day streak", "+20%")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Badges Section
        Text(
            text = "Badges",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Display all badges
        Badge.entries.forEach { badge ->
            BadgeShowcaseCard(badge = badge)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun BadgeShowcaseCard(badge: Badge) {
    val gradient = getBadgeGradient(badge)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Badge icon with gradient background
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge.icon,
                    fontSize = 28.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Badge info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = badge.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Persistence indicator
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (badge.isPermanent)
                            Color(0xFF4CAF50).copy(alpha = 0.15f)
                        else
                            Color(0xFFFF9800).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (badge.isPermanent) "Permanent" else "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (badge.isPermanent)
                                Color(0xFF4CAF50)
                            else
                                Color(0xFFFF9800),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Badge gradient colors (same as HomeScreen)
private fun getBadgeGradient(badge: Badge): Brush {
    return when (badge) {
        Badge.WEEK_STREAK, Badge.MONTH_STREAK -> Brush.horizontalGradient(
            colors = listOf(Color(0xFFFF6B35), Color(0xFFD32F2F))
        )
        Badge.EARLY_BIRD -> Brush.horizontalGradient(
            colors = listOf(Color(0xFFFF9A8B), Color(0xFF4FC3F7))
        )
        Badge.NIGHT_OWL -> Brush.horizontalGradient(
            colors = listOf(Color(0xFF7C4DFF), Color(0xFF303F9F))
        )
        Badge.WEEKEND_WARRIOR -> Brush.horizontalGradient(
            colors = listOf(Color(0xFFE53935), Color(0xFF8B0000))
        )
        Badge.MARATHON_RUNNER -> Brush.horizontalGradient(
            colors = listOf(Color(0xFF00897B), Color(0xFF00E5FF))
        )
        Badge.CENTURION -> Brush.horizontalGradient(
            colors = listOf(Color(0xFFFFD700), Color(0xFFFF8F00))
        )
        Badge.POINT_MASTER_100 -> Brush.horizontalGradient(
            colors = listOf(Color(0xFFFFEB3B), Color(0xFFFFD700))
        )
        Badge.POINT_MASTER_500 -> Brush.horizontalGradient(
            colors = listOf(Color(0xFFFFD700), Color(0xFFFF9800))
        )
        Badge.POINT_MASTER_1000 -> Brush.horizontalGradient(
            colors = listOf(Color(0xFFFFE082), Color(0xFFFF6F00))
        )
        Badge.CONSISTENT -> Brush.horizontalGradient(
            colors = listOf(Color(0xFF66BB6A), Color(0xFF00C853))
        )
        Badge.DIVERSIFIED -> Brush.horizontalGradient(
            colors = listOf(Color(0xFFFF6B6B), Color(0xFFFFE66D), Color(0xFF4ECDC4))
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
