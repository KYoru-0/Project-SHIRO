package com.example.shiro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shiro.ui.theme.ProjectSHIROTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

@Composable
fun ShiroScreen() {
    var isBlinking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                onBlinkRequest = {
                    scope.launch {
                        isBlinking = true
                        delay(250)
                        isBlinking = false
                    }
                },
                eyeScale = 0.6f,
                eyeOffsetX = 5.dp,
                eyeOffsetY = 0.dp,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(1536f / 1024f)
            )
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
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.6f)
                .height(56.dp)
        ) {
            Text(
                text = "Blink",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ShiroCharacter(
    isBlinking: Boolean,
    onBlinkRequest: () -> Unit,
    modifier: Modifier = Modifier,
    eyeScale: Float = 0.6f,
    eyeOffsetX: Dp = 5.dp,
    eyeOffsetY: Dp = 0.dp
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .scale(scale.value)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                scope.launch {
                    onBlinkRequest()
                    scale.animateTo(0.93f, animationSpec = spring())
                    scale.animateTo(1.04f, animationSpec = spring())
                    scale.animateTo(1f, animationSpec = spring())
                }
            },
        contentAlignment = Alignment.Center
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
                    transformOrigin = TransformOrigin(0.5f, 0.513f)
                )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ShiroScreenPreview() {
    ProjectSHIROTheme {
        ShiroScreen()
    }
}
