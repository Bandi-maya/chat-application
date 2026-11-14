package com.example.chat

import com.example.chat.data.api.InactiveAccountCatchUp
import org.junit.Assert.*
import org.junit.Test

class InactiveAccountCatchUpTest {
    private fun target(key: String = "b", expires: Long? = 100_000) =
        InactiveAccountCatchUp.Target(key, "hash$key", "https://example.invalid", "tenant", "private", expires, "Account", "user$key")
    private class Fixture(val targets: List<InactiveAccountCatchUp.Target>) {
        val memory = mutableMapOf<String,String>()
        val fetched = mutableListOf<String>()
        val alerted = mutableListOf<String>()
        val cleared = mutableListOf<String>()
        var valid = true
        var allowed = true
        var fingerprint: InactiveAccountCatchUp.Summary? = InactiveAccountCatchUp.Summary(1, 10, "digest")
        var fetchHook: () -> Unit = {}
        val runner = InactiveAccountCatchUp({ targets }, { valid }, { fetched += it.key; fetchHook(); fingerprint },
            { memory[it] }, { key, value -> memory[key] = value }, { alerted += it.key; allowed },
            { cleared += it.key }, now = { 10_000 })
    }
    @Test fun unchangedNudgesDoNotDuplicateAlerts() {
        val f=Fixture(listOf(target())); f.runner.run(); f.runner.run()
        assertEquals(listOf("b"), f.alerted)
    }
    @Test fun revokedAccountIsNotFetched() {
        val f=Fixture(listOf(target())); f.valid=false; f.runner.run(); assertTrue(f.fetched.isEmpty())
    }
    @Test fun removedOrSwitchedAccountDuringFetchCannotAlert() {
        val f=Fixture(listOf(target())); f.fetchHook={ f.valid=false }; f.runner.run(); assertTrue(f.alerted.isEmpty())
    }
    @Test fun expiredCredentialsAreNotUsed() {
        val f=Fixture(listOf(target(expires=14_000))); f.runner.run(); assertTrue(f.fetched.isEmpty())
    }
    @Test fun failedFetchDoesNotEraseRetryStateOrAlert() {
        val f=Fixture(listOf(target())); f.fingerprint=null; f.runner.run(); assertTrue(f.alerted.isEmpty()); assertTrue(f.memory.isEmpty())
    }
    @Test fun notificationsDeniedRemainRetryable() {
        val f=Fixture(listOf(target())); f.allowed=false; f.runner.run(); assertTrue(f.memory.isEmpty())
        f.allowed=true; f.runner.run(); assertEquals(2,f.alerted.size)
    }
    @Test fun noUnreadClearsOnlyThatAccountAlert() {
        val f=Fixture(listOf(target())); f.fingerprint=InactiveAccountCatchUp.Summary(0,0,""); f.runner.run(); assertEquals(listOf("b"),f.cleared); assertTrue(f.alerted.isEmpty())
    }
    @Test fun unreadDecreaseDoesNotRealert() {
        val f=Fixture(listOf(target())); f.fingerprint=InactiveAccountCatchUp.Summary(3,10,"first"); f.runner.run()
        f.fingerprint=InactiveAccountCatchUp.Summary(2,10,"second"); f.runner.run(); assertEquals(1,f.alerted.size)
    }
    @Test fun newMessageWithSameCountAndNewTimestampAlerts() {
        val f=Fixture(listOf(target())); f.runner.run()
        f.fingerprint=InactiveAccountCatchUp.Summary(1,20,"next"); f.runner.run(); assertEquals(2,f.alerted.size)
    }
    @Test fun duplicateRegistryEntriesAreFetchedOnce() {
        val f=Fixture(listOf(target(),target())); f.runner.run(); assertEquals(listOf("b"),f.fetched)
    }
    @Test fun boundedToThreeInactiveAccounts() {
        val f=Fixture((1..9).map { target(it.toString()) }); f.runner.run(); assertEquals(3,f.fetched.size)
    }
    @Test fun reentrantNudgeDoesNotStartSecondSweep() {
        val f=Fixture(listOf(target())); f.fetchHook={ f.runner.run() }; f.runner.run(); assertEquals(1,f.fetched.size)
    }
    @Test fun failedAccountDoesNotPreventOtherAccounts() {
        val a=target("a"); val b=target("b"); val seen=mutableListOf<String>()
        InactiveAccountCatchUp({listOf(a,b)},{true},{if(it===a) error("offline") else InactiveAccountCatchUp.Summary(1,10,"digest")},{null},{_,_->},{seen+=it.key;true},{},now={10_000}).run()
        assertEquals(listOf("b"),seen)
    }    @Test fun sweepStopsAdmittingWorkAfterBudget() {
        var elapsed = 0L
        val fetched = mutableListOf<String>()
        InactiveAccountCatchUp({listOf(target("a"),target("b"))},{true},{fetched+=it.key; elapsed=3_001; null},
            {null},{_,_->},{true},{},now={10_000},elapsed={elapsed}).run()
        assertEquals(listOf("a"),fetched)
    }

    @Test fun cancellationPropagatesAndReleasesSingleFlightGate() {
        val f = Fixture(listOf(target()))
        f.fetchHook = { throw java.util.concurrent.CancellationException("cancel") }
        try { f.runner.run(); fail("Cancellation was swallowed") }
        catch (_: java.util.concurrent.CancellationException) { }
        assertTrue(f.alerted.isEmpty())
        f.fetchHook = {}
        f.runner.run()
        assertEquals(listOf("b"), f.alerted)
    }

}
