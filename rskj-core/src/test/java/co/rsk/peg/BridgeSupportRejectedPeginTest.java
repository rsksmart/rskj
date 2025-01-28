package co.rsk.peg;

import static co.rsk.peg.BridgeSupportTestUtil.createValidPmtForTransactions;
import static co.rsk.peg.BridgeSupportTestUtil.mockChainOfStoredBlocks;
import static co.rsk.peg.pegin.RejectedPeginReason.INVALID_AMOUNT;
import static co.rsk.peg.pegin.RejectedPeginReason.PEGIN_V1_INVALID_PAYLOAD;
import static co.rsk.peg.utils.NonRefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.PartialMerkleTree;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationArgs;
import co.rsk.peg.federation.FederationFactory;
import co.rsk.peg.federation.FederationMember;
import co.rsk.peg.federation.FederationStorageProvider;
import co.rsk.peg.federation.FederationSupport;
import co.rsk.peg.federation.FederationTestUtils;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.lockingcap.LockingCapSupport;
import co.rsk.peg.pegin.RejectedPeginReason;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.NonRefundablePeginReason;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BridgeSupportRejectedPeginTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final FederationConstants federationMainnetConstants = bridgeMainnetConstants.getFederationConstants();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final ActivationConfig.ForBlock arrowHeadActivations = ActivationConfigsForTest.arrowhead600()
        .forBlock(0);
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all()
        .forBlock(0);

    private static final Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(
        ActivationConfigsForTest.all().forBlock(0));
    private static final Coin belowMinimumPeginTxValue = minimumPeginTxValue.minus(Coin.SATOSHI);

    private static final int FIRST_OUTPUT_INDEX = 0;

    private Repository repository;
    private BridgeStorageProvider provider;
    private FederationStorageProvider federationStorageProvider;

    private Address userAddress;

    private Federation activeFederation;
    private Federation retiringFederation;

    private BtcBlockStoreWithCache.Factory mockFactory;
    private BridgeEventLogger bridgeEventLogger;
    private BtcLockSenderProvider btcLockSenderProvider;
    private PeginInstructionsProvider peginInstructionsProvider;

    private final List<UTXO> retiringFederationUtxos = new ArrayList<>();
    private final List<UTXO> activeFederationUtxos = new ArrayList<>();
    private PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations;
    private Block rskExecutionBlock;
    private Transaction rskTx;

    private int heightAtWhichToStartUsingPegoutIndex;

    private co.rsk.bitcoinj.core.BtcBlock registerHeader;

    public static Stream<Arguments> activationsProvider() {
        return Stream.of(
            Arguments.of(allActivations),
            Arguments.of(arrowHeadActivations)
        );
    }

    @BeforeEach
    void init() throws IOException {
        registerHeader = null;

        userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");
        NetworkParameters btcParams = bridgeMainnetConstants.getBtcParams();

        List<BtcECKey> erpPubKeys = federationMainnetConstants.getErpFedPubKeysList();
        long activationDelay = federationMainnetConstants.getErpFedActivationDelay();

        List<BtcECKey> retiringFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa04", "fa05", "fa06"}, true
        );
        retiringFedSigners.sort(BtcECKey.PUBKEY_COMPARATOR);
        List<FederationMember> retiringFedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(
            retiringFedSigners);
        Instant retiringCreationTime = Instant.ofEpochMilli(1000L);
        long retiringFedCreationBlockNumber = 1;

        FederationArgs retiringFedArgs =
            new FederationArgs(retiringFedMembers, retiringCreationTime,
                retiringFedCreationBlockNumber, btcParams);
        retiringFederation = FederationFactory.buildP2shErpFederation(retiringFedArgs, erpPubKeys,
            activationDelay);

        List<BtcECKey> activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa07", "fa08", "fa09", "fa10", "fa11"}, true
        );
        activeFedSigners.sort(BtcECKey.PUBKEY_COMPARATOR);
        List<FederationMember> activeFedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(
            activeFedSigners);
        long activeFedCreationBlockNumber = 2L;
        Instant creationTime = Instant.ofEpochMilli(1000L);
        FederationArgs activeFedArgs =
            new FederationArgs(activeFedMembers, creationTime, activeFedCreationBlockNumber,
                btcParams);
        activeFederation = FederationFactory.buildP2shErpFederation(activeFedArgs, erpPubKeys,
            activationDelay);

        mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        bridgeEventLogger = mock(BridgeEventLogger.class);
        btcLockSenderProvider = new BtcLockSenderProvider();

        peginInstructionsProvider = new PeginInstructionsProvider();

        provider = mock(BridgeStorageProvider.class);
        when(provider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(
            Optional.empty());

        repository = mock(Repository.class);
        when(repository.getBalance(PrecompiledContracts.BRIDGE_ADDR)).thenReturn(
            co.rsk.core.Coin.fromBitcoin(bridgeMainnetConstants.getMaxRbtc()));
        LockingCapSupport lockingCapSupport = mock(LockingCapSupport.class);
        when(lockingCapSupport.getLockingCap()).thenReturn(
            Optional.of(bridgeMainnetConstants.getMaxRbtc()));

        federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getOldFederationBtcUTXOs())
            .thenReturn(retiringFederationUtxos);
        when(federationStorageProvider.getNewFederationBtcUTXOs(any(NetworkParameters.class),
            any(ActivationConfig.ForBlock.class)))
            .thenReturn(activeFederationUtxos);

        pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(
            pegoutsWaitingForConfirmations);

        when(federationStorageProvider.getNewFederation(any(FederationConstants.class),
            any(ActivationConfig.ForBlock.class)))
            .thenReturn(activeFederation);

        // Set execution block right after the fed creation block
        long executionBlockNumber = activeFederation.getCreationBlockNumber() + 1;
        rskExecutionBlock = mock(Block.class);

        when(rskExecutionBlock.getNumber()).thenReturn(executionBlockNumber);

        rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(PegTestUtils.createHash3(1));

        int btcHeightWhenPegoutTxIndexActivates = bridgeMainnetConstants.getBtcHeightWhenPegoutTxIndexActivates();
        int pegoutTxIndexGracePeriodInBtcBlocks = bridgeMainnetConstants.getPegoutTxIndexGracePeriodInBtcBlocks();

        heightAtWhichToStartUsingPegoutIndex =
            btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;
    }

    private PartialMerkleTree createPmtAndMockBlockStore(BtcTransaction btcTransaction)
        throws BlockStoreException {

        PartialMerkleTree pmt = createValidPmtForTransactions(
            Collections.singletonList(btcTransaction.getHash()), btcMainnetParams);

        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcMainnetParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"),
            heightAtWhichToStartUsingPegoutIndex);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcMainnetParams,
            1,
            BitcoinTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"),
            heightAtWhichToStartUsingPegoutIndex
                + bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations());
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        co.rsk.bitcoinj.core.BtcBlock btcBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcMainnetParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        mockChainOfStoredBlocks(
            btcBlockStore,
            btcBlock,
            heightAtWhichToStartUsingPegoutIndex
                + bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations(),
            heightAtWhichToStartUsingPegoutIndex
        );
        return pmt;
    }

    @ParameterizedTest
    @MethodSource("activationsProvider")
    void registerBtcTransaction_whenBelowTheMinimum_shouldRejectPegin(
        ActivationConfig.ForBlock activations)
        throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX,
            new Script(new byte[]{}));
        btcTransaction.addOutput(belowMinimumPeginTxValue, activeFederation.getAddress());

        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .withRskExecutionBlock(rskExecutionBlock)
            .build();

        BridgeSupport bridgeSupport = BridgeSupportBuilder.builder()
            .withBtcBlockStoreFactory(mockFactory)
            .withBridgeConstants(bridgeMainnetConstants)
            .withRepository(repository)
            .withProvider(provider)
            .withActivations(activations)
            .withEventLogger(bridgeEventLogger)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withExecutionBlock(rskExecutionBlock)
            .withFederationSupport(federationSupport)
            .build();

        // act
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            heightAtWhichToStartUsingPegoutIndex,
            createPmtAndMockBlockStore(btcTransaction).bitcoinSerialize()
        );

        // assert

        // tx should be marked as processed since RSKIP459 is active
        var shouldMarkTxAsProcessed = activations == allActivations ? times(1) : never();
        verify(provider, shouldMarkTxAsProcessed).setHeightBtcTxhashAlreadyProcessed(any(),
            anyLong());

        verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, INVALID_AMOUNT);
        verify(bridgeEventLogger, times(1)).logNonRefundablePegin(btcTransaction,
            NonRefundablePeginReason.INVALID_AMOUNT);
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("activationsProvider")
    void registerBtcTransaction_whenUndeterminedSender_shouldRejectPegin(
        ActivationConfig.ForBlock activations)
        throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        // return empty to simulate undetermined sender
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            new Script(new byte[]{})
        );
        btcTransaction.addOutput(amountToSend, activeFederation.getAddress());

        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .withRskExecutionBlock(rskExecutionBlock)
            .build();

        BridgeSupport bridgeSupport = BridgeSupportBuilder.builder()
            .withBtcBlockStoreFactory(mockFactory)
            .withBridgeConstants(bridgeMainnetConstants)
            .withRepository(repository)
            .withProvider(provider)
            .withActivations(activations)
            .withEventLogger(bridgeEventLogger)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withExecutionBlock(rskExecutionBlock)
            .withFederationSupport(federationSupport)
            .build();

        // act
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            heightAtWhichToStartUsingPegoutIndex,
            createPmtAndMockBlockStore(btcTransaction).bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, times(1)).logRejectedPegin(
            btcTransaction, RejectedPeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER
        );
        verify(bridgeEventLogger, times(1)).logNonRefundablePegin(
            btcTransaction,
            LEGACY_PEGIN_UNDETERMINED_SENDER
        );

        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(), any(), any());

        // tx should be marked as processed since RSKIP459 is active
        var shouldMarkTxAsProcessed = activations == allActivations ? times(1) : never();
        verify(provider, shouldMarkTxAsProcessed).setHeightBtcTxhashAlreadyProcessed(any(),
            anyLong());

        Assertions.assertTrue(activeFederationUtxos.isEmpty());
        Assertions.assertTrue(retiringFederationUtxos.isEmpty());
        Assertions.assertTrue(pegoutsWaitingForConfirmations.getEntries().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("activationsProvider")
    void registerBtcTransaction_whenPeginV1WithInvalidPayloadAndUnderminedSender_shouldRejectPegin(
        ActivationConfig.ForBlock activations)
        throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        // return empty to simulate undetermined sender
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            new Script(new byte[]{})
        );
        btcTransaction.addOutput(Coin.COIN, activeFederation.getAddress());
        btcTransaction.addOutput(Coin.ZERO,
            PegTestUtils.createOpReturnScriptForRskWithCustomPayload(1, new byte[]{}));

        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .withRskExecutionBlock(rskExecutionBlock)
            .build();

        BridgeSupport bridgeSupport = BridgeSupportBuilder.builder()
            .withBtcBlockStoreFactory(mockFactory)
            .withBridgeConstants(bridgeMainnetConstants)
            .withRepository(repository)
            .withProvider(provider)
            .withActivations(activations)
            .withEventLogger(bridgeEventLogger)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withExecutionBlock(rskExecutionBlock)
            .withFederationSupport(federationSupport)
            .build();

        // act
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            heightAtWhichToStartUsingPegoutIndex,
            createPmtAndMockBlockStore(btcTransaction).bitcoinSerialize()
        );

        // assert

        // tx should be marked as processed since RSKIP459 is active
        var shouldMarkTxAsProcessed = activations == allActivations ? times(1) : never();
        verify(provider, shouldMarkTxAsProcessed).setHeightBtcTxhashAlreadyProcessed(any(),
            anyLong());

        verify(bridgeEventLogger, times(1)).logRejectedPegin(
            btcTransaction, PEGIN_V1_INVALID_PAYLOAD
        );
        verify(bridgeEventLogger, times(1)).logNonRefundablePegin(
            btcTransaction,
            LEGACY_PEGIN_UNDETERMINED_SENDER
        );

        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(), any(), any());
        verify(bridgeEventLogger, never()).logPegoutTransactionCreated(any(), any());

        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
        assertTrue(pegoutsWaitingForConfirmations.getEntries().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("activationsProvider")
    void registerBtcTransaction_whenUtxoToActiveFedBelowMinimumAndUtxoToRetiringFedAboveMinimum_shouldRejectPegin(
        ActivationConfig.ForBlock activations
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX,
            new Script(new byte[]{}));
        btcTransaction.addOutput(belowMinimumPeginTxValue, activeFederation.getAddress());
        btcTransaction.addOutput(minimumPeginTxValue, retiringFederation.getAddress());

        when(federationStorageProvider.getOldFederation(federationMainnetConstants,
            activations)).thenReturn(retiringFederation);
        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .withRskExecutionBlock(rskExecutionBlock)
            .build();

        BridgeSupport bridgeSupport = BridgeSupportBuilder.builder()
            .withBtcBlockStoreFactory(mockFactory)
            .withBridgeConstants(bridgeMainnetConstants)
            .withRepository(repository)
            .withProvider(provider)
            .withActivations(activations)
            .withEventLogger(bridgeEventLogger)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withExecutionBlock(rskExecutionBlock)
            .withFederationSupport(federationSupport)
            .build();

        // act
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            heightAtWhichToStartUsingPegoutIndex,
            createPmtAndMockBlockStore(btcTransaction).bitcoinSerialize()
        );

        // assert
        var shouldMarkTxAsProcessed = activations == allActivations ? times(1) : never();
        verify(provider, shouldMarkTxAsProcessed).setHeightBtcTxhashAlreadyProcessed(any(),
            anyLong());

        verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction,
            RejectedPeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER);
        verify(bridgeEventLogger, times(1)).logNonRefundablePegin(btcTransaction,
            NonRefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER);
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    // flyover pegin
    @ParameterizedTest
    @MethodSource("activationsProvider")
    void registerBtcTransaction_whenAttemptToRegisterFlyoverPegin_shouldIgnorePegin(
        ActivationConfig.ForBlock activations)
        throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams,
            "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams,
            "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .withRskExecutionBlock(rskExecutionBlock)
            .build();

        BridgeSupport bridgeSupport = BridgeSupportBuilder.builder()
            .withBtcBlockStoreFactory(mockFactory)
            .withBridgeConstants(bridgeMainnetConstants)
            .withRepository(repository)
            .withProvider(provider)
            .withActivations(activations)
            .withEventLogger(bridgeEventLogger)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withExecutionBlock(rskExecutionBlock)
            .withFederationSupport(federationSupport)
            .build();

        Keccak256 flyoverDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address flyoverFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeMainnetConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        BtcTransaction btcTransaction = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX,
            new Script(new byte[]{}));
        btcTransaction.addOutput(minimumPeginTxValue, flyoverFederationAddress);

        // act
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            heightAtWhichToStartUsingPegoutIndex,
            createPmtAndMockBlockStore(btcTransaction).bitcoinSerialize()
        );

        assertUnknownTxIsIgnored();
    }

    private void assertUnknownTxIsIgnored() throws IOException {
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logNonRefundablePegin(any(), any());
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("activationsProvider")
    void registerBtcTransaction_whenNoUtxoToFed_shouldIgnorePegin(
        ActivationConfig.ForBlock activations)
        throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX,
            new Script(new byte[]{}));
        btcTransaction.addOutput(minimumPeginTxValue, userAddress);

        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .withRskExecutionBlock(rskExecutionBlock)
            .build();

        BridgeSupport bridgeSupport = BridgeSupportBuilder.builder()
            .withBtcBlockStoreFactory(mockFactory)
            .withBridgeConstants(bridgeMainnetConstants)
            .withRepository(repository)
            .withProvider(provider)
            .withActivations(activations)
            .withEventLogger(bridgeEventLogger)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withExecutionBlock(rskExecutionBlock)
            .withFederationSupport(federationSupport)
            .build();

        // act
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            heightAtWhichToStartUsingPegoutIndex,
            createPmtAndMockBlockStore(btcTransaction).bitcoinSerialize()
        );

        // assert
        assertUnknownTxIsIgnored();
    }
}
