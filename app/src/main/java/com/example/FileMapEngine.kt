package com.example

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class MapEntry(
  val id: Int,
  val name: String,
  val relativePath: String,
  val depth: Int,
  val isDirectory: Boolean,
  val sizeBytes: Long,
  val formattedSize: String,
  val category: FileCategory,
  val extension: String,
  val crc32: Long = 0L
)

data class GeneratedFileMap(
  val fileName: String,
  val filePath: String,
  val fileSizeBytes: Long,
  val formattedSize: String,
  val totalEntries: Int,
  val maxDepth: Int,
  val entries: List<MapEntry>,
  val jsonString: String
)

object FileMapEngine {

  private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "apk", "jar", "obb", "xapk")

  /**
   * Crea el mapa milimétrico del archivo en formato JSON.
   * Emite cada elemento a través de [onEntryDiscovered] en tiempo real para alimentar la animación.
   */
  suspend fun createMilimetricMap(
    context: Context,
    fileItem: FileItem,
    onProgress: (phase: String, currentCount: Int) -> Unit = { _, _ -> },
    onEntryDiscovered: (MapEntry) -> Unit = {}
  ): GeneratedFileMap = withContext(Dispatchers.IO) {
    val entries = mutableListOf<MapEntry>()
    val ext = fileItem.extension.lowercase()
    val isArchive = ARCHIVE_EXTENSIONS.contains(ext)
    val fileObj = File(fileItem.path)
    val existsOnDisk = fileObj.exists()

    onProgress("Abriendo archivo...", 0)
    delay(180L)

    if (fileItem.isDirectory && existsOnDisk) {
      // 1. Es una carpeta completa: escaneo recursivo milimétrico
      onProgress("Inspeccionando árbol de carpetas...", 0)
      scanDirectoryRecursive(fileObj, fileObj.absolutePath, 0, entries, onEntryDiscovered)
    } else if (isArchive) {
      // 2. Es un archivo comprimido o contenedor: lectura de todas sus capas internas
      onProgress("Desglosando capas internas del archivo...", 0)
      val inputStream: InputStream? = if (existsOnDisk) {
        fileObj.inputStream()
      } else if (fileItem.uri != null) {
        context.contentResolver.openInputStream(fileItem.uri)
      } else null

      if (inputStream != null) {
        scanZipEntries(inputStream, entries, onEntryDiscovered)
      } else {
        // Fallback como archivo individual
        val entry = createSingleFileEntry(fileItem, 1)
        entries.add(entry)
        onEntryDiscovered(entry)
      }
    } else {
      // 3. Es un archivo individual (documento, apk, txt, etc.)
      onProgress("Registrando estructura del archivo...", 0)
      val entry = createSingleFileEntry(fileItem, 1)
      entries.add(entry)
      onEntryDiscovered(entry)
      delay(150L)
    }

    // Si el archivo comprimido no tenía entradas legibles directamente, agregamos la entrada raíz
    if (entries.isEmpty()) {
      val fallbackEntry = createSingleFileEntry(fileItem, 1)
      entries.add(fallbackEntry)
      onEntryDiscovered(fallbackEntry)
    }

    onProgress("Generando mapa milimétrico JSON...", entries.size)
    delay(120L)

    val maxDepth = entries.maxOfOrNull { it.depth } ?: 0
    val totalSize = if (fileItem.sizeBytes > 0) fileItem.sizeBytes else entries.sumOf { it.sizeBytes }
    val formattedSize = FileRepository.formatFileSize(totalSize)

    // Construcción del JSON milimétrico
    val jsonObject = JSONObject().apply {
      put("mapVersion", "1.0")
      put("fileName", fileItem.name)
      put("filePath", fileItem.path)
      put("fileSizeBytes", totalSize)
      put("formattedSize", formattedSize)
      put("totalEntries", entries.size)
      put("maxDepth", maxDepth)
      put("createdAt", System.currentTimeMillis())

      val jsonArray = JSONArray()
      entries.forEach { entry ->
        val entryObj = JSONObject().apply {
          put("id", entry.id)
          put("name", entry.name)
          put("relativePath", entry.relativePath)
          put("depth", entry.depth)
          put("isDirectory", entry.isDirectory)
          put("sizeBytes", entry.sizeBytes)
          put("formattedSize", entry.formattedSize)
          put("category", entry.category.name)
          put("extension", entry.extension)
          put("crc32", entry.crc32)
        }
        jsonArray.put(entryObj)
      }
      put("entries", jsonArray)
    }

    val jsonString = jsonObject.toString(2)
    onProgress("Mapa milimétrico completado", entries.size)

    GeneratedFileMap(
      fileName = fileItem.name,
      filePath = fileItem.path,
      fileSizeBytes = totalSize,
      formattedSize = formattedSize,
      totalEntries = entries.size,
      maxDepth = maxDepth,
      entries = entries,
      jsonString = jsonString
    )
  }

  private suspend fun scanZipEntries(
    inputStream: InputStream,
    entries: MutableList<MapEntry>,
    onEntryDiscovered: (MapEntry) -> Unit
  ) {
    ZipInputStream(inputStream).use { zis ->
      var zipEntry: ZipEntry? = zis.nextEntry
      var counter = 1

      while (zipEntry != null) {
        val entryName = zipEntry.name.trimEnd('/')
        val simpleName = entryName.substringAfterLast('/')
        val isDir = zipEntry.isDirectory
        val depth = entryName.count { it == '/' }
        val size = if (zipEntry.size >= 0) zipEntry.size else 0L
        val cat = FileRepository.getFileCategory(simpleName, isDir)
        val ext = simpleName.substringAfterLast('.', "")

        val entry = MapEntry(
          id = counter++,
          name = if (simpleName.isBlank()) entryName else simpleName,
          relativePath = zipEntry.name,
          depth = depth,
          isDirectory = isDir,
          sizeBytes = size,
          formattedSize = if (isDir) "Subcarpeta" else FileRepository.formatFileSize(size),
          category = cat,
          extension = ext,
          crc32 = zipEntry.crc
        )

        entries.add(entry)
        onEntryDiscovered(entry)

        // Pequeño retardo de milisegundos para que la animación de absorción sea suave y apreciable
        if (counter % 3 == 0) {
          delay(40L)
        }

        zipEntry = zis.nextEntry
      }
    }
  }

  private suspend fun scanDirectoryRecursive(
    dir: File,
    rootBasePath: String,
    currentDepth: Int,
    entries: MutableList<MapEntry>,
    onEntryDiscovered: (MapEntry) -> Unit
  ) {
    val files = dir.listFiles() ?: return
    for (f in files) {
      if (f.name.startsWith(".")) continue
      val isDir = f.isDirectory
      val relPath = f.absolutePath.removePrefix(rootBasePath).removePrefix("/")
      val size = if (isDir) 0L else f.length()
      val cat = FileRepository.getFileCategory(f.name, isDir)

      val entry = MapEntry(
        id = entries.size + 1,
        name = f.name,
        relativePath = relPath,
        depth = currentDepth,
        isDirectory = isDir,
        sizeBytes = size,
        formattedSize = if (isDir) "Subcarpeta" else FileRepository.formatFileSize(size),
        category = cat,
        extension = f.name.substringAfterLast('.', "")
      )

      entries.add(entry)
      onEntryDiscovered(entry)
      delay(30L)

      if (isDir) {
        scanDirectoryRecursive(f, rootBasePath, currentDepth + 1, entries, onEntryDiscovered)
      }
    }
  }

  private fun createSingleFileEntry(fileItem: FileItem, id: Int): MapEntry {
    return MapEntry(
      id = id,
      name = fileItem.name,
      relativePath = fileItem.name,
      depth = 0,
      isDirectory = fileItem.isDirectory,
      sizeBytes = fileItem.sizeBytes,
      formattedSize = fileItem.formattedSize,
      category = fileItem.category,
      extension = fileItem.extension
    )
  }
}
