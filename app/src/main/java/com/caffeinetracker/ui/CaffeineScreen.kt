package com.caffeinetracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caffeinetracker.R
import com.caffeinetracker.physics.LiquidPhysics
import com.caffeinetracker.pk.CaffeineKinetics
import com.caffeinetracker.pk.Dose
import kotlinx.coroutines.isActive
import androidx.compose.runtime.withFrameNanos

private val Ink = Color(0xFFF6F1E7)
private val ButtonFill = Color(0xE6140E0B)

@Composable
fun CaffeineScreen(viewModel: CaffeineViewModel = viewModel()) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val doses by viewModel.doses.collectAsStateWithLifecycle()
        val loaded = doses
        if (loaded == null) {
            Box(Modifier.fillMaxSize().background(Color.Black))
            return@MaterialTheme
        }
        CaffeineGlass(
            doses = loaded,
            onAddDose = viewModel::addDose,
        )
    }
}

@Composable
private fun CaffeineGlass(
    doses: List<Dose>,
    onAddDose: (Int) -> Unit,
) {
    val sensors = rememberTiltSensors()
    val physics = remember { LiquidPhysics() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var phi by remember { mutableFloatStateOf(0f) }
    var wave by remember { mutableFloatStateOf(0f) }
    var wavePhase by remember { mutableFloatStateOf(0f) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ticker by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val pourLag = remember { Animatable(0f) }
    var seenIds by remember { mutableStateOf<Set<String>?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val ids = remember(doses) { doses.map { it.id }.toSet() }
    val previousIds = seenIds
    val pendingPourMg = if (previousIds != null) {
        doses.filter { it.id !in previousIds }.sumOf { it.amountMg }.toFloat()
    } else {
        0f
    }

    LaunchedEffect(ids) {
        val previous = seenIds
        if (previous == null) {
            seenIds = ids
            return@LaunchedEffect
        }
        val added = doses.filter { it.id !in previous }.sumOf { it.amountMg }
        seenIds = ids
        if (added > 0) {
            pourLag.snapTo(pourLag.value + added.toFloat())
            pourLag.animateTo(0f, tween(durationMillis = 1000, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastFrame = 0L
            var lastTick = System.currentTimeMillis()
            while (isActive) {
                withFrameNanos { frameTime ->
                    val now = System.currentTimeMillis()
                    clock = now
                    if (now - lastTick >= 1_000L) {
                        ticker = now
                        lastTick = now
                    }
                    val dt = if (lastFrame == 0L) {
                        0.016f
                    } else {
                        ((frameTime - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    }
                    lastFrame = frameTime
                    val sample = sensors.sample
                    physics.step(
                        dt = dt,
                        targetPhi = LiquidPhysics.restAngle(sample.gx, sample.gy),
                        gyroZ = sample.gyroZ,
                    )
                    phi = physics.phi
                    wave = physics.wave
                    wavePhase = physics.wavePhase
                }
            }
        }
    }

    val trueRemaining = CaffeineKinetics.remainingMg(doses, clock)
    val shownRemaining = (trueRemaining - pourLag.value - pendingPourMg).coerceAtLeast(0.0)
    val fraction = (shownRemaining / CaffeineKinetics.FULL_SCALE_MG).coerceIn(0.0, 1.0).toFloat()
    val todayMg = CaffeineKinetics.intakeSinceLocalMidnight(doses, clock)
    val countdown = countdownText(
        millis = CaffeineKinetics.millisUntilCleared(CaffeineKinetics.remainingMg(doses, ticker)),
        hasAnyDose = doses.isNotEmpty(),
    )

    Box(Modifier.fillMaxSize()) {
        CoffeeLiquid(
            fraction = fraction,
            phi = phi,
            wave = wave,
            wavePhase = wavePhase,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = countdown,
                color = Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(20.dp))
            DoseButton(todayMg = todayMg, onClick = { showDialog = true })
        }
    }

    if (showDialog) {
        DoseDialog(
            onDismiss = { showDialog = false },
            onConfirm = { mg ->
                showDialog = false
                onAddDose(mg)
            },
        )
    }
}

@Composable
private fun DoseButton(todayMg: Int, onClick: () -> Unit) {
    val label = todayMg.toString()
    val description = stringResource(R.string.today_intake_cd, todayMg)
    val fontSize = when {
        label.length >= 5 -> 28.sp
        label.length == 4 -> 34.sp
        else -> 42.sp
    }
    Box(
        modifier = Modifier
            .size(148.dp)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .background(ButtonFill, CircleShape)
            .border(1.5.dp, Ink, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Ink,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun DoseDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val milligrams = text.toIntOrNull()?.takeIf { it in 1..9_999 }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_dose_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { updated ->
                    if (updated.length <= 4 && updated.all(Char::isDigit)) text = updated
                },
                label = { Text(stringResource(R.string.milligrams)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { milligrams?.let(onConfirm) },
                ),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { milligrams?.let(onConfirm) },
                enabled = milligrams != null,
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun countdownText(millis: Long, hasAnyDose: Boolean): String {
    if (millis <= 0L) {
        return stringResource(if (hasAnyDose) R.string.caffeine_cleared else R.string.no_caffeine)
    }
    val totalSeconds = ((millis + 999L) / 1000L).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0L -> stringResource(R.string.countdown_hms, hours, minutes, seconds)
        minutes > 0L -> stringResource(R.string.countdown_ms, minutes, seconds)
        else -> stringResource(R.string.countdown_s, seconds)
    }
}
