package com.example

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
import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Entidad que guarda la sesión de conexión/match activa entre dos personas.
 * Permanece en la base de datos mientras estén conectados y se elimina automáticamente
 * cuando se separan o finaliza el match.
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

/**
 * Entidad que guarda el estado de la sesión: permisos y si es emisor o receptor.
 */
@Entity(tableName = "app_session_state")
data class AppSessionState(
  @PrimaryKey
  val id: Int = 1,
  val permissionsGranted: Boolean,
  val currentMode: String, // "EMISOR" o "RECEPTOR"
  val lastUpdated: Long = System.currentTimeMillis()
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
  entities = [ActiveMatchSession::class, AppSessionState::class],
  version = 1,
  exportSchema = false
)
abstract class ShareStudioDatabase : RoomDatabase() {
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
          "sharestudio.db" // Base de datos SQLite propia llamada sharestudio.db
        ).build()
        INSTANCE = instance
        instance
      }
    }
  }
}

/**
 * Repositorio para coordinar la base de datos de ShareStudio
 */
class ShareStudioRepository(private val database: ShareStudioDatabase) {

  val activeMatches: Flow<List<ActiveMatchSession>> = database.matchSessionDao().getActiveMatchSessions()
  val sessionState: Flow<AppSessionState?> = database.appSessionDao().getSessionState()

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
