package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ShareStudio", appName)
    val actionStart = context.getString(R.string.action_start)
    assertEquals("empezar", actionStart)
    val actionSearching = context.getString(R.string.action_searching)
    assertEquals("buscando", actionSearching)
    val actionFound = context.getString(R.string.action_found)
    assertEquals("encontrado", actionFound)
    val modeEmisor = context.getString(R.string.mode_emisor)
    assertEquals("emisor", modeEmisor)
    val modeReceptor = context.getString(R.string.mode_receptor)
    assertEquals("receptor", modeReceptor)
    val downloadsTitle = context.getString(R.string.downloads_title)
    assertEquals("Descargas", downloadsTitle)
  }

  @Test
  fun `verify wifi direct initial state`() {
    val state = WifiDirectUiState()
    assertEquals(P2pConnectionStatus.IDLE, state.connectionStatus)
    assertFalse(state.isGroupOwner)
    assertFalse(state.isGroupFormed)
    assertTrue(state.autoMatchEnabled)
    assertEquals(120, state.remainingSeconds)
    assertFalse(state.isSearchingActive)
  }

  @Test
  fun `verify file categories categorization`() {
    assertEquals(FileCategory.COMPRESSED, FileRepository.getFileCategory("archivo.zip", false))
    assertEquals(FileCategory.COMPRESSED, FileRepository.getFileCategory("juego.obb", false))
    assertEquals(FileCategory.COMPRESSED, FileRepository.getFileCategory("paquete.rar", false))
    assertEquals(FileCategory.APK, FileRepository.getFileCategory("aplicacion.apk", false))
    assertEquals(FileCategory.DOCUMENT, FileRepository.getFileCategory("documento.txt", false))
    assertEquals(FileCategory.DOCUMENT, FileRepository.getFileCategory("datos.csv", false))
    assertEquals(FileCategory.OTHER, FileRepository.getFileCategory("archivo.bin", false))
    assertTrue(FileRepository.isImageOrMedia("foto.jpg"))
    assertTrue(FileRepository.isImageOrMedia("video.mp4"))
    assertFalse(FileRepository.isImageOrMedia("documento.pdf"))
    assertEquals(FileCategory.DIRECTORY, FileRepository.getFileCategory("Descargas", true))
  }

  @Test
  fun `verify sharestudio sqlite database operations`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = ShareStudioDatabase.getDatabase(context)
    val repository = ShareStudioRepository(db)

    // Inicialmente vacía
    repository.clearActiveMatchSession()
    val initialMatches = repository.activeMatches.first()
    assertTrue(initialMatches.isEmpty())

    // Guardar match activo
    repository.saveMatchSession(
      role = "EMISOR",
      peerDeviceName = "Pixel 8",
      peerDeviceAddress = "02:00:00:00:00:00",
      groupOwnerAddress = "192.168.49.1",
      networkName = "DIRECT-xy-ShareStudio"
    )

    val activeMatches = repository.activeMatches.first()
    assertEquals(1, activeMatches.size)
    assertEquals("EMISOR", activeMatches[0].role)
    assertEquals("Pixel 8", activeMatches[0].peerDeviceName)

    // Cuando se separan o finaliza el match, se borra de la base de datos
    repository.clearActiveMatchSession()
    val clearedMatches = repository.activeMatches.first()
    assertTrue(clearedMatches.isEmpty())

    // Guardar estado de sesión (permisos y rol)
    repository.saveSessionState(permissionsGranted = true, currentMode = "EMISOR")
    val sessionState = repository.sessionState.first()
    assertTrue(sessionState?.permissionsGranted == true)
    assertEquals("EMISOR", sessionState?.currentMode)
  }

  @Test
  fun `verify file map creation and sqlite persistence`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = ShareStudioDatabase.getDatabase(context)
    val repository = ShareStudioRepository(db)

    val testFile = FileItem(
      name = "documento_prueba.pdf",
      path = "/storage/emulated/0/Download/documento_prueba.pdf",
      isDirectory = false,
      sizeBytes = 1024L,
      formattedSize = "1 KB",
      extension = "pdf",
      category = FileCategory.DOCUMENT
    )

    // Crear mapa milimétrico
    val map = FileMapEngine.createMilimetricMap(context, testFile)
    assertEquals("documento_prueba.pdf", map.fileName)
    assertEquals(1, map.totalEntries)
    assertTrue(map.jsonString.contains("mapVersion"))
    assertTrue(map.jsonString.contains("entries"))

    // Guardar mapa en SQLite
    val id = repository.saveFileMap(
      originalFileName = map.fileName,
      originalFilePath = map.filePath,
      role = "EMISOR",
      fileSizeBytes = map.fileSizeBytes,
      formattedSize = map.formattedSize,
      totalEntries = map.totalEntries,
      maxDepth = map.maxDepth,
      mapJson = map.jsonString
    )
    assertTrue(id > 0)

    val storedMaps = repository.fileMaps.first()
    assertEquals(1, storedMaps.size)
    assertEquals("documento_prueba.pdf", storedMaps[0].originalFileName)
    assertEquals("EMISOR", storedMaps[0].role)
    assertTrue(storedMaps[0].mapJson.contains("documento_prueba.pdf"))
  }
}
