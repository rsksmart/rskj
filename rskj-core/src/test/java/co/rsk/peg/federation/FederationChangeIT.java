package co.rsk.peg.federation;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.RskTestUtils.createRskBlock;
import static co.rsk.peg.BridgeEventsTestUtils.getEncodedData;
import static co.rsk.peg.BridgeEventsTestUtils.getEncodedTopics;
import static co.rsk.peg.BridgeSupportTestUtil.assertFederatorSigning;
import static co.rsk.peg.BridgeSupportTestUtil.assertPegoutTxSigHashWasSaved;
import static co.rsk.peg.BridgeSupportTestUtil.buildUpdateCollectionsTransaction;
import static co.rsk.peg.BridgeSupportTestUtil.createValidPmtForTransactions;
import static co.rsk.peg.BridgeSupportTestUtil.recreateChainFromPmt;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.createHash;
import static co.rsk.peg.bitcoin.UtxoUtils.extractOutpointValues;
import static co.rsk.peg.federation.FederationStorageIndexKey.NEW_FEDERATION_BTC_UTXOS_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcBlock;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.PartialMerkleTree;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.RedeemScriptParserFactory;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.bitcoinj.script.ScriptParser;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.BridgeEvents;
import co.rsk.peg.BridgeSerializationUtils;
import co.rsk.peg.BridgeStorageProvider;
import co.rsk.peg.BridgeSupport;
import co.rsk.peg.BridgeUtils;
import co.rsk.peg.BtcBlockStoreWithCache;
import co.rsk.peg.PegTestUtils;
import co.rsk.peg.PegUtils;
import co.rsk.peg.PegoutsWaitingForConfirmations;
import co.rsk.peg.PegoutsWaitingForConfirmations.Entry;
import co.rsk.peg.ReleaseTransactionAssertions;
import co.rsk.peg.RepositoryBtcBlockStoreWithCache;
import co.rsk.peg.bitcoin.BitcoinTestAssertions;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.bitcoin.UtxoUtils;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.lockingcap.LockingCapStorageProviderImpl;
import co.rsk.peg.lockingcap.LockingCapSupport;
import co.rsk.peg.lockingcap.LockingCapSupportImpl;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.peg.vote.ABICallSpec;
import co.rsk.rpc.modules.trace.CallType;
import co.rsk.rpc.modules.trace.ProgramSubtrace;
import co.rsk.rpc.modules.trace.TraceType;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import co.rsk.test.builders.PegoutTransactionBuilder;
import co.rsk.test.builders.UTXOBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.InternalTransaction;
import org.junit.jupiter.api.Test;

class FederationChangeIT {
    private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ActivationConfigsForTest.all().forBlock(0);
    private static final ActivationConfig.ForBlock VETIVER_ACTIVATIONS = ActivationConfigsForTest.vetiver900().forBlock(0);
    private static final RskAddress BRIDGE_ADDRESS = PrecompiledContracts.BRIDGE_ADDR;
    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final FederationConstants FEDERATION_CONSTANTS = BRIDGE_CONSTANTS.getFederationConstants();
    private static final NetworkParameters NETWORK_PARAMS = BRIDGE_CONSTANTS.getBtcParams();
    private static final List<BtcECKey> ORIGINAL_FEDERATION_MEMBERS_KEYS = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[] {
            "member01", "member02", "member03", "member04", "member05", "member06", "member07", "member08", "member09"
        },
        true
    );
    private static final List<FederationMember> ORIGINAL_FEDERATION_MEMBERS = FederationTestUtils.getFederationMembersWithBtcKeys(ORIGINAL_FEDERATION_MEMBERS_KEYS);
    private static final List<BtcECKey> ORIGINAL_SEGWIT_FEDERATION_MEMBERS_KEYS = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[] {
            "segwitMember01", "segwitMember02", "segwitMember03", "segwitMember04", "segwitMember05",
            "segwitMember06", "segwitMember07", "segwitMember08", "segwitMember09", "segwitMember10",
            "segwitMember11", "segwitMember12", "segwitMember13", "segwitMember14", "segwitMember15",
            "segwitMember16", "segwitMember17", "segwitMember18", "segwitMember19", "segwitMember20"
        },
        true
    );
    private static final List<FederationMember> ORIGINAL_SEGWIT_FEDERATION_MEMBERS = FederationTestUtils.getFederationMembersWithBtcKeys(ORIGINAL_SEGWIT_FEDERATION_MEMBERS_KEYS);
    private static final List<BtcECKey> NEW_FEDERATION_MEMBERS_KEYS = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[]{
            "member01", "member02", "member03", "member04", "member05", "member06", "member07", "member08", "member09",
            "newMember10", "newMember11", "newMember12", "newMember13", "newMember14", "newMember15", "newMember16",
            "newMember17", "newMember18", "newMember19", "newMember20"
        },
        true
    );
    private static final int LEGACY_PEGIN_PROTOCOL_VERSION = 0;
    private static final int PEGIN_V1_PROTOCOL_VERSION = 1;
    private static final int NEW_FEDERATION_MEMBERS_SIZE = NEW_FEDERATION_MEMBERS_KEYS.size();
    private static final int NEW_FEDERATION_THRESHOLD = NEW_FEDERATION_MEMBERS_SIZE / 2 + 1;
    private static final List<FederationMember> NEW_FEDERATION_MEMBERS = FederationTestUtils.getFederationMembersWithBtcKeys(NEW_FEDERATION_MEMBERS_KEYS);
    private static final SignatureCache SIGNATURE_CACHE = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
    private static final Transaction UPDATE_COLLECTIONS_TX = buildUpdateCollectionsTransaction();
    private static final Transaction FIRST_AUTHORIZED_TX = TransactionUtils.getTransactionFromCaller(
        SIGNATURE_CACHE,
        FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress()
    );
    private static final Transaction SECOND_AUTHORIZED_TX = TransactionUtils.getTransactionFromCaller(
        SIGNATURE_CACHE,
        FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress()
    );
    private static final Transaction UNAUTHORIZED_TX = TransactionUtils.getTransactionFromCaller(
        SIGNATURE_CACHE,
        FederationChangeCaller.UNAUTHORIZED.getRskAddress()
    );

    private static final Transaction REGISTRATION_TX = mock(Transaction.class);
    private static final RskAddress LBC_ADDRESS = RskTestUtils.generateAddress("lbc");
    private static final Keccak256 DERIVATION_ARGUMENTS_HASH = RskTestUtils.createHash(0);
    private static final Address LIQUIDITY_PROVIDER_BTC_ADDRESS = BitcoinTestUtils.createP2PKHAddress(
        NETWORK_PARAMS,
        "liqProvider"
    );
    private static final Transaction FLYOVER_REGISTRATION_TX = new InternalTransaction(
        Keccak256.ZERO_HASH.getBytes(),
        0,
        0,
        null,
        null,
        null,
        LBC_ADDRESS.getBytes(),
        null,
        null,
        null,
        null,
        null
    );
    private Address userRefundBtcAddress;

    private ActivationConfig.ForBlock activations;
    private Repository repository;
    private BridgeStorageProvider bridgeStorageProvider;
    private BtcBlockStoreWithCache.Factory btcBlockStoreFactory;
    private BtcBlockStoreWithCache btcBlockStore;
    private BtcLockSenderProvider btcLockSenderProvider;
    private PeginInstructionsProvider peginInstructionsProvider;
    private List<LogInfo> logs;
    private BridgeEventLogger bridgeEventLogger;
    private FeePerKbSupport feePerKbSupport;
    private Block currentBlock;
    private StorageAccessor bridgeStorageAccessor;
    private FederationStorageProvider federationStorageProvider;
    private FederationSupport federationSupport;
    private LockingCapSupport lockingCapSupport;
    private BridgeSupport bridgeSupport;
    private PartialMerkleTree pmtWithTransactions;
    private int btcBlockWithPmtHeight;
    private int nextUtxoHashSeed = 1000;

    @Test
    void changeFederation_withVetiverActivations_fromP2shErpToSegwit() throws Exception {
        // Arrange
        setUp(VETIVER_ACTIVATIONS);

        // Create a default original federation using the list of UTXOs
        var originalFederation = createOriginalP2shErpFederation();
        var originalUTXOs = federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMS, activations);

        // Act & Assert
        assertPeginsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender0");
        assertPegoutsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender0");
        // Create pending federation using the new federation keys
        voteToCreateEmptyPendingFederation();
        voteToAddFederatorPublicKeysToPendingFederation();

        var pendingFederation = federationStorageProvider.getPendingFederation();
        assertPendingFederationIsBuiltAsExpected(pendingFederation);

        voteToCommitPendingFederation(originalFederation);
        var newFederationOpt = federationSupport.getProposedFederation();
        assertTrue(newFederationOpt.isPresent());
        var newFederation = newFederationOpt.get();
        var expectedProposedFederation = createExpectedProposedFederation();
        assertEquals(expectedProposedFederation, newFederation);

        assertPeginsShouldNotWorkToFed(newFederation, "sender1");

        // Proceed with SVP process
        callUpdateCollectionsAndAssertSvpFundTxIsCreated();
        registerSignedSvpFundTx(ORIGINAL_FEDERATION_MEMBERS_KEYS);

        assertPeginsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender2");
        assertPegoutsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender2");
        assertPeginsShouldNotWorkToFed(newFederation, "sender3");
        assertPegoutsShouldNotWorkToFed(newFederation, "sender3");

        callUpdateCollectionsAndAssertSvpSpendTxIsCreated();
        addSignaturesToAndRegisterSvpSpendTx();

        // Validations post commit
        assertLastRetiredFederationP2SHScriptMatchesWithOriginalFederation(originalFederation);
        assertUTXOsReferenceMovedFromNewToOldFederation(originalUTXOs);
        assertNewAndOldFederationsReferences(newFederation, originalFederation);
        assertNextFederationCreationBlockHeight(newFederation.getCreationBlockNumber());

        assertPeginsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender4");
        assertPegoutsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender4");
        assertPeginsShouldNotWorkToFed(newFederation, "sender5");
        assertPegoutsShouldNotWorkToFed(newFederation, "sender5");

        // Move blockchain until the activation phase
        activateNewFederation();
        assertActiveAndRetiringFederationsHaveExpectedAddress(newFederation.getAddress(), originalFederation.getAddress());
        assertMigrationHasNotStarted();

        assertPeginsShouldWorkToFed(originalFederation, federationSupport.getRetiringFederationBtcUTXOs(), "sender6");
        assertPegoutsShouldWorkToFed(originalFederation, federationSupport.getRetiringFederationBtcUTXOs(), "sender6");
        assertPeginsShouldWorkToFed(newFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender7");
        assertPegoutsShouldWorkToFed(newFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender7");

        // Move blockchain until the migration phase
        activateMigration();

        // Calling update collections should start migration. Only one pegout entry has ever existed at
        // this point, so these single-entry assertions hold regardless of how many further legacy-capped
        // rounds are needed below to fully drain the retiring federation.
        Set<Entry> pegoutEntriesBeforeRound1 = getPegoutEntriesSnapshot();
        int logsSizeBeforeRound1 = logs.size();
        callUpdateCollections();
        assertMigrationHasStarted();
        assertPegoutTxSigHashesAreSaved();
        verifyPegouts();
        assertMigrationRoundWasSettledAsExpected(pegoutEntriesBeforeRound1, logsSizeBeforeRound1, newFederation.getAddress());

        // Check again live federations references are as expected
        assertNewAndOldFederationsReferences(newFederation, originalFederation);
        assertActiveAndRetiringFederationsHaveExpectedAddress(newFederation.getAddress(), originalFederation.getAddress());

        assertPeginsShouldWorkToFed(originalFederation, federationSupport.getRetiringFederationBtcUTXOs(), "sender8");
        assertPegoutsShouldWorkToFed(originalFederation, federationSupport.getRetiringFederationBtcUTXOs(), "sender8");
        assertPeginsShouldWorkToFed(newFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender9");
        assertPegoutsShouldWorkToFed(newFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender9");

        // Under pre-RSKIP455 activations, the retiring federation wallet is capped at
        // legacyMaxInputsPerMigrationTransaction UTXOs per migration tx, so draining the rest can take
        // several more rounds; each needs its own rskTxHash (see callUpdateCollections(Transaction)).
        int maxDrainingRounds = 10;
        int drainingRounds = 0;
        while (!federationSupport.getRetiringFederationBtcUTXOs().isEmpty() && drainingRounds < maxDrainingRounds) {
            Set<Entry> pegoutEntriesBeforeRound = getPegoutEntriesSnapshot();
            int logsSizeBeforeRound = logs.size();
            callUpdateCollections(buildUpdateCollectionsTransaction(100 + drainingRounds));
            assertMigrationRoundWasSettledAsExpected(pegoutEntriesBeforeRound, logsSizeBeforeRound, newFederation.getAddress());
            drainingRounds++;
        }
        assertTrue(federationSupport.getRetiringFederationBtcUTXOs().isEmpty());
        assertPegoutTxSigHashesAreSaved();
        verifyPegouts();

        // Move blockchain until the end of the migration phase: even with pre-RSKIP455 activations,
        // the retiring federation is fully drained above, so it clears with no funds left behind.
        long migrationCreationRskBlockNumber = currentBlock.getNumber();
        int logsSizeBeforeEndMigration = logs.size();
        endMigration();
        assertPegoutConfirmedEventWasEmitted(logsSizeBeforeEndMigration, migrationCreationRskBlockNumber);

        assertOnlyActiveFedIsLive(newFederation);
        assertPeginsShouldNotWorkToFed(originalFederation, "sender10");
        assertPegoutsShouldNotWorkToFed(originalFederation, "sender10");
        assertPeginsShouldWorkToFed(newFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender11");
        assertPegoutsShouldWorkToFed(newFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender11");
    }

    @Test
    void changeFederation_withAllActivations_fromSegwitToSegwit_splitMigrationOutputs() throws Exception {
        // Arrange
        setUp(ALL_ACTIVATIONS);

        // Both the retiring and the new federation are segwit, with 20 members each
        var originalFederation = createOriginalSegwitFederation();
        var originalUTXOs = federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMS, activations);

        // Act & Assert
        assertPeginsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender0");
        assertPegoutsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender0");
        // Create pending federation using the new federation keys
        voteToCreateEmptyPendingFederation();
        voteToAddFederatorPublicKeysToPendingFederation();

        var pendingFederation = federationStorageProvider.getPendingFederation();
        assertPendingFederationIsBuiltAsExpected(pendingFederation);

        voteToCommitPendingFederation(originalFederation);
        var newFederationOpt = federationSupport.getProposedFederation();
        assertTrue(newFederationOpt.isPresent());
        var newFederation = newFederationOpt.get();
        var expectedProposedFederation = createExpectedProposedFederation();
        assertEquals(expectedProposedFederation, newFederation);

        assertPeginsShouldNotWorkToFed(newFederation, "sender1");

        // Proceed with SVP process
        callUpdateCollectionsAndAssertSvpFundTxIsCreated();
        registerSignedSvpFundTx(ORIGINAL_SEGWIT_FEDERATION_MEMBERS_KEYS);

        assertPeginsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender2");
        assertPegoutsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender2");
        assertPeginsShouldNotWorkToFed(newFederation, "sender3");
        assertPegoutsShouldNotWorkToFed(newFederation, "sender3");

        callUpdateCollectionsAndAssertSvpSpendTxIsCreated();
        addSignaturesToAndRegisterSvpSpendTx();

        // Validations post commit
        assertLastRetiredFederationP2SHScriptMatchesWithOriginalFederation(originalFederation);
        assertUTXOsReferenceMovedFromNewToOldFederation(originalUTXOs);
        assertNewAndOldFederationsReferences(newFederation, originalFederation);
        assertNextFederationCreationBlockHeight(newFederation.getCreationBlockNumber());

        assertPeginsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender4");
        assertPegoutsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender4");
        assertPeginsShouldNotWorkToFed(newFederation, "sender5");
        assertPegoutsShouldNotWorkToFed(newFederation, "sender5");

        // Move blockchain until the activation phase
        activateNewFederation();
        assertActiveAndRetiringFederationsHaveExpectedAddress(newFederation.getAddress(), originalFederation.getAddress());
        assertMigrationHasNotStarted();

        assertPeginsShouldWorkToFed(originalFederation, federationSupport.getRetiringFederationBtcUTXOs(), "sender6");
        assertPegoutsShouldWorkToFed(originalFederation, federationSupport.getRetiringFederationBtcUTXOs(), "sender6");
        assertPeginsShouldWorkToFed(newFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender7");
        assertPegoutsShouldWorkToFed(newFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender7");

        // Move blockchain until the migration phase
        activateMigration();

        // Round 1: the retiring federation is realistically sized (50 UTXOs plus the pegin/pegout
        // activity above), so its balance is already well past the single-output threshold. This
        // naturally exercises RSKIP455's fixed-value multiple-outputs bucket ([40, 1000) BTC on mainnet).
        Set<Entry> pegoutEntriesBeforeRound1 = getPegoutEntriesSnapshot();
        int logsSizeBeforeRound1 = logs.size();
        callUpdateCollections();
        assertMigrationHasStarted();
        assertPegoutTxSigHashesAreSaved();
        verifyPegouts();
        assertMigrationRoundWasSettledAsExpected(pegoutEntriesBeforeRound1, logsSizeBeforeRound1, newFederation.getAddress());

        // Check again live federations references are as expected
        assertNewAndOldFederationsReferences(newFederation, originalFederation);
        assertActiveAndRetiringFederationsHaveExpectedAddress(newFederation.getAddress(), originalFederation.getAddress());

        assertPeginsShouldWorkToFed(originalFederation, federationSupport.getRetiringFederationBtcUTXOs(), "sender8");
        assertPegoutsShouldWorkToFed(originalFederation, federationSupport.getRetiringFederationBtcUTXOs(), "sender8");
        assertPeginsShouldWorkToFed(newFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender9");
        assertPegoutsShouldWorkToFed(newFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender9");

        // Round 2: top up the retiring federation past the large multiple-outputs threshold
        // (1000 BTC on mainnet) to force a migration transaction with evenly distributed outputs.
        // This round needs its own rskTxHash since round 1's entry hasn't been confirmed away yet
        // (see PegoutsWaitingForSignatures uniqueness check).
        Set<Entry> pegoutEntriesBeforeRound2 = getPegoutEntriesSnapshot();
        int logsSizeBeforeRound2 = logs.size();
        injectUtxoToRetiringFederation(originalFederation.getAddress(), Coin.COIN.multiply(1105));
        callUpdateCollections(buildUpdateCollectionsTransaction(300));
        assertPegoutTxSigHashesAreSaved();
        verifyPegouts();
        assertMigrationRoundWasSettledAsExpected(pegoutEntriesBeforeRound2, logsSizeBeforeRound2, newFederation.getAddress());
        assertTrue(federationSupport.getRetiringFederationBtcUTXOs().isEmpty());

        // Round 3: top up the (now empty) retiring federation with a small balance, comfortably
        // under the single-output threshold (40 BTC on mainnet), to exercise RSKIP455's single-output
        // bucket while all activations are active - as opposed to the Vetiver test's single output,
        // which is single simply because pre-RSKIP455 migrations are always single-output regardless
        // of value, not because of this threshold check.
        Set<Entry> pegoutEntriesBeforeRound3 = getPegoutEntriesSnapshot();
        int logsSizeBeforeRound3 = logs.size();
        injectUtxoToRetiringFederation(originalFederation.getAddress(), Coin.COIN.multiply(30));
        callUpdateCollections(buildUpdateCollectionsTransaction(400));
        assertPegoutTxSigHashesAreSaved();
        verifyPegouts();
        assertMigrationRoundWasSettledAsExpected(pegoutEntriesBeforeRound3, logsSizeBeforeRound3, newFederation.getAddress());
        assertTrue(federationSupport.getRetiringFederationBtcUTXOs().isEmpty());

        // Move blockchain until the end of the migration phase
        long migrationCreationRskBlockNumber = currentBlock.getNumber();
        int logsSizeBeforeEndMigration = logs.size();
        endMigration();
        assertPegoutConfirmedEventWasEmitted(logsSizeBeforeEndMigration, migrationCreationRskBlockNumber);

        assertOnlyActiveFedIsLive(newFederation);
        assertPeginsShouldNotWorkToFed(originalFederation, "sender10");
        assertPegoutsShouldNotWorkToFed(originalFederation, "sender10");
        assertPeginsShouldWorkToFed(newFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender11");
        assertPegoutsShouldWorkToFed(newFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender11");
    }

    @Test
    void rollbackPendingFederation_afterPartiallyBuilt_wipesStateAndAllowsNewFederationChangeToProceed() throws Exception {
        // Arrange
        setUp(ALL_ACTIVATIONS);
        var originalFederation = createOriginalSegwitFederation();

        assertPeginsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender0");
        assertPegoutsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender0");

        // Start a federation change but only partially build the pending federation
        voteToCreateEmptyPendingFederation();
        var firstNewMember = NEW_FEDERATION_MEMBERS.get(0);
        voteToAddFederatorPublicKeysToPendingFederation(
            firstNewMember.getBtcPublicKey(),
            firstNewMember.getRskPublicKey(),
            firstNewMember.getMstPublicKey()
        );
        assertEquals(1, federationSupport.getPendingFederationSize());

        // Act: roll back before the pending federation is complete
        voteToRollbackPendingFederation();

        // Assert: the aborted federation change left no trace behind
        assertFalse(federationSupport.getProposedFederation().isPresent());
        assertEquals(originalFederation, federationSupport.getActiveFederation());
        assertPeginsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender1");
        assertPegoutsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender1");

        // A brand-new federation change should proceed normally from here, unaffected by the rolled-back
        // attempt or the vote it partially cast for the first new member
        voteToCreateEmptyPendingFederation();
        voteToAddFederatorPublicKeysToPendingFederation();

        var pendingFederation = federationStorageProvider.getPendingFederation();
        assertPendingFederationIsBuiltAsExpected(pendingFederation);

        voteToCommitPendingFederation(originalFederation);
        var newFederationOpt = federationSupport.getProposedFederation();
        assertTrue(newFederationOpt.isPresent());
        var expectedProposedFederation = createExpectedProposedFederation();
        assertEquals(expectedProposedFederation, newFederationOpt.get());
    }

    @Test
    void svpValidationPeriodExpiresWithoutSuccessfulSpendTx_processesSvpFailureAndAllowsFederationElectionAgain() throws Exception {
        // Arrange
        setUp(VETIVER_ACTIVATIONS);
        var originalFederation = createOriginalP2shErpFederation();

        assertPeginsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender0");
        assertPegoutsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender0");

        // Commit a pending federation, entering the SVP process
        voteToCreateEmptyPendingFederation();
        voteToAddFederatorPublicKeysToPendingFederation();
        voteToCommitPendingFederation(originalFederation);

        var proposedFederationOpt = federationSupport.getProposedFederation();
        assertTrue(proposedFederationOpt.isPresent());
        var proposedFederation = proposedFederationOpt.get();

        // The svp fund tx gets created, but is never signed and registered by the federators
        callUpdateCollectionsAndAssertSvpFundTxIsCreated();

        // Act: move the blockchain past the svp validation period without the spend tx ever completing
        advanceBlockchainPastSvpValidationPeriod(proposedFederation);

        int logsSizeBeforeSvpFailure = logs.size();
        callUpdateCollections();

        // Assert: the svp failure was processed and the federation election was allowed again
        assertCommitFederationFailedEventWasEmitted(logsSizeBeforeSvpFailure, proposedFederation, currentBlock.getNumber());

        assertFalse(federationSupport.getProposedFederation().isPresent());
        assertFalse(bridgeStorageProvider.getSvpFundTxHashUnsigned().isPresent());
        assertFalse(bridgeStorageProvider.getSvpFundTxSigned().isPresent());
        assertFalse(bridgeStorageProvider.getSvpSpendTxHashUnsigned().isPresent());
        assertFalse(bridgeStorageProvider.getSvpSpendTxWaitingForSignatures().isPresent());

        // The original federation remains the only live one; the failed proposal never got to activate
        assertEquals(originalFederation, bridgeSupport.getActiveFederation());
        assertTrue(bridgeSupport.getRetiringFederationAddress().isEmpty());
        assertPeginsShouldNotWorkToFed(proposedFederation, "sender1");
        assertPegoutsShouldNotWorkToFed(proposedFederation, "sender1");
        assertPeginsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender2");
        assertPegoutsShouldWorkToFed(originalFederation, federationSupport.getActiveFederationBtcUTXOs(), "sender2");

        // A brand-new federation change should now be able to proceed from scratch
        voteToCreateEmptyPendingFederation();
        voteToAddFederatorPublicKeysToPendingFederation();

        var pendingFederation = federationStorageProvider.getPendingFederation();
        assertPendingFederationIsBuiltAsExpected(pendingFederation);

        voteToCommitPendingFederation(originalFederation);
        var newFederationOpt = federationSupport.getProposedFederation();
        assertTrue(newFederationOpt.isPresent());
        var expectedProposedFederation = createExpectedProposedFederation();
        assertEquals(expectedProposedFederation, newFederationOpt.get());
    }

    private void setUp(ActivationConfig.ForBlock activations) throws Exception {
        this.activations = activations;
        repository = createRepository();
        repository.addBalance(BRIDGE_ADDRESS, co.rsk.core.Coin.fromBitcoin(BRIDGE_CONSTANTS.getMaxRbtc()));

        bridgeStorageProvider =
            new BridgeStorageProvider(repository, NETWORK_PARAMS, activations);

        btcBlockStoreFactory =
            new RepositoryBtcBlockStoreWithCache.Factory(NETWORK_PARAMS, 100, 100);
        btcBlockStore =
            btcBlockStoreFactory.newInstance(repository, BRIDGE_CONSTANTS, bridgeStorageProvider, activations);
        // Setting a chain head different from genesis to avoid having to read the checkpoints file
        addNewBtcBlockOnTipOfChain(btcBlockStore);
        repository.save();

        peginInstructionsProvider = new PeginInstructionsProvider();
        btcLockSenderProvider = new BtcLockSenderProvider();

        logs = new ArrayList<>();
        bridgeEventLogger = new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            activations,
            logs
        );

        bridgeStorageAccessor = new InMemoryStorage();

        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);

        var blockNumber = 0L;
        currentBlock = createRskBlock(blockNumber);

        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(FEDERATION_CONSTANTS)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(currentBlock)
            .withActivations(activations)
            .build();

        var lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        lockingCapSupport = new LockingCapSupportImpl(
            lockingCapStorageProvider,
            activations,
            BRIDGE_CONSTANTS.getLockingCapConstants(),
            SIGNATURE_CACHE);

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.SATOSHI);

        bridgeSupport = BridgeSupportBuilder.builder()
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(currentBlock)
            .withActivations(activations)
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withLockingCapSupport(lockingCapSupport)
            .build();
    }

    private Federation createOriginalP2shErpFederation() {
        return createOriginalFederationWith50Utxos(ORIGINAL_FEDERATION_MEMBERS, false);
    }

    private Federation createOriginalSegwitFederation() {
        return createOriginalFederationWith50Utxos(ORIGINAL_SEGWIT_FEDERATION_MEMBERS, true);
    }

    private Federation createOriginalFederationWith50Utxos(List<FederationMember> members, boolean segwit) {
        var originalFederationArgs = new FederationArgs(members, Instant.EPOCH, 0, NETWORK_PARAMS);
        var erpPubKeys = FEDERATION_CONSTANTS.getErpFedPubKeysList();
        var activationDelay = FEDERATION_CONSTANTS.getErpFedActivationDelay();

        Federation originalFederation = segwit
            ? FederationFactory.buildP2shP2wshErpFederation(originalFederationArgs, erpPubKeys, activationDelay)
            : FederationFactory.buildP2shErpFederation(originalFederationArgs, erpPubKeys, activationDelay);
        // Set original federation
        federationStorageProvider.setNewFederation(originalFederation);

        // Set new UTXOs
        int numberOfUtxos = 50;
        Script outputScript = ScriptBuilder.createOutputScript(originalFederation.getAddress());
        List<UTXO> originalUTXOs = UTXOBuilder.builder()
            .withScriptPubKey(outputScript)
            .withValue(Coin.COIN)
            .buildMany(numberOfUtxos, i -> createHash(i + 1));

        bridgeStorageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), originalUTXOs, BridgeSerializationUtils::serializeUTXOList);

        return originalFederation;
    }

    private UTXO buildUtxo(Address address, Coin value, int hashSeed) {
        Script outputScript = ScriptBuilder.createOutputScript(address);
        return UTXOBuilder.builder()
            .withTransactionHash(createHash(hashSeed))
            .withScriptPubKey(outputScript)
            .withValue(value)
            .build();
    }

    private void injectUtxoToRetiringFederation(Address retiringFederationAddress, Coin value) {
        UTXO utxo = buildUtxo(retiringFederationAddress, value, nextUtxoHashSeed++);
        federationStorageProvider.getOldFederationBtcUTXOs().add(utxo);
    }

    private Set<Entry> getPegoutEntriesSnapshot() throws IOException {
        return new HashSet<>(bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(activations));
    }

    private Federation createExpectedProposedFederation() {
        var expectedFederationArgs = new FederationArgs(
            NEW_FEDERATION_MEMBERS,
            Instant.EPOCH,
            0,
            NETWORK_PARAMS
        );
        var erpPubKeys = FEDERATION_CONSTANTS.getErpFedPubKeysList();
        var activationDelay = FEDERATION_CONSTANTS.getErpFedActivationDelay();

        return FederationFactory.buildP2shP2wshErpFederation(expectedFederationArgs, erpPubKeys, activationDelay);
    }

    private int voteToCreatePendingFederation(Transaction tx) {
        var createFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.CREATE.getKey(), new byte[][]{});
        return federationSupport.voteFederationChange(tx, createFederationAbiCallSpec, SIGNATURE_CACHE, bridgeEventLogger);
    }

    private int voteToAddFederatorPublicKeysToPendingFederation(Transaction tx, BtcECKey btcPublicKey, ECKey rskPublicKey, ECKey mstPublicKey) {
        ABICallSpec addFederatorAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(),
            new byte[][]{ btcPublicKey.getPubKey(), rskPublicKey.getPubKey(), mstPublicKey.getPubKey() }
        );

        return federationSupport.voteFederationChange(tx, addFederatorAbiCallSpec, SIGNATURE_CACHE, bridgeEventLogger);
    }

    private int voteCommitPendingFederationWithHash(Transaction tx, Keccak256 pendingFederationHash) {
        var commitFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.COMMIT.getKey(), new byte[][]{ pendingFederationHash.getBytes() });

        return federationSupport.voteFederationChange(tx, commitFederationAbiCallSpec, SIGNATURE_CACHE, bridgeEventLogger);
    }

    private int voteCommitPendingFederation(Transaction tx) {
        var pendingFederationHash = federationSupport.getPendingFederationHash();
        return voteCommitPendingFederationWithHash(tx, pendingFederationHash);
    }

    private void voteToCreateEmptyPendingFederation() {
        // Voting for any other federation change function must fail while no pending federation exists yet
        assertFederationNonExistentIsReturnedWhenNoPendingFederationExists();

        // An unauthorized caller must not be able to kick off a federation change
        var resultFromUnauthorizedCaller = voteToCreatePendingFederation(UNAUTHORIZED_TX);
        assertEquals(FederationChangeResponseCode.UNAUTHORIZED_CALLER.getCode(), resultFromUnauthorizedCaller);
        assertNull(federationSupport.getPendingFederationHash());

        // A single authorized vote does not reach quorum, so it must not create the pending federation yet
        var resultFromFirstAuthorizer = voteToCreatePendingFederation(FIRST_AUTHORIZED_TX);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromFirstAuthorizer);
        assertNull(federationSupport.getPendingFederationHash());

        // Voting with enough authorizers to create the pending federation
        var resultFromSecondAuthorizer = voteToCreatePendingFederation(SECOND_AUTHORIZED_TX);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromSecondAuthorizer);

        assertEquals(0, federationSupport.getPendingFederationSize());
        assertNotNull(federationSupport.getPendingFederationHash());

        // The pending federation is still empty, well below the minimum member requirement, so it can't be committed yet
        var resultFromCommittingIncompletePendingFederation = voteCommitPendingFederation(FIRST_AUTHORIZED_TX);
        assertEquals(FederationChangeResponseCode.INSUFFICIENT_MEMBERS.getCode(), resultFromCommittingIncompletePendingFederation);
        assertFalse(federationSupport.getProposedFederation().isPresent());
        assertEquals(0, federationSupport.getPendingFederationSize());
    }

    private void assertFederationNonExistentIsReturnedWhenNoPendingFederationExists() {
        var resultFromAddVote = voteToAddFederatorPublicKeysToPendingFederation(
            FIRST_AUTHORIZED_TX, new BtcECKey(), new ECKey(), new ECKey()
        );
        assertEquals(FederationChangeResponseCode.FEDERATION_NON_EXISTENT.getCode(), resultFromAddVote);

        var resultFromCommitVote = voteCommitPendingFederationWithHash(FIRST_AUTHORIZED_TX, RskTestUtils.createHash(1));
        assertEquals(FederationChangeResponseCode.FEDERATION_NON_EXISTENT.getCode(), resultFromCommitVote);

        var resultFromRollbackVote = voteRollbackPendingFederation(FIRST_AUTHORIZED_TX);
        assertEquals(FederationChangeResponseCode.FEDERATION_NON_EXISTENT.getCode(), resultFromRollbackVote);
    }

    private void voteToAddFederatorPublicKeysToPendingFederation(BtcECKey btcPublicKey, ECKey rskPublicKey, ECKey mstPublicKey) {
        int resultFromFirstAuthorizer = voteToAddFederatorPublicKeysToPendingFederation(FIRST_AUTHORIZED_TX, btcPublicKey, rskPublicKey, mstPublicKey);
        int resultFromSecondAuthorizer = voteToAddFederatorPublicKeysToPendingFederation(SECOND_AUTHORIZED_TX, btcPublicKey, rskPublicKey, mstPublicKey);

        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromFirstAuthorizer);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromSecondAuthorizer);
    }

    private void voteToAddFederatorPublicKeysToPendingFederation() {
        var expectedPendingFederationSize = 0;

        for (FederationMember member : NEW_FEDERATION_MEMBERS) {
            var memberBtcKey = member.getBtcPublicKey();
            var memberRskKey = member.getRskPublicKey();
            var memberMstKey = member.getMstPublicKey();

            voteToAddFederatorPublicKeysToPendingFederation(memberBtcKey, memberRskKey, memberMstKey);

            assertEquals(++expectedPendingFederationSize, federationSupport.getPendingFederationSize());
            assertTrue(federationStorageProvider.getPendingFederation().getMembers().contains(member));
        }

        // A vote is rejected if any single key - BTC, RSK, or MST - already belongs to a member of the
        // pending federation, even when the other two keys of the vote are fresh
        var alreadyAddedMember = NEW_FEDERATION_MEMBERS.get(0);
        assertAddingFederatorWithDuplicateKeyIsRejected(
            alreadyAddedMember.getBtcPublicKey(), new ECKey(), new ECKey(), expectedPendingFederationSize
        );
        assertAddingFederatorWithDuplicateKeyIsRejected(
            new BtcECKey(), alreadyAddedMember.getRskPublicKey(), new ECKey(), expectedPendingFederationSize
        );
        assertAddingFederatorWithDuplicateKeyIsRejected(
            new BtcECKey(), new ECKey(), alreadyAddedMember.getMstPublicKey(), expectedPendingFederationSize
        );
        // Voting again with the exact same triple - i.e. re-adding the same federator - must also be rejected
        assertAddingFederatorWithDuplicateKeyIsRejected(
            alreadyAddedMember.getBtcPublicKey(),
            alreadyAddedMember.getRskPublicKey(),
            alreadyAddedMember.getMstPublicKey(),
            expectedPendingFederationSize
        );
    }

    private void assertAddingFederatorWithDuplicateKeyIsRejected(BtcECKey btcKey, ECKey rskKey, ECKey mstKey, int expectedPendingFederationSize) {
        var result = voteToAddFederatorPublicKeysToPendingFederation(FIRST_AUTHORIZED_TX, btcKey, rskKey, mstKey);
        assertEquals(FederationChangeResponseCode.FEDERATOR_ALREADY_PRESENT.getCode(), result);
        assertEquals(expectedPendingFederationSize, federationSupport.getPendingFederationSize());
    }

    private int voteRollbackPendingFederation(Transaction tx) {
        var rollbackFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.ROLLBACK.getKey(), new byte[][]{});
        return federationSupport.voteFederationChange(tx, rollbackFederationAbiCallSpec, SIGNATURE_CACHE, bridgeEventLogger);
    }

    private void voteToRollbackPendingFederation() {
        var resultFromFirstAuthorizer = voteRollbackPendingFederation(FIRST_AUTHORIZED_TX);
        var resultFromSecondAuthorizer = voteRollbackPendingFederation(SECOND_AUTHORIZED_TX);

        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromFirstAuthorizer);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromSecondAuthorizer);

        assertNull(federationStorageProvider.getPendingFederation());
        assertNull(federationSupport.getPendingFederationHash());
        assertEquals(FederationChangeResponseCode.FEDERATION_NON_EXISTENT.getCode(), federationSupport.getPendingFederationSize());
    }

    private void voteToCommitPendingFederation(Federation activeFederationBeforeCommit) {
        // Pending Federation should exist
        var pendingFederation = federationStorageProvider.getPendingFederation();
        assertNotNull(pendingFederation);

        // An unauthorized caller must not be able to commit the pending federation
        var resultFromUnauthorizedCaller = voteCommitPendingFederationWithHash(UNAUTHORIZED_TX, pendingFederation.getHash());
        assertEquals(FederationChangeResponseCode.UNAUTHORIZED_CALLER.getCode(), resultFromUnauthorizedCaller);
        assertFalse(federationSupport.getProposedFederation().isPresent());

        // Committing with a hash that doesn't match the current pending federation must be rejected
        var mismatchedHash = RskTestUtils.createHash(9999);
        var resultFromMismatchedHash = voteCommitPendingFederationWithHash(FIRST_AUTHORIZED_TX, mismatchedHash);
        assertEquals(FederationChangeResponseCode.PENDING_FEDERATION_MISMATCHED_HASH.getCode(), resultFromMismatchedHash);
        assertFalse(federationSupport.getProposedFederation().isPresent());
        assertEquals(pendingFederation, federationStorageProvider.getPendingFederation());

        int logsSizeBeforeCommit = logs.size();
        long expectedActivationBlockNumber = currentBlock.getNumber() + FEDERATION_CONSTANTS.getFederationActivationAge(activations);

        var firstVoteResult = voteCommitPendingFederation(FIRST_AUTHORIZED_TX);
        var secondVoteResult = voteCommitPendingFederation(SECOND_AUTHORIZED_TX);

        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteResult);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), secondVoteResult);

        // Since the proposed federation is committed, it should be null in storage
        assertNull(federationStorageProvider.getPendingFederation());

        var committedFederation = federationSupport.getProposedFederation();
        assertTrue(committedFederation.isPresent());
        assertCommitFederationEventWasEmitted(
            logsSizeBeforeCommit,
            activeFederationBeforeCommit,
            committedFederation.get(),
            expectedActivationBlockNumber
        );
    }

    private void callUpdateCollectionsAndAssertSvpFundTxIsCreated() throws Exception {
        // Get UTXO size before creating fund tx
        var activeFederationUtxosSizeBeforeCreatingFundTx =
            federationSupport.getActiveFederationBtcUTXOs().size();
        int logsSizeBeforeFundTx = logs.size();

        // Next call to update collections will create svp fund tx
        bridgeSupport.updateCollections(UPDATE_COLLECTIONS_TX);
        bridgeSupport.save();

        var svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();
        assertTrue(svpFundTxHashUnsigned.isPresent());
        assertEquals(activeFederationUtxosSizeBeforeCreatingFundTx - 1, federationSupport.getActiveFederationBtcUTXOs().size());

        var pegoutsTxs = bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(activations).stream().toList();
        assertEquals(1, pegoutsTxs.size());
        var fundTxEntry = pegoutsTxs.get(0);
        var fundTx = fundTxEntry.getBtcTransaction();
        // One output to the proposed federation, one to its flyover counterpart; the fee is absorbed
        // separately from the active federation's remaining balance, not from these two outputs.
        Coin requestedAmount = BRIDGE_CONSTANTS.getSvpFundTxOutputsValue().multiply(2);
        assertLogReleaseRequested(logsSizeBeforeFundTx, fundTxEntry.getPegoutCreationRskTxHash(), fundTx.getHash(), requestedAmount);
        assertLogPegoutTransactionCreated(logsSizeBeforeFundTx, fundTx);
    }

    private void registerSignedSvpFundTx(List<BtcECKey> activeFederationMembersKeys) throws Exception {
        var pegoutsTxs =
            bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(activations).stream().toList();
        assertEquals(1, pegoutsTxs.size());
        var svpFundTx = new BtcTransaction(NETWORK_PARAMS, pegoutsTxs.get(0).getBtcTransaction().bitcoinSerialize());

        int neededSignatures = federationSupport.getActiveFederationThreshold();
        signInputs(svpFundTx, activeFederationMembersKeys.subList(0, neededSignatures));

        int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
        registerBtcTransaction(svpFundTx);

        assertEquals(activeFederationUtxosSizeBeforeRegisteringTx + 1, federationSupport.getActiveFederationBtcUTXOs().size());
        var svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();
        assertFalse(svpFundTxHashUnsigned.isPresent());
        var svpFundTransactionSigned = bridgeStorageProvider.getSvpFundTxSigned();
        assertTrue(svpFundTransactionSigned.isPresent());

        // simulate removal to leave state clean
        assertTrue(bridgeStorageProvider.getPegoutsWaitingForConfirmations().removeEntry(pegoutsTxs.get(0)));
    }

    private void callUpdateCollectionsAndAssertSvpSpendTxIsCreated() throws Exception {
        int logsSizeBeforeSpendTx = logs.size();

        // Next call to update collections will create svp spend tx
        bridgeSupport.updateCollections(UPDATE_COLLECTIONS_TX);
        bridgeSupport.save();

        var svpFundTransactionSigned = bridgeStorageProvider.getSvpFundTxSigned();
        assertFalse(svpFundTransactionSigned.isPresent());
        var svpSpendTransactionHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
        assertTrue(svpSpendTransactionHashUnsigned.isPresent());
        var svpSpendTxWaitingForSignatures = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
        assertTrue(svpSpendTxWaitingForSignatures.isPresent());

        var svpSpendTxCreationHash = svpSpendTxWaitingForSignatures.get().getKey();
        var svpSpendTx = svpSpendTxWaitingForSignatures.get().getValue();
        Coin amountSentToActiveFed = svpSpendTx.getOutput(0).getValue();
        assertLogReleaseRequested(logsSizeBeforeSpendTx, svpSpendTxCreationHash, svpSpendTx.getHash(), amountSentToActiveFed);
    }

    private void addSignaturesToAndRegisterSvpSpendTx() throws Exception {
        var svpSpendTxWaitingForSignatures = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
        assertTrue(svpSpendTxWaitingForSignatures.isPresent());

        var proposedFederation = federationSupport.getProposedFederation();
        assertTrue(proposedFederation.isPresent());

        // Add the signatures for the svp spend tx
        var svpSpendTxCreationHash = svpSpendTxWaitingForSignatures.get().getKey();
        var svpSpendTx = svpSpendTxWaitingForSignatures.get().getValue();
        var svpSpendTxSigHashes = IntStream.range(0, svpSpendTx.getInputs().size())
            .mapToObj(i -> BitcoinUtils.generateSigHashForSegwitTransactionInput(svpSpendTx, i, svpSpendTx.getInput(i).getValue()))
            .toList();

        int logsSizeBeforeSigning = logs.size();
        for (BtcECKey proposedFederatorSignerKey : NEW_FEDERATION_MEMBERS_KEYS.subList(0, NEW_FEDERATION_THRESHOLD)) {
            List<byte[]> signatures = BitcoinTestUtils.generateSignerEncodedSignatures(proposedFederatorSignerKey, svpSpendTxSigHashes);
            bridgeSupport.addSignature(proposedFederatorSignerKey, signatures, svpSpendTxCreationHash);
            assertFederatorSigning(
                svpSpendTxCreationHash.getBytes(),
                svpSpendTx,
                svpSpendTxSigHashes,
                proposedFederation.get(),
                proposedFederatorSignerKey,
                logs
            );
        }

        // Verify that the svp spend tx was released
        assertLogReleaseBtc(logsSizeBeforeSigning, svpSpendTxCreationHash, svpSpendTx);
        svpSpendTxWaitingForSignatures = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
        assertFalse(svpSpendTxWaitingForSignatures.isPresent());

        var activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
        // Register the svp spend tx
        registerBtcTransaction(svpSpendTx);

        assertEquals(activeFederationUtxosSizeBeforeRegisteringTx + 1, federationSupport.getActiveFederationBtcUTXOs().size());
        var svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
        assertFalse(svpSpendTxHashUnsigned.isPresent());
        var newFederationOpt = federationSupport.getProposedFederation();
        assertFalse(newFederationOpt.isPresent());
    }

    private void assertLogReleaseBtc(int logsSizeBefore, Keccak256 rskTxHash, BtcTransaction btcTx) {
        CallTransaction.Function releaseBtcEvent = BridgeEvents.RELEASE_BTC.getEvent();

        byte[] rskTxHashSerialized = rskTxHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(releaseBtcEvent, rskTxHashSerialized);

        byte[] btcTxSerialized = btcTx.bitcoinSerialize();
        byte[] encodedData = getEncodedData(releaseBtcEvent, btcTxSerialized);

        assertEventWasEmittedSince(logsSizeBefore, encodedTopics, encodedData);
    }

    /**
     * Unlike a whole-history search, this only looks at logs emitted after {@code logsSizeBefore} was captured,
     * and requires the topics AND data to match on the SAME log entry - proving both the exact value and that
     * it was emitted as a result of the action taken since the checkpoint, not some earlier step.
     */
    private void assertEventWasEmittedSince(int logsSizeBefore, List<DataWord> expectedTopics, byte[] expectedData) {
        List<LogInfo> logsSinceCheckpoint = logs.subList(logsSizeBefore, logs.size());
        boolean eventWasEmitted = logsSinceCheckpoint.stream()
            .anyMatch(log -> log.getTopics().equals(expectedTopics) && Arrays.equals(log.getData(), expectedData));
        assertTrue(eventWasEmitted);
    }

    private void assertNoEventWasEmittedSince(int logsSizeBefore) {
        assertEquals(logsSizeBefore, logs.size());
    }

    private void activateNewFederation() {
        // Move the required blocks ahead for the new powpeg to become active
        var blockNumber = currentBlock.getNumber() +
            FEDERATION_CONSTANTS.getFederationActivationAge(activations);
        currentBlock = createRskBlock(blockNumber);

        advanceBlockchainTo(currentBlock);
    }

    private void advanceBlockchainPastSvpValidationPeriod(Federation proposedFederation) {
        // The svp is over as soon as the validation period elapses, regardless of whether the svp
        // spend tx was ever registered
        var blockNumber = proposedFederation.getCreationBlockNumber() +
            FEDERATION_CONSTANTS.getValidationPeriodDurationInBlocks();
        currentBlock = createRskBlock(blockNumber);

        advanceBlockchainTo(currentBlock);
    }

    private void activateMigration() {
        // Move the required blocks ahead for the new federation to start migrating,
        // adding 1 as the migration is exclusive
        var blockNumber = currentBlock.getNumber() +
            FEDERATION_CONSTANTS.getFundsMigrationAgeSinceActivationBegin() +
            1L;
        currentBlock = createRskBlock(blockNumber);

        advanceBlockchainTo(currentBlock);
    }

    private void endMigration() throws Exception {
        // Move the required blocks ahead for the new federation to finish migrating,
        // adding 1 as the migration is exclusive
        var blockNumber = currentBlock.getNumber() +
            FEDERATION_CONSTANTS.getFundsMigrationAgeSinceActivationEnd(activations) +
            1L;
        currentBlock = createRskBlock(blockNumber);

        advanceBlockchainTo(currentBlock);

        // The first update collections after the migration finished should migrate the remaining funds
        callUpdateCollections();

        // The next updateCollections, having no funds left to migrate, should get rid of the retiring fed
        currentBlock = createRskBlock(blockNumber + 1);
        advanceBlockchainTo(currentBlock);
        callUpdateCollections();
    }

    private void callUpdateCollections() throws Exception {
        callUpdateCollections(UPDATE_COLLECTIONS_TX);
    }

    private void callUpdateCollections(Transaction updateCollectionsTx) throws Exception {
        bridgeSupport.updateCollections(updateCollectionsTx);
        bridgeSupport.save();
    }

    private void assertPeginsShouldWorkToFed(Federation federation, List<UTXO> federationUtxosReference, String senderSeed) throws Exception {
        var federationAddress = federation.getAddress();

        assertLegacyP2pkhPeginWorks(federationAddress, federationUtxosReference, senderSeed);
        assertLegacyP2shP2wpkhPeginWorks(federationAddress, federationUtxosReference, senderSeed);
        assertPeginV1Works(federationAddress, federationUtxosReference, senderSeed);
        assertFlyoverPeginWorks(federation, federationUtxosReference, senderSeed);
    }

    private void assertLegacyP2pkhPeginWorks(Address federationAddress, List<UTXO> federationUtxosReference, String senderSeed) throws Exception {
        var legacyP2pkhPeginToFed = createLegacyP2pkhPegin(federationAddress, senderSeed);
        var expectedReceiver = BitcoinTestUtils.getRskAddressFromBtcPublicKey(BitcoinTestUtils.getBtcEcKeyFromSeed(senderSeed));
        assertPeginWorks(legacyP2pkhPeginToFed, federationUtxosReference, expectedReceiver, LEGACY_PEGIN_PROTOCOL_VERSION);
    }

    private void assertLegacyP2shP2wpkhPeginWorks(Address federationAddress, List<UTXO> federationUtxosReference, String senderSeed) throws Exception {
        var legacyP2shP2wpkhPeginToFed = createLegacyP2shP2wpkhPegin(federationAddress, senderSeed);
        var expectedReceiver = BitcoinTestUtils.getRskAddressFromBtcPublicKey(BitcoinTestUtils.getBtcEcKeyFromSeed(senderSeed));
        assertPeginWorks(legacyP2shP2wpkhPeginToFed, federationUtxosReference, expectedReceiver, LEGACY_PEGIN_PROTOCOL_VERSION);
    }

    private void assertPeginV1Works(Address federationAddress, List<UTXO> federationUtxosReference, String senderSeed) throws Exception {
        var receiver = RskTestUtils.generateAddress("receiver");
        var peginV1ToFed = createPeginV1(federationAddress, senderSeed, receiver);

        assertPeginWorks(peginV1ToFed, federationUtxosReference, receiver, PEGIN_V1_PROTOCOL_VERSION);
    }

    private void assertPeginWorks(
        BtcTransaction pegin,
        List<UTXO> federationUtxosReference,
        RskAddress expectedReceiver,
        int expectedProtocolVersion
    ) throws Exception {
        int utxosSizeBeforeRegisteringPeginV1 = federationUtxosReference.size();
        int logsSizeBeforePegin = logs.size();
        int subtracesSizeBeforePegin = bridgeSupport.getSubtraces().size();
        var receiverBalanceBeforePegin = repository.getBalance(expectedReceiver);
        var bridgeBalanceBeforePegin = repository.getBalance(BRIDGE_ADDRESS);

        registerBtcTransaction(pegin);

        // assert pegin was processed
        assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(pegin.getHash()));
        // assert utxo was registered
        assertEquals(utxosSizeBeforeRegisteringPeginV1 + 1, federationUtxosReference.size());
        assertPeginBtcEventWasEmitted(logsSizeBeforePegin, pegin, expectedReceiver, Coin.COIN, expectedProtocolVersion);

        // assert funds were actually transferred from the bridge to the receiver
        var peginAmountInWeis = co.rsk.core.Coin.fromBitcoin(Coin.COIN);
        assertEquals(receiverBalanceBeforePegin.add(peginAmountInWeis), repository.getBalance(expectedReceiver));
        assertEquals(bridgeBalanceBeforePegin.subtract(peginAmountInWeis), repository.getBalance(BRIDGE_ADDRESS));

        // assert an internal transaction recorded the transfer
        assertPeginInternalTransactionWasRecorded(subtracesSizeBeforePegin, expectedReceiver, peginAmountInWeis);
    }

    private void assertPeginInternalTransactionWasRecorded(
        int subtracesSizeBeforePegin,
        RskAddress expectedReceiver,
        co.rsk.core.Coin expectedAmount
    ) {
        List<ProgramSubtrace> subtraces = bridgeSupport.getSubtraces();
        assertEquals(subtracesSizeBeforePegin + 1, subtraces.size());

        var peginSubtrace = subtraces.get(subtracesSizeBeforePegin);
        assertEquals(TraceType.CALL, peginSubtrace.getTraceType());
        assertEquals(CallType.CALL, peginSubtrace.getCallType());

        var invokeData = peginSubtrace.getInvokeData();
        assertEquals(DataWord.valueOf(BRIDGE_ADDRESS.getBytes()), invokeData.getCallerAddress());
        assertEquals(DataWord.valueOf(expectedReceiver.getBytes()), invokeData.getOwnerAddress());
        assertEquals(DataWord.valueOf(expectedAmount.getBytes()), invokeData.getCallValue());
    }

    private void assertFlyoverPeginWorks(Federation federation, List<UTXO> federationUtxosReference, String senderSeed) throws Exception {
        var flyoverPegin = createFlyoverPegin(federation, senderSeed);
        int utxosSizeBeforeRegisteringFlyoverPegin = federationUtxosReference.size();
        int logsSizeBeforeFlyoverPegin = logs.size();
        int subtracesSizeBeforeFlyoverPegin = bridgeSupport.getSubtraces().size();
        var lbcBalanceBeforeFlyoverPegin = repository.getBalance(LBC_ADDRESS);
        var bridgeBalanceBeforeFlyoverPegin = repository.getBalance(BRIDGE_ADDRESS);

        var flyoverPeginBtcTx = flyoverPegin.getLeft();
        registerFlyoverBtcTransaction(flyoverPeginBtcTx);

        // assert flyover derivation hash was used
        assertTrue(bridgeStorageProvider.isFlyoverDerivationHashUsed(
            flyoverPeginBtcTx.getHash(),
            flyoverPegin.getRight()
        ));
        // assert utxo was registered
        assertEquals(utxosSizeBeforeRegisteringFlyoverPegin + 1, federationUtxosReference.size());
        // Flyover crediting goes straight to the LBC contract; no PEGIN_BTC/LOCK_BTC event is emitted for it
        assertNoEventWasEmittedSince(logsSizeBeforeFlyoverPegin);

        // assert funds were actually transferred from the bridge to the LBC contract
        var flyoverPeginAmountInWeis = co.rsk.core.Coin.fromBitcoin(Coin.COIN);
        assertEquals(lbcBalanceBeforeFlyoverPegin.add(flyoverPeginAmountInWeis), repository.getBalance(LBC_ADDRESS));
        assertEquals(bridgeBalanceBeforeFlyoverPegin.subtract(flyoverPeginAmountInWeis), repository.getBalance(BRIDGE_ADDRESS));

        // assert an internal transaction recorded the transfer
        assertPeginInternalTransactionWasRecorded(subtracesSizeBeforeFlyoverPegin, LBC_ADDRESS, flyoverPeginAmountInWeis);
    }

    private void assertPegoutsShouldWorkToFed(Federation federation, List<UTXO> federationUtxosReference, String senderSeed) throws Exception {
        var pegout = createPegout(federation, senderSeed);
        // save pegout index
        BitcoinUtils.getSigHashForPegoutIndex(pegout)
            .ifPresent(inputSigHash -> bridgeStorageProvider.setPegoutTxSigHash(inputSigHash));

        int utxosSizeBeforeRegisteringPegout = federationUtxosReference.size();
        registerBtcTransaction(pegout);

        // assert pegout was processed
        assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(pegout.getHash()));
        // assert utxo was registered
        assertEquals(utxosSizeBeforeRegisteringPegout + 1, federationUtxosReference.size());
    }

    private void assertPeginsShouldNotWorkToFed(Federation federation, String senderSeed) throws Exception {
        var federationAddress = federation.getAddress();

        assertLegacyP2pkhPeginDoesNotWork(federationAddress, senderSeed);
        assertLegacyP2shP2wpkhPeginDoesNotWork(federationAddress, senderSeed);
        assertPeginV1DoesNotWork(federationAddress, senderSeed);

        assertFlyoverPeginDoesNotWork(federation, senderSeed);
    }

    private void assertLegacyP2pkhPeginDoesNotWork(Address federationAddress, String senderSeed) throws Exception {
        var legacyP2pkhPeginToFed = createLegacyP2pkhPegin(federationAddress, senderSeed);
        assertPeginDoesNotWork(legacyP2pkhPeginToFed);
    }

    private void assertLegacyP2shP2wpkhPeginDoesNotWork(Address federationAddress, String senderSeed) throws Exception {
        var legacyP2shP2wpkhPeginToFed = createLegacyP2shP2wpkhPegin(federationAddress, senderSeed);
        assertPeginDoesNotWork(legacyP2shP2wpkhPeginToFed);
    }

    private void assertPeginV1DoesNotWork(Address federationAddress, String senderSeed) throws Exception {
        var receiver = RskTestUtils.generateAddress("receiver");
        var peginV1ToFed = createPeginV1(federationAddress, senderSeed, receiver);

        assertPeginDoesNotWork(peginV1ToFed);
    }

    private void assertPeginDoesNotWork(BtcTransaction pegin) throws Exception {
        int activeFederationUtxosSizeBeforeRegisteringPegin = federationSupport.getActiveFederationBtcUTXOs().size();
        int retiringFederationUtxosSizeBeforeRegisteringPegin = federationSupport.getRetiringFederationBtcUTXOs().size();
        int logsSizeBeforePegin = logs.size();
        int subtracesSizeBeforePegin = bridgeSupport.getSubtraces().size();
        var bridgeBalanceBeforePegin = repository.getBalance(BRIDGE_ADDRESS);

        registerBtcTransaction(pegin);

        // assert pegin was not processed
        assertFalse(bridgeSupport.isBtcTxHashAlreadyProcessed(pegin.getHash()));
        // assert no utxos were registered
        assertEquals(activeFederationUtxosSizeBeforeRegisteringPegin, federationSupport.getActiveFederationBtcUTXOs().size());
        assertEquals(retiringFederationUtxosSizeBeforeRegisteringPegin, federationSupport.getRetiringFederationBtcUTXOs().size());
        // A pegin sent to an address that isn't part of any live federation is classified as an unknown
        // transaction type and silently ignored - no event is emitted for it
        assertNoEventWasEmittedSince(logsSizeBeforePegin);
        // assert no funds were moved and no internal transaction was recorded
        assertEquals(bridgeBalanceBeforePegin, repository.getBalance(BRIDGE_ADDRESS));
        assertEquals(subtracesSizeBeforePegin, bridgeSupport.getSubtraces().size());
    }

    private void assertFlyoverPeginDoesNotWork(Federation federation, String senderSeed) throws Exception {
        var flyoverPegin = createFlyoverPegin(federation, senderSeed);
        int activeFederationUtxosSizeBeforeRegisteringPegin = federationSupport.getActiveFederationBtcUTXOs().size();
        int retiringFederationUtxosSizeBeforeRegisteringPegin = federationSupport.getRetiringFederationBtcUTXOs().size();
        int logsSizeBeforeFlyoverPegin = logs.size();
        int subtracesSizeBeforeFlyoverPegin = bridgeSupport.getSubtraces().size();
        var bridgeBalanceBeforeFlyoverPegin = repository.getBalance(BRIDGE_ADDRESS);

        var flyoverPeginBtcTx = flyoverPegin.getLeft();
        registerFlyoverBtcTransaction(flyoverPeginBtcTx);

        // assert flyover derivation hash was not used
        assertFalse(bridgeStorageProvider.isFlyoverDerivationHashUsed(
            flyoverPeginBtcTx.getHash(),
            flyoverPegin.getRight()
        ));
        // assert no utxos were registered
        assertEquals(activeFederationUtxosSizeBeforeRegisteringPegin, federationSupport.getActiveFederationBtcUTXOs().size());
        assertEquals(retiringFederationUtxosSizeBeforeRegisteringPegin, federationSupport.getRetiringFederationBtcUTXOs().size());
        // Flyover rejections are communicated via FlyoverTxResponseCodes, not bridge events
        assertNoEventWasEmittedSince(logsSizeBeforeFlyoverPegin);
        // assert no funds were moved and no internal transaction was recorded
        assertEquals(bridgeBalanceBeforeFlyoverPegin, repository.getBalance(BRIDGE_ADDRESS));
        assertEquals(subtracesSizeBeforeFlyoverPegin, bridgeSupport.getSubtraces().size());
    }

    private void assertPegoutsShouldNotWorkToFed(Federation federation, String senderSeed) throws Exception {
        var pegout = createPegout(federation, senderSeed);
        var activeFederationUtxosSizeBeforeRegisteringPegout = federationSupport.getActiveFederationBtcUTXOs().size();
        var retiringFederationUtxosSizeBeforeRegisteringPegout = federationSupport.getRetiringFederationBtcUTXOs().size();

        registerBtcTransaction(pegout);

        // assert pegout was not processed
        assertFalse(bridgeSupport.isBtcTxHashAlreadyProcessed(pegout.getHash()));
        // assert no utxos were registered
        assertEquals(activeFederationUtxosSizeBeforeRegisteringPegout, federationSupport.getActiveFederationBtcUTXOs().size());
        assertEquals(retiringFederationUtxosSizeBeforeRegisteringPegout, federationSupport.getRetiringFederationBtcUTXOs().size());
    }

    private BtcTransaction createLegacyP2pkhPegin(Address federationAddress, String senderSeed) {
        var peginBtcTx = new BtcTransaction(NETWORK_PARAMS);
        var senderPublicKey = BitcoinTestUtils.getBtcEcKeyFromSeed(senderSeed);

        peginBtcTx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, senderPublicKey));
        peginBtcTx.addOutput(Coin.COIN, federationAddress);

        return peginBtcTx;
    }

    private BtcTransaction createLegacyP2shP2wpkhPegin(Address federationAddress, String senderSeed) {
        var peginBtcTx = new BtcTransaction(NETWORK_PARAMS);
        var senderPublicKey = BitcoinTestUtils.getBtcEcKeyFromSeed(senderSeed);
        var redeemScript = ByteUtil.merge(new byte[]{ 0x00, 0x14}, senderPublicKey.getPubKeyHash());
        var witnessScript = new ScriptBuilder()
            .data(redeemScript)
            .build();

        peginBtcTx.addInput(BitcoinTestUtils.createHash(1), 0, witnessScript);
        var txWit = new TransactionWitness(2);
        txWit.setPush(0, new byte[72]); // push for signatures
        txWit.setPush(1, senderPublicKey.getPubKey());
        peginBtcTx.setWitness(0, txWit);

        peginBtcTx.addOutput(Coin.COIN, federationAddress);

        return peginBtcTx;
    }

    private BtcTransaction createPeginV1(Address federationAddress, String senderSeed, RskAddress destinationAddress) {
        var peginBtcTx = new BtcTransaction(NETWORK_PARAMS);
        var senderPublicKey = BitcoinTestUtils.getBtcEcKeyFromSeed(senderSeed);

        peginBtcTx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, senderPublicKey));
        peginBtcTx.addOutput(Coin.COIN, federationAddress);
        var opReturnOutputScript = PegTestUtils.createOpReturnScriptForRsk(
            PEGIN_V1_PROTOCOL_VERSION,
            destinationAddress,
            Optional.empty()
        );
        peginBtcTx.addOutput(Coin.ZERO, opReturnOutputScript);

        return peginBtcTx;
    }

    private Pair<BtcTransaction, Keccak256> createFlyoverPegin(Federation federation, String senderSeed) {
        var flyoverPeginBtcTx = new BtcTransaction(NETWORK_PARAMS);
        flyoverPeginBtcTx.addInput(BitcoinTestUtils.createHash(0), 0, new Script(new byte[]{}));

        userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(NETWORK_PARAMS, senderSeed);
        var flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            DERIVATION_ARGUMENTS_HASH,
            userRefundBtcAddress,
            LIQUIDITY_PROVIDER_BTC_ADDRESS,
            LBC_ADDRESS,
            activations
        );

        var flyoverFederationAddress = PegUtils.getFlyoverFederationAddress(NETWORK_PARAMS, flyoverDerivationHash, federation);
        flyoverPeginBtcTx.addOutput(Coin.COIN, flyoverFederationAddress);

        return Pair.of(flyoverPeginBtcTx, flyoverDerivationHash);
    }

    private BtcTransaction createPegout(Federation federation, String senderSeed) {
        Address receiverAddress = BitcoinTestUtils.createP2PKHAddress(NETWORK_PARAMS, senderSeed);
        return PegoutTransactionBuilder.builder()
            .withNetworkParameters(NETWORK_PARAMS)
            .withActiveFederation(federation)
            .withOutput(Coin.COIN, receiverAddress)
            .withChangeAmount(Coin.COIN.multiply(10))
            .build();
    }

    private void registerFlyoverBtcTransaction(BtcTransaction flyoverPeginBtcTx) throws Exception {
        setUpForTransactionRegistration(flyoverPeginBtcTx);

        bridgeSupport.registerFlyoverBtcTransaction(
            FLYOVER_REGISTRATION_TX,
            flyoverPeginBtcTx.bitcoinSerialize(),
            btcBlockWithPmtHeight,
            pmtWithTransactions.bitcoinSerialize(),
            DERIVATION_ARGUMENTS_HASH,
            userRefundBtcAddress,
            LBC_ADDRESS,
            LIQUIDITY_PROVIDER_BTC_ADDRESS,
            true
        );
        bridgeSupport.save();
    }

    private void registerBtcTransaction(BtcTransaction btcTx) throws Exception {
        setUpForTransactionRegistration(btcTx);

        bridgeSupport.registerBtcTransaction(
            REGISTRATION_TX,
            btcTx.bitcoinSerialize(),
            btcBlockWithPmtHeight,
            pmtWithTransactions.bitcoinSerialize()
        );
        bridgeSupport.save();
    }

    private void setUpForTransactionRegistration(BtcTransaction btcTx) throws Exception {
        pmtWithTransactions = createValidPmtForTransactions(List.of(btcTx), NETWORK_PARAMS);
        btcBlockWithPmtHeight = BRIDGE_CONSTANTS.getBtcHeightWhenPegoutTxIndexActivates() + BRIDGE_CONSTANTS.getPegoutTxIndexGracePeriodInBtcBlocks(); // we want pegout tx index to be activated
        var chainHeight = btcBlockWithPmtHeight + BRIDGE_CONSTANTS.getBtc2RskMinimumAcceptableConfirmations();
        recreateChainFromPmt(btcBlockStore, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, NETWORK_PARAMS);
        bridgeStorageProvider.save();
    }

    private void advanceBlockchainTo(Block executionBlock) {
        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(FEDERATION_CONSTANTS)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = BridgeSupportBuilder.builder()
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withLockingCapSupport(lockingCapSupport)
            .build();
    }

    private static void addNewBtcBlockOnTipOfChain(BtcBlockStore blockStore) throws Exception {
        var chainHead = blockStore.getChainHead();
        var btcBlock = new BtcBlock(
            NETWORK_PARAMS,
            1,
            chainHead.getHeader().getHash(),
            BitcoinTestUtils.createHash(chainHead.getHeight() + 1),
            0,
            0,
            0,
            List.of()
        );
        var storedBlock = new StoredBlock(
            btcBlock,
            BigInteger.ZERO,
            chainHead.getHeight() + 1
        );

        blockStore.put(storedBlock);
        blockStore.setChainHead(storedBlock);
    }

    // Every federation built in this class is an ErpFederation (P2SH-ERP or P2SH-P2WSH-ERP)
    private Script getFederationDefaultRedeemScript(Federation federation) {
        return ((ErpFederation) federation).getDefaultRedeemScript();
    }

    private static Script getFederationDefaultP2SHScript(Federation federation) {
        return ((ErpFederation) federation).getDefaultP2SHScript();
    }

    private void signInputs(BtcTransaction transaction, List<BtcECKey> keysToSign) {
        List<TransactionInput> inputs = transaction.getInputs();
        IntStream.range(0, inputs.size()).forEach(i ->
            BitcoinTestUtils.signLegacyTransactionInputFromP2shMultiSig(transaction, i, keysToSign)
        );
    }
    
    // Assert federation change related methods
    private void assertUTXOsReferenceMovedFromNewToOldFederation(List<UTXO> utxos) {
        // Assert old federation exists in storage
        assertNotNull(federationStorageProvider.getOldFederation(FEDERATION_CONSTANTS, activations));
        // Assert new federation exists in storage
        assertNotNull(federationStorageProvider.getNewFederation(FEDERATION_CONSTANTS, activations));
        // Assert old federation holds the original utxos
        List<UTXO> utxosToMigrate = federationStorageProvider.getOldFederationBtcUTXOs();
        assertTrue(utxosToMigrate.containsAll(utxos));
        // Assert the new federation does not have any utxos yet
        assertTrue(federationStorageProvider
            .getNewFederationBtcUTXOs(NETWORK_PARAMS, activations)
            .isEmpty()
        );
    }

    private void assertNewAndOldFederationsReferences(Federation expectedNewFederation, Federation expectedOldFederation) {
        FederationConstants federationConstants = FEDERATION_CONSTANTS;
        assertEquals(expectedNewFederation, federationStorageProvider.getNewFederation(federationConstants, activations));
        assertEquals(expectedOldFederation, federationStorageProvider.getOldFederation(federationConstants, activations));
    }

    private void assertActiveAndRetiringFederationsHaveExpectedAddress(Address expectedNewFederationAddress, Address expectedOldFederationAddress) {
        assertEquals(expectedNewFederationAddress, bridgeSupport.getActiveFederationAddress());
        Optional<Address> retiringFederationAddress = bridgeSupport.getRetiringFederationAddress();
        assertTrue(retiringFederationAddress.isPresent());
        assertEquals(expectedOldFederationAddress, retiringFederationAddress.get());
    }

    private void assertNextFederationCreationBlockHeight(long newFederationCreationBlockNumber) {
        Optional<Long> nextFederationCreationBlockHeight = federationStorageProvider.getNextFederationCreationBlockHeight(activations);
        assertTrue(nextFederationCreationBlockHeight.isPresent());
        assertEquals(newFederationCreationBlockNumber, nextFederationCreationBlockHeight.get());
    }

    private void assertMigrationHasNotStarted() throws Exception {
        // Current block is behind fedActivationAge + fundsMigrationAgeBegin
        var blockNumber = FEDERATION_CONSTANTS.getFederationActivationAge(activations) +
            FEDERATION_CONSTANTS.getFundsMigrationAgeSinceActivationBegin();
        assertTrue(currentBlock.getNumber() <= blockNumber);

        // Pegouts waiting for confirmations should be empty
        assertTrue(bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(activations).isEmpty());
    }
     
    private void assertMigrationHasStarted() throws Exception {
        // Pegouts waiting for confirmations should not be empty
        // Expecting only one element since the retiring federation had less than 50 UTXOs
        assertEquals(1, bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(activations).size());
    }

    private void assertOnlyActiveFedIsLive(Federation newFederation) {
        // New active federation still there, retiring federation no longer there
        assertEquals(newFederation, bridgeSupport.getActiveFederation());
        Optional<Address> retiringFederationAddress = bridgeSupport.getRetiringFederationAddress();
        assertTrue(retiringFederationAddress.isEmpty());
    }
    
    private void assertLastRetiredFederationP2SHScriptMatchesWithOriginalFederation(Federation originalFederation) {
        var lastRetiredFederationP2SHScriptOptional = federationStorageProvider.getLastRetiredFederationP2SHScript(activations);
        assertTrue(lastRetiredFederationP2SHScriptOptional.isPresent());
        Script lastRetiredFederationP2SHScript = lastRetiredFederationP2SHScriptOptional.get();

        assertNotEquals(lastRetiredFederationP2SHScript, originalFederation.getP2SHScript());
        assertEquals(lastRetiredFederationP2SHScript, getFederationDefaultP2SHScript(originalFederation));
    }

    private void assertPendingFederationIsBuiltAsExpected(PendingFederation pendingFederation) {
        assertNotNull(pendingFederation);
        assertEquals(NEW_FEDERATION_MEMBERS_SIZE, pendingFederation.getSize());
        assertTrue(pendingFederation.getMembers().containsAll(NEW_FEDERATION_MEMBERS));
    }

    private void assertLogPegoutTransactionCreated(int logsSizeBefore, BtcTransaction pegoutTransaction) {
        CallTransaction.Function pegoutTransactionCreatedEvent = BridgeEvents.PEGOUT_TRANSACTION_CREATED.getEvent();

        Sha256Hash pegoutTransactionHash = pegoutTransaction.getHash();
        byte[] pegoutTransactionHashSerialized = pegoutTransactionHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(pegoutTransactionCreatedEvent, pegoutTransactionHashSerialized);

        List<Coin> outpointValues = extractOutpointValues(pegoutTransaction);
        byte[] serializedOutpointValues = UtxoUtils.encodeOutpointValues(outpointValues);
        byte[] encodedData = getEncodedData(pegoutTransactionCreatedEvent, serializedOutpointValues);

        assertEventWasEmittedSince(logsSizeBefore, encodedTopics, encodedData);
    }

    private void assertLogReleaseRequested(
        int logsSizeBefore,
        Keccak256 releaseCreationTxHash,
        Sha256Hash pegoutTransactionHash,
        Coin requestedAmount
    ) {
        CallTransaction.Function releaseRequestedEvent = BridgeEvents.RELEASE_REQUESTED.getEvent();

        byte[] releaseCreationTxHashSerialized = releaseCreationTxHash.getBytes();
        byte[] pegoutTransactionHashSerialized = pegoutTransactionHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(
            releaseRequestedEvent,
            releaseCreationTxHashSerialized,
            pegoutTransactionHashSerialized
        );

        byte[] encodedData = getEncodedData(releaseRequestedEvent, requestedAmount.getValue());

        assertEventWasEmittedSince(logsSizeBefore, encodedTopics, encodedData);
    }

    private void assertPegoutConfirmedEventWasEmitted(int logsSizeBefore, long pegoutCreationRskBlockNumber) throws Exception {
        var pegoutsTxs = bridgeStorageProvider.getPegoutsWaitingForSignatures()
            .entrySet().stream()
            .toList();

        // More than one migration round may have been needed to fully drain the retiring federation,
        // each created at the same block and therefore confirmed together here.
        assertFalse(pegoutsTxs.isEmpty());
        for (var pegoutTxEntry : pegoutsTxs) {
            assertLogPegoutConfirmed(logsSizeBefore, pegoutTxEntry.getValue().getHash(), pegoutCreationRskBlockNumber);
        }
    }

    private void assertLogPegoutConfirmed(int logsSizeBefore, Sha256Hash btcTxHash, long pegoutCreationRskBlockNumber) {
        CallTransaction.Function pegoutConfirmedEvent = BridgeEvents.PEGOUT_CONFIRMED.getEvent();

        byte[] btcTxHashSerialized = btcTxHash.getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(pegoutConfirmedEvent, btcTxHashSerialized);

        byte[] encodedData = getEncodedData(pegoutConfirmedEvent, pegoutCreationRskBlockNumber);

        assertEventWasEmittedSince(logsSizeBefore, encodedTopics, encodedData);
    }

    private void assertCommitFederationEventWasEmitted(
        int logsSizeBefore,
        Federation oldFederation,
        Federation newFederation,
        long expectedActivationBlockNumber
    ) {
        CallTransaction.Function commitFederationEvent = BridgeEvents.COMMIT_FEDERATION.getEvent();

        List<DataWord> encodedTopics = getEncodedTopics(commitFederationEvent);

        byte[] oldFederationFlatPubKeys = BitcoinTestUtils.flatKeysAsByteArray(oldFederation.getBtcPublicKeys());
        String oldFederationBtcAddress = oldFederation.getAddress().toBase58();
        byte[] newFederationFlatPubKeys = BitcoinTestUtils.flatKeysAsByteArray(newFederation.getBtcPublicKeys());
        String newFederationBtcAddress = newFederation.getAddress().toBase58();

        byte[] encodedData = getEncodedData(
            commitFederationEvent,
            oldFederationFlatPubKeys,
            oldFederationBtcAddress,
            newFederationFlatPubKeys,
            newFederationBtcAddress,
            expectedActivationBlockNumber
        );

        assertEventWasEmittedSince(logsSizeBefore, encodedTopics, encodedData);
    }

    private void assertCommitFederationFailedEventWasEmitted(int logsSizeBefore, Federation proposedFederation, long expectedBlockNumber) {
        CallTransaction.Function commitFederationFailedEvent = BridgeEvents.COMMIT_FEDERATION_FAILED.getEvent();

        List<DataWord> encodedTopics = getEncodedTopics(commitFederationFailedEvent);

        byte[] proposedFederationRedeemScriptSerialized = proposedFederation.getRedeemScript().getProgram();
        byte[] encodedData = getEncodedData(commitFederationFailedEvent, proposedFederationRedeemScriptSerialized, expectedBlockNumber);

        assertEventWasEmittedSince(logsSizeBefore, encodedTopics, encodedData);
    }

    private void assertPeginBtcEventWasEmitted(
        int logsSizeBefore,
        BtcTransaction peginTransaction,
        RskAddress expectedReceiver,
        Coin expectedAmount,
        int expectedProtocolVersion
    ) {
        CallTransaction.Function peginBtcEvent = BridgeEvents.PEGIN_BTC.getEvent();

        byte[] btcTxHashSerialized = peginTransaction.getHash().getBytes();
        List<DataWord> encodedTopics = getEncodedTopics(peginBtcEvent, expectedReceiver.toString(), btcTxHashSerialized);

        byte[] encodedData = getEncodedData(peginBtcEvent, expectedAmount.getValue(), expectedProtocolVersion);

        assertEventWasEmittedSince(logsSizeBefore, encodedTopics, encodedData);
    }

    /**
     * Finds the single new migration transaction created since {@code pegoutEntriesBeforeMigration} was
     * snapshotted, then asserts RELEASE_REQUESTED and PEGOUT_TRANSACTION_CREATED were emitted for it (since
     * {@code logsSizeBeforeMigration}) and that its outputs match the RSKIP455 thresholds for the given
     * retiring federation balance.
     */
    private void assertMigrationRoundWasSettledAsExpected(
        Set<Entry> pegoutEntriesBeforeMigration,
        int logsSizeBeforeMigration,
        Address destinationAddress
    ) throws IOException {
        Set<Entry> newPegoutEntries = getPegoutEntriesSnapshot();
        newPegoutEntries.removeAll(pegoutEntriesBeforeMigration);
        assertEquals(1, newPegoutEntries.size());
        Entry migrationEntry = newPegoutEntries.iterator().next();
        BtcTransaction migrationTransaction = migrationEntry.getBtcTransaction();

        Coin requestedAmount = migrationTransaction.getFee().add(migrationTransaction.getOutputSum());
        assertLogReleaseRequested(
            logsSizeBeforeMigration,
            migrationEntry.getPegoutCreationRskTxHash(),
            migrationTransaction.getHash(),
            requestedAmount
        );
        assertLogPegoutTransactionCreated(logsSizeBeforeMigration, migrationTransaction);

        if (!activations.isActive(ConsensusRule.RSKIP455)) {
            // Pre-RSKIP455, migration transactions are always built with a single output regardless of balance
            ReleaseTransactionAssertions.assertOneMigrationTxOutput(
                migrationTransaction,
                requestedAmount,
                destinationAddress,
                NETWORK_PARAMS
            );
            return;
        }

        List<Coin> expectedOutputValues = BridgeUtils.calculateMigrationTransactionOutputsValues(requestedAmount, BRIDGE_CONSTANTS);
        if (expectedOutputValues.size() == 1) {
            ReleaseTransactionAssertions.assertOneMigrationTxOutput(
                migrationTransaction,
                requestedAmount,
                destinationAddress,
                NETWORK_PARAMS
            );
        } else {
            ReleaseTransactionAssertions.assertMigrationTxWithMultipleOutputs(
                migrationTransaction,
                expectedOutputValues,
                destinationAddress,
                NETWORK_PARAMS
            );
        }
    }
    
    private void verifyPegouts() throws Exception {
        var activeFederation = federationStorageProvider.getNewFederation(FEDERATION_CONSTANTS, activations);
        var retiringFederation = federationStorageProvider.getOldFederation(FEDERATION_CONSTANTS, activations);

        for (PegoutsWaitingForConfirmations.Entry pegoutEntry : bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(activations)) {
            var pegoutBtcTransaction = pegoutEntry.getBtcTransaction();

            List<TransactionInput> inputs = pegoutBtcTransaction.getInputs();
            for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
                TransactionInput input = inputs.get(inputIndex);

                // Each input should contain the right scriptSig
                Script inputRedeemScript = BitcoinUtils.extractRedeemScriptFromInput(pegoutBtcTransaction, inputIndex).orElseThrow();

                // Get the standard redeem script to compare against, since it could be a flyover redeem script
                var redeemScriptChunks = ScriptParser.parseScriptProgram(
                    inputRedeemScript.getProgram());

                var redeemScriptParser = RedeemScriptParserFactory.get(redeemScriptChunks);
                var inputStandardRedeemScriptChunks = redeemScriptParser.extractStandardRedeemScriptChunks();
                var inputStandardRedeemScript = new ScriptBuilder().addChunks(inputStandardRedeemScriptChunks).build();

                Optional<Federation> spendingFederationOptional = Optional.empty();
                if (inputStandardRedeemScript.equals(getFederationDefaultRedeemScript(activeFederation))) {
                    spendingFederationOptional = Optional.of(activeFederation);
                } else if (retiringFederation != null &&
                    inputStandardRedeemScript.equals(getFederationDefaultRedeemScript(retiringFederation))) {
                    spendingFederationOptional = Optional.of(retiringFederation);
                } else {
                    fail("Pegout scriptsig does not match any Federation");
                }

                Federation spendingFederation = spendingFederationOptional.get();
                boolean isSegwitFederation = spendingFederation.getFormatVersion() == FederationFormatVersion.P2SH_P2WSH_ERP_FEDERATION.getFormatVersion();
                if (isSegwitFederation) {
                    // Segwit federations carry their signatures in the witness, not the scriptSig
                    BitcoinTestAssertions.assertP2shP2wshWitnessWithoutSignaturesHasProperFormat(
                        pegoutBtcTransaction.getWitness(inputIndex),
                        getFederationDefaultRedeemScript(spendingFederation)
                    );
                    continue;
                }

                // Check the script sig composition
                var inputScriptChunks = input.getScriptSig().getChunks();
                assertEquals(ScriptOpCodes.OP_0, inputScriptChunks.get(0).opcode);
                for (int i = 1; i <= spendingFederation.getNumberOfSignaturesRequired(); i++) {
                    assertEquals(ScriptOpCodes.OP_0, inputScriptChunks.get(i).opcode);
                }

                int index = spendingFederation.getNumberOfSignaturesRequired() + 1;
                if (spendingFederation instanceof ErpFederation) {
                    // Should include an additional OP_0
                    assertEquals(ScriptOpCodes.OP_0, inputScriptChunks.get(index).opcode);
                }
            }
        }
    }

    private void assertPegoutTxSigHashesAreSaved() throws IOException {
        var pegoutsTxs = bridgeStorageProvider.getPegoutsWaitingForConfirmations()
            .getEntries(activations).stream()
            .map(Entry::getBtcTransaction)
            .toList();

        for (var pegoutTx : pegoutsTxs) {
            assertPegoutTxSigHashWasSaved(bridgeStorageProvider, pegoutTx);
        }
    }
}
