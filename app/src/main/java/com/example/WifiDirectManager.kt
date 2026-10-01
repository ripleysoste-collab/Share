package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class P2pConnectionStatus {
  IDLE,
  HOSTING_GROUP,
  DISCOVERING,
  MATCHING,
  CONNECTING,
  CONNECTED,
  TIMED_OUT,
  ERROR
}

data class WifiDirectUiState(
  val isWifiP2pEnabled: Boolean = false,
  val connectionStatus: P2pConnectionStatus = P2pConnectionStatus.IDLE,
  val isGroupOwner: Boolean = false,
  val isGroupFormed: Boolean = false,
  val networkName: String? = null,
  val passphrase: String? = null,
  val groupOwnerAddress: String? = null,
  val peers: List<WifiP2pDevice> = emptyList(),
  val matchedDeviceName: String? = null,
  val thisDeviceName: String = "",
  val statusMessage: String = "Wi-Fi Direct listo",
  val autoMatchEnabled: Boolean = true,
  val remainingSeconds: Int = 120,
  val isSearchingActive: Boolean = false
)

class WifiDirectManager(private val context: Context) {

  private val wifiP2pManager: WifiP2pManager? =
    context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
  private var channel: WifiP2pManager.Channel? = null
  private var receiver: BroadcastReceiver? = null

  val repository: ShareStudioRepository =
    ShareStudioRepository(ShareStudioDatabase.getDatabase(context))

  private val _uiState = MutableStateFlow(WifiDirectUiState())
  val uiState: StateFlow<WifiDirectUiState> = _uiState.asStateFlow()

  // Control de corrutinas para el temporizador de 2 minutos
  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  private var timerJob: Job? = null

  // Evita reintentos continuos si una conexión ya está en curso
  private var isConnectingOrConnected = false

  init {
    wifiP2pManager?.let { manager ->
      channel = manager.initialize(context, context.mainLooper, null)
    }
  }

  fun hasRequiredPermissions(): Boolean {
    val permissions = getRequiredPermissions()
    return permissions.all {
      ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
  }

  fun getRequiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      arrayOf(
        Manifest.permission.NEARBY_WIFI_DEVICES,
        Manifest.permission.ACCESS_FINE_LOCATION
      )
    } else {
      arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
      )
    }
  }

  fun registerReceiver() {
    if (receiver != null) return

    val intentFilter = IntentFilter().apply {
      addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
      addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
      addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
      addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    receiver = object : BroadcastReceiver() {
      @SuppressLint("MissingPermission")
      override fun onReceive(ctx: Context?, intent: Intent?) {
        when (intent?.action) {
          WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
            val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
            val isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
            _uiState.update {
              it.copy(isWifiP2pEnabled = isEnabled)
            }
          }

          WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
            if (hasRequiredPermissions()) {
              channel?.let { ch ->
                wifiP2pManager?.requestPeers(ch) { peersList: WifiP2pDeviceList? ->
                  val list = peersList?.deviceList?.toList() ?: emptyList()
                  _uiState.update {
                    it.copy(peers = list)
                  }

                  // Lógica de match automático para el Receptor si está activo
                  if (_uiState.value.autoMatchEnabled &&
                      !isConnectingOrConnected &&
                      !_uiState.value.isGroupOwner &&
                      _uiState.value.isSearchingActive
                  ) {
                    attemptAutoMatch(list)
                  }
                }
              }
            }
          }

          WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
            @Suppress("DEPRECATION")
            val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
            if (networkInfo?.isConnected == true) {
              channel?.let { ch ->
                wifiP2pManager?.requestConnectionInfo(ch) { info: WifiP2pInfo? ->
                  val connInfo = info ?: return@requestConnectionInfo

                  if (hasRequiredPermissions()) {
                    wifiP2pManager?.requestGroupInfo(ch) { group: WifiP2pGroup? ->
                      val grp = group
                      if (connInfo.isGroupOwner) {
                        // Modo Emisor: solo hay match/conexión real si hay un receptor conectado al grupo
                        val connectedClient = grp?.clientList?.firstOrNull()
                        if (connectedClient != null) {
                          isConnectingOrConnected = true
                          cancelTwoMinuteTimer()
                          val targetName = connectedClient.deviceName.takeIf { !it.isNullOrBlank() } ?: "Receptor conectado"
                          _uiState.update {
                            it.copy(
                              isGroupFormed = true,
                              isGroupOwner = true,
                              groupOwnerAddress = connInfo.groupOwnerAddress?.hostAddress,
                              connectionStatus = P2pConnectionStatus.CONNECTED,
                              isSearchingActive = false,
                              matchedDeviceName = targetName,
                              networkName = grp.networkName,
                              passphrase = grp.passphrase,
                              statusMessage = "¡Match establecido! Conectado con $targetName"
                            )
                          }
                          scope.launch {
                            repository.saveMatchSession(
                              role = "EMISOR",
                              peerDeviceName = targetName,
                              groupOwnerAddress = connInfo.groupOwnerAddress?.hostAddress,
                              networkName = grp.networkName
                            )
                          }
                        } else {
                          // Red propia creada, pero aún NO hay receptor conectado: la condición de match NO está lista
                          isConnectingOrConnected = false
                          _uiState.update {
                            it.copy(
                              isGroupFormed = true,
                              isGroupOwner = true,
                              groupOwnerAddress = connInfo.groupOwnerAddress?.hostAddress,
                              connectionStatus = P2pConnectionStatus.HOSTING_GROUP,
                              matchedDeviceName = null,
                              networkName = grp?.networkName,
                              passphrase = grp?.passphrase,
                              statusMessage = "Red propia activa (esperando conexión de receptor...)"
                            )
                          }
                          scope.launch {
                            repository.clearActiveMatchSession()
                          }
                        }
                      } else {
                        // Modo Receptor: conectado a la red del emisor
                        isConnectingOrConnected = true
                        cancelTwoMinuteTimer()
                        val ownerName = grp?.owner?.deviceName.takeIf { !it.isNullOrBlank() } ?: "Emisor cercano"
                        _uiState.update {
                          it.copy(
                            isGroupFormed = true,
                            isGroupOwner = false,
                            groupOwnerAddress = connInfo.groupOwnerAddress?.hostAddress,
                            connectionStatus = P2pConnectionStatus.CONNECTED,
                            isSearchingActive = false,
                            matchedDeviceName = ownerName,
                            networkName = grp?.networkName,
                            statusMessage = "¡Match establecido! Conectado al emisor $ownerName"
                          )
                        }
                        scope.launch {
                          repository.saveMatchSession(
                            role = "RECEPTOR",
                            peerDeviceName = ownerName,
                            groupOwnerAddress = connInfo.groupOwnerAddress?.hostAddress,
                            networkName = grp?.networkName
                          )
                        }
                      }
                    }
                  }
                }
              }
            } else {
              isConnectingOrConnected = false
              // Al separarse o terminar el match, se borra de la base de datos la conexión
              scope.launch {
                repository.clearActiveMatchSession()
              }

              if (_uiState.value.connectionStatus == P2pConnectionStatus.CONNECTED) {
                _uiState.update {
                  it.copy(
                    isGroupFormed = false,
                    matchedDeviceName = null,
                    connectionStatus = if (it.isGroupOwner) P2pConnectionStatus.HOSTING_GROUP else P2pConnectionStatus.IDLE,
                    statusMessage = if (it.isGroupOwner) "Red propia activa (esperando receptor)" else "Desconectado"
                  )
                }
              }
            }
          }

          WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
            device?.let { dev ->
              _uiState.update {
                it.copy(thisDeviceName = dev.deviceName ?: "")
              }
            }
          }
        }
      }
    }

    ContextCompat.registerReceiver(
      context,
      receiver,
      intentFilter,
      ContextCompat.RECEIVER_NOT_EXPORTED
    )
  }

  fun unregisterReceiver() {
    cancelTwoMinuteTimer()
    receiver?.let {
      try {
        context.unregisterReceiver(it)
      } catch (_: Exception) {}
      receiver = null
    }
  }

  /**
   * Inicia el temporizador de 2 minutos (120 segundos).
   * Si no se conecta antes de 2 minutos, detiene la búsqueda y notifica al usuario.
   */
  private fun startTwoMinuteTimer(isSender: Boolean) {
    cancelTwoMinuteTimer()
    _uiState.update {
      it.copy(
        remainingSeconds = 120,
        isSearchingActive = true
      )
    }

    timerJob = scope.launch {
      for (second in 120 downTo 1) {
        delay(1000L)
        val remaining = second - 1
        _uiState.update {
          it.copy(
            remainingSeconds = remaining,
            statusMessage = if (isSender) {
              "Emisor activo: esperando receptor (${formatTime(remaining)})"
            } else {
              "Receptor buscando emisor (${formatTime(remaining)})"
            }
          )
        }
      }
      // Se agotaron los 2 minutos: detener búsqueda para ahorrar batería
      stopDueToTimeout(isSender)
    }
  }

  private fun cancelTwoMinuteTimer() {
    timerJob?.cancel()
    timerJob = null
  }

  @SuppressLint("MissingPermission")
  private fun stopDueToTimeout(isSender: Boolean) {
    val mgr = wifiP2pManager
    val ch = channel
    if (mgr != null && ch != null) {
      if (isSender) {
        mgr.removeGroup(ch, null)
      } else {
        mgr.stopPeerDiscovery(ch, null)
      }
    }
    isConnectingOrConnected = false
    _uiState.update {
      it.copy(
        connectionStatus = P2pConnectionStatus.TIMED_OUT,
        isSearchingActive = false,
        isGroupFormed = false,
        statusMessage = "Búsqueda detenida tras 2 min. Toca la bolita para volver a empezar."
      )
    }
  }

  private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
  }

  /**
   * Intenta hacer match automático con el emisor encontrado.
   */
  private fun attemptAutoMatch(peers: List<WifiP2pDevice>) {
    if (peers.isEmpty()) return

    // Prioriza dispositivos que ya son Group Owner (nuestro emisor que creó la red)
    val bestCandidate = peers.firstOrNull { it.isGroupOwner }
      ?: peers.firstOrNull { it.status == WifiP2pDevice.AVAILABLE }
      ?: peers.firstOrNull()

    bestCandidate?.let { device ->
      val devName = device.deviceName.takeIf { !it.isNullOrBlank() } ?: "Emisor cercano"
      _uiState.update {
        it.copy(
          connectionStatus = P2pConnectionStatus.MATCHING,
          matchedDeviceName = devName,
          statusMessage = "Match encontrado: $devName. Conectando automáticamente..."
        )
      }
      connectToDevice(device)
    }
  }

  /**
   * Configura el modo EMISOR (por defecto):
   * Crea su propia red Wi-Fi Direct (Group Owner) y permanece activo buscando hasta 2 minutos.
   */
  @SuppressLint("MissingPermission")
  fun startAsSender() {
    val mgr = wifiP2pManager ?: return
    val ch = channel ?: return
    if (!hasRequiredPermissions()) {
      _uiState.update { it.copy(statusMessage = "Permisos requeridos para Wi-Fi Direct") }
      return
    }

    isConnectingOrConnected = false
    _uiState.update {
      it.copy(
        isGroupOwner = true,
        connectionStatus = P2pConnectionStatus.HOSTING_GROUP,
        statusMessage = "Iniciando red propia (Emisor)...",
        isSearchingActive = true
      )
    }

    // Inicia temporizador de 2 minutos
    startTwoMinuteTimer(isSender = true)

    // Remueve grupo previo para crear una red propia limpia
    mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
      override fun onSuccess() {
        createSenderGroup(mgr, ch)
      }

      override fun onFailure(reason: Int) {
        createSenderGroup(mgr, ch)
      }
    })
  }

  @SuppressLint("MissingPermission")
  private fun createSenderGroup(mgr: WifiP2pManager, ch: WifiP2pManager.Channel) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        val config = WifiP2pConfig.Builder()
          .setNetworkName("DIRECT-ShareStudio")
          .setPassphrase("12345678")
          .build()

        mgr.createGroup(ch, config, object : WifiP2pManager.ActionListener {
          override fun onSuccess() {
            onGroupCreatedSuccess(mgr, ch)
          }

          override fun onFailure(reason: Int) {
            createStandardGroup(mgr, ch)
          }
        })
        return
      } catch (_: Throwable) {
        // Fallback al método estándar en caso de no soportar config customizada
      }
    }
    createStandardGroup(mgr, ch)
  }

  @SuppressLint("MissingPermission")
  private fun createStandardGroup(mgr: WifiP2pManager, ch: WifiP2pManager.Channel) {
    mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
      override fun onSuccess() {
        onGroupCreatedSuccess(mgr, ch)
      }

      override fun onFailure(reason: Int) {
        _uiState.update {
          it.copy(
            connectionStatus = P2pConnectionStatus.ERROR,
            statusMessage = "Error al crear red propia (código $reason)"
          )
        }
      }
    })
  }

  @SuppressLint("MissingPermission")
  private fun onGroupCreatedSuccess(mgr: WifiP2pManager, ch: WifiP2pManager.Channel) {
    _uiState.update {
      it.copy(
        isGroupOwner = true,
        isGroupFormed = true,
        connectionStatus = P2pConnectionStatus.HOSTING_GROUP,
        statusMessage = "Red propia activa (Emisor listo para match - 2:00)"
      )
    }

    mgr.requestGroupInfo(ch) { group ->
      group?.let { grp ->
        _uiState.update {
          it.copy(
            networkName = grp.networkName,
            passphrase = grp.passphrase
          )
        }
      }
    }
  }

  /**
   * Configura el modo RECEPTOR:
   * El receptor NO tiene red Wi-Fi propia. Desmantela cualquier grupo y busca la red del emisor
   * para conectarse automáticamente hasta por 2 minutos.
   */
  @SuppressLint("MissingPermission")
  fun startAsReceiver() {
    val mgr = wifiP2pManager ?: return
    val ch = channel ?: return
    if (!hasRequiredPermissions()) {
      _uiState.update { it.copy(statusMessage = "Permisos requeridos para Wi-Fi Direct") }
      return
    }

    isConnectingOrConnected = false
    _uiState.update {
      it.copy(
        isGroupOwner = false,
        isGroupFormed = false,
        connectionStatus = P2pConnectionStatus.DISCOVERING,
        statusMessage = "Receptor buscando emisor (2:00)...",
        isSearchingActive = true
      )
    }

    // Inicia temporizador de 2 minutos para el receptor
    startTwoMinuteTimer(isSender = false)

    // El receptor NO crea red Wi-Fi: remueve cualquier grupo que tuviera
    mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
      override fun onSuccess() {
        discoverAvailablePeers(mgr, ch)
      }

      override fun onFailure(reason: Int) {
        discoverAvailablePeers(mgr, ch)
      }
    })
  }

  @SuppressLint("MissingPermission")
  private fun discoverAvailablePeers(mgr: WifiP2pManager, ch: WifiP2pManager.Channel) {
    mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
      override fun onSuccess() {
        _uiState.update {
          it.copy(
            statusMessage = "Buscando emisor cercano para conectar..."
          )
        }
      }

      override fun onFailure(reason: Int) {
        _uiState.update {
          it.copy(
            connectionStatus = P2pConnectionStatus.ERROR,
            statusMessage = "Fallo al iniciar búsqueda (código $reason)"
          )
        }
      }
    })
  }

  /**
   * Conectar a un dispositivo emisor detectado.
   */
  @SuppressLint("MissingPermission")
  fun connectToDevice(device: WifiP2pDevice, onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
    val mgr = wifiP2pManager ?: return
    val ch = channel ?: return
    if (!hasRequiredPermissions()) return

    isConnectingOrConnected = true
    val config = WifiP2pConfig().apply {
      deviceAddress = device.deviceAddress
      wps.setup = WpsInfo.PBC // Configuración Push Button: conexión automática sin solicitar contraseña manual
      groupOwnerIntent = 0    // El receptor explícitamente se conecta como cliente al emisor
    }

    val targetName = device.deviceName.takeIf { !it.isNullOrBlank() } ?: "Emisor"
    _uiState.update {
      it.copy(
        connectionStatus = P2pConnectionStatus.CONNECTING,
        matchedDeviceName = targetName,
        statusMessage = "Conectando automáticamente a $targetName..."
      )
    }

    mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
      override fun onSuccess() {
        cancelTwoMinuteTimer()
        _uiState.update {
          it.copy(
            connectionStatus = P2pConnectionStatus.CONNECTED,
            isSearchingActive = false,
            statusMessage = "¡Match establecido con $targetName!"
          )
        }
        onSuccess()
      }

      override fun onFailure(reason: Int) {
        isConnectingOrConnected = false
        _uiState.update {
          it.copy(
            connectionStatus = P2pConnectionStatus.ERROR,
            statusMessage = "Reintentando conexión automática..."
          )
        }
        onFailure(reason)
      }
    })
  }

  /**
   * Desconectar o reiniciar estado.
   */
  @SuppressLint("MissingPermission")
  fun disconnect() {
    val mgr = wifiP2pManager ?: return
    val ch = channel ?: return
    cancelTwoMinuteTimer()
    isConnectingOrConnected = false
    mgr.removeGroup(ch, null)
    scope.launch {
      repository.clearActiveMatchSession()
    }
    _uiState.update {
      it.copy(
        connectionStatus = P2pConnectionStatus.IDLE,
        matchedDeviceName = null,
        isGroupFormed = false,
        isSearchingActive = false,
        statusMessage = "Desconectado"
      )
    }
  }
}
