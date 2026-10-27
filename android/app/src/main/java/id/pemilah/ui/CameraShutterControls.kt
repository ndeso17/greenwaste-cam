package id.pemilah.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.pemilah.R

/** Pure presentation: the owner still decides when operation state actually changes. */
@Composable
internal fun CameraShutterControls(
    state: OperationUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = state.isRunning
    val enabled = if (running) StartGuardProjection.canStop(state) else StartGuardProjection.canStart(state)
    val sorting = state.stage == OperationStage.SORTING
    val label = stringResource(
        if (running) {
            if (sorting) R.string.operation_stop_sorting else R.string.operation_stop_monitoring
        } else {
            if (sorting) R.string.operation_start_sorting else R.string.operation_start_monitoring
        },
    )
    val transition = updateTransition(running, label = "shutterState")
    val innerSize by transition.animateDp({ tween(200) }, label = "shutterShapeSize") {
        if (it) 32.dp else 60.dp
    }
    val innerRadius by transition.animateDp({ tween(200) }, label = "shutterCornerRadius") {
        if (it) 8.dp else 30.dp
    }
    val innerColor by transition.animateColor({ tween(200) }, label = "shutterColor") {
        if (it) Color(0xFFFF5252) else Color(0xFF58DC94)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        if (pressed) 0.92f else 1f, tween(120), label = "shutterPress",
    )
    Column(
        modifier = modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.76f))))
            .padding(top = 10.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Equal side slots keep the shutter centered when the secondary action appears.
            Spacer(Modifier.size(48.dp))
            Box(
                modifier = Modifier.size(80.dp)
                    .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    .border(3.dp, Color.White.copy(alpha = if (enabled) 1f else 0.6f), CircleShape)
                    .semantics { contentDescription = label }
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        interactionSource = interaction,
                        indication = null,
                    ) { if (running) onStop() else onStart() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(innerSize)
                        .background(
                            if (enabled) innerColor else Color(0xFF757575),
                            RoundedCornerShape(innerRadius),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!running) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null,
                            tint = Color.Black, modifier = Modifier.size(32.dp))
                    }
                }
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (running) {
                    val stopLabel = stringResource(R.string.action_stop_now)
                    val stopInteraction = remember { MutableInteractionSource() }
                    val stopPressed by stopInteraction.collectIsPressedAsState()
                    val stopScale = animateFloatAsState(
                        if (stopPressed) 0.9f else 1f, tween(120), label = "secondaryStopPress",
                    )
                    Box(
                        Modifier.fillMaxSize()
                            .graphicsLayer { scaleX = stopScale.value; scaleY = stopScale.value }
                            .background(Color.Black.copy(alpha = 0.64f), CircleShape)
                            .border(1.dp, Color.White, CircleShape)
                            .semantics { contentDescription = stopLabel }
                            .clickable(
                                role = Role.Button, interactionSource = stopInteraction,
                                indication = null, onClick = onStop,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Stop, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
        Text(label, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
        // Keep this slot in both states so the shutter never jumps during a state transition.
        Text(stringResource(R.string.operation_stop_hint), color = Color.White,
            fontSize = 10.sp, lineHeight = 12.sp)
    }
}
