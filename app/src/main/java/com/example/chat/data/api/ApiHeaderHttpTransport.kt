package com.example.chat.data.api

/** Optional response metadata; existing status/body clients keep their unchanged contract. */
data class ApiHeaderResponse(val code: Int, val body: String, val etag: String?)
interface ApiHeaderHttpTransport : ApiHttpTransport {
    fun executeWithHeaders(method: String, url: String, headers: Map<String, String>, body: String?): ApiHeaderResponse
}
