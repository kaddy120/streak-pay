package com.workpointstracker.ui.session

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.workpointstracker.WorkPointsApplication
import com.workpointstracker.data.model.Session
import com.workpointstracker.data.model.SessionType
import com.workpointstracker.data.remote.ApiService
import com.workpointstracker.data.remote.SessionResponse
import com.workpointstracker.data.remote.UpdateSessionRequest
import com.workpointstracker.shared.PointsCalculator
import com.workpointstracker.ui.theme.DayJobColor
import com.workpointstracker.ui.theme.EarlyMorningColor
import com.workpointstracker.ui.theme.SideWorkColor
import com.workpointstracker.util.FormatUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ViewModel
class SessionDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService: ApiService = (application as WorkPointsApplication).apiService
    private val pointsCalculator = PointsCalculator()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    private val _editedStartTime = MutableStateFlow<LocalDateTime?>(null)
    val editedStartTime: StateFlow<LocalDateTime?> = _editedStartTime

    private val _editedEndTime = MutableStateFlow<LocalDateTime?>(null)
    val editedEndTime: StateFlow<LocalDateTime?> = _editedEndTime

    private val _previewPoints = MutableStateFlow<Double?>(null)
    val previewPoints: StateFlow<Double?> = _previewPoints

    private val _previewSessionType = MutableStateFlow<SessionType?>(null)
    val previewSessionType: StateFlow<SessionType?> = _previewSessionType

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess

    private val _resumeReady = MutableStateFlow(false)
    val resumeReady: StateFlow<Boolean> = _resumeReady

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadSession(sessionId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val resp = apiService.getSession(sessionId)
                _session.value = resp.toLocalSession()
                _editedStartTime.value = resp.startTime
                _editedEndTime.value = resp.endTime
                updatePreview()
            } catch (_: Exception) {
                _session.value = null
            }
            _isLoading.value = false
        }
    }

    private fun SessionResponse.toLocalSession() = Session(
        id = id,
        startTime = startTime,
        endTime = endTime,
        durationMinutes = durationMinutes,
        pointsEarned = pointsEarned,
        type = SessionType.valueOf(type.name),
        isPaused = isPaused,
        pausedAt = pausedAt,
        totalPausedSeconds = totalPausedSeconds
    )

    fun updateStartTime(newTime: LocalDateTime) {
        _editedStartTime.value = newTime
        validateAndUpdatePreview()
    }

    fun updateEndTime(newTime: LocalDateTime) {
        _editedEndTime.value = newTime
        validateAndUpdatePreview()
    }

    private fun validateAndUpdatePreview() {
        val startTime = _editedStartTime.value ?: return
        val endTime = _editedEndTime.value
        val originalSession = _session.value ?: return
        val today = LocalDate.now()

        if (startTime.toLocalDate() != today) {
            _validationError.value = "Can only edit sessions from today"
            _previewPoints.value = null
            return
        }

        if (endTime != null && !endTime.isAfter(startTime)) {
            _validationError.value = "End time must be after start time"
            _previewPoints.value = null
            return
        }

        val effectiveEndTime = endTime ?: LocalDateTime.now()
        val durationMinutes = (ChronoUnit.SECONDS.between(startTime, effectiveEndTime) - originalSession.totalPausedSeconds) / 60
        if (durationMinutes < 15) {
            _validationError.value = "Duration must be at least 15 minutes"
            _previewPoints.value = null
            return
        }

        _validationError.value = null
        updatePreview()
    }

    private fun updatePreview() {
        viewModelScope.launch {
            val startTime = _editedStartTime.value ?: return@launch
            val endTime = _editedEndTime.value
            val originalSession = _session.value ?: return@launch

            val effectiveEndTime = endTime ?: LocalDateTime.now()
            val durationMinutes = (ChronoUnit.SECONDS.between(startTime, effectiveEndTime) - originalSession.totalPausedSeconds) / 60

            if (durationMinutes < 15) {
                _previewPoints.value = null
                return@launch
            }

            // Use shared PointsCalculator for client-side preview
            val sharedType = pointsCalculator.determineSessionType(startTime)
            val sessionType = SessionType.valueOf(sharedType.name)

            // Get streak from API for accurate calculation
            val currentStreak = try {
                apiService.getStreakInfo().currentStreak
            } catch (_: Exception) { 0 }

            val result = pointsCalculator.calculatePoints(
                startTime = startTime,
                durationMinutes = durationMinutes,
                streakDays = currentStreak,
                isFirstSessionOfDay = false // approximation for preview
            )

            _previewPoints.value = result.points
            _previewSessionType.value = SessionType.valueOf(result.sessionType.name)
        }
    }

    fun saveSession() {
        viewModelScope.launch {
            val originalSession = _session.value ?: return@launch
            val startTime = _editedStartTime.value ?: return@launch
            val endTime = _editedEndTime.value ?: return@launch

            if (_validationError.value != null) return@launch

            val durationMinutes = (ChronoUnit.SECONDS.between(startTime, endTime) - originalSession.totalPausedSeconds) / 60

            try {
                apiService.updateSession(originalSession.id, UpdateSessionRequest(
                    startTime = startTime,
                    endTime = endTime,
                    durationMinutes = durationMinutes
                ))
                _saveSuccess.value = true
            } catch (e: Exception) {
                _validationError.value = "Failed to save: ${e.message}"
            }
        }
    }

    fun stopRunningSession() {
        viewModelScope.launch {
            val originalSession = _session.value ?: return@launch
            val startTime = _editedStartTime.value ?: return@launch
            val endTime = LocalDateTime.now()

            if (_validationError.value != null && _validationError.value != "Can only edit sessions from today") {
                return@launch
            }

            val durationMinutes = (ChronoUnit.SECONDS.between(startTime, endTime) - originalSession.totalPausedSeconds) / 60
            if (durationMinutes < 15) {
                _validationError.value = "Duration must be at least 15 minutes"
                return@launch
            }

            try {
                apiService.updateSession(originalSession.id, UpdateSessionRequest(
                    startTime = startTime,
                    endTime = endTime,
                    isPaused = false
                ))
                _saveSuccess.value = true
            } catch (e: Exception) {
                _validationError.value = "Failed to stop: ${e.message}"
            }
        }
    }

    fun deleteSession() {
        viewModelScope.launch {
            val session = _session.value ?: return@launch
            try {
                apiService.deleteSession(session.id)
                _deleteSuccess.value = true
            } catch (e: Exception) {
                _validationError.value = "Failed to delete: ${e.message}"
            }
        }
    }

    fun saveStartTimeAndResume() {
        viewModelScope.launch {
            val originalSession = _session.value ?: return@launch
            val editedStart = _editedStartTime.value ?: return@launch

            try {
                apiService.updateSession(originalSession.id, UpdateSessionRequest(
                    startTime = if (editedStart != originalSession.startTime) editedStart else null,
                    isPaused = false
                ))
            } catch (e: Exception) {
                _validationError.value = "Failed to save: ${e.message}"
                return@launch
            }
            _resumeReady.value = true
        }
    }

    fun isSessionFromToday(): Boolean {
        val session = _session.value ?: return false
        return session.startTime.toLocalDate() == LocalDate.now()
    }

    fun isRunningSession(): Boolean {
        return _session.value?.endTime == null
    }

    fun getPreviewDuration(): Long {
        val startTime = _editedStartTime.value ?: return 0
        val endTime = _editedEndTime.value ?: LocalDateTime.now()
        val session = _session.value ?: return 0
        return (ChronoUnit.SECONDS.between(startTime, endTime) - session.totalPausedSeconds) / 60
    }
}

// UI
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: Long,
    onBackClick: () -> Unit,
    onResumeSession: () -> Unit,
    viewModel: SessionDetailViewModel = viewModel()
) {
    val session by viewModel.session.collectAsState()
    val editedStartTime by viewModel.editedStartTime.collectAsState()
    val editedEndTime by viewModel.editedEndTime.collectAsState()
    val previewPoints by viewModel.previewPoints.collectAsState()
    val previewSessionType by viewModel.previewSessionType.collectAsState()
    val validationError by viewModel.validationError.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val deleteSuccess by viewModel.deleteSuccess.collectAsState()
    val resumeReady by viewModel.resumeReady.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    LaunchedEffect(saveSuccess, deleteSuccess) {
        if (saveSuccess || deleteSuccess) {
            onBackClick()
        }
    }

    LaunchedEffect(resumeReady) {
        if (resumeReady) {
            onResumeSession()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (session != null && viewModel.isSessionFromToday() && !viewModel.isRunningSession()) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (session == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Session not found")
            }
        } else if (!viewModel.isSessionFromToday()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Cannot edit past sessions",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Only sessions from today can be edited",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val currentSession = session!!
            val isRunning = viewModel.isRunningSession()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                SessionTypeBadge(
                    sessionType = previewSessionType ?: currentSession.type,
                    originalType = currentSession.type
                )

                Spacer(modifier = Modifier.height(24.dp))

                TimeEditorCard(
                    label = "Start Time",
                    dateTime = editedStartTime ?: currentSession.startTime,
                    onDateClick = { showStartDatePicker = true },
                    onTimeClick = { showStartTimePicker = true }
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isRunning) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "End Time",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Session in progress",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    TimeEditorCard(
                        label = "End Time",
                        dateTime = editedEndTime ?: currentSession.endTime!!,
                        onDateClick = { showEndDatePicker = true },
                        onTimeClick = { showEndTimePicker = true }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Duration", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = FormatUtils.formatDuration(viewModel.getPreviewDuration()),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                PointsPreviewCard(
                    originalPoints = currentSession.pointsEarned,
                    previewPoints = previewPoints,
                    sessionType = previewSessionType ?: currentSession.type
                )

                validationError?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (isRunning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.saveStartTimeAndResume() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Resume")
                        }
                        Button(
                            onClick = { viewModel.stopRunningSession() },
                            modifier = Modifier.weight(1f),
                            enabled = validationError == null
                        ) {
                            Text("Stop")
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.saveSession() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = validationError == null && previewPoints != null
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Session") },
            text = { Text("Are you sure you want to delete this session? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showStartDatePicker) {
        val currentDateTime = editedStartTime ?: session?.startTime ?: LocalDateTime.now()
        DatePickerDialog(
            initialDate = currentDateTime.toLocalDate(),
            onDateSelected = { date ->
                viewModel.updateStartTime(LocalDateTime.of(date, currentDateTime.toLocalTime()))
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }

    if (showStartTimePicker) {
        val currentDateTime = editedStartTime ?: session?.startTime ?: LocalDateTime.now()
        TimePickerDialog(
            initialTime = currentDateTime.toLocalTime(),
            onTimeSelected = { time ->
                viewModel.updateStartTime(LocalDateTime.of(currentDateTime.toLocalDate(), time))
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }

    if (showEndDatePicker) {
        val currentDateTime = editedEndTime ?: session?.endTime ?: LocalDateTime.now()
        DatePickerDialog(
            initialDate = currentDateTime.toLocalDate(),
            onDateSelected = { date ->
                viewModel.updateEndTime(LocalDateTime.of(date, currentDateTime.toLocalTime()))
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }

    if (showEndTimePicker) {
        val currentDateTime = editedEndTime ?: session?.endTime ?: LocalDateTime.now()
        TimePickerDialog(
            initialTime = currentDateTime.toLocalTime(),
            onTimeSelected = { time ->
                viewModel.updateEndTime(LocalDateTime.of(currentDateTime.toLocalDate(), time))
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
}

@Composable
private fun SessionTypeBadge(sessionType: SessionType, originalType: SessionType) {
    val color = when (sessionType) {
        SessionType.DAY_JOB -> DayJobColor
        SessionType.SIDE_WORK -> SideWorkColor
        SessionType.EARLY_MORNING -> EarlyMorningColor
    }
    val hasChanged = sessionType != originalType

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .border(1.dp, color, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = sessionType.name.replace("_", " "),
                color = color,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        if (hasChanged) {
            Text(
                text = "(was ${originalType.name.replace("_", " ")})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimeEditorCard(
    label: String,
    dateTime: LocalDateTime,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(onClick = onDateClick, modifier = Modifier.weight(1f)) {
                    Text(dateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")))
                }
                OutlinedButton(onClick = onTimeClick, modifier = Modifier.weight(1f)) {
                    Text(dateTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                }
            }
        }
    }
}

@Composable
private fun PointsPreviewCard(
    originalPoints: Double,
    previewPoints: Double?,
    sessionType: SessionType
) {
    val color = when (sessionType) {
        SessionType.DAY_JOB -> DayJobColor
        SessionType.SIDE_WORK -> SideWorkColor
        SessionType.EARLY_MORNING -> EarlyMorningColor
    }
    val hasChanged = previewPoints != null && previewPoints != originalPoints

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Points",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasChanged) {
                    Column {
                        Text(
                            text = "Original: ${FormatUtils.formatPoints(originalPoints)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "New: ${FormatUtils.formatPoints(previewPoints!!)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                    val diff = previewPoints!! - originalPoints
                    val diffText = if (diff >= 0) "+${FormatUtils.formatPoints(diff)}" else FormatUtils.formatPoints(diff)
                    Text(
                        text = diffText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (diff >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = FormatUtils.formatPoints(previewPoints ?: originalPoints),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toEpochDay() * 24 * 60 * 60 * 1000
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                        onDateSelected(date)
                    }
                }
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time") },
        text = { TimePicker(state = timePickerState) },
        confirmButton = {
            TextButton(
                onClick = {
                    onTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
                }
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
