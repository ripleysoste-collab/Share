package com.example

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
  matchedReceiverName: String?,
  onBack: () -> Unit,
  onSendFiles: (List<FileItem>) -> Unit
) {
  val context = LocalContext.current
  var currentDirectory by remember { mutableStateOf<File?>(null) }
  var fileItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }
  val selectedFiles = remember { mutableStateListOf<FileItem>() }

  // Carga inicial y por navegación de carpetas
  LaunchedEffect(currentDirectory) {
    fileItems = FileRepository.loadDirectory(context, currentDirectory)
  }

  // Manejo del botón atrás físico o gestual
  BackHandler {
    val parent = currentDirectory?.parentFile
    if (parent != null && parent.exists() && parent.canRead()) {
      currentDirectory = parent
    } else if (currentDirectory != null) {
      currentDirectory = null
    } else {
      onBack()
    }
  }

  // Selector SAF del sistema para elegir archivos de cualquier otra ubicación
  val openDocumentLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenMultipleDocuments()
  ) { uris ->
    if (uris.isNotEmpty()) {
      val items = uris.map { uri ->
        val path = uri.path ?: "Archivo"
        val name = path.substringAfterLast('/')
        FileItem(
          name = name,
          path = uri.toString(),
          isDirectory = false,
          sizeBytes = 0L,
          formattedSize = "Documento",
          extension = name.substringAfterLast('.', "").lowercase(),
          category = FileRepository.getFileCategory(name, false),
          uri = uri
        )
      }
      onSendFiles(items)
    }
  }

  Scaffold(
    containerColor = Color.White,
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              text = currentDirectory?.name ?: stringResource(R.string.downloads_title),
              style = TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = Color(0xFF1E293B)
              )
            )
            matchedReceiverName?.let { receiver ->
              Text(
                text = "Enviando a: $receiver",
                style = TextStyle(
                  fontSize = 12.sp,
                  color = Color(0xFF2563EB),
                  fontWeight = FontWeight.Medium
                )
              )
            }
          }
        },
        navigationIcon = {
          IconButton(
            onClick = {
              val parent = currentDirectory?.parentFile
              if (parent != null && parent.exists()) {
                currentDirectory = parent
              } else if (currentDirectory != null) {
                currentDirectory = null
              } else {
                onBack()
              }
            },
            modifier = Modifier.testTag("file_explorer_back")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.action_back),
              tint = Color(0xFF1E293B)
            )
          }
        },
        actions = {
          IconButton(
            onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.testTag("open_external_storage_button")
          ) {
            Icon(
              imageVector = Icons.Default.FolderOpen,
              contentDescription = stringResource(R.string.open_external_picker),
              tint = Color(0xFF2563EB)
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color.White
        )
      )
    },
    bottomBar = {
      AnimatedVisibility(
        visible = selectedFiles.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
      ) {
        Surface(
          color = Color.White,
          shadowElevation = 8.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .navigationBarsPadding()
              .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "${selectedFiles.size} seleccionado(s)",
              style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E293B)
              )
            )

            Button(
              onClick = { onSendFiles(selectedFiles.toList()) },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
              shape = RoundedCornerShape(percent = 50),
              contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
              modifier = Modifier.testTag("send_selected_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.send_selected),
                style = TextStyle(
                  color = Color.White,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 14.sp
                )
              )
            }
          }
        }
      }
    }
  ) { padding ->
    if (fileItems.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(32.dp)
        ) {
          Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(48.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = stringResource(R.string.no_files_found),
            style = TextStyle(
              fontSize = 14.sp,
              color = Color(0xFF64748B),
              fontWeight = FontWeight.Medium
            )
          )
          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
            shape = RoundedCornerShape(percent = 50)
          ) {
            Text(
              text = stringResource(R.string.open_external_picker),
              style = TextStyle(color = Color(0xFF2563EB), fontSize = 13.sp)
            )
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .testTag("file_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(fileItems, key = { it.path }) { item ->
          val isSelected = selectedFiles.contains(item)
          FileItemRow(
            item = item,
            isSelected = isSelected,
            onClick = {
              if (item.isDirectory) {
                currentDirectory = File(item.path)
              } else {
                if (isSelected) {
                  selectedFiles.remove(item)
                } else {
                  selectedFiles.add(item)
                }
              }
            }
          )
        }
      }
    }
  }
}

/**
 * Fila de diseño para cada elemento:
 * - Carpetas: Icono azul pequeño con nombre y número de elementos.
 * - Archivos comprimidos (.zip, .obb, .rar): Icono cafecito distintivo.
 * - Archivos normales (.txt, .csv, etc.): Blancos con borde limpio.
 */
@Composable
fun FileItemRow(
  item: FileItem,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val (icon, iconTint, badgeBackground, itemBackground) = when (item.category) {
    FileCategory.DIRECTORY -> {
      // Carpeta azul pequeña
      Quadruple(
        Icons.Default.Folder,
        Color(0xFF2563EB),
        Color(0xFFEFF6FF),
        Color.White
      )
    }
    FileCategory.COMPRESSED -> {
      // Archivos comprimidos cafecitos (.zip, .rar, .obb, etc.)
      Quadruple(
        Icons.Default.Archive,
        Color(0xFF795548),
        Color(0xFFEFEBE9),
        Color.White
      )
    }
    FileCategory.APK -> {
      Quadruple(
        Icons.Default.Android,
        Color(0xFF10B981),
        Color(0xFFECFDF5),
        Color.White
      )
    }
    FileCategory.IMAGE -> {
      Quadruple(
        Icons.Default.Image,
        Color(0xFF0284C7),
        Color(0xFFF0F9FF),
        Color.White
      )
    }
    FileCategory.MEDIA -> {
      Quadruple(
        Icons.Default.Movie,
        Color(0xFF8B5CF6),
        Color(0xFFF5F3FF),
        Color.White
      )
    }
    FileCategory.DOCUMENT, FileCategory.OTHER -> {
      // Archivos normales blancos / limpios
      Quadruple(
        Icons.Default.Description,
        Color(0xFF64748B),
        Color(0xFFF8FAFC),
        Color.White
      )
    }
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .border(
        width = if (isSelected) 1.5.dp else 1.dp,
        color = if (isSelected) Color(0xFF2563EB) else Color(0xFFF1F5F9),
        shape = RoundedCornerShape(12.dp)
      )
      .background(if (isSelected) Color(0xFFEFF6FF) else itemBackground)
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Icono decorativo según categoría
    Box(
      modifier = Modifier
        .size(38.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(badgeBackground),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(if (item.category == FileCategory.DIRECTORY) 22.dp else 20.dp)
      )
    }

    Spacer(modifier = Modifier.width(14.dp))

    // Nombre y tamaño/detalles
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = item.name,
        style = TextStyle(
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium,
          color = Color(0xFF0F172A)
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(3.dp))

      Text(
        text = item.formattedSize,
        style = TextStyle(
          fontSize = 12.sp,
          color = Color(0xFF64748B)
        )
      )
    }

    // Indicador de selección para archivos no directorio
    if (!item.isDirectory) {
      Box(
        modifier = Modifier
          .size(22.dp)
          .clip(CircleShape)
          .border(
            width = 1.5.dp,
            color = if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1),
            shape = CircleShape
          )
          .background(if (isSelected) Color(0xFF2563EB) else Color.Transparent),
        contentAlignment = Alignment.Center
      ) {
        if (isSelected) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(13.dp)
          )
        }
      }
    }
  }
}

private data class Quadruple<A, B, C, D>(
  val first: A,
  val second: B,
  val third: C,
  val fourth: D
)
