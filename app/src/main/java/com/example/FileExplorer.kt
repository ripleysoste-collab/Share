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
  DOCUMENT,   // .txt, .csv, .pdf, .doc -> blanco / gris limpio
  APK,        // .apk
  IMAGE,      // .jpg, .png, .img, etc.
  MEDIA,      // audio, video
  OTHER
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

  fun getFileCategory(name: String, isDirectory: Boolean): FileCategory {
    if (isDirectory) return FileCategory.DIRECTORY
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
      "zip", "rar", "7z", "tar", "gz", "obb", "bz2", "xz" -> FileCategory.COMPRESSED
      "apk", "xapk", "apks" -> FileCategory.APK
      "txt", "csv", "json", "xml", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "log" -> FileCategory.DOCUMENT
      "jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "img", "ico" -> FileCategory.IMAGE
      "mp4", "mkv", "mov", "avi", "webm", "mp3", "m4a", "wav", "flac", "ogg" -> FileCategory.MEDIA
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
   * Carga los archivos de la carpeta de Descargas (directo de disco y MediaStore)
   */
  fun loadDirectory(context: Context, directory: File? = null): List<FileItem> {
    val targetDir = directory ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val result = mutableListOf<FileItem>()

    // 1. Lectura por sistema de archivos directo
    if (targetDir.exists() && targetDir.isDirectory) {
      val files = targetDir.listFiles()
      if (files != null) {
        for (f in files) {
          val isDir = f.isDirectory
          val name = f.name
          val size = if (isDir) 0L else f.length()
          val count = if (isDir) f.listFiles()?.size ?: 0 else 0
          val cat = getFileCategory(name, isDir)
          result.add(
            FileItem(
              name = name,
              path = f.absolutePath,
              isDirectory = isDir,
              sizeBytes = size,
              formattedSize = if (isDir) "$count elementos" else formatFileSize(size),
              extension = name.substringAfterLast('.', "").lowercase(),
              category = cat,
              itemCount = count
            )
          )
        }
      }
    }

    // 2. Si el sistema de archivos directo no devolvió archivos (común en Android 11+ sin permisos legacy),
    // consultamos MediaStore.Downloads
    if (result.isEmpty() && directory == null) {
      val mediaStoreItems = loadFromMediaStore(context)
      result.addAll(mediaStoreItems)
    }

    // Ordenar: carpetas primero, luego archivos por nombre
    return result.sortedWith(
      compareByDescending<FileItem> { it.isDirectory }
        .thenBy { it.name.lowercase() }
    )
  }

  private fun loadFromMediaStore(context: Context): List<FileItem> {
    val items = mutableListOf<FileItem>()
    try {
      val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
      } else {
        MediaStore.Files.getContentUri("external")
      }

      val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE
      )

      context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
      )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

        while (cursor.moveToNext()) {
          val id = cursor.getLong(idColumn)
          val name = cursor.getString(nameColumn) ?: "Archivo"
          val size = cursor.getLong(sizeColumn)
          val uri = ContentUris.withAppendedId(collection, id)

          items.add(
            FileItem(
              name = name,
              path = uri.toString(),
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
