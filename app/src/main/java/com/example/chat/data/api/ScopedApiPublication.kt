package com.example.chat.data.api

/** Fetch may suspend; the final publication is short, synchronous and account-scoped. */
suspend fun <T> publishScopedApiResult(
    expected: ApiAccountScope,
    guarded: (ApiAccountScope, () -> Unit) -> Boolean,
    fetch: suspend () -> T,
    publish: (T) -> Unit,
): Boolean {
    val result = fetch()
    return guarded(expected) { publish(result) }
}
