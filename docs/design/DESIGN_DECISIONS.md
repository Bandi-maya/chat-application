# Design Decisions

Initial source review: 27 Oct 2026, 01:43 IST. Build launch update: 01:49; runtime compile failure update: 01:54. Source review and unit results are not emulator acceptance.

Required: incremental existing-project improvement; no placeholders replacing functions; no broad architecture rewrite; free-only; preserved backend/crypto; no all-bug-free claim.

Proposed: shared token/primitives contract, state-driven Android call capsule and systematic settings round-trip matrix. Map-first route local patch unbuilt at initial cut. Call state/transport is separate from UI capsule. No global FLAG_SECURE or token migration without scoped decision/proof. No new paid relay. Shared-file changes require conflict review and regression checks.
