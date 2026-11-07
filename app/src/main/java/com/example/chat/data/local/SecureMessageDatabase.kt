package com.example.chat.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/** Encrypted local message DB (matrix 11 search foundation, matrix 18 row 5).
 *
 *  Room over SQLCipher: the passphrase is generated once and stored in
 *  SecureKeyValueStore (Android Keystore), so the DB file is unreadable off
 *  device. Content stored here is the DECRYPTED message text the user can
 *  already see; SQLCipher protects it at rest, and it powers offline global
 *  search (messages + captions) without a server round trip.
 */
@Entity(tableName = "messages")
data class LocalMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val type: String,
    val text: String,
    val caption: String?,
    val createdAtMillis: Long,
    val editedAtMillis: Long?,
    val isDeletedForEveryone: Boolean,
)

@Dao
interface LocalMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<LocalMessageEntity>)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAtMillis ASC")
    suspend fun forConversation(conversationId: String): List<LocalMessageEntity>

    /** Global search (matrix 11): message text + captions, newest first. */
    @Query(
        "SELECT * FROM messages WHERE isDeletedForEveryone = 0 AND " +
            "(text LIKE '%' || :query || '%' OR caption LIKE '%' || :query || '%') " +
            "ORDER BY createdAtMillis DESC LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int = 100): List<LocalMessageEntity>

    /** Sender filter in groups + date jump (matrix 11 P2). */
    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId AND senderId = :senderId AND " +
            "isDeletedForEveryone = 0 AND (text LIKE '%' || :query || '%' OR caption LIKE '%' || :query || '%') " +
            "ORDER BY createdAtMillis DESC LIMIT :limit"
    )
    suspend fun searchInConversationFromSender(
        conversationId: String, senderId: String, query: String, limit: Int = 100,
    ): List<LocalMessageEntity>

    @Query("UPDATE messages SET isDeletedForEveryone = 1, text = '', caption = NULL WHERE id = :messageId")
    suspend fun tombstone(messageId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String)

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int
}

@Database(entities = [LocalMessageEntity::class], version = 1, exportSchema = false)
abstract class SecureMessageDatabase : RoomDatabase() {
    abstract fun messages(): LocalMessageDao
}
