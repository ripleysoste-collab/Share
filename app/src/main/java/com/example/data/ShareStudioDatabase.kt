package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Entidad SQLite que almacena el mapa milimétrico del archivo en formato JSON.
 * Guarda el mapa tanto en el Emisor como en el Receptor.
 */
@Entity(tableName = "file_maps")
data class FileMapEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val originalFileName: String,
  val originalFilePath: String,
  val role: String, // "EMISOR" o "RECEPTOR"
  val fileSizeBytes: Long,
  val formattedSize: String,
  val totalEntries: Int,
  val maxDepth: Int,
  val mapJson: String, // Estructura JSON milimétrica completa
  val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface FileMapDao {
  @Query("SELECT * FROM file_maps ORDER BY createdAt DESC")
  fun getAllMaps(): Flow<List<FileMapEntity>>

  @Query("SELECT * FROM file_maps WHERE id = :id LIMIT 1")
  suspend fun getMapById(id: Long): FileMapEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMap(map: FileMapEntity): Long

  @Query("DELETE FROM file_maps WHERE id = :id")
  suspend fun deleteMap(id: Long)

  @Query("DELETE FROM file_maps")
  suspend fun clearAllMaps()
}

/**
 * Entidad que guarda la sesión de conexión/match activa entre dos personas.
 */
@Entity(tableName = "active_match_sessions")
data class ActiveMatchSession(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val role: String, // "EMISOR" o "RECEPTOR"
  val peerDeviceName: String,
  val peerDeviceAddress: String? = null,
  val groupOwnerAddress: String? = null,
  val networkName: String? = null,
  val connectedAt: Long = System.currentTimeMillis(),
  val status: String = "CONNECTED"
)

@Dao
interface MatchSessionDao {
  @Query("SELECT * FROM active_match_sessions ORDER BY connectedAt DESC")
  fun getActiveMatchSessions(): Flow<List<ActiveMatchSession>>

  @Query("SELECT * FROM active_match_sessions LIMIT 1")
  suspend fun getCurrentMatch(): ActiveMatchSession?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMatch(match: ActiveMatchSession): Long

  @Query("DELETE FROM active_match_sessions")
  suspend fun clearActiveMatches()

  @Delete
  suspend fun deleteMatch(match: ActiveMatchSession)
}

/**
 * Entidad que guarda el estado de la sesión: permisos y si es emisor o receptor.
 */
@Entity(tableName = "app_session_state")
data class AppSessionState(
  @PrimaryKey
  val id: Int = 1,
  val permissionsGranted: Boolean,
  val currentMode: String,
  val lastUpdated: Long = System.currentTimeMillis()
)

@Dao
interface AppSessionDao {
  @Query("SELECT * FROM app_session_state WHERE id = 1")
  fun getSessionState(): Flow<AppSessionState?>

  @Query("SELECT * FROM app_session_state WHERE id = 1")
  suspend fun getSessionStateOnce(): AppSessionState?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun updateSessionState(state: AppSessionState)
}

@Database(
  entities = [FileMapEntity::class, ActiveMatchSession::class, AppSessionState::class],
  version = 2,
  exportSchema = false
)
abstract class ShareStudioDatabase : RoomDatabase() {
  abstract fun fileMapDao(): FileMapDao
  abstract fun matchSessionDao(): MatchSessionDao
  abstract fun appSessionDao(): AppSessionDao

  companion object {
    @Volatile
    private var INSTANCE: ShareStudioDatabase? = null

    fun getDatabase(context: Context): ShareStudioDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          ShareStudioDatabase::class.java,
          "sharestudio.db"
        )
          .fallbackToDestructiveMigration()
          .build()
        // Forzar la creación física inmediata del archivo SQLite en el almacenamiento interno del dispositivo
        try {
          instance.openHelper.writableDatabase
        } catch (_: Exception) {}
        INSTANCE = instance
        instance
      }
    }

    /**
     * Devuelve la ruta física y el estado del archivo SQLite en el teléfono
     */
    fun getDatabaseInfo(context: Context): String {
      val dbFile = context.getDatabasePath("sharestudio.db")
      return if (dbFile.exists()) {
        "SQLite Activo: ${dbFile.name} (${dbFile.length()} bytes)"
      } else {
        "SQLite no creado aún"
      }
    }
  }
}

/**
 * Repositorio central de datos SQLite ubicado en la carpeta data
 */
class ShareStudioRepository(private val database: ShareStudioDatabase) {

  val fileMaps: Flow<List<FileMapEntity>> = database.fileMapDao().getAllMaps()
  val activeMatches: Flow<List<ActiveMatchSession>> = database.matchSessionDao().getActiveMatchSessions()
  val sessionState: Flow<AppSessionState?> = database.appSessionDao().getSessionState()

  suspend fun saveFileMap(
    originalFileName: String,
    originalFilePath: String,
    role: String,
    fileSizeBytes: Long,
    formattedSize: String,
    totalEntries: Int,
    maxDepth: Int,
    mapJson: String
  ): Long {
    val entity = FileMapEntity(
      originalFileName = originalFileName,
      originalFilePath = originalFilePath,
      role = role,
      fileSizeBytes = fileSizeBytes,
      formattedSize = formattedSize,
      totalEntries = totalEntries,
      maxDepth = maxDepth,
      mapJson = mapJson,
      createdAt = System.currentTimeMillis()
    )
    return database.fileMapDao().insertMap(entity)
  }

  suspend fun getFileMapById(id: Long): FileMapEntity? {
    return database.fileMapDao().getMapById(id)
  }

  suspend fun deleteFileMap(id: Long) {
    database.fileMapDao().deleteMap(id)
  }

  suspend fun saveMatchSession(
    role: String,
    peerDeviceName: String,
    peerDeviceAddress: String? = null,
    groupOwnerAddress: String? = null,
    networkName: String? = null
  ): Long {
    val session = ActiveMatchSession(
      role = role,
      peerDeviceName = peerDeviceName,
      peerDeviceAddress = peerDeviceAddress,
      groupOwnerAddress = groupOwnerAddress,
      networkName = networkName,
      connectedAt = System.currentTimeMillis(),
      status = "CONNECTED"
    )
    return database.matchSessionDao().insertMatch(session)
  }

  suspend fun clearActiveMatchSession() {
    database.matchSessionDao().clearActiveMatches()
  }

  suspend fun saveSessionState(permissionsGranted: Boolean, currentMode: String) {
    val state = AppSessionState(
      id = 1,
      permissionsGranted = permissionsGranted,
      currentMode = currentMode,
      lastUpdated = System.currentTimeMillis()
    )
    database.appSessionDao().updateSessionState(state)
  }
}
