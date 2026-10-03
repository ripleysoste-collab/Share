package com.example

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiverWaitingScreen(
  matchedSenderName: String?,
  syncManager: P2pMapSyncManager,
  onBack: () -> Unit
) {
  val syncState by syncManager.syncState.collectAsState()
  var showJsonDialog by remember { mutableStateOf(false) }

  // Iniciar servidor para escuchar el mapa milimétrico
  LaunchedEffect(Unit) {
    syncManager.startServer()
  }

  BackHandler {
    syncManager.stopServer()
    onBack()
  }

  Scaffold(
    containerColor = Color.White,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = if (syncState.isMapReceived) "Mapa Recibido" else "Receptor",
            style = TextStyle(
              fontSize = 17.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF0F172A)
            )
          )
        },
        navigationIcon = {
          IconButton(
            onClick = {
              syncManager.stopServer()
              onBack()
            },
            modifier = Modifier.testTag("receiver_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.action_back),
              tint = Color(0xFF1E293B)
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
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
        modifier = Modifier.padding(horizontal = 28.dp)
      ) {
        // Animación brillante según el estado (esperando, recibiendo o completado)
        ReceiverGlowingOrbAnimation(
          isReceiving = syncState.isReceiving,
          isCompleted = syncState.isMapReceived,
          modifier = Modifier.size(230.dp)
        )

        Spacer(modifier = Modifier.height(30.dp))

        // Título dinámico
        Text(
          text = when {
            syncState.isMapReceived -> "¡Mapa Milimétrico Recibido!"
            syncState.isReceiving -> "Recibiendo mapa de archivo..."
            else -> "Esperando Selección de Archivo"
          },
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = if (syncState.isMapReceived) Color(0xFF059669) else Color(0xFF0F172A),
            textAlign = TextAlign.Center
          )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtítulo con detalles
        val senderLabel = matchedSenderName?.takeIf { it.isNotBlank() } ?: "Emisor"
        Text(
          text = when {
            syncState.isMapReceived -> {
              "El mapa del archivo ${syncState.fileName} se guardó exitosamente en la base de datos SQLite (data).\n${syncState.totalEntries} elementos organizados."
            }
            syncState.isReceiving -> {
              "Sincronizando esquema de capas y profundidad con $senderLabel..."
            }
            else -> {
              "Conectado a $senderLabel.\nEl emisor está eligiendo el archivo para crear el mapa milimétrico."
            }
          },
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center
          ),
          modifier = Modifier.testTag("receiver_waiting_info")
        )

        // Tarjeta con información del archivo recibido
        if (syncState.isMapReceived && syncState.fileName != null) {
          Spacer(modifier = Modifier.height(20.dp))
          Surface(
            color = Color(0xFFF0FDF4),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFDCFCE7)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Description,
                  contentDescription = null,
                  tint = Color(0xFF16A34A),
                  modifier = Modifier.size(20.dp)
                )
              }

              Spacer(modifier = Modifier.width(12.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Mapa: ${syncState.fileName}",
                  style = TextStyle(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF14532D)
                  ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "${syncState.totalEntries} elementos • Guardado en SQLite (data)",
                  style = TextStyle(
                    fontSize = 11.5.sp,
                    color = Color(0xFF15803D)
                  )
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Botón para ver el mapa JSON recibido
          Button(
            onClick = { showJsonDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(percent = 50),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Code,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Ver Mapa JSON Recibido",
              style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            )
          }
        }
      }
    }
  }

  // Diálogo para inspeccionar el mapa JSON recibido
  if (showJsonDialog && syncState.mapJson != null) {
    Dialog(onDismissRequest = { showJsonDialog = false }) {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 24.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
        ) {
          Text(
            text = "Mapa JSON Recibido (Receptor)",
            style = TextStyle(
              fontSize = 16.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF0F172A)
            )
          )
          Text(
            text = "${syncState.fileName} • ${syncState.totalEntries} elementos",
            style = TextStyle(
              fontSize = 12.sp,
              color = Color(0xFF64748B)
            )
          )

          Spacer(modifier = Modifier.height(14.dp))

          Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0F172A),
            modifier = Modifier
              .fillMaxWidth()
              .height(280.dp)
          ) {
            Text(
              text = syncState.mapJson ?: "",
              style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFF38BDF8),
                lineHeight = 15.sp
              ),
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          Button(
            onClick = { showJsonDialog = false },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            shape = RoundedCornerShape(percent = 50),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "Cerrar",
              style = TextStyle(color = Color.White, fontWeight = FontWeight.SemiBold)
            )
          }
        }
      }
    }
  }
}

/**
 * Animación brillante y minimalista para el receptor.
 */
@Composable
fun ReceiverGlowingOrbAnimation(
  isReceiving: Boolean,
  isCompleted: Boolean,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "receiver_orb")

  val pulseProgress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "pulse"
  )

  val coreScale by infiniteTransition.animateFloat(
    initialValue = 0.95f,
    targetValue = 1.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "core_scale"
  )

  val primaryColor = when {
    isCompleted -> Color(0xFF059669)
    isReceiving -> Color(0xFF0284C7)
    else -> Color(0xFF2563EB)
  }

  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val maxRadius = size.minDimension / 2f
      val centerOffset = center

      if (!isCompleted) {
        val r = 38.dp.toPx() + (maxRadius - 38.dp.toPx()) * pulseProgress
        val alpha = (1f - pulseProgress).coerceIn(0f, 1f) * 0.45f
        drawCircle(
          color = primaryColor.copy(alpha = alpha),
          radius = r,
          center = centerOffset,
          style = Stroke(width = 2.dp.toPx())
        )
      }
    }

    Box(
      modifier = Modifier
        .size(76.dp)
        .scale(coreScale)
        .shadow(
          elevation = 10.dp,
          shape = CircleShape,
          ambientColor = primaryColor.copy(alpha = 0.3f),
          spotColor = primaryColor.copy(alpha = 0.5f)
        )
        .clip(CircleShape)
        .background(
          Brush.radialGradient(
            colors = if (isCompleted) {
              listOf(Color(0xFF34D399), Color(0xFF059669))
            } else if (isReceiving) {
              listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
            } else {
              listOf(Color(0xFF60A5FA), Color(0xFF2563EB))
            }
          )
        ),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = when {
          isCompleted -> Icons.Default.CheckCircle
          isReceiving -> Icons.Default.Sync
          else -> Icons.Default.Download
        },
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(34.dp)
      )
    }
  }
}
