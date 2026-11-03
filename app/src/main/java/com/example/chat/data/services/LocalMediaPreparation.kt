package com.example.chat.data.services

/** Initialization is transactional: partial media must not be reused as a complete stream. */
internal object LocalMediaPreparation {
    fun run(initialize: () -> Unit, rollback: () -> Unit) {
        try {
            initialize()
        } catch (failure: Throwable) {
            try { rollback() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }
}
