package com.example.shiro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shiro.ui.theme.ProjectSHIROTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProjectSHIROTheme {
                ShiroScreen()
            }
        }
    }
}

val LightBlue = Color(0xFFE0F2FE)
val ButtonColor = Color(0xFF38BDF8)
val ButtonTextColor = Color.White

enum class SquishDirection {
    LEFT, CENTER, RIGHT
}

@Composable
fun ShiroScreen() {
    var isBlinking by remember { mutableStateOf(false) }
    var squishDirection by remember { mutableStateOf(SquishDirection.CENTER) }
    var squishDurationSec by remember { mutableStateOf(0.4f) }
    val squishProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun triggerSquish(direction: SquishDirection = SquishDirection.CENTER) {
        squishDirection = direction
        scope.launch {
            val totalMs = (squishDurationSec * 1000f).roundToInt().coerceAtLeast(60)
            val compressMs = (totalMs * 0.35f).roundToInt().coerceAtLeast(30)
            val reboundMs = (totalMs - compressMs).coerceAtLeast(30)

            squishProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = compressMs, easing = FastOutSlowInEasing)
            )
            squishProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = reboundMs, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBlue)
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ShiroCharacter(
                isBlinking = isBlinking,
                squishProgress = squishProgress.value,
                squishDirection = squishDirection,
                onTap = {
                    scope.launch {
                        isBlinking = true
                        delay(250)
                        isBlinking = false
                    }
                    triggerSquish(SquishDirection.CENTER)
                },
                eyeScale = 0.6f,
                eyeOffsetX = 5.dp,
                eyeOffsetY = 0.dp,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(1536f / 1024f)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(0.92f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Squish duration",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF0369A1)
                )
                Text(
                    text = String.format(Locale.US, "%.2fs", squishDurationSec),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0369A1)
                )
            }

            Slider(
                value = squishDurationSec,
                onValueChange = { squishDurationSec = it },
                valueRange = 0.1f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = ButtonColor,
                    activeTrackColor = ButtonColor
                ),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .height(28.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(0.95f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { triggerSquish(SquishDirection.LEFT) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonColor,
                        contentColor = ButtonTextColor
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "◀ Left",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { triggerSquish(SquishDirection.CENTER) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonColor,
                        contentColor = ButtonTextColor
                    ),
                    modifier = Modifier
                        .weight(1.1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "▼ Center",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { triggerSquish(SquishDirection.RIGHT) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonColor,
                        contentColor = ButtonTextColor
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Right ▶",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        isBlinking = true
                        delay(300)
                        isBlinking = false
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonColor,
                    contentColor = ButtonTextColor
                ),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(52.dp)
            ) {
                Text(
                    text = "Blink",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ShiroCharacter(
    isBlinking: Boolean,
    squishProgress: Float,
    squishDirection: SquishDirection,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    eyeScale: Float = 0.6f,
    eyeOffsetX: Dp = 5.dp,
    eyeOffsetY: Dp = 0.dp
) {
    val interactionSource = remember { MutableInteractionSource() }

    val bodyScaleY = 1f - squishProgress * 0.22f
    val bodyScaleX = 1f + squishProgress * 0.16f

    val skewAmount = when (squishDirection) {
        SquishDirection.LEFT -> 0.32f * squishProgress
        SquishDirection.RIGHT -> -0.32f * squishProgress
        SquishDirection.CENTER -> 0f
    }

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTap
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val pivotX = size.width * 0.5f
                    val pivotY = size.height * 0.922f

                    drawIntoCanvas { canvas ->
                        canvas.save()
                        canvas.nativeCanvas.translate(pivotX, pivotY)
                        canvas.nativeCanvas.skew(skewAmount, 0f)
                        canvas.nativeCanvas.scale(bodyScaleX, bodyScaleY)
                        canvas.nativeCanvas.translate(-pivotX, -pivotY)

                        drawContent()
                        canvas.restore()
                    }
                }
        ) {
            Image(
                painter = painterResource(id = R.drawable.shiro_body_neutral),
                contentDescription = "Shiro Body",
                modifier = Modifier.fillMaxSize()
            )

            Image(
                painter = painterResource(
                    id = if (isBlinking) {
                        R.drawable.shiro_eyes_closed
                    } else {
                        R.drawable.shiro_eyes_neutral
                    }
                ),
                contentDescription = if (isBlinking) "Shiro Eyes Closed" else "Shiro Eyes Neutral",
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = eyeOffsetX, y = eyeOffsetY)
                    .graphicsLayer(
                        scaleX = eyeScale,
                        scaleY = eyeScale,
                        transformOrigin = TransformOrigin(0.5f, 0.5137f)
                    )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ShiroScreenPreview() {
    ProjectSHIROTheme {
        ShiroScreen()
    }
}
