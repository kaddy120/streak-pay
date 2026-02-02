package com.workpointstracker.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workpointstracker.data.model.Session
import com.workpointstracker.data.model.SessionType
import com.workpointstracker.ui.theme.DayJobColor
import com.workpointstracker.ui.theme.EarlyMorningColor
import com.workpointstracker.ui.theme.SideWorkColor
import com.workpointstracker.util.FormatUtils

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val timerElapsed by viewModel.timerElapsedSeconds.collectAsState()
    val timerRunning by viewModel.timerRunning.collectAsState()
    val timerPaused by viewModel.timerPaused.collectAsState()
    val canStopTimer by viewModel.canStopTimer.collectAsState()
    val recentSessions by viewModel.recentSessions.collectAsState()

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
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = FormatUtils.formatPoints(uiState.totalPoints),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Timer card
        Card(
            modifier = Modifier.fillMaxWidth(),
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

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = FormatUtils.formatElapsedTime(timerElapsed),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp
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
                                modifier = Modifier.size(width = 120.dp, height = 56.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start")
                            }
                        }
                        timerRunning -> {
                            Button(
                                onClick = { viewModel.pauseTimer() },
                                modifier = Modifier.size(width = 120.dp, height = 56.dp)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pause")
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Button(
                                onClick = { viewModel.stopTimer() },
                                modifier = Modifier.size(width = 120.dp, height = 56.dp),
                                enabled = canStopTimer,
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stop")
                            }
                        }
                        timerPaused -> {
                            Button(
                                onClick = { viewModel.resumeTimer() },
                                modifier = Modifier.size(width = 120.dp, height = 56.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Resume")
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Button(
                                onClick = { viewModel.stopTimer() },
                                modifier = Modifier.size(width = 120.dp, height = 56.dp),
                                enabled = canStopTimer
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Stop")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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
                        SessionItem(session)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SessionItem(session: Session) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
