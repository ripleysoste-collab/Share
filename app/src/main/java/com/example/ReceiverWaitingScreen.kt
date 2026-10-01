package com.example

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiverWaitingScreen(
  matchedSenderName: String?,
  onBack: () -> Unit
) {
  BackHandler {
    onBack()
  }

  Scaffold(
    containerColor = Color.White,
    topBar = {
      TopAppBar(
        title = {},
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("receiver_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.action_back),
              tint = Color(0xFF1E293B)
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color.White
        )
      )
    }
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .background(Color.White),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 32.dp)
      ) {
        // Animación increíble, elegante y limpia de ondas expansivas continuas
        CleanWaitingRadarAnimation(
          modifier = Modifier.size(240.dp)
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Título de espera
        Text(
          text = stringResource(R.string.receiver_waiting_title),
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center
          )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Subtítulo con el nombre del emisor conectado
        val senderLabel = matchedSenderName?.takeIf { it.isNotBlank() } ?: "Emisor"
        Text(
          text = "Conectado a $senderLabel.\nEsperando a que elija los archivos para transferir.",
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
          ),
          modifier = Modifier.testTag("receiver_waiting_info")
        )
      }
    }
  }
}

/**
 * Animación de espera increíblemente limpia y fluida:
 * 3 ondas concéntricas expansivas suaves con centro azul resplandeciente e icono de descarga.
 */
@Composable
fun CleanWaitingRadarAnimation(modifier: Modifier = Modifier) {
  val infiniteTransition = rememberInfiniteTransition(label = "waiting_animation")

  // Onda 1
  val wave1Progress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "wave1"
  )

  // Onda 2 (desfasada 800ms)
  val wave2Progress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2400, delayMillis = 800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "wave2"
  )

  // Pulso suave del núcleo central
  val coreScale by infiniteTransition.animateFloat(
    initialValue = 0.94f,
    targetValue = 1.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "core_pulse"
  )

  val primaryBlue = Color(0xFF2563EB)

  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    // Canvas que dibuja las ondas concéntricas suaves
    Canvas(modifier = Modifier.fillMaxSize()) {
      val maxRadius = size.minDimension / 2f
      val centerOffset = center

      // Dibujar onda 1
      if (wave1Progress > 0f) {
        val r1 = 36.dp.toPx() + (maxRadius - 36.dp.toPx()) * wave1Progress
        val alpha1 = (1f - wave1Progress).coerceIn(0f, 1f) * 0.45f
        drawCircle(
          color = primaryBlue.copy(alpha = alpha1),
          radius = r1,
          center = centerOffset,
          style = Stroke(width = 2.dp.toPx())
        )
      }

      // Dibujar onda 2
      if (wave2Progress > 0f) {
        val r2 = 36.dp.toPx() + (maxRadius - 36.dp.toPx()) * wave2Progress
        val alpha2 = (1f - wave2Progress).coerceIn(0f, 1f) * 0.35f
        drawCircle(
          color = primaryBlue.copy(alpha = alpha2),
          radius = r2,
          center = centerOffset,
          style = Stroke(width = 1.8.dp.toPx())
        )
      }
    }

    // Núcleo central limpio con icono de descarga flotante
    Box(
      modifier = Modifier
        .size(72.dp)
        .scale(coreScale)
        .shadow(
          elevation = 8.dp,
          shape = CircleShape,
          ambientColor = Color(0x332563EB),
          spotColor = Color(0x4D2563EB)
        )
        .clip(CircleShape)
        .background(primaryBlue),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.Download,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(32.dp)
      )
    }
  }
}
