package co.rsk.peg.federation;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.RskTestUtils.createRskBlock;
import static co.rsk.peg.BridgeSupportTestUtil.*;
import static co.rsk.peg.bitcoin.UtxoUtils.extractOutpointValues;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.*;
import co.rsk.peg.PegoutsWaitingForConfirmations.Entry;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.BitcoinUtils;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.lockingcap.LockingCapStorageProvider;
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
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockTxSignatureCache;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FederationChangeIT {
    private static final ActivationConfig.ForBlock ALL_ACTIVATIONS = ActivationConfigsForTest.all().forBlock(0);
    private static final RskAddress BRIDGE_ADDRESS = PrecompiledContracts.BRIDGE_ADDR;
    private static final BridgeConstants BRIDGE_CONSTANTS = BridgeMainNetConstants.getInstance();
    private static final FederationConstants FEDERATION_CONSTANTS = BRIDGE_CONSTANTS.getFederationConstants();
    private static final NetworkParameters NETWORK_PARAMS = BRIDGE_CONSTANTS.getBtcParams();
    private static final int MAX_INPUTS_PER_MIGRAION_TX = BRIDGE_CONSTANTS.getMaxInputsPerMigrationTransaction(ALL_ACTIVATIONS);
    private static final List<BtcECKey> ORIGINAL_FEDERATION_MEMBERS_KEYS = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[] {
            "segwitMember01", "segwitMember02", "segwitMember03", "segwitMember04", "segwitMember05",
            "segwitMember06", "segwitMember07", "segwitMember08", "segwitMember09", "segwitMember10",
            "segwitMember11", "segwitMember12", "segwitMember13", "segwitMember14", "segwitMember15",
            "segwitMember16", "segwitMember17", "segwitMember18", "segwitMember19", "segwitMember20"
        },
        true
    );
    private static final Federation ORIGINAL_FEDERATION = P2shP2wshErpFederationBuilder.builder()
        .withMembersBtcPublicKeys(ORIGINAL_FEDERATION_MEMBERS_KEYS)
        .build();
    private static final List<BtcECKey> NEW_FEDERATION_MEMBERS_KEYS = BitcoinTestUtils.getBtcEcKeysFromSeeds(
        new String[]{
            "member01", "member02", "member03", "member04", "member05", "member06", "member07", "member08", "member09",
            "newMember10", "newMember11", "newMember12", "newMember13", "newMember14", "newMember15", "newMember16",
            "newMember17", "newMember18", "newMember19", "newMember20"
        },
        true
    );
    private static final int NEW_FEDERATION_MEMBERS_SIZE = NEW_FEDERATION_MEMBERS_KEYS.size();
    private static final List<FederationMember> NEW_FEDERATION_MEMBERS = FederationTestUtils.getFederationMembersWithBtcKeys(NEW_FEDERATION_MEMBERS_KEYS);
    private static final FederationArgs NEW_FEDERATION_ARGS =
        new FederationArgs(NEW_FEDERATION_MEMBERS, Instant.EPOCH, 0, NETWORK_PARAMS);
    private static final Federation NEW_FEDERATION = FederationFactory.buildP2shP2wshErpFederation(
        NEW_FEDERATION_ARGS,
        FEDERATION_CONSTANTS.getErpFedPubKeysList(),
        FEDERATION_CONSTANTS.getErpFedActivationDelay()
    );
    private static final int LEGACY_PEGIN_PROTOCOL_VERSION = 0;
    private static final int PEGIN_V1_PROTOCOL_VERSION = 1;
    private static final Coin PEGIN_VALUE = Coin.COIN;
    // Different from any utxo value the federations hold (pegins are of 1 BTC), so the pegout
    // never spends an utxo for its exact value and always has a change output back to the federation
    private static final Coin PEGOUT_VALUE = Coin.COIN.divide(2);
    // Funding pegins are big enough so the first migration tx, with max inputs, also reaches the max outputs
    private static final Coin FUNDING_PEGIN_VALUE = Coin.COIN.multiply(10);
    // pegin output to the federation is the first one
    private static final long PEGIN_OUTPUT_INDEX_TO_FED = 0;
    private static final Coin FLYOVER_PEGIN_VALUE = Coin.COIN;
    // flyover pegin has a single output, to the flyover federation address
    private static final long FLYOVER_PEGIN_OUTPUT_INDEX_TO_FED = 0;
    private static final int BTC_BLOCK_WITH_PMT_HEIGHT =
        BRIDGE_CONSTANTS.getBtcHeightWhenPegoutTxIndexActivates() + BRIDGE_CONSTANTS.getPegoutTxIndexGracePeriodInBtcBlocks();
    // svp fund tx change output comes after the proposed and flyover proposed federation outputs
    private static final int SVP_FUND_TX_CHANGE_OUTPUT_INDEX = 2;
    // svp spend tx sends everything back to the active federation in its only output
    private static final int SVP_SPEND_TX_OUTPUT_INDEX_TO_ACTIVE_FED = 0;
    private static final SignatureCache SIGNATURE_CACHE = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
    private static final Transaction FIRST_AUTHORIZED_TX = TransactionUtils.getTransactionFromCaller(
        SIGNATURE_CACHE,
        FederationChangeCaller.FIRST_AUTHORIZED.getRskAddress()
    );
    private static final Transaction SECOND_AUTHORIZED_TX = TransactionUtils.getTransactionFromCaller(
        SIGNATURE_CACHE,
        FederationChangeCaller.SECOND_AUTHORIZED.getRskAddress()
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
    private FederationStorageProvider federationStorageProvider;
    private FederationSupport federationSupport;
    private LockingCapSupport lockingCapSupport;
    private BridgeSupport bridgeSupport;
    private long updateCollectionsTxNonce = 0;
    private long newFederationCreationBlockNumber;
    private BtcTransaction delayedPegout;

    @BeforeEach
    void setup() throws Exception {
        repository = createRepository();
        repository.addBalance(BRIDGE_ADDRESS, co.rsk.core.Coin.fromBitcoin(BRIDGE_CONSTANTS.getMaxRbtc()));
        bridgeStorageProvider = new BridgeStorageProvider(repository, NETWORK_PARAMS, ALL_ACTIVATIONS);
        btcBlockStoreFactory =
            new RepositoryBtcBlockStoreWithCache.Factory(NETWORK_PARAMS, 100, 100);
        btcBlockStore =
            btcBlockStoreFactory.newInstance(repository, BRIDGE_CONSTANTS, bridgeStorageProvider, ALL_ACTIVATIONS);
        // Setting a chain head different from genesis to avoid having to read the checkpoints file
        addNewBtcBlockOnTipOfChain(btcBlockStore);
        repository.save();

        peginInstructionsProvider = new PeginInstructionsProvider();
        btcLockSenderProvider = new BtcLockSenderProvider();

        StorageAccessor bridgeStorageAccessor = new InMemoryStorage();
        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
        LockingCapStorageProvider lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);
        // start the locking cap at its maximum
        lockingCapStorageProvider.setLockingCap(BRIDGE_CONSTANTS.getMaxRbtc());
        lockingCapStorageProvider.save(ALL_ACTIVATIONS);

        logs = new ArrayList<>();
        var blockNumber = 0L;
        currentBlock = createRskBlock(blockNumber);

        bridgeEventLogger = new BridgeEventLoggerImpl(
            BRIDGE_CONSTANTS,
            ALL_ACTIVATIONS,
            logs
        );

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.SATOSHI);
        lockingCapSupport = new LockingCapSupportImpl(
            lockingCapStorageProvider,
            ALL_ACTIVATIONS,
            BRIDGE_CONSTANTS.getLockingCapConstants(),
            SIGNATURE_CACHE
        );
        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(FEDERATION_CONSTANTS)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(currentBlock)
            .withActivations(ALL_ACTIVATIONS)
            .build();
        bridgeSupport = BridgeSupportBuilder.builder()
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(currentBlock)
            .withActivations(ALL_ACTIVATIONS)
            .withBridgeConstants(BRIDGE_CONSTANTS)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withLockingCapSupport(lockingCapSupport)
            .build();
    }

    @Test
    void changeFederation_success() throws Exception {
        // arrange
        federationStorageProvider.setNewFederation(ORIGINAL_FEDERATION);
        // As many UTXOs as the max inputs of a migration tx, so the first migration tx is built at both RSKIP455 maximums
        sendPeginsToFed(ORIGINAL_FEDERATION, MAX_INPUTS_PER_MIGRAION_TX, FUNDING_PEGIN_VALUE);

        // Act
        // first, check normal peg flow to the active fed works
        var originalUTXOs = federationStorageProvider.getNewFederationBtcUTXOs(NETWORK_PARAMS, ALL_ACTIVATIONS);
        assertPegFlowThroughActiveFed(ORIGINAL_FEDERATION, ORIGINAL_FEDERATION_MEMBERS_KEYS, "sender0");

        // a pegout is released while the original fed is active, but the powpeg delays its registration
        advanceProcessUntilDelayedPegoutIsReleased(ORIGINAL_FEDERATION, ORIGINAL_FEDERATION_MEMBERS_KEYS, "delayedPegoutSender");

        advanceProcessUntilPendingFederationCommit();
        assertNewFederation();

        // check peg flow for both the new proposed fed and the original one
        assertPeginsShouldNotWorkToFed(NEW_FEDERATION, "sender1");

        // svp process
        advanceProcessUntilSvpFundTxIsRegistered();

        // peg flow
        assertPeginsShouldWorkToFed(ORIGINAL_FEDERATION, federationSupport.getActiveFederationBtcUTXOs(), "sender4");
        assertPeginsShouldNotWorkToFed(NEW_FEDERATION, "sender5");

        advanceProcessUntilSvpSpendTxIsRegistered();

        // peg flow
        assertPegFlowThroughActiveFed(ORIGINAL_FEDERATION, ORIGINAL_FEDERATION_MEMBERS_KEYS, "sender8");
        assertPeginsShouldNotWorkToFed(NEW_FEDERATION, "sender9");

        // after svp spend tx is registered, the proposed federation is
        // set as the new active one, and the original remains as the retiring
        assertFederationsHandover(originalUTXOs);

        // advance blockchain until the new fed activation starts.
        // migration shouldn't have started yet
        advanceBlockchainUntilNewFedActivationPhase();
        assertMigrationHasNotStarted();

        // peg flow should be available for both feds now
        assertPeginsShouldWorkToFed(ORIGINAL_FEDERATION, federationSupport.getRetiringFederationBtcUTXOs(), "sender10");
        assertPeginsShouldWorkToFed(NEW_FEDERATION, federationSupport.getActiveFederationBtcUTXOs(), "sender11");

        // Move blockchain until the migration phase
        advanceBlockchainUntilMigration();

        advanceProcessUntilMigrationIsDone();
        assertStatePostMigration();

        // peg flow should be available for new fed
        assertPegFlowThroughActiveFed(NEW_FEDERATION, NEW_FEDERATION_MEMBERS_KEYS, "sender12");
        // but not for the original one anymore
        assertPeginsShouldNotWorkToFed(ORIGINAL_FEDERATION, "sender13");
        // when the powpeg finally registers the delayed pegout, its change to the original fed is discarded
        assertPegoutChangeToNotLiveFedIsDiscarded(delayedPegout);
    }

    private void sendPeginsToFed(Federation federation, int numberOfPegins, Coin peginValue) throws Exception {
        for (int i = 0; i < numberOfPegins; i++) {
            sendPeginToFed(federation, "peginSender" + i, peginValue);
        }
    }

    private void sendPeginToFed(Federation federation, String senderSeed, Coin peginValue) throws Exception {
        var pegin = createLegacyP2pkhPegin(federation.getAddress(), senderSeed, peginValue);
        registerBtcTransaction(pegin);
    }

    private void advanceProcessUntilDelayedPegoutIsReleased(Federation federation, List<BtcECKey> federationMembersKeys, String senderSeed) throws Exception {
        // The user requests the pegout
        var pegoutRequestTx = buildPegoutRequestTransaction(
            co.rsk.core.Coin.fromBitcoin(PEGOUT_VALUE),
            0,
            RskTestUtils.getEcKeyFromSeed(senderSeed)
        );
        bridgeSupport.releaseBtc(pegoutRequestTx);
        bridgeSupport.save();

        // The bridge creates the pegout once the blocks between pegouts have passed
        currentBlock = createRskBlock(bridgeSupport.getNextPegoutCreationBlockNumber());
        advanceBlockchainTo(currentBlock);
        var pegoutCreationTx = buildUpdateCollectionsTransaction(updateCollectionsTxNonce++);
        callUpdateCollections(pegoutCreationTx);
        var pegoutCreationRskTxHash = pegoutCreationTx.getHash();

        // Once it has enough confirmations, the bridge moves it to be signed
        advanceBlockchainUntilPegoutConfirmation();
        delayedPegout = getReleaseWFSBtcTx(pegoutCreationRskTxHash);

        // The federation signs it, and the last required signature releases it.
        // The powpeg does not register it yet, so its change stays pending to the federation
        signPegout(pegoutCreationRskTxHash, delayedPegout, federation, federationMembersKeys);
    }

    private void advanceProcessUntilPendingFederationCommit() {
        createPendingFederation();
        addNewMembersToPendingFederation();
        var pendingFederation = federationStorageProvider.getPendingFederation();
        assertPendingFederationIsBuiltAsExpected(pendingFederation);

        commitPendingFederation(ORIGINAL_FEDERATION);
    }

    private void createPendingFederation() {
        // A single authorized vote does not reach quorum, so it must not create the pending federation yet
        var resultFromFirstAuthorizer = voteToCreatePendingFederation(FIRST_AUTHORIZED_TX);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromFirstAuthorizer);

        // Voting with enough authorizers to create the pending federation
        var resultFromSecondAuthorizer = voteToCreatePendingFederation(SECOND_AUTHORIZED_TX);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromSecondAuthorizer);

        assertEquals(0, federationSupport.getPendingFederationSize());
        assertNotNull(federationSupport.getPendingFederationHash());
    }

    private int voteToCreatePendingFederation(Transaction tx) {
        var createFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.CREATE.getKey(), new byte[][]{});
        return federationSupport.voteFederationChange(tx, createFederationAbiCallSpec, SIGNATURE_CACHE, bridgeEventLogger);
    }

    private void addNewMembersToPendingFederation() {
        var expectedPendingFederationSize = 0;

        for (FederationMember member : NEW_FEDERATION_MEMBERS) {
            var memberBtcKey = member.getBtcPublicKey();
            var memberRskKey = member.getRskPublicKey();
            var memberMstKey = member.getMstPublicKey();

            addFederatorPublicKeysToPendingFederation(memberBtcKey, memberRskKey, memberMstKey);

            assertEquals(++expectedPendingFederationSize, federationSupport.getPendingFederationSize());
            assertTrue(federationStorageProvider.getPendingFederation().getMembers().contains(member));
        }
    }

    private void addFederatorPublicKeysToPendingFederation(BtcECKey btcPublicKey, ECKey rskPublicKey, ECKey mstPublicKey) {
        int resultFromFirstAuthorizer = sendAddFederatorPublicKeysVote(FIRST_AUTHORIZED_TX, btcPublicKey, rskPublicKey, mstPublicKey);
        int resultFromSecondAuthorizer = sendAddFederatorPublicKeysVote(SECOND_AUTHORIZED_TX, btcPublicKey, rskPublicKey, mstPublicKey);

        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromFirstAuthorizer);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), resultFromSecondAuthorizer);
    }

    private int sendAddFederatorPublicKeysVote(Transaction tx, BtcECKey btcPublicKey, ECKey rskPublicKey, ECKey mstPublicKey) {
        ABICallSpec addFederatorAbiCallSpec = new ABICallSpec(FederationChangeFunction.ADD_MULTI.getKey(),
            new byte[][]{ btcPublicKey.getPubKey(), rskPublicKey.getPubKey(), mstPublicKey.getPubKey() }
        );

        return federationSupport.voteFederationChange(tx, addFederatorAbiCallSpec, SIGNATURE_CACHE, bridgeEventLogger);
    }

    private void commitPendingFederation(Federation activeFederationBeforeCommit) {
        // Pending Federation should exist
        var pendingFederation = federationStorageProvider.getPendingFederation();
        assertNotNull(pendingFederation);

        int logsStartIndex = logs.size();
        long expectedActivationBlockNumber = currentBlock.getNumber() + FEDERATION_CONSTANTS.getFederationActivationAge(ALL_ACTIVATIONS);

        var firstVoteResult = voteCommitPendingFederation(FIRST_AUTHORIZED_TX);
        var secondVoteResult = voteCommitPendingFederation(SECOND_AUTHORIZED_TX);

        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), firstVoteResult);
        assertEquals(FederationChangeResponseCode.SUCCESSFUL.getCode(), secondVoteResult);

        // Since the proposed federation is committed, it should be null in storage
        assertNull(federationStorageProvider.getPendingFederation());

        var committedFederation = federationSupport.getProposedFederation();
        assertTrue(committedFederation.isPresent());
        assertEquals(NEW_FEDERATION, committedFederation.get());
        assertCommitFederationEventWasEmitted(
            logsStartIndex,
            expectedActivationBlockNumber
        );
    }

    private int voteCommitPendingFederation(Transaction tx) {
        var pendingFederationHash = federationSupport.getPendingFederationHash();
        return voteCommitPendingFederationWithHash(tx, pendingFederationHash);
    }

    private int voteCommitPendingFederationWithHash(Transaction tx, Keccak256 pendingFederationHash) {
        var commitFederationAbiCallSpec = new ABICallSpec(FederationChangeFunction.COMMIT.getKey(), new byte[][]{ pendingFederationHash.getBytes() });

        return federationSupport.voteFederationChange(tx, commitFederationAbiCallSpec, SIGNATURE_CACHE, bridgeEventLogger);
    }

    private void advanceProcessUntilSvpFundTxIsRegistered() throws Exception {
        advanceBlockchainUntilSvpFundTxIsCreated();

        var svpFundTxEntry = getSvpFundTxWFC();
        var svpFundTx = svpFundTxEntry.getBtcTransaction();
        var svpFundTxCreationRskTxHash = svpFundTxEntry.getPegoutCreationRskTxHash();
        var svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned().orElseThrow();
        int logsStartIndex = logs.size();
        long svpFundTxCreationBlockNumber = currentBlock.getNumber();
        advanceBlockchainUntilPegoutConfirmation();

        assertLogPegoutConfirmed(logsStartIndex, svpFundTxHashUnsigned, svpFundTxEntry.getPegoutCreationRskBlockNumber());
        assertReleaseIsInPegoutsWFS(svpFundTxCreationRskTxHash, logsStartIndex, svpFundTxCreationBlockNumber);

        // peg flow
        assertPegFlowThroughActiveFed(ORIGINAL_FEDERATION, ORIGINAL_FEDERATION_MEMBERS_KEYS, "sender2");
        assertPeginsShouldNotWorkToFed(NEW_FEDERATION, "sender3");

        // the active federation signs it, and the last required signature releases it
        logsStartIndex = logs.size();
        signPegout(svpFundTxCreationRskTxHash, svpFundTx, federationSupport.getActiveFederation(), ORIGINAL_FEDERATION_MEMBERS_KEYS);
        // assert svp fund tx was released
        assertLogReleaseBtc(logsStartIndex, svpFundTxCreationRskTxHash, svpFundTx);
        assertFalse(bridgeStorageProvider.getPegoutsWaitingForSignatures().containsKey(svpFundTxCreationRskTxHash));

        // we have to simulate the powpeg sends the svp fund tx to be registered
        int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
        registerBtcTransaction(svpFundTx);
        assertSvpFundTxWasRegistered(activeFederationUtxosSizeBeforeRegisteringTx, svpFundTx);
    }

    private void advanceBlockchainUntilSvpFundTxIsCreated() throws Exception {
        // Get UTXO size before creating fund tx
        var activeFederationUtxosSizeBeforeCreatingFundTx =
            federationSupport.getActiveFederationBtcUTXOs().size();
        int logsStartIndex = logs.size();

        // Next call to update collections will create svp fund tx
        callUpdateCollections();

        assertSvpFundTxWasCreated(logsStartIndex, activeFederationUtxosSizeBeforeCreatingFundTx);
    }

    private PegoutsWaitingForConfirmations.Entry getSvpFundTxWFC() throws IOException {
        var svpFundTxHash = bridgeStorageProvider.getSvpFundTxHashUnsigned().orElseThrow();
        return bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(ALL_ACTIVATIONS).stream()
            .filter(entry -> entry.getBtcTransaction().getHash().equals(svpFundTxHash))
            .findFirst()
            .orElseThrow();
    }

    private void advanceProcessUntilSvpSpendTxIsRegistered() throws Exception {
        advanceBlockchainUntilSvpSpendTxIsCreated();

        // peg flow
        assertPegFlowThroughActiveFed(ORIGINAL_FEDERATION, ORIGINAL_FEDERATION_MEMBERS_KEYS, "sender6");
        assertPeginsShouldNotWorkToFed(NEW_FEDERATION, "sender7");

        var svpSpendTxWFS = getSvpSpendTxWFS();
        var svpSpendTxCreationRskTxHash = svpSpendTxWFS.getKey();
        var svpSpendTx = svpSpendTxWFS.getValue();
        int logsStartIndex = logs.size();
        signPegout(
            svpSpendTxCreationRskTxHash,
            svpSpendTx,
            NEW_FEDERATION,
            NEW_FEDERATION_MEMBERS_KEYS
        );
        assertLogReleaseBtc(logsStartIndex, svpSpendTxCreationRskTxHash, svpSpendTx);
        assertTrue(bridgeStorageProvider.getSvpSpendTxWaitingForSignatures().isEmpty());

        // we have to simulate the powpeg sends the svp spend tx to be registered
        var activeFederationUtxosSizeBeforeRegisteringSvpSpendTx = federationSupport.getActiveFederationBtcUTXOs().size();
        registerBtcTransaction(svpSpendTx);
        assertSvpSpendTxWasRegistered(svpSpendTx, activeFederationUtxosSizeBeforeRegisteringSvpSpendTx);
    }

    private void advanceBlockchainUntilSvpSpendTxIsCreated() throws Exception {
        int logsStartIndex = logs.size();

        // Next call to update collections will create svp spend tx
        callUpdateCollections();

        var svpFundTransactionSigned = bridgeStorageProvider.getSvpFundTxSigned();
        assertFalse(svpFundTransactionSigned.isPresent());
        var svpSpendTransactionHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
        assertTrue(svpSpendTransactionHashUnsigned.isPresent());
        var svpSpendTxWaitingForSignatures = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
        assertTrue(svpSpendTxWaitingForSignatures.isPresent());

        var svpSpendTxCreationHash = svpSpendTxWaitingForSignatures.get().getKey();
        var svpSpendTx = svpSpendTxWaitingForSignatures.get().getValue();
        Coin amountSentToActiveFed = svpSpendTx.getOutput(0).getValue();
        assertLogReleaseRequested(logsStartIndex, svpSpendTxCreationHash, svpSpendTx.getHash(), amountSentToActiveFed);
    }

    private Map.Entry<Keccak256, BtcTransaction> getSvpSpendTxWFS() {
        var svpSpendTxWaitingForSignatures = bridgeStorageProvider.getSvpSpendTxWaitingForSignatures();
        assertTrue(svpSpendTxWaitingForSignatures.isPresent());
        return svpSpendTxWaitingForSignatures.get();
    }

    private void advanceBlockchainUntilNewFedActivationPhase() {
        // Move to the block where the new powpeg becomes active, counted from its creation
        var blockNumber = newFederationCreationBlockNumber +
            FEDERATION_CONSTANTS.getFederationActivationAge(ALL_ACTIVATIONS);
        currentBlock = createRskBlock(blockNumber);

        advanceBlockchainTo(currentBlock);
    }

    private void advanceBlockchainUntilMigration() {
        // Move the required blocks ahead for the new federation to start migrating,
        // adding 1 as the migration is exclusive
        var blockNumber = currentBlock.getNumber() +
            FEDERATION_CONSTANTS.getFundsMigrationAgeSinceActivationBegin() +
            1L;
        currentBlock = createRskBlock(blockNumber);

        advanceBlockchainTo(currentBlock);
    }

    private void advanceProcessUntilMigrationIsDone() throws Exception {
        // Round 1: the retiring federation has more UTXOs than the max inputs per migration tx, and they sum up above
        // the large migration threshold, so the migration tx is built at both RSKIP455 maximums:
        // max inputs and max outputs, with the value evenly distributed among them
        int logsStartIndex = logs.size();
        var round1MigrationCreationTx = buildUpdateCollectionsTransaction(updateCollectionsTxNonce++);
        callUpdateCollections(round1MigrationCreationTx);
        assertMigrationHasStarted();
        assertFederationsPendingBtcUTXOsAreSaved();

        var round1MigrationEntry = getPegoutWFCEntry(round1MigrationCreationTx.getHash());
        assertMigrationTxWasCreated(round1MigrationEntry, logsStartIndex);
        assertMigrationTxHasMaxInputsAndOutputs(round1MigrationEntry.getBtcTransaction());

        // Check again live federations references are as expected
        assertNewAndOldFederationsReferences(NEW_FEDERATION, ORIGINAL_FEDERATION);
        assertActiveAndRetiringFederationsHaveExpectedAddress(NEW_FEDERATION.getAddress(), ORIGINAL_FEDERATION.getAddress());

        // Round 2: while round 1 waits for confirmations, users keep pegging in to the retiring federation.
        // Together with the UTXOs round 1 could not take, its balance is between the multiple outputs
        // thresholds, so the migration tx has multiple outputs of a fixed value
        sendPeginsToFed(ORIGINAL_FEDERATION, 50, PEGIN_VALUE);
        logsStartIndex = logs.size();
        var round2MigrationCreationTx = buildUpdateCollectionsTransaction(updateCollectionsTxNonce++);
        callUpdateCollections(round2MigrationCreationTx);
        assertFederationsPendingBtcUTXOsAreSaved();

        var round2MigrationEntry = getPegoutWFCEntry(round2MigrationCreationTx.getHash());
        assertMigrationTxWasCreated(round2MigrationEntry, logsStartIndex);
        assertMigrationTxHasFixedValueOutputs(round2MigrationEntry.getBtcTransaction());
        assertTrue(federationSupport.getRetiringFederationBtcUTXOs().isEmpty());

        // Round 3: a few more pegins to the retiring federation, now below the multiple outputs threshold,
        // so the migration tx has a single output
        assertPeginsShouldWorkToFed(ORIGINAL_FEDERATION, federationSupport.getRetiringFederationBtcUTXOs(), "sender14");
        assertPeginsShouldWorkToFed(NEW_FEDERATION, federationSupport.getActiveFederationBtcUTXOs(), "sender15");
        logsStartIndex = logs.size();
        var round3MigrationCreationTx = buildUpdateCollectionsTransaction(updateCollectionsTxNonce++);
        callUpdateCollections(round3MigrationCreationTx);
        assertFederationsPendingBtcUTXOsAreSaved();

        var round3MigrationEntry = getPegoutWFCEntry(round3MigrationCreationTx.getHash());
        assertMigrationTxWasCreated(round3MigrationEntry, logsStartIndex);
        assertMigrationTxHasOneOutput(round3MigrationEntry.getBtcTransaction());
        assertTrue(federationSupport.getRetiringFederationBtcUTXOs().isEmpty());

        // The three migration txs were created in the same block, so they get enough confirmations together.
        // The bridge moves them to be signed one per updateCollections call, sorted by their btc tx
        advanceBlockchainUntilPegoutsHaveEnoughConfirmations();

        var migrationEntriesInConfirmationOrder = Stream.of(round1MigrationEntry, round2MigrationEntry, round3MigrationEntry)
            .sorted(Entry.BTC_TX_COMPARATOR)
            .toList();
        advanceProcessUntilMigrationTxIsRegistered(migrationEntriesInConfirmationOrder.get(0));
        advanceProcessUntilMigrationTxIsRegistered(migrationEntriesInConfirmationOrder.get(1));
        advanceProcessUntilMigrationTxIsRegistered(migrationEntriesInConfirmationOrder.get(2));
    }

    private Entry getPegoutWFCEntry(Keccak256 pegoutCreationRskTxHash) throws IOException {
        return bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(ALL_ACTIVATIONS).stream()
            .filter(entry -> entry.getPegoutCreationRskTxHash().equals(pegoutCreationRskTxHash))
            .findFirst()
            .orElseThrow();
    }

    private void advanceProcessUntilMigrationTxIsRegistered(Entry migrationEntry) throws Exception {
        var migrationTxCreationRskTxHash = migrationEntry.getPegoutCreationRskTxHash();

        // the bridge moves the confirmed migration tx to be signed
        int logsStartIndex = logs.size();
        callUpdateCollections();
        assertReleaseIsInPegoutsWFS(migrationTxCreationRskTxHash, logsStartIndex, migrationEntry.getPegoutCreationRskBlockNumber());

        // the retiring federation signs it, and the last required signature releases it
        var migrationTx = getReleaseWFSBtcTx(migrationTxCreationRskTxHash);
        logsStartIndex = logs.size();
        signPegout(migrationTxCreationRskTxHash, migrationTx, ORIGINAL_FEDERATION, ORIGINAL_FEDERATION_MEMBERS_KEYS);
        assertLogReleaseBtc(logsStartIndex, migrationTxCreationRskTxHash, migrationTx);
        assertFalse(bridgeStorageProvider.getPegoutsWaitingForSignatures().containsKey(migrationTxCreationRskTxHash));

        // we have to simulate the powpeg sends the migration tx to be registered
        int activeFederationUtxosSizeBeforeRegisteringTx = federationSupport.getActiveFederationBtcUTXOs().size();
        registerPegoutTransaction(migrationTx);
        assertMigrationTxWasRegistered(activeFederationUtxosSizeBeforeRegisteringTx, migrationTx);
    }

    private void endMigration() throws Exception {
        // Move the required blocks ahead for the new federation to finish migrating,
        // adding 1 as the migration is exclusive
        var blockNumber = currentBlock.getNumber() +
            FEDERATION_CONSTANTS.getFundsMigrationAgeSinceActivationEnd(ALL_ACTIVATIONS) +
            1L;
        currentBlock = createRskBlock(blockNumber);

        advanceBlockchainTo(currentBlock);
        callUpdateCollections();

        // The next updateCollections, having no funds left to migrate, should get rid of the retiring fed
        currentBlock = createRskBlock(blockNumber + 1);
        advanceBlockchainTo(currentBlock);
        callUpdateCollections();
    }

    private void callUpdateCollections() throws Exception {
        // Each updateCollections call is a different rsk tx, as it happens in the network
        callUpdateCollections(buildUpdateCollectionsTransaction(updateCollectionsTxNonce++));
    }

    private void callUpdateCollections(Transaction updateCollectionsTx) throws Exception {
        bridgeSupport.updateCollections(updateCollectionsTx);
        bridgeSupport.save();
    }

    /**
     * Advances the chain until the pegout created in {@code releaseCreationRskTxHash} has enough confirmations,
     * and calls updateCollections so the bridge moves it to pegoutsWaitingForSignatures.
     * Assumes it is the only pegout waiting for confirmations, since only one is moved per updateCollections call.
     */
    private void advanceBlockchainUntilPegoutConfirmation() throws Exception {
        advanceBlockchainUntilPegoutsHaveEnoughConfirmations();
        callUpdateCollections();
    }

    private void advanceBlockchainUntilPegoutsHaveEnoughConfirmations() {
        currentBlock = createRskBlock(currentBlock.getNumber() + BRIDGE_CONSTANTS.getRsk2BtcMinimumAcceptableConfirmations() + 1L);
        advanceBlockchainTo(currentBlock);
    }

    private BtcTransaction getReleaseWFSBtcTx(Keccak256 releaseCreationRskTxHash) throws IOException {
        var pegoutsWFS = bridgeStorageProvider.getPegoutsWaitingForSignatures();
        assertTrue(pegoutsWFS.containsKey(releaseCreationRskTxHash));

        return pegoutsWFS.get(releaseCreationRskTxHash);
    }

    private void signPegout(Keccak256 releaseCreationRskTxHash, BtcTransaction pegoutTx, Federation signingFederation, List<BtcECKey> signingFederationKeys) throws IOException {
        int threshold = signingFederation.getNumberOfSignaturesRequired();
        var sigHashes = IntStream.range(0, pegoutTx.getInputs().size())
            .mapToObj(i -> BitcoinUtils.generateSigHashForSegwitTransactionInput(pegoutTx, i, pegoutTx.getInput(i).getValue()))
            .toList();

        for (BtcECKey signerKey : signingFederationKeys.subList(0, threshold)) {
            List<byte[]> signatures = BitcoinTestUtils.generateSignerEncodedSignatures(signerKey, sigHashes);
            bridgeSupport.addSignature(signerKey, signatures, releaseCreationRskTxHash);
        }
    }

    private void registerBtcTransaction(BtcTransaction btcTx) throws Exception {
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTransactionRegistration(
            bridgeStorageProvider,
            BRIDGE_CONSTANTS,
            BTC_BLOCK_WITH_PMT_HEIGHT,
            btcTx,
            btcBlockStore
        );

        bridgeSupport.registerBtcTransaction(
            REGISTRATION_TX,
            btcTx.bitcoinSerialize(),
            BTC_BLOCK_WITH_PMT_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();
    }

    private void registerFlyoverBtcTransaction(BtcTransaction flyoverPeginBtcTx) throws Exception {
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTransactionRegistration(
            bridgeStorageProvider,
            BRIDGE_CONSTANTS,
            BTC_BLOCK_WITH_PMT_HEIGHT,
            flyoverPeginBtcTx,
            btcBlockStore
        );

        bridgeSupport.registerFlyoverBtcTransaction(
            FLYOVER_REGISTRATION_TX,
            flyoverPeginBtcTx.bitcoinSerialize(),
            BTC_BLOCK_WITH_PMT_HEIGHT,
            pmt.bitcoinSerialize(),
            DERIVATION_ARGUMENTS_HASH,
            userRefundBtcAddress,
            LBC_ADDRESS,
            LIQUIDITY_PROVIDER_BTC_ADDRESS,
            true
        );
        bridgeSupport.save();
    }

    private void registerPegoutTransaction(BtcTransaction pegoutTx) throws Exception {
        PartialMerkleTree pmt = buildPMTAndRecreateChainForTxIdRegistration(
            bridgeStorageProvider,
            BRIDGE_CONSTANTS,
            BTC_BLOCK_WITH_PMT_HEIGHT,
            pegoutTx,
            btcBlockStore
        );

        bridgeSupport.registerPegoutTransaction(
            pegoutTx.getHash(),
            BTC_BLOCK_WITH_PMT_HEIGHT,
            pmt.bitcoinSerialize()
        );
        bridgeSupport.save();
    }

    private BtcTransaction createLegacyP2pkhPegin(Address federationAddress, String senderSeed) {
        return createLegacyP2pkhPegin(federationAddress, senderSeed, PEGIN_VALUE);
    }

    private BtcTransaction createLegacyP2pkhPegin(Address federationAddress, String senderSeed, Coin peginValue) {
        var peginBtcTx = new BtcTransaction(NETWORK_PARAMS);
        var senderPublicKey = BitcoinTestUtils.getBtcEcKeyFromSeed(senderSeed);

        peginBtcTx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, senderPublicKey));
        peginBtcTx.addOutput(peginValue, federationAddress);

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

        peginBtcTx.addOutput(PEGIN_VALUE, federationAddress);

        return peginBtcTx;
    }

    private BtcTransaction createPeginV1(Address federationAddress, String senderSeed, RskAddress destinationAddress) {
        var peginBtcTx = new BtcTransaction(NETWORK_PARAMS);
        var senderPublicKey = BitcoinTestUtils.getBtcEcKeyFromSeed(senderSeed);

        peginBtcTx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, senderPublicKey));
        peginBtcTx.addOutput(PEGIN_VALUE, federationAddress);
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
            ALL_ACTIVATIONS
        );

        var flyoverFederationAddress = PegUtils.getFlyoverFederationAddress(NETWORK_PARAMS, flyoverDerivationHash, federation);
        flyoverPeginBtcTx.addOutput(FLYOVER_PEGIN_VALUE, flyoverFederationAddress);

        return Pair.of(flyoverPeginBtcTx, flyoverDerivationHash);
    }

    private Coin getMigrationRequestedAmount(BtcTransaction migrationTransaction) {
        return migrationTransaction.getFee().add(migrationTransaction.getOutputSum());
    }

    // Every federation built in this class is an ErpFederation (P2SH-ERP or P2SH-P2WSH-ERP)
    private static Script getFederationDefaultP2SHScript(Federation federation) {
        return ((ErpFederation) federation).getDefaultP2SHScript();
    }

    private void advanceBlockchainTo(Block executionBlock) {
        federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(FEDERATION_CONSTANTS)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(ALL_ACTIVATIONS)
            .build();

        bridgeSupport = BridgeSupportBuilder.builder()
            .withProvider(bridgeStorageProvider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(executionBlock)
            .withActivations(ALL_ACTIVATIONS)
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

    private void assertPegFlowThroughActiveFed(Federation federation, List<BtcECKey> federationMembersKeys, String senderSeed) throws Exception {
        assertPeginsShouldWorkToFed(federation, federationSupport.getActiveFederationBtcUTXOs(), senderSeed);
        assertPegoutsShouldWorkToFed(federation, federationMembersKeys, senderSeed);
    }

    private void assertPeginsShouldWorkToFed(Federation federation, List<UTXO> federationUtxosReference, String senderSeed) throws Exception {
        var federationAddress = federation.getAddress();

        var senderRskAddress = BitcoinTestUtils.getRskAddressFromBtcPublicKey(BitcoinTestUtils.getBtcEcKeyFromSeed(senderSeed));
        var peginV1Receiver = RskTestUtils.generateAddress("receiver");

        var legacyP2pkhPegin = createLegacyP2pkhPegin(federationAddress, senderSeed);
        assertPeginWorks(legacyP2pkhPegin, federationAddress, federationUtxosReference, senderRskAddress, LEGACY_PEGIN_PROTOCOL_VERSION);

        var legacyP2shP2wpkhPegin = createLegacyP2shP2wpkhPegin(federationAddress, senderSeed);
        assertPeginWorks(legacyP2shP2wpkhPegin, federationAddress, federationUtxosReference, senderRskAddress, LEGACY_PEGIN_PROTOCOL_VERSION);

        var peginV1 = createPeginV1(federationAddress, senderSeed, peginV1Receiver);
        assertPeginWorks(peginV1, federationAddress, federationUtxosReference, peginV1Receiver, PEGIN_V1_PROTOCOL_VERSION);

        assertFlyoverPeginWorks(federation, federationUtxosReference, senderSeed);
    }

    private void assertPeginWorks(
        BtcTransaction pegin,
        Address federationAddress,
        List<UTXO> federationUtxosReference,
        RskAddress expectedReceiver,
        int expectedProtocolVersion
    ) throws Exception {
        assertPeginRegistration(pegin, federationUtxosReference, expectedReceiver, expectedProtocolVersion);

        assertLogUtxosRegistered(
            logs,
            pegin.getHash(),
            List.of(PEGIN_VALUE),
            List.of(PEGIN_OUTPUT_INDEX_TO_FED),
            federationAddress
        );
    }

    private void assertPeginRegistration(
        BtcTransaction pegin,
        List<UTXO> federationUtxosReference,
        RskAddress expectedReceiver,
        int expectedProtocolVersion
    ) throws Exception {
        int utxosSizeBeforeRegisteringPeginV1 = federationUtxosReference.size();
        int logsStartIndex = logs.size();
        int subtracesSizeBeforePegin = bridgeSupport.getSubtraces().size();
        var receiverBalanceBeforePegin = repository.getBalance(expectedReceiver);
        var bridgeBalanceBeforePegin = repository.getBalance(BRIDGE_ADDRESS);

        registerBtcTransaction(pegin);

        // assert pegin was processed
        assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(pegin.getHash()));
        // assert utxo was registered
        assertEquals(utxosSizeBeforeRegisteringPeginV1 + 1, federationUtxosReference.size());
        assertPeginBtcEventWasEmitted(logsStartIndex, pegin, expectedReceiver, expectedProtocolVersion);

        // assert funds were actually transferred from the bridge to the receiver
        var peginAmountInWeis = co.rsk.core.Coin.fromBitcoin(PEGIN_VALUE);
        assertEquals(receiverBalanceBeforePegin.add(peginAmountInWeis), repository.getBalance(expectedReceiver));
        assertEquals(bridgeBalanceBeforePegin.subtract(peginAmountInWeis), repository.getBalance(BRIDGE_ADDRESS));

        // assert an internal transaction recorded the transfer
        assertPeginInternalTransactionWasRecorded(subtracesSizeBeforePegin, expectedReceiver, peginAmountInWeis);
    }

    private void assertPeginBtcEventWasEmitted(
        int logsStartIndex,
        BtcTransaction peginTransaction,
        RskAddress expectedReceiver,
        int expectedProtocolVersion
    ) {
        List<LogInfo> logsSinceCheckpoint = logs.subList(logsStartIndex, logs.size());
        assertLogPeginBtc(logsSinceCheckpoint, peginTransaction, expectedReceiver, PEGIN_VALUE, expectedProtocolVersion);
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
        int logsStartIndex = logs.size();

        FederationChangeIT.this.assertFlyoverPeginWorks(flyoverPegin, federationUtxosReference);

        // Flyover crediting goes straight to the LBC contract; no PEGIN_BTC/LOCK_BTC event is emitted for it,
        // so its only event is flyover_utxos_registered
        assertEquals(logsStartIndex + 1, logs.size());
        assertLogFlyoverUtxosRegistered(
            logs.subList(logsStartIndex, logs.size()),
            flyoverPegin.getLeft().getHash(),
            List.of(FLYOVER_PEGIN_VALUE),
            List.of(FLYOVER_PEGIN_OUTPUT_INDEX_TO_FED),
            federation.getAddress(),
            flyoverPegin.getRight()
        );
    }

    private void assertFlyoverPeginWorks(Pair<BtcTransaction, Keccak256> flyoverPegin, List<UTXO> federationUtxosReference) throws Exception {
        int utxosSizeBeforeRegisteringFlyoverPegin = federationUtxosReference.size();
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

        // assert funds were actually transferred from the bridge to the LBC contract
        var flyoverPeginAmountInWeis = co.rsk.core.Coin.fromBitcoin(FLYOVER_PEGIN_VALUE);
        assertEquals(lbcBalanceBeforeFlyoverPegin.add(flyoverPeginAmountInWeis), repository.getBalance(LBC_ADDRESS));
        assertEquals(bridgeBalanceBeforeFlyoverPegin.subtract(flyoverPeginAmountInWeis), repository.getBalance(BRIDGE_ADDRESS));

        // assert an internal transaction recorded the transfer
        assertPeginInternalTransactionWasRecorded(subtracesSizeBeforeFlyoverPegin, LBC_ADDRESS, flyoverPeginAmountInWeis);
    }

    private void assertPegoutsShouldWorkToFed(Federation federation, List<BtcECKey> federationMembersKeys, String senderSeed) throws Exception {
        // The user requests the pegout
        var pegoutRequestTx = buildPegoutRequestTransaction(
            co.rsk.core.Coin.fromBitcoin(PEGOUT_VALUE),
            0,
            RskTestUtils.getEcKeyFromSeed(senderSeed)
        );
        bridgeSupport.releaseBtc(pegoutRequestTx);
        bridgeSupport.save();
        assertTrue(bridgeStorageProvider.getReleaseRequestQueue().getEntries().stream()
            .anyMatch(entry -> entry.getRskTxHash().equals(pegoutRequestTx.getHash())));

        // The bridge creates the pegout once the blocks between pegouts have passed
        currentBlock = createRskBlock(Math.max(currentBlock.getNumber(), bridgeSupport.getNextPegoutCreationBlockNumber()));
        advanceBlockchainTo(currentBlock);
        var pegoutCreationTx = buildUpdateCollectionsTransaction(updateCollectionsTxNonce++);
        callUpdateCollections(pegoutCreationTx);
        var pegoutCreationRskTxHash = pegoutCreationTx.getHash();

        // Once it has enough confirmations, the bridge moves it to be signed
        int logsStartIndex = logs.size();
        var pegoutCreationBlockNumber = currentBlock.getNumber();
        advanceBlockchainUntilPegoutConfirmation();
        assertReleaseIsInPegoutsWFS(pegoutCreationRskTxHash, logsStartIndex, pegoutCreationBlockNumber);
        var pegout = bridgeStorageProvider.getPegoutsWaitingForSignatures().get(pegoutCreationRskTxHash);

        // The federation signs it, and the last required signature releases it
        logsStartIndex = logs.size();
        signPegout(pegoutCreationRskTxHash, pegout, federation, federationMembersKeys);
        assertLogReleaseBtc(logsStartIndex, pegoutCreationRskTxHash, pegout);
        assertFalse(bridgeStorageProvider.getPegoutsWaitingForSignatures().containsKey(pegoutCreationRskTxHash));

        // We have to simulate the powpeg registers it once it is broadcast and mined
        int activeFederationUtxosSizeBeforeRegisteringPegout = federationSupport.getActiveFederationBtcUTXOs().size();
        registerPegoutTransaction(pegout);

        // The pegout change goes back to the federation
        var federationOutputScript = ScriptBuilder.createOutputScript(federation.getAddress());
        int changeOutputIndex = IntStream.range(0, pegout.getOutputs().size())
            .filter(outputIndex -> pegout.getOutput(outputIndex).getScriptPubKey().equals(federationOutputScript))
            .findFirst()
            .orElseThrow();
        assertEquals(activeFederationUtxosSizeBeforeRegisteringPegout + 1, federationSupport.getActiveFederationBtcUTXOs().size());
        assertReleaseWasRegistered(
            bridgeStorageProvider,
            federationSupport,
            logs,
            (int) currentBlock.getNumber(),
            pegout,
            List.of(pegout.getOutput(changeOutputIndex).getValue()),
            List.of((long) changeOutputIndex),
            federation.getAddress()
        );
    }

    private void assertReleaseIsInPegoutsWFS(Keccak256 releaseCreationRskTxHash, int logsStartIndex, long pegoutTxCreationBlockNumber) throws IOException {
        var pegoutWFSBtcTx = getReleaseWFSBtcTx(releaseCreationRskTxHash);
        assertLogPegoutConfirmed(logsStartIndex, pegoutWFSBtcTx.getHash(), pegoutTxCreationBlockNumber);
    }

    private void assertLogPegoutConfirmed(int logsStartIndex, Sha256Hash btcTxHash, long pegoutCreationRskBlockNumber) {
        List<LogInfo> logsSinceCheckpoint = logs.subList(logsStartIndex, logs.size());
        BridgeSupportTestUtil.assertLogPegoutConfirmed(logsSinceCheckpoint, btcTxHash, pegoutCreationRskBlockNumber);
    }

    private void assertLogReleaseBtc(int logsStartIndex, Keccak256 rskTxHash, BtcTransaction btcTx) {
        List<LogInfo> logsSinceCheckpoint = logs.subList(logsStartIndex, logs.size());
        BridgeSupportTestUtil.assertLogReleaseBtc(logsSinceCheckpoint, btcTx, rskTxHash);
    }

    private void assertPendingFederationIsBuiltAsExpected(PendingFederation pendingFederation) {
        assertNotNull(pendingFederation);
        assertEquals(NEW_FEDERATION_MEMBERS_SIZE, pendingFederation.getSize());
        assertTrue(pendingFederation.getMembers().containsAll(NEW_FEDERATION_MEMBERS));
    }

    private void assertCommitFederationEventWasEmitted(
        int logsStartIndex,
        long expectedActivationBlockNumber
    ) {
        List<LogInfo> logsSinceCheckpoint = logs.subList(logsStartIndex, logs.size());
        assertLogCommitFederation(logsSinceCheckpoint, ORIGINAL_FEDERATION, NEW_FEDERATION, expectedActivationBlockNumber);
    }

    private void assertNewFederation() {
        var newFederation = federationSupport.getProposedFederation();
        assertTrue(newFederation.isPresent());
        assertEquals(NEW_FEDERATION, newFederation.get());

        // The federation is created when the pending federation is committed, so its creation block depends on the flow
        newFederationCreationBlockNumber = newFederation.get().getCreationBlockNumber();
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

    private void assertPeginDoesNotWork(BtcTransaction pegin) throws Exception {
        int activeFederationUtxosSizeBeforeRegisteringPegin = federationSupport.getActiveFederationBtcUTXOs().size();
        int retiringFederationUtxosSizeBeforeRegisteringPegin = federationSupport.getRetiringFederationBtcUTXOs().size();
        int logsStartIndex = logs.size();
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
        assertNoEventWasEmittedSince(logsStartIndex);
        // assert no funds were moved and no internal transaction was recorded
        assertEquals(bridgeBalanceBeforePegin, repository.getBalance(BRIDGE_ADDRESS));
        assertEquals(subtracesSizeBeforePegin, bridgeSupport.getSubtraces().size());
    }

    private void assertNoEventWasEmittedSince(int logsStartIndex) {
        assertEquals(logsStartIndex, logs.size());
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

    private void assertFlyoverPeginDoesNotWork(Federation federation, String senderSeed) throws Exception {
        var flyoverPegin = createFlyoverPegin(federation, senderSeed);
        int activeFederationUtxosSizeBeforeRegisteringPegin = federationSupport.getActiveFederationBtcUTXOs().size();
        int retiringFederationUtxosSizeBeforeRegisteringPegin = federationSupport.getRetiringFederationBtcUTXOs().size();
        int logsStartIndex = logs.size();
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
        assertNoEventWasEmittedSince(logsStartIndex);
        // assert no funds were moved and no internal transaction was recorded
        assertEquals(bridgeBalanceBeforeFlyoverPegin, repository.getBalance(BRIDGE_ADDRESS));
        assertEquals(subtracesSizeBeforeFlyoverPegin, bridgeSupport.getSubtraces().size());
    }

    private void assertSvpFundTxWasCreated(int logsStartIndex, int activeFederationUtxosSizeBeforeCreatingFundTx) throws IOException {
        var svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();
        assertTrue(svpFundTxHashUnsigned.isPresent());
        assertEquals(activeFederationUtxosSizeBeforeCreatingFundTx - 1, federationSupport.getActiveFederationBtcUTXOs().size());

        var pegoutsTxs = bridgeStorageProvider
            .getPegoutsWaitingForConfirmations()
            .getEntries(ALL_ACTIVATIONS)
            .stream()
            .toList();
        assertEquals(1, pegoutsTxs.size());
        var fundTxEntry = pegoutsTxs.get(0);
        var fundTx = fundTxEntry.getBtcTransaction();
        // One output to the proposed federation, one to its flyover counterpart; the fee is absorbed
        // separately from the active federation's remaining balance, not from these two outputs.
        Coin requestedAmount = BRIDGE_CONSTANTS.getSvpFundTxOutputsValue().multiply(2);
        assertLogReleaseRequested(logsStartIndex, fundTxEntry.getPegoutCreationRskTxHash(), fundTx.getHash(), requestedAmount);
        assertLogPegoutTransactionCreated(logsStartIndex, fundTx);
    }

    private void assertLogReleaseRequested(
        int logsStartIndex,
        Keccak256 releaseCreationTxHash,
        Sha256Hash pegoutTransactionHash,
        Coin requestedAmount
    ) {
        List<LogInfo> logsSinceCheckpoint = logs.subList(logsStartIndex, logs.size());
        BridgeSupportTestUtil.assertLogReleaseRequested(logsSinceCheckpoint, releaseCreationTxHash, pegoutTransactionHash, requestedAmount);
    }

    private void assertLogPegoutTransactionCreated(int logsStartIndex, BtcTransaction pegoutTransaction) {
        List<LogInfo> logsSinceCheckpoint = logs.subList(logsStartIndex, logs.size());
        List<Coin> outpointValues = extractOutpointValues(pegoutTransaction);
        BridgeSupportTestUtil.assertLogPegoutTransactionCreated(logsSinceCheckpoint, pegoutTransaction, outpointValues);
    }

    private void assertSvpFundTxWasRegistered(int activeFederationUtxosSizeBeforeRegisteringTx, BtcTransaction svpFundTx) throws Exception {
        assertEquals(activeFederationUtxosSizeBeforeRegisteringTx + 1, federationSupport.getActiveFederationBtcUTXOs().size());
        var svpFundTxHashUnsigned = bridgeStorageProvider.getSvpFundTxHashUnsigned();
        assertFalse(svpFundTxHashUnsigned.isPresent());
        var svpFundTransactionSigned = bridgeStorageProvider.getSvpFundTxSigned();
        assertTrue(svpFundTransactionSigned.isPresent());

        Coin svpFundTxChangeValue = svpFundTx.getOutput(SVP_FUND_TX_CHANGE_OUTPUT_INDEX).getValue();
        assertLogUtxosRegistered(
            logs,
            svpFundTx.getHash(),
            List.of(svpFundTxChangeValue),
            List.of((long) SVP_FUND_TX_CHANGE_OUTPUT_INDEX),
            federationSupport.getActiveFederationAddress()
        );
    }

    private void assertSvpSpendTxWasRegistered(BtcTransaction svpSpendTx, int activeFederationUtxosSizeBeforeRegisteringTx) {
        assertLogUtxosRegistered(
            logs,
            svpSpendTx.getHash(),
            List.of(svpSpendTx.getOutput(SVP_SPEND_TX_OUTPUT_INDEX_TO_ACTIVE_FED).getValue()),
            List.of((long) SVP_SPEND_TX_OUTPUT_INDEX_TO_ACTIVE_FED),
            federationSupport.getActiveFederationAddress()
        );

        assertEquals(activeFederationUtxosSizeBeforeRegisteringTx + 1, federationSupport.getActiveFederationBtcUTXOs().size());
        var svpSpendTxHashUnsigned = bridgeStorageProvider.getSvpSpendTxHashUnsigned();
        assertFalse(svpSpendTxHashUnsigned.isPresent());
        var newFederationOpt = federationSupport.getProposedFederation();
        assertFalse(newFederationOpt.isPresent());
    }

    private void assertFederationsHandover(List<UTXO> originalUTXOs) {
        assertLastRetiredFederationP2SHScriptMatchesWithOriginalFederation();
        assertUTXOsReferenceMovedFromNewToOldFederation(originalUTXOs);
        assertNewAndOldFederationsReferences(NEW_FEDERATION, ORIGINAL_FEDERATION);
        assertNextFederationCreationBlockHeight(newFederationCreationBlockNumber);
    }

    private void assertLastRetiredFederationP2SHScriptMatchesWithOriginalFederation() {
        var lastRetiredFederationP2SHScriptOptional = federationStorageProvider.getLastRetiredFederationP2SHScript(ALL_ACTIVATIONS);
        assertTrue(lastRetiredFederationP2SHScriptOptional.isPresent());
        Script lastRetiredFederationP2SHScript = lastRetiredFederationP2SHScriptOptional.get();

        assertNotEquals(lastRetiredFederationP2SHScript, ORIGINAL_FEDERATION.getP2SHScript());
        assertEquals(lastRetiredFederationP2SHScript, getFederationDefaultP2SHScript(ORIGINAL_FEDERATION));
    }

    private void assertUTXOsReferenceMovedFromNewToOldFederation(List<UTXO> utxos) {
        // Assert old federation exists in storage
        assertNotNull(federationStorageProvider.getOldFederation(FEDERATION_CONSTANTS, ALL_ACTIVATIONS));
        // Assert new federation exists in storage
        assertNotNull(federationStorageProvider.getNewFederation(FEDERATION_CONSTANTS, ALL_ACTIVATIONS));
        // Assert old federation holds the original utxos
        List<UTXO> utxosToMigrate = federationStorageProvider.getOldFederationBtcUTXOs();
        assertTrue(utxosToMigrate.containsAll(utxos));
        // Assert the new federation does not have any utxos yet
        assertTrue(federationStorageProvider
            .getNewFederationBtcUTXOs(NETWORK_PARAMS, ALL_ACTIVATIONS)
            .isEmpty()
        );
    }

    private void assertNewAndOldFederationsReferences(Federation expectedNewFederation, Federation expectedOldFederation) {
        FederationConstants federationConstants = FEDERATION_CONSTANTS;
        assertEquals(expectedNewFederation, federationStorageProvider.getNewFederation(federationConstants, ALL_ACTIVATIONS));
        assertEquals(expectedOldFederation, federationStorageProvider.getOldFederation(federationConstants, ALL_ACTIVATIONS));
    }

    private void assertNextFederationCreationBlockHeight(long newFederationCreationBlockNumber) {
        Optional<Long> nextFederationCreationBlockHeight = federationStorageProvider.getNextFederationCreationBlockHeight(ALL_ACTIVATIONS);
        assertTrue(nextFederationCreationBlockHeight.isPresent());
        assertEquals(newFederationCreationBlockNumber, nextFederationCreationBlockHeight.get());
    }

    private void assertMigrationHasNotStarted() throws Exception {
        // Current block is behind the new federation's creation + fedActivationAge + fundsMigrationAgeBegin
        var blockNumber = newFederationCreationBlockNumber +
            FEDERATION_CONSTANTS.getFederationActivationAge(ALL_ACTIVATIONS) +
            FEDERATION_CONSTANTS.getFundsMigrationAgeSinceActivationBegin();
        assertTrue(currentBlock.getNumber() <= blockNumber);

        // Pegouts waiting for confirmations should be empty
        assertTrue(bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(ALL_ACTIVATIONS).isEmpty());
    }

    private void assertMigrationHasStarted() throws Exception {
        // Pegouts waiting for confirmations should not be empty
        // Expecting only one element since the retiring federation had less than 50 UTXOs
        assertEquals(1, bridgeStorageProvider.getPegoutsWaitingForConfirmations().getEntries(ALL_ACTIVATIONS).size());
    }

    private void assertFederationsPendingBtcUTXOsAreSaved() throws IOException {
        var pegoutsTxs = bridgeStorageProvider.getPegoutsWaitingForConfirmations()
            .getEntries(ALL_ACTIVATIONS).stream()
            .map(Entry::getBtcTransaction)
            .toList();

        for (var pegoutTx : pegoutsTxs) {
            assertFederationsPendingBtcUTXOsWereSaved(federationStorageProvider, federationSupport, pegoutTx);
        }
    }

    private void assertMigrationTxWasCreated(Entry migrationEntry, int logsStartIndex) {
        BtcTransaction migrationTransaction = migrationEntry.getBtcTransaction();

        assertLogReleaseRequested(
            logsStartIndex,
            migrationEntry.getPegoutCreationRskTxHash(),
            migrationTransaction.getHash(),
            getMigrationRequestedAmount(migrationTransaction)
        );
        assertLogPegoutTransactionCreated(logsStartIndex, migrationTransaction);
    }

    private void assertMigrationTxHasMaxInputsAndOutputs(BtcTransaction migrationTransaction) {
        // above the large migration threshold, the value is evenly distributed among the max outputs
        assertEquals(MAX_INPUTS_PER_MIGRAION_TX, migrationTransaction.getInputs().size());
        assertEquals(BRIDGE_CONSTANTS.getMaxOutputsPerMigrationTransaction(), migrationTransaction.getOutputs().size());
        assertMigrationTxHasExpectedMultipleOutputs(migrationTransaction);
    }

    private void assertMigrationTxHasExpectedMultipleOutputs(BtcTransaction migrationTransaction) {
        List<Coin> expectedOutputValues = BridgeUtils.calculateMigrationTransactionOutputsValues(
            getMigrationRequestedAmount(migrationTransaction),
            BRIDGE_CONSTANTS
        );
        ReleaseTransactionAssertions.assertMigrationTxWithMultipleOutputs(
            migrationTransaction,
            expectedOutputValues,
            NEW_FEDERATION.getAddress(),
            NETWORK_PARAMS
        );
    }

    private void assertActiveAndRetiringFederationsHaveExpectedAddress(Address expectedNewFederationAddress, Address expectedOldFederationAddress) {
        assertEquals(expectedNewFederationAddress, bridgeSupport.getActiveFederationAddress());
        Optional<Address> retiringFederationAddress = bridgeSupport.getRetiringFederationAddress();
        assertTrue(retiringFederationAddress.isPresent());
        assertEquals(expectedOldFederationAddress, retiringFederationAddress.get());
    }

    private void assertMigrationTxHasFixedValueOutputs(BtcTransaction migrationTransaction) {
        // between the multiple outputs thresholds, the value is split in outputs of a fixed value, below the max outputs
        assertTrue(migrationTransaction.getOutputs().size() > 1);
        assertTrue(migrationTransaction.getOutputs().size() < BRIDGE_CONSTANTS.getMaxOutputsPerMigrationTransaction());
        assertMigrationTxHasExpectedMultipleOutputs(migrationTransaction);
    }

    private void assertMigrationTxHasOneOutput(BtcTransaction migrationTransaction) {
        ReleaseTransactionAssertions.assertOneMigrationTxOutput(
            migrationTransaction,
            getMigrationRequestedAmount(migrationTransaction),
            NEW_FEDERATION.getAddress(),
            NETWORK_PARAMS
        );
    }

    private void assertMigrationTxWasRegistered(int activeFederationUtxosSizeBeforeRegisteringTx, BtcTransaction migrationTx) throws IOException {
        // every migration tx output is a new utxo for the new federation
        int migrationTxOutputsSize = migrationTx.getOutputs().size();
        assertEquals(activeFederationUtxosSizeBeforeRegisteringTx + migrationTxOutputsSize, federationSupport.getActiveFederationBtcUTXOs().size());
        int currentBlockNumber = (int) currentBlock.getNumber();
        List<Coin> expectedOutputsValues = migrationTx.getOutputs().stream().map(TransactionOutput::getValue).toList();
        List<Long> expectedOutputsIndexes = LongStream.range(0, migrationTxOutputsSize).boxed().toList();
        assertReleaseWasRegistered(
            bridgeStorageProvider,
            federationSupport,
            logs,
            currentBlockNumber,
            migrationTx,
            expectedOutputsValues,
            expectedOutputsIndexes,
            NEW_FEDERATION.getAddress()
        );
    }

    private void assertStatePostMigration() throws Exception {
        // Move blockchain until the end of the migration phase: the retiring federation is fully drained above,
        // so it clears with no funds left behind.
        endMigration();

        assertOnlyActiveFedIsLive();
    }

    private void assertOnlyActiveFedIsLive() {
        // New active federation still there, retiring federation no longer there
        assertEquals(NEW_FEDERATION, bridgeSupport.getActiveFederation());
        Optional<Address> retiringFederationAddress = bridgeSupport.getRetiringFederationAddress();
        assertTrue(retiringFederationAddress.isEmpty());
    }

    private void assertPegoutChangeToNotLiveFedIsDiscarded(BtcTransaction pegout) throws Exception {
        // The pegout change is still pending to a federation that is no longer live
        assertTrue(federationSupport.hasFederationsPendingBtcUTXOs(pegout.getHash()));
        int activeFederationUtxosSizeBeforeRegisteringPegout = federationSupport.getActiveFederationBtcUTXOs().size();
        int logsStartIndex = logs.size();

        // We have to simulate the powpeg registers it
        registerPegoutTransaction(pegout);

        // assert pegout was processed
        assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(pegout.getHash()));
        // assert its pending utxos were discarded, since they do not belong to any live federation
        assertFalse(federationSupport.hasFederationsPendingBtcUTXOs(pegout.getHash()));
        // assert no utxos were registered
        assertEquals(activeFederationUtxosSizeBeforeRegisteringPegout, federationSupport.getActiveFederationBtcUTXOs().size());
        assertNoEventWasEmittedSince(logsStartIndex);
    }
}
