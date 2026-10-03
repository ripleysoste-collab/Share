package com.example

import android.util.Log
import com.example.data.ShareStudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

data class MapSyncState(
  val isListening: Boolean = false,
  val isReceiving: Boolean = false,
  val isMapReceived: Boolean = false,
  val fileName: String? = null,
  val formattedSize: String? = null,
  val totalEntries: Int = 0,
  val mapJson: String? = null,
  val statusMessage: String = "Esperando emisor..."
)

class P2pMapSyncManager(
  private val repository: ShareStudioRepository,
  private val scope: CoroutineScope
) {
  private val TAG = "P2pMapSyncManager"
  private val PORT = 8988

  private var serverSocket: ServerSocket? = null
  private var listenJob: Job? = null

  private val _syncState = MutableStateFlow(MapSyncState())
  val syncState: StateFlow<MapSyncState> = _syncState.asStateFlow()

  /**
   * Inicia el servidor en el Receptor para recibir el mapa milimétrico del emisor.
   */
  fun startServer() {
    if (serverSocket != null && !serverSocket!!.isClosed) return

    listenJob = scope.launch(Dispatchers.IO) {
      try {
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(PORT))
        serverSocket = server
        _syncState.update { it.copy(isListening = true, statusMessage = "Esperando mapa del emisor...") }
        Log.d(TAG, "Servidor de sincronización de mapa activo en puerto $PORT")

        while (!server.isClosed) {
          val clientSocket = server.accept()
          handleIncomingConnection(clientSocket)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Servidor de mapa detenido o error: ${e.message}")
        _syncState.update { it.copy(isListening = false) }
      }
    }
  }

  private suspend fun handleIncomingConnection(socket: Socket) = withContext(Dispatchers.IO) {
    try {
      _syncState.update {
        it.copy(
          isReceiving = true,
          statusMessage = "Recibiendo mapa milimétrico..."
        )
      }

      val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
      val payload = reader.readLine()

      if (!payload.isNullOrBlank()) {
        val json = JSONObject(payload)
        val action = json.optString("action")

        if (action == "FILE_MAP_SYNC") {
          val fileName = json.optString("fileName", "Archivo")
          val filePath = json.optString("filePath", "")
          val fileSizeBytes = json.optLong("fileSizeBytes", 0L)
          val formattedSize = json.optString("formattedSize", "0 B")
          val totalEntries = json.optInt("totalEntries", 0)
          val maxDepth = json.optInt("maxDepth", 0)
          val mapJson = json.optString("mapJson", "")

          // Guardar en la base de datos SQLite del Receptor
          repository.saveFileMap(
            originalFileName = fileName,
            originalFilePath = filePath,
            role = "RECEPTOR",
            fileSizeBytes = fileSizeBytes,
            formattedSize = formattedSize,
            totalEntries = totalEntries,
            maxDepth = maxDepth,
            mapJson = mapJson
          )

          // Confirmar recepción al emisor
          val writer = PrintWriter(socket.getOutputStream(), true)
          writer.println("{\"status\":\"OK\",\"message\":\"MAP_STORED\"}")

          _syncState.update {
            it.copy(
              isReceiving = false,
              isMapReceived = true,
              fileName = fileName,
              formattedSize = formattedSize,
              totalEntries = totalEntries,
              mapJson = mapJson,
              statusMessage = "¡Mapa milimétrico recibido y guardado en SQLite!"
            )
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error procesando mapa entrante: ${e.message}")
    } finally {
      try {
        socket.close()
      } catch (_: Exception) {}
    }
  }

  /**
   * Envía el mapa milimétrico al receptor y lo guarda en la base de datos SQLite local del Emisor.
   */
  suspend fun sendMapToReceiver(
    receiverHost: String?,
    map: GeneratedFileMap
  ): Boolean = withContext(Dispatchers.IO) {
    // 1. Guardar primero en la base de datos SQLite local del Emisor
    try {
      repository.saveFileMap(
        originalFileName = map.fileName,
        originalFilePath = map.filePath,
        role = "EMISOR",
        fileSizeBytes = map.fileSizeBytes,
        formattedSize = map.formattedSize,
        totalEntries = map.totalEntries,
        maxDepth = map.maxDepth,
        mapJson = map.jsonString
      )
    } catch (e: Exception) {
      Log.e(TAG, "Error guardando mapa en emisor: ${e.message}")
    }

    if (receiverHost.isNullOrBlank()) {
      Log.w(TAG, "No hay dirección IP de receptor disponible para transmisión en red")
      return@withContext false
    }

    // 2. Transmitir el mapa vía Socket al receptor
    try {
      val socket = Socket()
      socket.connect(InetSocketAddress(receiverHost, PORT), 4000)

      val writer = PrintWriter(socket.getOutputStream(), true)
      val payload = JSONObject().apply {
        put("action", "FILE_MAP_SYNC")
        put("fileName", map.fileName)
        put("filePath", map.filePath)
        put("fileSizeBytes", map.fileSizeBytes)
        put("formattedSize", map.formattedSize)
        put("totalEntries", map.totalEntries)
        put("maxDepth", map.maxDepth)
        put("mapJson", map.jsonString)
      }

      writer.println(payload.toString())

      // Esperar confirmación
      val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
      val response = reader.readLine()
      socket.close()
      Log.d(TAG, "Mapa transmitido con éxito al receptor: $response")
      return@withContext true
    } catch (e: Exception) {
      Log.e(TAG, "No se pudo transmitir el mapa por socket: ${e.message}")
      return@withContext false
    }
  }

  fun stopServer() {
    try {
      listenJob?.cancel()
      serverSocket?.close()
      serverSocket = null
    } catch (_: Exception) {}
    _syncState.update { it.copy(isListening = false) }
  }
}
