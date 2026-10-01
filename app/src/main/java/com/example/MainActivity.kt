package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.ShareDropTheme

enum class AppScreen {
  HOME,
  SENDER_EXPLORER,
  RECEIVER_WAITING,
  HISTORY
}

enum class TransferMode {
  SENDER, // 'emisor' (default)
  RECEIVER // 'receptor'
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      ShareDropTheme {
        MainAppContainer()
      }
    }
  }
}

@Composable
fun MainAppContainer() {
  val context = LocalContext.current
  val wifiDirectManager = remember { WifiDirectManager(context) }
  val p2pState by wifiDirectManager.uiState.collectAsStateWithLifecycle()
  var transferMode by remember { mutableStateOf(TransferMode.SENDER) }
  var currentScreen by remember { mutableStateOf(AppScreen.HOME) }

  // Regla estricta: Hasta que no haya una conexión/condición real establecida entre los dispositivos,
  // no se cambia de interfaz. Si se pierde la conexión o se separan, regresa inmediatamente al inicio.
  LaunchedEffect(p2pState.connectionStatus, p2pState.matchedDeviceName) {
    val isConnectionEstablished = p2pState.connectionStatus == P2pConnectionStatus.CONNECTED &&
      !p2pState.matchedDeviceName.isNullOrBlank()
    if (!isConnectionEstablished && (currentScreen == AppScreen.SENDER_EXPLORER || currentScreen == AppScreen.RECEIVER_WAITING)) {
      currentScreen = AppScreen.HOME
    }
  }

  AnimatedContent(
    targetState = currentScreen,
    transitionSpec = { fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220)) },
    label = "screen_transition"
  ) { screen ->
    when (screen) {
      AppScreen.HOME -> {
        StartScreen(
          wifiDirectManager = wifiDirectManager,
          p2pState = p2pState,
          transferMode = transferMode,
          onTransferModeChange = { transferMode = it },
          onOpenHistory = { currentScreen = AppScreen.HISTORY },
          onProceedToMatchScreen = {
            val isConnectionEstablished = p2pState.connectionStatus == P2pConnectionStatus.CONNECTED &&
              !p2pState.matchedDeviceName.isNullOrBlank()
            if (isConnectionEstablished) {
              currentScreen = if (transferMode == TransferMode.SENDER) {
                AppScreen.SENDER_EXPLORER
              } else {
                AppScreen.RECEIVER_WAITING
              }
            }
          }
        )
      }
      AppScreen.SENDER_EXPLORER -> {
        FileExplorerScreen(
          matchedReceiverName = p2pState.matchedDeviceName,
          onBack = { currentScreen = AppScreen.HOME },
          onSendFiles = { files ->
            // Archivos seleccionados para enviar
          }
        )
      }
      AppScreen.RECEIVER_WAITING -> {
        ReceiverWaitingScreen(
          matchedSenderName = p2pState.matchedDeviceName,
          onBack = { currentScreen = AppScreen.HOME }
        )
      }
      AppScreen.HISTORY -> {
        HistoryScreen(
          repository = wifiDirectManager.repository,
          onBack = { currentScreen = AppScreen.HOME }
        )
      }
    }
  }
}

@Composable
fun StartScreen(
  wifiDirectManager: WifiDirectManager,
  p2pState: WifiDirectUiState,
  transferMode: TransferMode,
  onTransferModeChange: (TransferMode) -> Unit,
  onOpenHistory: () -> Unit,
  onProceedToMatchScreen: () -> Unit,
  onStartClick: () -> Unit = {}
) {
  // Condición estricta: Solo hay match cuando la conexión está verdaderamente establecida con un dispositivo par
  val isConnectionEstablished = p2pState.connectionStatus == P2pConnectionStatus.CONNECTED &&
    !p2pState.matchedDeviceName.isNullOrBlank()

  // Hasta que no haya una condición/conexión establecida, no cambia a la interfaz de emisor o receptor
  LaunchedEffect(isConnectionEstablished) {
    if (isConnectionEstablished) {
      kotlinx.coroutines.delay(1000L)
      onProceedToMatchScreen()
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { grantedMap ->
    val allGranted = grantedMap.values.all { it }
    if (allGranted) {
      if (transferMode == TransferMode.SENDER) {
        wifiDirectManager.startAsSender()
      } else {
        wifiDirectManager.startAsReceiver()
      }
    }
  }

  DisposableEffect(Unit) {
    wifiDirectManager.registerReceiver()
    if (wifiDirectManager.hasRequiredPermissions()) {
      if (transferMode == TransferMode.SENDER) {
        wifiDirectManager.startAsSender()
      } else {
        wifiDirectManager.startAsReceiver()
      }
    } else {
      permissionLauncher.launch(wifiDirectManager.getRequiredPermissions())
    }
    onDispose {
      wifiDirectManager.unregisterReceiver()
    }
  }

  LaunchedEffect(transferMode) {
    if (wifiDirectManager.hasRequiredPermissions()) {
      if (transferMode == TransferMode.SENDER) {
        wifiDirectManager.startAsSender()
      } else {
        wifiDirectManager.startAsReceiver()
      }
    }
  }

  Scaffold(
    containerColor = Color.White,
    contentColor = Color(0xFF1E293B),
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White)
      .testTag("start_screen")
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(Color.White)
    ) {
      // Fila superior: a la izquierda botón en píldora (emisor / receptor), a la derecha icono de tres rayitas
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(top = 16.dp, start = 20.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        ModeToggleButton(
          mode = transferMode,
          onToggle = {
            val newMode = if (transferMode == TransferMode.SENDER) {
              TransferMode.RECEIVER
            } else {
              TransferMode.SENDER
            }
            onTransferModeChange(newMode)
          },
          modifier = Modifier.testTag("mode_toggle_button")
        )

        IconButton(
          onClick = onOpenHistory,
          modifier = Modifier
            .size(48.dp)
            .testTag("menu_button")
        ) {
          Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = stringResource(R.string.menu_options),
            tint = Color(0xFF1E293B),
            modifier = Modifier.size(28.dp)
          )
        }
      }

      // Bolita de carga limpia moviéndose continuamente en el centro de la pantalla
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          ContinuousLoadingCircleButton(
            isSearching = p2pState.isSearchingActive,
            onClick = {
              if (isConnectionEstablished) {
                onProceedToMatchScreen()
              } else {
                if (!wifiDirectManager.hasRequiredPermissions()) {
                  permissionLauncher.launch(wifiDirectManager.getRequiredPermissions())
                } else {
                  if (transferMode == TransferMode.SENDER) {
                    wifiDirectManager.startAsSender()
                  } else {
                    wifiDirectManager.startAsReceiver()
                  }
                }
                onStartClick()
              }
            },
            modifier = Modifier.testTag("start_button")
          )

          Spacer(modifier = Modifier.height(18.dp))

          // Estado limpio y sutil de la conexión Wi-Fi Direct
          Text(
            text = p2pState.statusMessage,
            style = TextStyle(
              fontSize = 12.sp,
              fontWeight = FontWeight.Normal,
              letterSpacing = 0.3.sp,
              color = Color(0xFF64748B),
              textAlign = TextAlign.Center
            ),
            modifier = Modifier
              .padding(horizontal = 32.dp)
              .testTag("p2p_status_text")
          )

          // Muestra la red y la clave del emisor para que no haya duda si el sistema solicita conexión
          if (transferMode == TransferMode.SENDER && (!p2pState.networkName.isNullOrBlank() || !p2pState.passphrase.isNullOrBlank()) && !isConnectionEstablished) {
            Spacer(modifier = Modifier.height(10.dp))
            androidx.compose.material3.Surface(
              shape = RoundedCornerShape(12.dp),
              color = Color(0xFFF8FAFC),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
              modifier = Modifier.padding(horizontal = 24.dp)
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
              ) {
                Text(
                  text = "Red Wi-Fi: ${p2pState.networkName ?: "DIRECT-ShareStudio"}",
                  style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B)
                  )
                )
                if (!p2pState.passphrase.isNullOrBlank()) {
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = "Clave Wi-Fi: ${p2pState.passphrase}",
                    style = TextStyle(
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Medium,
                      color = Color(0xFF0284C7)
                    )
                  )
                }
              }
            }
          }
        }
      }

      // Botón pequeño moderno con bordes redondos en la parte inferior: "buscando" o "encontrado" en color azul
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center
      ) {
        SearchingPillButton(
          isMatched = isConnectionEstablished,
          onClick = {
            if (isConnectionEstablished) {
              onProceedToMatchScreen()
            } else {
              if (!wifiDirectManager.hasRequiredPermissions()) {
                permissionLauncher.launch(wifiDirectManager.getRequiredPermissions())
              } else {
                if (transferMode == TransferMode.SENDER) {
                  wifiDirectManager.startAsSender()
                } else {
                  wifiDirectManager.startAsReceiver()
                }
              }
            }
          },
          modifier = Modifier.testTag("searching_button")
        )
      }
    }
  }
}

/**
 * Botón azul redondeado en la parte superior izquierda.
 * Muestra la palabra completa en letras pequeñas: "emisor" o "receptor".
 */
@Composable
fun ModeToggleButton(
  mode: TransferMode,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.92f else 1.0f,
    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
    label = "mode_toggle_scale"
  )

  // Palabras completas en letras pequeñas: "emisor" y "receptor"
  val modeText = if (mode == TransferMode.SENDER) {
    stringResource(R.string.mode_emisor)
  } else {
    stringResource(R.string.mode_receptor)
  }

  val description = if (mode == TransferMode.SENDER) {
    stringResource(R.string.mode_sender_desc)
  } else {
    stringResource(R.string.mode_receiver_desc)
  }

  val pillShape = RoundedCornerShape(percent = 50)
  val modernBlue = Color(0xFF2563EB)

  Box(
    modifier = modifier
      .minimumInteractiveComponentSize()
      .scale(scale)
      .shadow(
        elevation = 3.dp,
        shape = pillShape,
        ambientColor = Color(0x332563EB),
        spotColor = Color(0x402563EB)
      )
      .clip(pillShape)
      .background(modernBlue)
      .clickable(
        interactionSource = interactionSource,
        indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.3f)),
        onClick = onToggle
      )
      .padding(horizontal = 14.dp, vertical = 7.dp)
      .semantics { contentDescription = description },
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = modeText,
      style = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        letterSpacing = 0.3.sp,
        color = Color.White,
        textAlign = TextAlign.Center
      ),
      modifier = Modifier.testTag("mode_letter_text")
    )
  }
}

@Composable
fun ContinuousLoadingCircleButton(
  onClick: () -> Unit,
  isSearching: Boolean = true,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.95f else 1.0f,
    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
    label = "button_scale"
  )

  // Animación continua de rotación mientras la búsqueda esté activa (hasta 2 minutos)
  val infiniteTransition = rememberInfiniteTransition(label = "loading_rotation")
  val activeRotation by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1600, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "rotation_angle"
  )

  val rotation = if (isSearching) activeRotation else 0f

  val circleSize = 116.dp
  val strokeWidth = 3.dp
  val trackColor = Color(0xFFE2E8F0)
  val indicatorColor = Color(0xFF3B82F6)

  Box(
    modifier = modifier
      .size(circleSize)
      .scale(scale)
      .shadow(
        elevation = 2.dp,
        shape = CircleShape,
        ambientColor = Color(0x08000000),
        spotColor = Color(0x10000000)
      )
      .clip(CircleShape)
      .background(Color.White)
      .clickable(
        interactionSource = interactionSource,
        indication = ripple(bounded = true, color = indicatorColor.copy(alpha = 0.2f)),
        onClick = onClick
      )
      .testTag("loading_circle"),
    contentAlignment = Alignment.Center
  ) {
    // Bolita de carga con arco moviéndose continuamente sin parar
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokePx = strokeWidth.toPx()
      val padding = strokePx / 2f
      val diameter = size.minDimension - strokePx
      val radius = diameter / 2f
      val centerOffset = center

      // Pista circular base
      drawCircle(
        color = trackColor,
        radius = radius,
        center = centerOffset,
        style = Stroke(width = strokePx)
      )

      // Arco en rotación continua sin parar
      rotate(degrees = rotation, pivot = centerOffset) {
        drawArc(
          color = indicatorColor,
          startAngle = -90f,
          sweepAngle = 100f,
          useCenter = false,
          topLeft = androidx.compose.ui.geometry.Offset(padding, padding),
          size = androidx.compose.ui.geometry.Size(diameter, diameter),
          style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
      }
    }

    // Texto interior: "empezar" en letras pequeñas, tiernas, cómodas y finas
    Text(
      text = stringResource(id = R.string.action_start),
      style = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 1.3.sp,
        color = Color(0xFF475569),
        textAlign = TextAlign.Center
      ),
      modifier = Modifier.testTag("start_text")
    )
  }
}

/**
 * Botón pequeño redondo azul que dice "buscando", moderno y con bordes redondos.
 */
@Composable
fun SearchingPillButton(
  isMatched: Boolean = false,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.94f else 1.0f,
    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
    label = "search_btn_scale"
  )

  val buttonShape = RoundedCornerShape(percent = 50)
  val modernBlue = Color(0xFF2563EB) // Azul moderno eléctrico

  Box(
    modifier = modifier
      .minimumInteractiveComponentSize()
      .scale(scale)
      .shadow(
        elevation = 3.dp,
        shape = buttonShape,
        ambientColor = Color(0x332563EB),
        spotColor = Color(0x402563EB)
      )
      .clip(buttonShape)
      .background(modernBlue)
      .clickable(
        interactionSource = interactionSource,
        indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.3f)),
        onClick = onClick
      )
      .padding(horizontal = 22.dp, vertical = 9.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = if (isMatched) {
        stringResource(id = R.string.action_found)
      } else {
        stringResource(id = R.string.action_searching)
      },
      style = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        letterSpacing = 0.5.sp,
        color = Color.White,
        textAlign = TextAlign.Center
      )
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
  repository: ShareStudioRepository,
  onBack: () -> Unit
) {
  val activeMatches by repository.activeMatches.collectAsStateWithLifecycle(emptyList())

  // Manejo del botón atrás físico o gesto del sistema
  BackHandler { onBack() }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.history_title),
            style = TextStyle(
              fontSize = 18.sp,
              fontWeight = FontWeight.Normal,
              letterSpacing = 0.5.sp,
              color = Color(0xFF1E293B)
            )
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.action_back),
              tint = Color(0xFF475569)
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color.White,
          titleContentColor = Color(0xFF1E293B),
          navigationIconContentColor = Color(0xFF475569)
        )
      )
    },
    containerColor = Color.White,
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White)
      .testTag("history_screen")
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(Color.White)
        .padding(horizontal = 24.dp),
      contentAlignment = Alignment.Center
    ) {
      if (activeMatches.isNotEmpty()) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Text(
            text = "Conexión activa guardada en base de datos",
            style = TextStyle(
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF2563EB)
            )
          )
          Spacer(modifier = Modifier.height(12.dp))
          activeMatches.forEach { match ->
            Text(
              text = "${match.role}: ${match.peerDeviceName}",
              style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1E293B)
              )
            )
          }
        }
      } else {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Text(
            text = stringResource(R.string.history_empty_title),
            style = TextStyle(
              fontSize = 16.sp,
              fontWeight = FontWeight.Medium,
              color = Color(0xFF64748B),
              letterSpacing = 0.4.sp
            ),
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = stringResource(R.string.history_empty_subtitle),
            style = TextStyle(
              fontSize = 13.sp,
              fontWeight = FontWeight.Normal,
              color = Color(0xFF94A3B8),
              letterSpacing = 0.2.sp
            ),
            textAlign = TextAlign.Center
          )
        }
      }
    }
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun StartScreenPreview() {
  ShareDropTheme {
    val context = LocalContext.current
    val manager = remember { WifiDirectManager(context) }
    StartScreen(
      wifiDirectManager = manager,
      p2pState = WifiDirectUiState(),
      transferMode = TransferMode.SENDER,
      onTransferModeChange = {},
      onOpenHistory = {},
      onProceedToMatchScreen = {}
    )
  }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun HistoryScreenPreview() {
  ShareDropTheme {
    val context = LocalContext.current
    val repo = remember { ShareStudioRepository(ShareStudioDatabase.getDatabase(context)) }
    HistoryScreen(repository = repo, onBack = {})
  }
}
