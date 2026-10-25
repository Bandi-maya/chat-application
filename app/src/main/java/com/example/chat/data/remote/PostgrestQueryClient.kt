package com.example.chat.data.remote

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

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
    }

    fun from(table: String): Query = Query(this, table)

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
                throw IOException("Query $table failed with HTTP ${response.code}")
            }
            if (text.isBlank() || text == "null") return@withContext emptyList()
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                PostgrestRpcClient.jsonToMap(arr.getJSONObject(i))
            }
        }
    }
}
