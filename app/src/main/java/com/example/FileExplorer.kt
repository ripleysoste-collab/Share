package com.example

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.DecimalFormat

enum class FileCategory {
  DIRECTORY,
  COMPRESSED, // .zip, .rar, .7z, .obb, .tar, .gz -> cafecito
  DOCUMENT,   // .txt, .csv, .pdf, .doc, .xls, .ppt -> blanco / gris limpio
  APK,        // .apk, .xapk
  OTHER       // otros archivos generales
}

data class FileItem(
  val name: String,
  val path: String,
  val isDirectory: Boolean,
  val sizeBytes: Long,
  val formattedSize: String,
  val extension: String,
  val category: FileCategory,
  val uri: Uri? = null,
  val itemCount: Int = 0
)

object FileRepository {

  private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "img", "ico", "heic", "heif", "tiff", "tif"
  )

  private val MEDIA_EXTENSIONS = setOf(
    "mp4", "mkv", "mov", "avi", "webm", "3gp", "ts", "m4v", "flv", "wmv",
    "mp3", "m4a", "wav", "flac", "ogg", "aac", "opus", "mid", "midi", "wma"
  )

  /**
   * Determina si el archivo es imagen o multimedia (video/audio).
   * Según el requerimiento, las imágenes y videos se excluyen del explorador de descargas.
   */
  fun isImageOrMedia(name: String, mimeType: String? = null): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    if (ext in IMAGE_EXTENSIONS || ext in MEDIA_EXTENSIONS) return true
    if (mimeType != null) {
      val lowerMime = mimeType.lowercase()
      if (lowerMime.startsWith("image/") || lowerMime.startsWith("video/") || lowerMime.startsWith("audio/")) {
        return true
      }
    }
    return false
  }

  fun getFileCategory(name: String, isDirectory: Boolean): FileCategory {
    if (isDirectory) return FileCategory.DIRECTORY
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
      "zip", "rar", "7z", "tar", "gz", "obb", "bz2", "xz", "iso", "7-zip" -> FileCategory.COMPRESSED
      "apk", "xapk", "apks" -> FileCategory.APK
      "txt", "csv", "json", "xml", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
      "log", "html", "htm", "epub", "rtf", "odt", "ods", "odp", "md", "tsv" -> FileCategory.DOCUMENT
      else -> FileCategory.OTHER
    }
  }

  fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val df = DecimalFormat("#,##0.#")
    return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
  }

  /**
   * Carga los archivos de la carpeta de Descargas (excluyendo imágenes y videos).
   * Lee mediante acceso a sistema de archivos y MediaStore.
   */
  fun loadDirectory(context: Context, directory: File? = null): List<FileItem> {
    val result = mutableListOf<FileItem>()
    val seenPathsOrNames = mutableSetOf<String>()

    // Rutas directas en disco
    val candidateDirs = if (directory != null) {
      listOf(directory)
    } else {
      listOfNotNull(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        File(Environment.getExternalStorageDirectory(), "Download"),
        File("/storage/emulated/0/Download"),
        File("/sdcard/Download")
      )
    }

    for (targetDir in candidateDirs) {
      if (targetDir.exists() && targetDir.isDirectory) {
        val files = targetDir.listFiles()
        if (files != null) {
          for (f in files) {
            val name = f.name
            if (name.startsWith(".")) continue // omitir ocultos

            val isDir = f.isDirectory
            if (!isDir && isImageOrMedia(name)) {
              // Excluir imágenes y videos de descargas según lo solicitado
              continue
            }

            val key = if (isDir) "dir:${f.absolutePath}" else "file:$name"
            if (!seenPathsOrNames.add(key)) continue

            val size = if (isDir) 0L else f.length()
            val count = if (isDir) {
              f.listFiles()?.count { !it.name.startsWith(".") && !isImageOrMedia(it.name) } ?: 0
            } else 0

            result.add(
              FileItem(
                name = name,
                path = f.absolutePath,
                isDirectory = isDir,
                sizeBytes = size,
                formattedSize = if (isDir) "$count archivos" else formatFileSize(size),
                extension = name.substringAfterLast('.', "").lowercase(),
                category = getFileCategory(name, isDir),
                itemCount = count
              )
            )
          }
        }
      }
    }

    // Si estamos en la raíz de descargas, complementar con MediaStore
    if (directory == null) {
      val mediaStoreItems = loadFromMediaStore(context)
      for (item in mediaStoreItems) {
        val key = "file:${item.name}"
        if (seenPathsOrNames.add(key)) {
          result.add(item)
        }
      }
    }

    // Ordenar: carpetas primero, luego archivos alfabéticamente
    return result.sortedWith(
      compareByDescending<FileItem> { it.isDirectory }
        .thenBy { it.name.lowercase() }
    )
  }

  private fun loadFromMediaStore(context: Context): List<FileItem> {
    val items = mutableListOf<FileItem>()
    try {
      val collection = MediaStore.Files.getContentUri("external")
      val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATA,
        MediaStore.MediaColumns.MIME_TYPE
      )

      // Filtrar únicamente los archivos de la carpeta Download / Downloads
      val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "(${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR ${MediaStore.MediaColumns.DATA} LIKE ?)"
      } else {
        "${MediaStore.MediaColumns.DATA} LIKE ?"
      }

      val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf("%Download%", "%Downloads%", "%/Download/%")
      } else {
        arrayOf("%/Download/%")
      }

      context.contentResolver.query(
        collection,
        projection,
        selection,
        selectionArgs,
        "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
      )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
        val mimeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

        while (cursor.moveToNext()) {
          val name = cursor.getString(nameColumn) ?: continue
          val mime = if (mimeColumn >= 0) cursor.getString(mimeColumn) else null

          // Excluir imágenes y videos
          if (isImageOrMedia(name, mime)) continue

          val id = cursor.getLong(idColumn)
          val size = cursor.getLong(sizeColumn)
          val dataPath = if (dataColumn >= 0) cursor.getString(dataColumn) else null
          val uri = ContentUris.withAppendedId(collection, id)

          items.add(
            FileItem(
              name = name,
              path = dataPath ?: uri.toString(),
              isDirectory = false,
              sizeBytes = size,
              formattedSize = formatFileSize(size),
              extension = name.substringAfterLast('.', "").lowercase(),
              category = getFileCategory(name, false),
              uri = uri
            )
          )
        }
      }
    } catch (_: Exception) {}
    return items
  }
}
