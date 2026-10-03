package com.example

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ShareStudioRepository
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pantalla que se activa cuando el emisor toca el archivo que va a transferir.
 * Muestra la bola central de succión con los elementos orbitando alrededor según su tipo,
 * mientras crea el mapa milimétrico JSON y lo guarda en SQLite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileMappingScreen(
  fileItem: FileItem,
  receiverHost: String?,
  repository: ShareStudioRepository,
  syncManager: P2pMapSyncManager,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var phaseMessage by remember { mutableStateOf("Abriendo archivo...") }
  var totalItemsMapped by remember { mutableStateOf(0) }
  var isMappingCompleted by remember { mutableStateOf(false) }
  var generatedMap by remember { mutableStateOf<GeneratedFileMap?>(null) }
  var showJsonPreviewDialog by remember { mutableStateOf(false) }

  // Lista viva de entradas descubiertas para alimentar las órbitas de succión
  val discoveredEntries = remember { mutableStateListOf<MapEntry>() }

  BackHandler {
    onBack()
  }

  // Ejecución del motor milimétrico
  LaunchedEffect(fileItem) {
    val result = FileMapEngine.createMilimetricMap(
      context = context,
      fileItem = fileItem,
      onProgress = { phase, count ->
        phaseMessage = phase
        totalItemsMapped = count
      },
      onEntryDiscovered = { entry ->
        if (discoveredEntries.size < 16) {
          discoveredEntries.add(entry)
        }
      }
    )

    generatedMap = result
    isMappingCompleted = true

    // Guardar en SQLite del Emisor y sincronizar con el Receptor por Socket
    scope.launch {
      syncManager.sendMapToReceiver(receiverHost, result)
    }
  }

  Scaffold(
    containerColor = Color.White,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Creando Mapa de Archivo",
            style = TextStyle(
              fontSize = 17.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF0F172A)
            )
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("mapping_back_button")
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
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Cabecera con nombre del archivo seleccionado
      Spacer(modifier = Modifier.height(6.dp))
      Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0xFFEFF6FF)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (fileItem.extension == "zip" || fileItem.extension == "rar") {
                Icons.Default.Archive
              } else Icons.Default.Description,
              contentDescription = null,
              tint = Color(0xFF2563EB),
              modifier = Modifier.size(20.dp)
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = fileItem.name,
              style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A)
              ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${fileItem.formattedSize} • Mapa Milimétrico JSON",
              style = TextStyle(
                fontSize = 11.5.sp,
                color = Color(0xFF64748B)
              )
            )
          }

          if (isMappingCompleted) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = "Completado",
              tint = Color(0xFF10B981),
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Animación brillante de la bola de carga con efecto de succión y elementos alrededor
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        SuctionMappingOrbAnimation(
          isCompleted = isMappingCompleted,
          entries = discoveredEntries,
          modifier = Modifier.fillMaxSize()
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Estado actual y métricas del mapa
      Text(
        text = phaseMessage,
        style = TextStyle(
          fontSize = 15.sp,
          fontWeight = FontWeight.Medium,
          color = if (isMappingCompleted) Color(0xFF059669) else Color(0xFF2563EB),
          textAlign = TextAlign.Center
        )
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = if (isMappingCompleted) {
          "Mapa guardado en SQLite (data). Estructura lista para envío."
        } else {
          "Identificando ubicación exacta de carpetas y archivos..."
        },
        style = TextStyle(
          fontSize = 12.5.sp,
          color = Color(0xFF64748B),
          textAlign = TextAlign.Center
        )
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Botón inferior para ver el mapa JSON resultante
      AnimatedVisibility(
        visible = isMappingCompleted,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
        ) {
          Button(
            onClick = { showJsonPreviewDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(percent = 50),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 11.dp),
            modifier = Modifier.testTag("preview_json_map_button")
          ) {
            Icon(
              imageVector = Icons.Default.Code,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Ver Mapa JSON Milimétrico",
              style = TextStyle(
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
              )
            )
          }
        }
      }
    }
  }

  // Diálogo para inspeccionar el mapa JSON en detalle
  if (showJsonPreviewDialog && generatedMap != null) {
    JsonMapPreviewDialog(
      map = generatedMap!!,
      onDismiss = { showJsonPreviewDialog = false }
    )
  }
}

/**
 * Animación central de la bola de carga brillante con efecto de succión y absorción:
 * Los archivos detectados orbitan y fluyen en espiral hacia el centro.
 */
@Composable
fun SuctionMappingOrbAnimation(
  isCompleted: Boolean,
  entries: List<MapEntry>,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "orb_suction")

  // Rotación del campo gravitacional
  val rotation by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 5000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "rotation"
  )

  // Pulso de succión de la bola central
  val corePulse by infiniteTransition.animateFloat(
    initialValue = 0.94f,
    targetValue = 1.06f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "core_pulse"
  )

  // Espiral de absorción
  val spiralProgress by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "spiral"
  )

  BoxWithConstraints(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    val widthPx = constraints.maxWidth.toFloat()
    val heightPx = constraints.maxHeight.toFloat()
    val centerRadius = (minOf(widthPx, heightPx) / 2.7f).coerceIn(110f, 170f)

    // Canvas de fondo: ondas de succión, espirales luminosas y líneas de atracción
    Canvas(modifier = Modifier.fillMaxSize()) {
      val centerOffset = center

      // 1. Anillos magnéticos de atracción
      for (i in 1..3) {
        val ringRadius = centerRadius * (0.8f + i * 0.35f)
        val ringAlpha = (0.22f / i)
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0xFF38BDF8).copy(alpha = ringAlpha), Color.Transparent),
            center = centerOffset,
            radius = ringRadius
          ),
          radius = ringRadius,
          center = centerOffset
        )
        drawCircle(
          color = Color(0xFF60A5FA).copy(alpha = 0.25f / i),
          radius = ringRadius,
          center = centerOffset,
          style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
        )
      }

      // 2. Líneas espirales de succión hacia el centro
      rotate(degrees = rotation, pivot = centerOffset) {
        for (a in 0 until 4) {
          val angleRad = Math.toRadians((a * 90.0) + (spiralProgress * 360.0))
          val startDist = centerRadius * 1.5f
          val endDist = centerRadius * 0.4f
          val startX = centerOffset.x + (cos(angleRad) * startDist).toFloat()
          val startY = centerOffset.y + (sin(angleRad) * startDist).toFloat()
          val endX = centerOffset.x + (cos(angleRad + 1.2) * endDist).toFloat()
          val endY = centerOffset.y + (sin(angleRad + 1.2) * endDist).toFloat()

          drawLine(
            brush = Brush.linearGradient(
              colors = listOf(Color(0xFF3B82F6).copy(alpha = 0.6f), Color.Transparent),
              start = Offset(startX, startY),
              end = Offset(endX, endY)
            ),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
          )
        }
      }
    }

    // 3. Elementos / Chips de archivos orbitando alrededor de la bolita
    val displayEntries = entries.take(8)
    displayEntries.forEachIndexed { index, entry ->
      val baseAngle = (index * (360f / maxOf(displayEntries.size, 1))) + (rotation * 0.4f)
      val angleRad = Math.toRadians(baseAngle.toDouble())
      // Radio dinámico con suave acercamiento hacia el centro (succión)
      val r = centerRadius * (1.15f - (spiralProgress * 0.15f))
      val xOffset = (cos(angleRad) * r).toInt()
      val yOffset = (sin(angleRad) * r).toInt()

      FloatingFileChip(
        entry = entry,
        modifier = Modifier.offset { IntOffset(xOffset, yOffset) }
      )
    }

    // 4. Bola de Carga y Núcleo de Succión Central
    val orbSize = 100.dp
    Box(
      modifier = Modifier
        .size(orbSize)
        .scale(corePulse)
        .shadow(
          elevation = 14.dp,
          shape = CircleShape,
          ambientColor = Color(0x332563EB),
          spotColor = Color(0x660284C7)
        )
        .clip(CircleShape)
        .background(
          Brush.radialGradient(
            colors = if (isCompleted) {
              listOf(Color(0xFF34D399), Color(0xFF059669), Color(0xFF065F46))
            } else {
              listOf(Color(0xFF60A5FA), Color(0xFF2563EB), Color(0xFF1E3A8A))
            }
          )
        ),
      contentAlignment = Alignment.Center
    ) {
      // Anillo de rotación sobre la bola
      Canvas(modifier = Modifier.fillMaxSize()) {
        rotate(degrees = -rotation * 1.5f, pivot = center) {
          drawArc(
            color = Color.White.copy(alpha = 0.85f),
            startAngle = 0f,
            sweepAngle = 110f,
            useCenter = false,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            topLeft = Offset(8.dp.toPx(), 8.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(
              size.width - 16.dp.toPx(),
              size.height - 16.dp.toPx()
            )
          )
        }
      }

      // Icono central
      Icon(
        imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Archive,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(38.dp)
      )
    }
  }
}

/**
 * Chip flotante para cada archivo o subcarpeta detectada en el mapa,
 * con su respectivo estilo de color e icono.
 */
@Composable
fun FloatingFileChip(
  entry: MapEntry,
  modifier: Modifier = Modifier
) {
  val (icon, iconTint, badgeBg) = when (entry.category) {
    FileCategory.DIRECTORY -> {
      Triple(Icons.Default.Folder, Color(0xFF2563EB), Color(0xFFEFF6FF))
    }
    FileCategory.COMPRESSED -> {
      Triple(Icons.Default.Archive, Color(0xFF795548), Color(0xFFEFEBE9))
    }
    FileCategory.APK -> {
      Triple(Icons.Default.Android, Color(0xFF10B981), Color(0xFFECFDF5))
    }
    FileCategory.DOCUMENT, FileCategory.OTHER -> {
      Triple(Icons.Default.Description, Color(0xFF64748B), Color(0xFFF8FAFC))
    }
  }

  Surface(
    shape = RoundedCornerShape(percent = 50),
    color = Color.White.copy(alpha = 0.95f),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    shadowElevation = 3.dp,
    modifier = modifier
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
      Box(
        modifier = Modifier
          .size(20.dp)
          .clip(CircleShape)
          .background(badgeBg),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = iconTint,
          modifier = Modifier.size(13.dp)
        )
      }

      Spacer(modifier = Modifier.width(6.dp))

      Text(
        text = entry.name.take(12),
        style = TextStyle(
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFF1E293B)
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

/**
 * Diálogo modal para inspeccionar el mapa JSON milimétrico generado.
 */
@Composable
fun JsonMapPreviewDialog(
  map: GeneratedFileMap,
  onDismiss: () -> Unit
) {
  Dialog(onDismissRequest = onDismiss) {
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
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column {
            Text(
              text = "Mapa JSON Milimétrico",
              style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A)
              )
            )
            Text(
              text = "${map.totalEntries} elementos • Profundidad máx: ${map.maxDepth}",
              style = TextStyle(
                fontSize = 12.sp,
                color = Color(0xFF64748B)
              )
            )
          }

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFECFDF5)
          ) {
            Text(
              text = "SQLite OK",
              style = TextStyle(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF059669)
              ),
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Contenedor de código JSON con scroll
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color(0xFF0F172A),
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .height(280.dp)
        ) {
          Text(
            text = map.jsonString,
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
          onClick = onDismiss,
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
