# AGENT.md — co.rsk.peg tests

Package-specific instructions for `rskj-core/src/test/java/co/rsk/peg`, the test
suite for the powpeg bridge. This extends the repo-root `/AGENT.md` and
`rskj-core/src/main/java/co/rsk/peg/AGENT.md` — read those first, in particular
the root's **Testing conventions** section (builders-first, no
`@ParameterizedTest`, the RSKIP-activation coverage shape, no method
overloading, arrange/act/assert commenting) — those rules apply here too and
aren't repeated below. This file adds package-specific detail: conventions and
shared fixtures **actually observed in these 163 test files**, plus a concrete
worked example of the root's RSKIP-activation rule against real peg classes.

## Assertions

JUnit 5 (`org.junit.jupiter.api.*`). Most assertions are plain
`Assertions.assert*` (`assertEquals`, `assertTrue`, `assertThrows`), but Hamcrest
matchers (`assertThat`/`Matchers.*`) show up in the same files for cases plain
JUnit assertions don't express well (e.g. `lessThanOrEqualTo`). No AssertJ. Use
`assertThrows` for exception cases, matching the rest of the package.

## Mocking

Follows the root's "avoid mocking as much as possible" rule already: Mockito
is used via **static factory calls** (`mock(Foo.class)`, `when(...)`,
`verify(...)`, `doReturn(...)`, `spy(...)`) — not `@Mock`/
`@ExtendWith(MockitoExtension.class)` (only a handful of files use the
annotation form) — but only for narrow boundary interfaces. The prevailing
approach, best exemplified by `BridgeSupportTest.java`, is to **build real
domain objects** (a real `Repository`, `BridgeStorageProvider`,
`FederationStorageProvider`, federation built via the builders below) and
mock only things like `BtcLockSenderProvider`, `BridgeEventLogger`, or a block
store factory. The `simples/` hand-rolled fakes (`SimpleBlockChain`,
`SimpleRskTransaction`) are used sparingly — prefer real objects + narrow
mocks over adding new hand-rolled fakes.

## Reuse these shared builders/utilities before writing new fixture code

Per the root's builders-first rule, these are the specific ones this package
already has — check here before adding a new one:

- **`PegTestUtils`** (this package) — static fixture helpers: `createHash()`,
  `createOpReturnScriptForRsk(...)`, `createRandomP2PKHBtcAddress(...)`,
  `createRandomBtcECKeys(int)`, `createFederation(BridgeConstants, String...)`,
  `createReleaseRequestQueueEntries(int)`.
- **`BridgeSupportTestUtil`** (this package) — custom domain assertions that
  check storage state and emitted events together:
  `assertLogReleaseBtc`, `assertPegoutWasAddedToPegoutsWaitingForConfirmations`,
  `assertFederatorSigning`, `assertTransactionWasProcessed`; plus BTC-chain/PMT
  builders (`createValidPmtForTransactions`, `mockChainOfStoredBlocks`,
  `recreateChainFromPmt`).
- **`BridgeEventsTestUtils`** (this package) — event-log topic/data helpers:
  `getEncodedTopics`, `getEncodedData`, `getLogsTopics`, `getLogsData`.
- **`federation.FederationTestUtils`** — federation factory functions:
  `getFederation(Integer... pks)`, `getFederationWithPrivateKeys(...)`,
  `getGenesisFederation(...)`, `getFederationMembers(int)`,
  `addSignatures(federation, signers, tx)`, `spendFromErpFed(...)`.
- **Federation builders** — `federation.P2shErpFederationBuilder`,
  `federation.P2shP2wshErpFederationBuilder`, `federation.PendingFederationBuilder`,
  `federation.StandardMultiSigFederationBuilder`. Genuine fluent builders:
  `builder()` → chained `withMembersBtcPublicKeys(...)`, `withErpPublicKeys(...)`,
  `withCreationTime(...)`, `withNetworkParameters(...)` → terminal `build()`.
  **Default to `P2shP2wshErpFederationBuilder` (segwit)** unless the test is
  specifically about legacy P2SH-only federation behavior — don't reach for
  `P2shErpFederationBuilder`/`StandardMultiSigFederationBuilder` as the default
  choice.
- **`bitcoin.BitcoinTestUtils`** — bitcoinj helpers: `getBtcEcKeyFromSeed`,
  `getBtcEcKeys(int)`, `createP2SHMultisigAddress(...)`,
  `signLegacyTransactionInputFromP2shMultiSig(...)`, `coinListOf(long...)`.
- **`co.rsk.test.builders.BridgeSupportBuilder`** and
  **`co.rsk.test.builders.FederationSupportBuilder`** (outside this package,
  shared repo-wide) — the standard way to construct a `BridgeSupport`/
  `FederationSupport` under test: `builder()` → chained `withBridgeConstants`,
  `withProvider`, `withRepository`, `withActivations`, `withFederationSupport`,
  `withEventLogger`, `withBtcBlockStoreFactory` → terminal `build()`.

## Constants and activation config

Use the real constants classes directly rather than mocking them —
`new BridgeRegTestConstants()`, `BridgeMainNetConstants.getInstance()`. For
activation state, use `org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest`
presets (`genesis()`, `all()`, `arrowhead600()`, `reed800()`, etc.) `.forBlock(0)`
— this is the dominant pattern; `mock(ActivationConfig.class)` is rare and not
preferred.

## Naming conventions

- **One scenario per test class is deliberate, not a code smell to fix.** Large
  production classes get split across many scenario-named test classes rather
  than one giant test class: `BridgeSupport` alone has ~12
  (`BridgeSupportRegisterBtcTransactionTest`, `BridgeSupportRejectedPeginTest`,
  `BridgeSupportReleaseBtcTest`, `BridgeSupportFlyoverTest`,
  `BridgeSupportSvpTest`, …) plus a catch-all `BridgeSupportTest`. When adding
  tests for a new scenario of an already heavily-tested class, prefer adding a
  new scenario-named class over growing an existing large one further.
- **Method naming** mixes plain camelCase (`getFeePerKb`) with
  `methodUnderTest_condition_shouldOutcome` (`registerBtcTransaction_whenBelowTheMinimum_shouldRejectPegin`).
  The latter is the more common style in newer, scenario-focused files — prefer
  it for new tests.

## Test structure

- `@BeforeEach` setup methods are near-universal.
- `@Nested` is already used in this package (~18 files) to group related
  scenarios within one class, occasionally with
  `@TestInstance(Lifecycle.PER_CLASS)` — the shape to follow for the root's
  "use `@Nested` for many variables/combinations" rule.
- `@ParameterizedTest` + `@MethodSource` does appear in a number of existing
  files (often to cover multiple activation states) — that's legacy; don't
  take its presence in older files as the pattern to follow (see root
  `AGENT.md`).

## Covering an RSKIP/activation-gated branch

Since production code branches heavily on `activations.isActive(RSKIPxxx)`,
here's the root's RSKIP-activation rule worked out against real peg classes —
default to `ActivationConfigsForTest.all()`, plus one dedicated test for the
specific RSKIP that changed behavior:

```java
@Test
void registerBtcTransaction_whenBelowMinimum_shouldRejectPegin() {
    // default: current behavior, all activations on
    ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0);
    BridgeSupport bridgeSupport = bridgeSupportBuilder.withActivations(activations).build();
    // act & assert against current behavior
}

@Test
void registerBtcTransaction_whenBelowMinimum_beforeRSKIP379_shouldKeepLegacyBehavior() {
    // one dedicated test locking in the pre-RSKIP379 behavior — nothing more
    ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);
    BridgeSupport bridgeSupport = bridgeSupportBuilder.withActivations(activations).build();
    // act & assert against the legacy expectation
}
```

So: write the bulk of a class's tests against `all()`, and only add a
dedicated pre-activation test for the specific RSKIP whose behavior actually
changed — not one test per historical activation preset, and not a
parameterized sweep.

## `*IT.java` classes in this unit-test tree

`BridgeIT.java` and `BridgeSupportIT.java` are heavier, integration-style tests
that still live under `src/test` (not `src/integrationTest`) — the `IT` suffix
here is only a naming convention marking a heavier test, **not** a signal that
it's skipped by `./gradlew test`. `BridgeIT` drives a `BlockChainBuilder`/`World`
DSL and executes real blocks end-to-end through the `Bridge` precompile;
`BridgeSupportIT` drives multi-step BTC blockchain/checkpoint scenarios with real
bitcoinj block stores. Expect these to be slower than a typical `*Test.java` and
follow their existing shape if you add another heavy end-to-end scenario, rather
than retrofitting one of the lighter `*Test.java` files to do the same job.

## Support directories

- **`simples/`** — hand-rolled fakes (`SimpleBlockChain`, `SimpleRskTransaction`),
  lightly used; not the dominant fixture style (see Mocking above).
- **`resources/`** — fixture constants (`TestConstants`).
- **`performance/`** — gas/timing benchmarks (`BridgePerformanceTest`, `Mean`,
  …), structurally separate from correctness tests; see the main package's
  `AGENT.md` for why the package-wide `--tests` filter also picks these up.
