package co.rsk.peg;

import static co.rsk.peg.BridgeSupportTestUtil.mockChainOfStoredBlocks;
import static co.rsk.peg.PegTestUtils.createFederation;
import static co.rsk.peg.bitcoin.UtxoUtils.extractOutpointValues;
import static co.rsk.peg.pegin.RejectedPeginReason.INVALID_AMOUNT;
import static co.rsk.peg.pegin.RejectedPeginReason.PEGIN_V1_INVALID_PAYLOAD;
import static co.rsk.peg.utils.UnrefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.PegoutsWaitingForConfirmations.Entry;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.CoinbaseInformation;
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
import co.rsk.peg.feeperkb.FeePerKbStorageProvider;
import co.rsk.peg.feeperkb.FeePerKbStorageProviderImpl;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.feeperkb.FeePerKbSupportImpl;
import co.rsk.peg.lockingcap.LockingCapSupport;
import co.rsk.peg.pegin.RejectedPeginReason;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.storage.BridgeStorageAccessorImpl;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.UnrefundablePeginReason;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.peg.whitelist.WhitelistStorageProvider;
import co.rsk.peg.whitelist.WhitelistSupportImpl;
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
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Repository;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RejectedPeginPocTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final FederationConstants federationMainnetConstants = bridgeMainnetConstants.getFederationConstants();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final ActivationConfig.ForBlock activations = ActivationConfigsForTest.all()
        .forBlock(0);
    private WhitelistStorageProvider whitelistStorageProvider;

    private static final Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(
        ActivationConfigsForTest.all().forBlock(0));
    private static final Coin belowMinimumPeginTxValue = minimumPeginTxValue.minus(Coin.SATOSHI);

    private static final int FIRST_OUTPUT_INDEX = 0;

    private BridgeStorageProvider provider;
    private FederationStorageProvider federationStorageProvider;
    private Address userAddress;

    private List<BtcECKey> activeFedSigners;
    private Federation activeFederation;

    private BtcBlockStoreWithCache.Factory mockFactory;
    private SignatureCache signatureCache;
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

    private void assertUnknownTxIsRejectedWithInvalidAmountReason(BtcTransaction btcTransaction)
        throws IOException {
        verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, INVALID_AMOUNT);
        verify(bridgeEventLogger, times(1)).logUnrefundablePegin(btcTransaction,
            UnrefundablePeginReason.INVALID_AMOUNT);
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    private void assertLegacyUndeterminedSenderPeginIsRejected(BtcTransaction btcTransaction)
        throws IOException {
        verify(bridgeEventLogger, times(1)).logRejectedPegin(
            btcTransaction, RejectedPeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER
        );
        verify(bridgeEventLogger, times(1)).logUnrefundablePegin(
            btcTransaction,
            LEGACY_PEGIN_UNDETERMINED_SENDER
        );

        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(), any(), any());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());

        Assertions.assertTrue(activeFederationUtxos.isEmpty());
        Assertions.assertTrue(retiringFederationUtxos.isEmpty());
        Assertions.assertTrue(pegoutsWaitingForConfirmations.getEntries().isEmpty());
    }

    private void assertUnknownTxIsIgnored() throws IOException {
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
    }

    private void assertInvalidPeginV1UndeterminedSenderIsRejected(BtcTransaction btcTransaction)
        throws IOException {
        verify(bridgeEventLogger, times(1)).logRejectedPegin(
            btcTransaction, PEGIN_V1_INVALID_PAYLOAD
        );
        verify(bridgeEventLogger, times(1)).logUnrefundablePegin(
            btcTransaction,
            LEGACY_PEGIN_UNDETERMINED_SENDER
        );

        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(), any(), any());
        verify(bridgeEventLogger, never()).logPegoutTransactionCreated(any(), any());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());

        assertTrue(activeFederationUtxos.isEmpty());
        assertTrue(retiringFederationUtxos.isEmpty());
        assertTrue(pegoutsWaitingForConfirmations.getEntries().isEmpty());
    }

    // unknown test

    @BeforeEach
    void init() throws IOException {
        registerHeader = null;

        userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");
        NetworkParameters btcParams = bridgeMainnetConstants.getBtcParams();

        Instant creationTime = Instant.ofEpochMilli(1000L);

        List<BtcECKey> erpPubKeys = federationMainnetConstants.getErpFedPubKeysList();
        long activationDelay = federationMainnetConstants.getErpFedActivationDelay();

        activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa07", "fa08", "fa09", "fa10", "fa11"}, true
        );
        activeFedSigners.sort(BtcECKey.PUBKEY_COMPARATOR);
        List<FederationMember> activeFedMembers = FederationTestUtils.getFederationMembersWithBtcKeys(
            activeFedSigners);
        long activeFedCreationBlockNumber = 2L;
        FederationArgs activeFedArgs =
            new FederationArgs(activeFedMembers, creationTime, activeFedCreationBlockNumber,
                btcParams);
        activeFederation = FederationFactory.buildP2shErpFederation(activeFedArgs, erpPubKeys,
            activationDelay);

        mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        bridgeEventLogger = mock(BridgeEventLogger.class);
        btcLockSenderProvider = new BtcLockSenderProvider();

        peginInstructionsProvider = new PeginInstructionsProvider();

        provider = mock(BridgeStorageProvider.class);
        when(provider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(
            Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        whitelistStorageProvider = mock(WhitelistStorageProvider.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class),
            any(int.class))).thenReturn(true);
        when(whitelistStorageProvider.getLockWhitelist(activations, btcMainnetParams)).thenReturn(
            lockWhitelist);

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

        // Set executionBlock right after the migration should start
        long blockNumber = activeFederation.getCreationBlockNumber() +
            federationMainnetConstants.getFederationActivationAge(activations) +
            federationMainnetConstants.getFundsMigrationAgeSinceActivationBegin() +
            1;
        rskExecutionBlock = mock(Block.class);

        when(rskExecutionBlock.getNumber()).thenReturn(blockNumber);

        rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(PegTestUtils.createHash3(1));

        int btcHeightWhenPegoutTxIndexActivates = bridgeMainnetConstants.getBtcHeightWhenPegoutTxIndexActivates();
        int pegoutTxIndexGracePeriodInBtcBlocks = bridgeMainnetConstants.getPegoutTxIndexGracePeriodInBtcBlocks();

        heightAtWhichToStartUsingPegoutIndex =
            btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;
    }

    private PartialMerkleTree createPmtAndMockBlockStore(BtcTransaction btcTransaction)
        throws BlockStoreException {
        PartialMerkleTree pmt = new PartialMerkleTree(btcMainnetParams, new byte[]{0x3f},
            Collections.singletonList(btcTransaction.getHash()), 1);
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

    private BridgeSupport buildBridgeSupport(ActivationConfig.ForBlock activations) {
        Repository repository = mock(Repository.class);
        when(repository.getBalance(PrecompiledContracts.BRIDGE_ADDR)).thenReturn(
            co.rsk.core.Coin.fromBitcoin(bridgeMainnetConstants.getMaxRbtc()));
        LockingCapSupport lockingCapSupport = mock(LockingCapSupport.class);
        when(lockingCapSupport.getLockingCap()).thenReturn(
            Optional.of(bridgeMainnetConstants.getMaxRbtc()));

        StorageAccessor bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);
        FeePerKbStorageProvider feePerKbStorageProvider = new FeePerKbStorageProviderImpl(
            bridgeStorageAccessor);
        FeePerKbSupport feePerKbSupport = new FeePerKbSupportImpl(
            bridgeMainnetConstants.getFeePerKbConstants(),
            feePerKbStorageProvider
        );

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        whitelistStorageProvider = mock(WhitelistStorageProvider.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class),
            any(int.class))).thenReturn(true);
        when(whitelistStorageProvider.getLockWhitelist(activations, btcMainnetParams)).thenReturn(
            lockWhitelist);

        FederationSupport federationSupport = FederationSupportBuilder.builder()
            .withFederationConstants(federationMainnetConstants)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .withRskExecutionBlock(rskExecutionBlock)
            .build();

        return BridgeSupportBuilder.builder()
            .withBtcBlockStoreFactory(mockFactory)
            .withBridgeConstants(bridgeMainnetConstants)
            .withRepository(repository)
            .withProvider(provider)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withEventLogger(bridgeEventLogger)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withExecutionBlock(rskExecutionBlock)
            .withFeePerKbSupport(feePerKbSupport)
            .withWhitelistSupport(
                new WhitelistSupportImpl(bridgeMainnetConstants.getWhitelistConstants(),
                    whitelistStorageProvider, activations, mock(SignatureCache.class)))
            .withFederationSupport(federationSupport)
            .build();
    }

    @Test
    void whenBelowTheMinimum_shouldRejectPegin()
        throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX,
            ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(belowMinimumPeginTxValue, activeFederation.getAddress());

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            heightAtWhichToStartUsingPegoutIndex,
            pmt.bitcoinSerialize()
        );

        // assert
        assertUnknownTxIsRejectedWithInvalidAmountReason(btcTransaction);
    }

    // flyover pegin
    @Test
    void whenAttemptToRegisterFlyoverPegin_shouldIgnoreIt()
        throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams,
            "userRefundBtcAddress");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams,
            "lpBtcAddress");
        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
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

        BtcECKey senderBtcKey = new BtcECKey();
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX,
            ScriptBuilder.createInputScript(null, senderBtcKey));

        Coin amountToSend = minimumPeginTxValue;
        btcTransaction.addOutput(amountToSend, flyoverFederationAddress);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction);

        // act
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            heightAtWhichToStartUsingPegoutIndex,
            pmt.bitcoinSerialize()
        );

        assertUnknownTxIsIgnored();
    }

    @Test
    void whenUndeterminedSender_shouldRejectPegin()
        throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"key1", "key2", "key3"}, true);

        Federation unknownFed = createFederation(bridgeMainnetConstants, signers);

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, unknownFed.getRedeemScript())
        );
        btcTransaction.addOutput(amountToSend, activeFederation.getAddress());

        FederationTestUtils.addSignatures(unknownFed, signers, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            heightAtWhichToStartUsingPegoutIndex,
            pmt.bitcoinSerialize()
        );

        // assert
        assertLegacyUndeterminedSenderPeginIsRejected(btcTransaction);
    }

    @Test
    void whenNoUtxoToTheFed_shouldRejectPegin()
        throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        BtcECKey senderBtcKey = new BtcECKey();

        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX,
            ScriptBuilder.createInputScript(null, senderBtcKey));

        Coin amountToSend = minimumPeginTxValue;
        btcTransaction.addOutput(amountToSend, userAddress);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction);
        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            heightAtWhichToStartUsingPegoutIndex,
            pmt.bitcoinSerialize()
        );

        assertUnknownTxIsIgnored();
    }

    @Test
    void whenPeginV1WithInvalidPayloadAndUnderminedSender_shouldRejectPegin()
        throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            new Script(new byte[]{})
        );

        btcTransaction.addOutput(amountToSend, activeFederation.getAddress());
        btcTransaction.addOutput(Coin.ZERO,
            PegTestUtils.createOpReturnScriptForRskWithCustomPayload(1, new byte[]{}));

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            heightAtWhichToStartUsingPegoutIndex,
            pmt.bitcoinSerialize()
        );

        // assert
        assertInvalidPeginV1UndeterminedSenderIsRejected(btcTransaction);
    }

    @Test
    void pegin_v1_to_active_fed_with_invalid_payload_and_unknown_sender_cannot_be_processed()
        throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"key1", "key2", "key3"}, true);

        Federation unknownFed = createFederation(bridgeMainnetConstants, signers);

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            new Script(new byte[]{})
        );

        btcTransaction.addOutput(amountToSend, activeFederation.getAddress());
        btcTransaction.addOutput(Coin.ZERO,
            PegTestUtils.createOpReturnScriptForRskWithCustomPayload(1, new byte[]{}));

        FederationTestUtils.addSignatures(unknownFed, signers, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            heightAtWhichToStartUsingPegoutIndex,
            pmt.bitcoinSerialize()
        );

        // assert
        assertInvalidPeginV1UndeterminedSenderIsRejected(btcTransaction);
    }
}
