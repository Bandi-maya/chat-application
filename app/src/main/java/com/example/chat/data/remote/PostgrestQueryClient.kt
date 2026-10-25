package com.example.chat.data.remote

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Table-query half of the Supabase PostgREST client (the RPC half is
 *  [PostgrestRpcClient]). Mirrors the supabase_dart reads the api branch
 *  performs: `_client.from(t).select(cols).eq(..).inFilter(..).order(..).limit(..)`
 *  -> GET {url}/rest/v1/{table} with PostgREST query params; rows returned as
 *  plain Kotlin maps in PostgREST order. */
class PostgrestQueryClient(
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val accessToken: suspend () -> String?,
    private val client: OkHttpClient = OkHttpClient(),
) {

    class Query(
        private val client: PostgrestQueryClient,
        private val table: String,
    ) {
        private var columns: String = "*"
        private val filters = mutableListOf<Pair<String, String>>()
        private var orderClause: String? = null
        private var limitValue: Int? = null

        fun select(cols: String = "*"): Query {
            columns = cols
            return this
        }

        fun eq(column: String, value: Any?): Query {
            filters += column to "eq.$value"
            return this
        }

        /** Mirrors supabase_dart .isFilter(column, null). */
        /** Mirrors supabase_dart .ilike(): pattern passed verbatim. */
        fun ilike(column: String, value: String): Query {
            filters.add(column to "ilike.$value")
            return this
        }

        fun isNull(column: String): Query {
            filters += column to "is.null"
            return this
        }

        fun inFilter(column: String, values: List<String>): Query {
            val joined = values.joinToString(",") { it }
            filters += column to "in.($joined)"
            return this
        }

        fun order(column: String, ascending: Boolean = true): Query {
            orderClause = "$column." + (if (ascending) "asc" else "desc")
            return this
        }

        fun limit(n: Int): Query {
            limitValue = n
            return this
        }

        suspend fun execute(): List<Map<String, Any?>> = client.runQuery(
            table, columns, filters, orderClause, limitValue,
        )

        /** Mirrors supabase_dart .delete() with the accumulated filters. */
        suspend fun delete(): Unit = client.runDelete(table, filters)

        /** Mirrors supabase_dart .update(values) with the accumulated
         *  filters. As with delete(), call eq()/inFilter() BEFORE update():
         *  a bare update would PATCH every row in the table. */
        suspend fun update(values: Map<String, Any?>): Unit {
            client.runUpdate(table, values, filters)
        }

        /** Mirrors supabase_dart .insert(values).select(): POST with
         *  Prefer: return=representation, returning the inserted rows. */
        suspend fun insert(values: Map<String, Any?>): List<Map<String, Any?>> =
            client.runInsert(table, values)

        /** Mirrors supabase_dart .upsert(values, onConflict: cols): POST with
         *  resolution=merge-duplicates. */
        suspend fun upsert(
            values: Map<String, Any?>,
            onConflict: String,
        ): Unit {
            client.runUpsert(table, values, onConflict, returnRows = false)
        }

        /** Mirrors supabase_dart .upsert(values, onConflict: ...).select():
         *  upsert returning the affected rows. */
        suspend fun upsertReturning(
            values: Map<String, Any?>,
            onConflict: String,
        ): List<Map<String, Any?>> = client.runUpsert(table, values, onConflict, returnRows = true)

        /** Mirrors supabase_dart .update(values).select(): PATCH returning
         *  the affected rows (needed by revokeDevice's maybeSingle check). */
        suspend fun updateReturning(values: Map<String, Any?>): List<Map<String, Any?>> =
            client.runUpdate(table, values, filters, returnRows = true)
    }

    fun from(table: String): Query = Query(this, table)

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }

    internal suspend fun runDelete(
        table: String,
        filters: List<Pair<String, String>>,
    ): Unit = withContext(Dispatchers.IO) {
        val urlBuilder = "$supabaseUrl/rest/v1/$table".toHttpUrl().newBuilder()
        for ((column, clause) in filters) urlBuilder.addQueryParameter(column, clause)
        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("apikey", publishableKey)
            .apply {
                accessToken()?.let { addHeader("Authorization", "Bearer $it") }
            }
            .delete()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Delete $table failed with HTTP ${response.code}")
            }
        }
    }

    internal suspend fun runUpsert(
        table: String,
        values: Map<String, Any?>,
        onConflict: String,
        returnRows: Boolean,
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val urlBuilder = "$supabaseUrl/rest/v1/$table".toHttpUrl().newBuilder()
            .addQueryParameter("on_conflict", onConflict)
        val body = JSONObject()
        for ((key, value) in values) {
            if (value == null) body.put(key, JSONObject.NULL) else body.put(key, value)
        }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("apikey", publishableKey)
            .addHeader("Content-Type", "application/json")
            .addHeader(
                "Prefer",
                if (returnRows) "resolution=merge-duplicates,return=representation"
                else "resolution=merge-duplicates,return=minimal",
            )
            .apply {
                accessToken()?.let { addHeader("Authorization", "Bearer $it") }
            }
            .post(body.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Upsert into $table failed with HTTP ${response.code}")
            }
            if (!returnRows) {
                emptyList()
            } else {
                val text = response.body?.string().orEmpty()
                if (text.isBlank()) emptyList() else PostgrestRpcClient.jsonToList(JSONArray(text)).filterIsInstance<Map<String, Any?>>()
            }
        }
    }

    internal suspend fun runInsert(
        table: String,
        values: Map<String, Any?>,
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val body = JSONObject()
        for ((key, value) in values) {
            if (value == null) body.put(key, JSONObject.NULL) else body.put(key, value)
        }
        // PostgREST accepts a single object or an array; supabase_dart
        // insert(map) sends one object, and .select() then returns an array.
        val request = Request.Builder()
            .url("$supabaseUrl/rest/v1/$table")
            .addHeader("apikey", publishableKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .apply {
                accessToken()?.let { addHeader("Authorization", "Bearer $it") }
            }
            .post(body.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Insert into $table failed with HTTP ${response.code}")
            }
            if (text.isBlank() || text == "null") return@withContext emptyList()
            val trimmed = text.trim()
            val arr = if (trimmed.startsWith("[")) JSONArray(trimmed)
            else JSONArray().put(JSONObject(trimmed))
            (0 until arr.length()).map { i ->
                PostgrestRpcClient.jsonToMap(arr.getJSONObject(i))
            }
        }
    }

    internal suspend fun runUpdate(
        table: String,
        values: Map<String, Any?>,
        filters: List<Pair<String, String>>,
        returnRows: Boolean = false,
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val urlBuilder = "$supabaseUrl/rest/v1/$table".toHttpUrl().newBuilder()
        for ((column, clause) in filters) urlBuilder.addQueryParameter(column, clause)
        val body = JSONObject()
        for ((key, value) in values) {
            if (value == null) body.put(key, JSONObject.NULL) else body.put(key, value)
        }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("apikey", publishableKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", if (returnRows) "return=representation" else "return=minimal")
            .apply {
                accessToken()?.let { addHeader("Authorization", "Bearer $it") }
            }
            .patch(body.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Update $table failed with HTTP ${response.code}")
            }
            if (!returnRows || text.isBlank() || text == "null") {
                return@withContext emptyList()
            }
            val arr = JSONArray(text.trim())
            (0 until arr.length()).map { i ->
                PostgrestRpcClient.jsonToMap(arr.getJSONObject(i))
            }
        }
    }

    internal suspend fun runQuery(
        table: String,
        columns: String,
        filters: List<Pair<String, String>>,
        orderClause: String?,
        limitValue: Int?,
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val urlBuilder = "$supabaseUrl/rest/v1/$table".toHttpUrl().newBuilder()
            .addQueryParameter("select", columns)
        for ((column, clause) in filters) urlBuilder.addQueryParameter(column, clause)
        orderClause?.let { urlBuilder.addQueryParameter("order", it) }
        limitValue?.let { urlBuilder.addQueryParameter("limit", it.toString()) }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .addHeader("apikey", publishableKey)
            .apply {
                accessToken()?.let { addHeader("Authorization", "Bearer $it") }
            }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // PostgREST errors are JSON {message, details, hint, code};
                // surface both so callers can classify 401/JWT/429 like the
                // Dart PostgrestException handlers do.
                val message = runCatching { JSONObject(text).optString("message") }
                    .getOrDefault("")
                throw IOException(
                    if (message.isNotEmpty()) "$message (HTTP ${response.code})"
                    else "Query $table failed with HTTP ${response.code}"
                )
            }
            if (text.isBlank() || text == "null") return@withContext emptyList()
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                PostgrestRpcClient.jsonToMap(arr.getJSONObject(i))
            }
        }
    }
}
