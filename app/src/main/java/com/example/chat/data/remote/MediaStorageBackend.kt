package com.example.chat.data.remote

/** Storage seam behind ChatMediaService / ProfileMediaService (upload +
 *  public/signed URL + remove). Backend migration: the production
 *  implementation is ApiMediaStorageClient over POST /chat/upload;
 *  SupabaseStorageClient remains for reference builds. */
interface MediaStorageBackend {
    suspend fun upload(
        bucket: String,
        path: String,
        bytes: ByteArray,
        contentType: String,
        onProgress: ((transferred: Long, total: Long) -> Unit)? = null,
    )

    fun getPublicUrl(bucket: String, path: String): String

    suspend fun createSignedUrl(bucket: String, path: String, expiresInSeconds: Int): String

    suspend fun remove(bucket: String, paths: List<String>)
}
