package com.workpointstracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workpointstracker.data.model.Session
import com.workpointstracker.data.model.SessionType
import com.workpointstracker.domain.usecase.Badge
import com.workpointstracker.ui.theme.DayJobColor
import com.workpointstracker.ui.theme.EarlyMorningColor
import com.workpointstracker.ui.theme.SideWorkColor
import com.workpointstracker.util.FormatUtils

@Composable
fun HomeScreen(
    onSessionClick: (Long) -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val timerElapsed by viewModel.timerElapsedSeconds.collectAsState()
    val timerRunning by viewModel.timerRunning.collectAsState()
    val timerPaused by viewModel.timerPaused.collectAsState()
    val canStopTimer by viewModel.canStopTimer.collectAsState()
    val recentSessions by viewModel.recentSessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val encouragementData by viewModel.encouragementData.collectAsState()

    // Refresh data when screen is resumed (including when navigating back)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.syncTimerStartTimeFromDatabase()
                viewModel.refreshEncouragementData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with greeting and points
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hi, ${uiState.userName}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = FormatUtils.formatPoints(uiState.totalPoints),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Encouragement card
        EncouragementCard(
            encouragementData = encouragementData,
            totalPoints = uiState.totalPoints
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Timer card
        val isSessionActive = timerRunning || timerPaused
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSessionActive && currentSessionId != null) {
                        Modifier.clickable { onSessionClick(currentSessionId!!) }
                    } else {
                        Modifier
                    }
                ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Current Session",
                    style = MaterialTheme.typography.titleMedium
                )

                if (isSessionActive && currentSessionId != null) {
                    Text(
                        text = "Tap to edit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = FormatUtils.formatElapsedTime(timerElapsed),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        !timerRunning && !timerPaused -> {
                            Button(
                                onClick = { viewModel.startTimer() },
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Start",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        timerRunning -> {
                            Button(
                                onClick = { viewModel.pauseTimer() },
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9800),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    Icons.Default.Pause,
                                    contentDescription = "Pause",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(20.dp))
                            Button(
                                onClick = { viewModel.stopTimer() },
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                enabled = canStopTimer,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE53935),
                                    contentColor = Color.White,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        timerPaused -> {
                            Button(
                                onClick = { viewModel.resumeTimer() },
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Resume",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(20.dp))
                            Button(
                                onClick = { viewModel.stopTimer() },
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                enabled = canStopTimer,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE53935),
                                    contentColor = Color.White,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recent sessions
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Recent Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn {
                    items(recentSessions) { session ->
                        SessionItem(
                            session = session,
                            onClick = { onSessionClick(session.id) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EncouragementCard(
    encouragementData: EncouragementData,
    totalPoints: Double
) {
    val streakInfo = encouragementData.streakInfo

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (streakInfo?.streakAtRisk == true)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Streak and Grace Period Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Streak info with grace period
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Streak count first
                    Text(
                        text = "${streakInfo?.currentStreak ?: 0} day streak",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // Spacer takes up remaining space
                    Spacer(modifier = Modifier.weight(1f))
                    // Grace period at the end
                    streakInfo?.gracePeriod?.let { gracePeriod ->
                        if (gracePeriod.hoursRemaining > 0) {
                            Text(
                                text = "Grace: ${gracePeriod.hoursRemaining}h ${gracePeriod.minutesRemaining}m",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Urgent warning if streak at risk
                if (streakInfo?.streakAtRisk == true) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "At risk!",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Motivational message
            if (encouragementData.motivationalMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = encouragementData.motivationalMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Badges section
            if (encouragementData.badges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Badges",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    encouragementData.badges.forEach { badge ->
                        BadgeChip(
                            badge = badge,
                            isHighlighted = encouragementData.highlightedBadges.contains(badge)
                        )
                    }
                }
            }

            // Progress toward next wish item
            encouragementData.nextWishItem?.let { wishItem ->
                val pointsNeeded = encouragementData.pointsToNextWishItem ?: 0.0
                if (pointsNeeded > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\uD83C\uDF81",  // 🎁
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = wishItem.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val progress = (totalPoints / FormatUtils.priceToPoints(wishItem.price))
                                .coerceIn(0.0, 1.0)
                                .toFloat()
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${FormatUtils.formatPoints(pointsNeeded)} pts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// Badge gradient color definitions
private fun getBadgeGradient(badge: Badge): Brush {
    return when (badge) {
        // Fire/Streak badges - Orange to Deep Red
        Badge.WEEK_STREAK, Badge.MONTH_STREAK -> Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFF6B35),  // Vibrant Orange
                Color(0xFFD32F2F)   // Deep Red
            )
        )
        // Early Bird - Soft Pink to Sky Blue (Dawn)
        Badge.EARLY_BIRD -> Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFF9A8B),  // Soft Coral Pink
                Color(0xFF4FC3F7)   // Sky Blue
            )
        )
        // Night Owl - Deep Purple to Indigo
        Badge.NIGHT_OWL -> Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF7C4DFF),  // Deep Purple
                Color(0xFF303F9F)   // Indigo
            )
        )
        // Weekend Warrior - Crimson to Dark Red
        Badge.WEEKEND_WARRIOR -> Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFE53935),  // Crimson
                Color(0xFF8B0000)   // Dark Red
            )
        )
        // Marathon Runner - Teal to Cyan
        Badge.MARATHON_RUNNER -> Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF00897B),  // Teal
                Color(0xFF00E5FF)   // Cyan
            )
        )
        // Centurion - Gold to Amber
        Badge.CENTURION -> Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFFD700),  // Gold
                Color(0xFFFF8F00)   // Amber
            )
        )
        // Point Collector - Yellow to Gold
        Badge.POINT_MASTER_100 -> Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFFEB3B),  // Yellow
                Color(0xFFFFD700)   // Gold
            )
        )
        // Point Expert - Gold to Orange (brighter)
        Badge.POINT_MASTER_500 -> Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFFD700),  // Gold
                Color(0xFFFF9800)   // Orange
            )
        )
        // Point Master - Radiant Gold
        Badge.POINT_MASTER_1000 -> Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFFE082),  // Light Gold
                Color(0xFFFF6F00)   // Deep Orange
            )
        )
        // Consistent - Green to Emerald
        Badge.CONSISTENT -> Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF66BB6A),  // Green
                Color(0xFF00C853)   // Emerald
            )
        )
        // Diversified - Rainbow gradient
        Badge.DIVERSIFIED -> Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFF6B6B),  // Red
                Color(0xFFFFE66D),  // Yellow
                Color(0xFF4ECDC4)   // Teal
            )
        )
    }
}

@Composable
fun BadgeChip(
    badge: Badge,
    isHighlighted: Boolean
) {
    val shape = RoundedCornerShape(16.dp)
    val gradient = getBadgeGradient(badge)

    Row(
        modifier = Modifier
            .then(
                if (isHighlighted) {
                    Modifier.shadow(
                        elevation = 6.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.3f),
                        spotColor = Color.Black.copy(alpha = 0.3f)
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(gradient)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = badge.icon,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = badge.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
fun SessionItem(
    session: Session,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when (session.type) {
                SessionType.DAY_JOB -> DayJobColor.copy(alpha = 0.1f)
                SessionType.SIDE_WORK -> SideWorkColor.copy(alpha = 0.1f)
                SessionType.EARLY_MORNING -> EarlyMorningColor.copy(alpha = 0.1f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = FormatUtils.formatDate(session.startTime),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${FormatUtils.formatTime(session.startTime)} - ${session.endTime?.let { FormatUtils.formatTime(it) } ?: ""}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = FormatUtils.formatDuration(session.durationMinutes),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "+${FormatUtils.formatPoints(session.pointsEarned)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (session.type) {
                        SessionType.DAY_JOB -> DayJobColor
                        SessionType.SIDE_WORK -> SideWorkColor
                        SessionType.EARLY_MORNING -> EarlyMorningColor
                    }
                )
                Text(
                    text = session.type.name.replace("_", " "),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
