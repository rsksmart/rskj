package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.PartialMerkleTree;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.StoredBlock;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.core.RskAddress;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
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

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static co.rsk.peg.BridgeSupportTestUtil.mockChainOfStoredBlocks;
import static co.rsk.peg.PegTestUtils.createFederation;
import static co.rsk.peg.utils.RejectedPeginReason.LEGACY_PEGIN_MULTISIG_SENDER;
import static co.rsk.peg.utils.RejectedPeginReason.NO_UTXO_OR_UTXO_BELOW_MINIMUM;
import static co.rsk.peg.utils.RejectedPeginReason.PEGIN_V1_INVALID_PAYLOAD;
import static co.rsk.peg.utils.UnrefundablePeginReason.LEGACY_PEGIN_UNDETERMINED_SENDER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BridgeSupportRegisterBtcTransactionTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final Context context = new Context(bridgeMainnetConstants.getBtcParams());
    private static final ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);
    private static final ActivationConfig.ForBlock tbd600Activations = ActivationConfigsForTest.tbd600().forBlock(0);

    private static final int FIRST_OUTPUT_INDEX = 0;
    private static final int FIRST_INPUT_INDEX = 0;

    private BridgeStorageProvider provider;
    private Address userAddress;

    private List<BtcECKey> retiredFedSigners;
    private Federation retiredFed;

    private List<BtcECKey> retiringFedSigners;
    private Federation retiringFederation;

    private List<BtcECKey> activeFedSigners;
    private Federation activeFederation;

    private BtcBlockStoreWithCache.Factory mockFactory;
    private SignatureCache signatureCache;
    private BridgeEventLogger bridgeEventLogger;
    private BtcLockSenderProvider btcLockSenderProvider;
    private PeginInstructionsProvider peginInstructionsProvider;

    private List<UTXO> retiringFederationUtxos = new ArrayList<>();
    private List<UTXO> activeFederationUtxos = new ArrayList<>();
    private PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations;
    private Block rskExecutionBlock;
    private Transaction rskTx;

    private int blockNumberToStartUsingPegoutIndex;

    @BeforeEach
    void init() throws IOException {
        userAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "userAddress");

        retiredFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        retiredFed = createFederation(bridgeMainnetConstants, retiredFedSigners);

        retiringFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa04", "fa05", "fa06"}, true
        );

        retiringFedSigners.sort(BtcECKey.PUBKEY_COMPARATOR);
        retiringFederation =  new P2shErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiringFedSigners),
            Instant.ofEpochMilli(1000L),
            1,
            bridgeMainnetConstants.getBtcParams(),
            bridgeMainnetConstants.getErpFedPubKeysList(),
            bridgeMainnetConstants.getErpFedActivationDelay(),
            tbd600Activations
        );

        activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa07", "fa08", "fa09", "fa10", "fa11"}, true
        );
        activeFedSigners.sort(BtcECKey.PUBKEY_COMPARATOR);
        activeFederation =  new P2shErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(activeFedSigners),
            Instant.ofEpochMilli(1000L),
            2L,
            bridgeMainnetConstants.getBtcParams(),
            bridgeMainnetConstants.getErpFedPubKeysList(),
            bridgeMainnetConstants.getErpFedActivationDelay(),
            tbd600Activations
        );

        mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        signatureCache =  new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        bridgeEventLogger = mock(BridgeEventLogger.class);
        btcLockSenderProvider = new BtcLockSenderProvider();
        peginInstructionsProvider = new PeginInstructionsProvider();

        provider = mock(BridgeStorageProvider.class);

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        when(provider.getLockWhitelist()).thenReturn(lockWhitelist);

        when(provider.getOldFederationBtcUTXOs())
            .thenReturn(retiringFederationUtxos);
        when(provider.getNewFederationBtcUTXOs())
            .thenReturn(activeFederationUtxos);

        pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);

        when(provider.getNewFederation()).thenReturn(activeFederation);

        // Set executionBlock right after the migration should start
        long blockNumber = activeFederation.getCreationBlockNumber() +
                               bridgeMainnetConstants.getFederationActivationAge(tbd600Activations) +
                               bridgeMainnetConstants.getFundsMigrationAgeSinceActivationBegin() +
                               1;
         rskExecutionBlock = mock(Block.class);


        when(rskExecutionBlock.getNumber()).thenReturn(blockNumber);

        rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(PegTestUtils.createHash3(1));

        int btcHeightWhenPegoutTxIndexActivates = bridgeMainnetConstants.getBtcHeightWhenPegoutTxIndexActivates();
        int pegoutTxIndexGracePeriodInBtcBlocks = bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations() * 5;
        blockNumberToStartUsingPegoutIndex = btcHeightWhenPegoutTxIndexActivates + pegoutTxIndexGracePeriodInBtcBlocks;
    }

    private PartialMerkleTree createPmtAndMockBlockStore(BtcTransaction btcTransaction, int height) throws BlockStoreException {
        PartialMerkleTree pmt = new PartialMerkleTree(btcMainnetParams, new byte[]{0x3f}, Collections.singletonList(btcTransaction.getHash()), 1);
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcMainnetParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcMainnetParams,
            1,
            PegTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        co.rsk.bitcoinj.core.BtcBlock btcBlock =
            new co.rsk.bitcoinj.core.BtcBlock(btcMainnetParams, 1, PegTestUtils.createHash(), blockMerkleRoot,
                1, 1, 1, new ArrayList<>());

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + BridgeSupportRegisterBtcTransactionTest.bridgeMainnetConstants.getBtc2RskMinimumAcceptableConfirmations(), height);
        return pmt;
    }

    private BtcLockSenderProvider mockBtcLockSenderProvider(BtcLockSender.TxSenderAddressType txSenderAddressType, Address btcAddress, RskAddress rskAddress) {
        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        when(btcLockSender.getTxSenderAddressType()).thenReturn(txSenderAddressType);
        when(btcLockSender.getBTCAddress()).thenReturn(btcAddress);
        when(btcLockSender.getRskAddress()).thenReturn(rskAddress);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        return btcLockSenderProvider;
    }

    private BridgeSupport buildBridgeSupport(ActivationConfig.ForBlock activations, BtcLockSenderProvider btcLockSenderProvider) {
        return new BridgeSupportBuilder()
            .withBtcBlockStoreFactory(mockFactory)
            .withBridgeConstants(bridgeMainnetConstants)
            .withProvider(provider)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withEventLogger(bridgeEventLogger)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withExecutionBlock(rskExecutionBlock)
            .build();
    }

    private static Stream<Arguments> common_args() {
        // before RSKIP379 activation
        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                false
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                true
            ),

            // after RSKIP379 activation but before blockNumber to start using Pegout Index
            Arguments.of(
                tbd600Activations,
                false,
                false
            ),
            Arguments.of(
                tbd600Activations,
                false,
                true
            ),

            // after RSKIP379 activation and after blockNumber to start using Pegout Index
            Arguments.of(
                tbd600Activations,
                true,
                false
            ),
            Arguments.of(
                tbd600Activations,
                true,
                true
            )
        );
    }

    private static Stream<Arguments> existing_retiring_fed_args() {
        return Stream.of(
            // before RSKIP379 activation
            Arguments.of(
                fingerrootActivations,
                false
            ),
            // after RSKIP379 activation but before blockNumber to start using Pegout Index
            Arguments.of(
                tbd600Activations,
                false
            ),
            // after RSKIP379 activation and after blockNumber to start using Pegout Index
            Arguments.of(
                tbd600Activations,
                true
            )
        );
    }

    // unknown test
    private static Stream<Arguments> registering_btc_transaction_sending_funds_to_unknown_address_args() {
        ActivationConfig.ForBlock fingerrootActivations  = ActivationConfigsForTest.fingerroot500().forBlock(0);
        ActivationConfig.ForBlock tbdActivations = ActivationConfigsForTest.tbd600().forBlock(0);

        return Stream.of(
            Arguments.of(
                fingerrootActivations,
                false,
                false,
                false,
                true
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                true,
                false,
                true
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                true,
                true,
                true
            ),
            Arguments.of(
                fingerrootActivations,
                false,
                false,
                true,
                true
            ),


            Arguments.of(
                tbdActivations,
                false,
                false,
                false,
                true
            ),
            Arguments.of(
                tbdActivations,
                false,
                true,
                false,
                true
            ),
            Arguments.of(
                tbdActivations,
                false,
                true,
                true,
                true
            ),
            Arguments.of(
                tbdActivations,
                false,
                false,
                true,
                true
            ),

            Arguments.of(
                tbdActivations,
                true,
                false,
                false,
                false
            ),
            Arguments.of(
                tbdActivations,
                true,
                true,
                false,
                false
            ),
            Arguments.of(
                tbdActivations,
                true,
                true,
                true,
                false
            ),
            Arguments.of(
                tbdActivations,
                true,
                false,
                true,
                false
            )
        );
    }

    @ParameterizedTest
    @MethodSource("registering_btc_transaction_sending_funds_to_unknown_address_args")
    void registering_btc_transaction_sending_funds_to_unknown_address(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean shouldSendAmountBelowMinimum,
        boolean existsRetiringFederation,
        boolean shouldProcessed
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        BtcECKey senderBtcKey = new BtcECKey();
        ECKey senderRskKey = ECKey.fromPublicOnly(senderBtcKey.getPubKey());
        RskAddress rskAddress = new RskAddress(senderRskKey.getAddress());

        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, senderBtcKey));

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        Coin belowMinimumPeginTxValue = minimumPeginTxValue.minus(Coin.SATOSHI);

        Coin amountToSend = shouldSendAmountBelowMinimum? belowMinimumPeginTxValue: minimumPeginTxValue;
        btcTransaction.addOutput(amountToSend, userAddress);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(provider.getOldFederation()).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert

        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());
        Assertions.assertTrue( activeFederationUtxos.isEmpty());
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());

        if (shouldProcessed) {
            verify(bridgeEventLogger, never()).logPeginBtc(rskAddress, btcTransaction, amountToSend, 0);
            verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        } else {
            verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        }
    }

    @ParameterizedTest
    @MethodSource("registering_btc_transaction_sending_funds_to_unknown_address_args")
    void registering_btc_transaction_many_outputs_to_unknown_addresses(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean shouldSendAmountBelowMinimum,
        boolean existsRetiringFederation,
        boolean shouldProcessed
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);

        BtcECKey senderBtcKey = new BtcECKey();
        ECKey senderRskKey = ECKey.fromPublicOnly(senderBtcKey.getPubKey());
        RskAddress rskAddress = new RskAddress(senderRskKey.getAddress());

        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, senderBtcKey));

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        Coin belowMinimumPeginTxValue = minimumPeginTxValue.minus(Coin.SATOSHI);
        Coin amountToSend = shouldSendAmountBelowMinimum? belowMinimumPeginTxValue: minimumPeginTxValue;

        btcTransaction.addOutput(amountToSend, userAddress);
        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(amountToSend, new BtcECKey().toAddress(btcMainnetParams));
        }

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation) {
            when(provider.getOldFederation()).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert

        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());
        Assertions.assertTrue( activeFederationUtxos.isEmpty());
        verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());

        if (shouldProcessed) {
            verify(bridgeEventLogger, never()).logPeginBtc(rskAddress, btcTransaction, amountToSend, 0);
            verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        } else {
            verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        }
    }

    // Pegin tests

    @ParameterizedTest
    @MethodSource("common_args")
    void pegin_to_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, activeFederation.getAddress());

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation){
            when(provider.getOldFederation()).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger,never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(amountToSend), eq(0));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        Assertions.assertEquals(1, activeFederationUtxos.size());
    }

    @ParameterizedTest
    @MethodSource("common_args")
    void pegin_multiple_outputs_to_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;


        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(minimumPeginTxValue, activeFederation.getAddress());
        }

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation){
            when(provider.getOldFederation()).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger,never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(minimumPeginTxValue.multiply(10)), eq(0));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        Assertions.assertEquals(10, activeFederationUtxos.size());
    }

    @ParameterizedTest
    @MethodSource("common_args")
    void pegin_to_active_fed_with_bech32_output(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, activeFederation.getAddress());
        btcTransaction.addOutput(PegTestUtils.createBech32Output(btcMainnetParams, Coin.COIN));

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation){
            when(provider.getOldFederation()).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger,never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(amountToSend), eq(0));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        Assertions.assertEquals(1, activeFederationUtxos.size());
    }

    @ParameterizedTest
    @MethodSource("common_args")
    void pegin_to_active_fed_equal_to_minimum_with_other_random_outputs_below_minimum(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        Coin belowMinimumPeginTxValue = minimumPeginTxValue.minus(Coin.SATOSHI);

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(minimumPeginTxValue, activeFederation.getAddress());
        btcTransaction.addOutput(PegTestUtils.createBech32Output(btcMainnetParams, belowMinimumPeginTxValue));
        btcTransaction.addOutput(belowMinimumPeginTxValue, userAddress);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation){
            when(provider.getOldFederation()).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger,never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(minimumPeginTxValue), eq(0));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        Assertions.assertEquals(1, activeFederationUtxos.size());
    }

    @ParameterizedTest
    @MethodSource("common_args")
    void pegin_to_active_fed_below_minimum(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        Coin belowMinimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations).minus(Coin.SATOSHI);
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(belowMinimumPeginTxValue, activeFederation.getAddress());

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation){
            when(provider.getOldFederation()).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        if (shouldUsePegoutTxIndex){
            verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, NO_UTXO_OR_UTXO_BELOW_MINIMUM);
        } else {
            verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        }

        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        Assertions.assertTrue(activeFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("common_args")
    void pegin_to_active_fed_below_and_above_minimum(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        Coin belowMinimumPeginTxValue = minimumPeginTxValue.minus(Coin.SATOSHI);
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(minimumPeginTxValue, activeFederation.getAddress());
        btcTransaction.addOutput(belowMinimumPeginTxValue, activeFederation.getAddress());

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation){
            when(provider.getOldFederation()).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        if (shouldUsePegoutTxIndex){
            verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, NO_UTXO_OR_UTXO_BELOW_MINIMUM);
        } else {
            verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        }

        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        Assertions.assertTrue(activeFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("common_args")
    void pegin_multiple_outputs_to_active_fed_sum_amount_equal_to_minimum_pegin(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        Coin amountPerOutput = minimumPeginTxValue.div(10);

        for (int i = 0; i < 10; i++) {
            btcTransaction.addOutput(amountPerOutput, activeFederation.getAddress());
        }

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation){
            when(provider.getOldFederation()).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        if (shouldUsePegoutTxIndex){
            verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, NO_UTXO_OR_UTXO_BELOW_MINIMUM);
        } else {
            verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        }

        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        Assertions.assertTrue(activeFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("existing_retiring_fed_args")
    void pegin_to_active_and_retiring_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(minimumPeginTxValue, activeFederation.getAddress());
        btcTransaction.addOutput(minimumPeginTxValue, retiringFederation.getAddress());

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(provider.getOldFederation()).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger,never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(minimumPeginTxValue.multiply(2)), eq(0));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        Assertions.assertEquals(1, activeFederationUtxos.size());
        Assertions.assertEquals(1, retiringFederationUtxos.size());
    }

    @ParameterizedTest
    @MethodSource("existing_retiring_fed_args")
    void pegin_to_active_fed_below_minimum_and_retiring_above_minimum(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        Coin belowMinimumPeginTxValue = minimumPeginTxValue.minus(Coin.SATOSHI);
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));

        btcTransaction.addOutput(belowMinimumPeginTxValue, activeFederation.getAddress());
        btcTransaction.addOutput(minimumPeginTxValue, retiringFederation.getAddress());

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(provider.getOldFederation()).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        if (shouldUsePegoutTxIndex){
            verify(bridgeEventLogger, times(1)).logRejectedPegin(btcTransaction, NO_UTXO_OR_UTXO_BELOW_MINIMUM);
        } else {
            verify(bridgeEventLogger, never()).logRejectedPegin(any(), any());
        }

        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        Assertions.assertTrue(activeFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("existing_retiring_fed_args")
    void pegin_to_active_and_retiring_fed_and_unknown_address(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        Coin minimumPeginTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(minimumPeginTxValue, activeFederation.getAddress());
        btcTransaction.addOutput(minimumPeginTxValue, retiringFederation.getAddress());
        btcTransaction.addOutput(Coin.COIN, userAddress);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(provider.getOldFederation()).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger,never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(minimumPeginTxValue.multiply(2)), eq(0));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        Assertions.assertEquals(1, activeFederationUtxos.size());
        Assertions.assertEquals(1, retiringFederationUtxos.size());
    }

    @ParameterizedTest
    @MethodSource("existing_retiring_fed_args")
    void pegin_v1_to_retiring_fed_can_be_registered(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, retiringFederation.getAddress());
        btcTransaction.addOutput(
            Coin.ZERO,
            PegTestUtils.createOpReturnScriptForRsk(
                1,
                PrecompiledContracts.BRIDGE_ADDR,
                Optional.empty()
            )
        );

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(provider.getOldFederation()).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger,never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(amountToSend), eq(1));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        Assertions.assertFalse(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("existing_retiring_fed_args")
    void pegin_from_multisig_to_retiring_fed_can_be_refunded(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        List<BtcECKey> signers = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"key1", "key2", "key3"}, true);

        Federation unknownFed = createFederation(bridgeMainnetConstants, signers);

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, unknownFed.getRedeemScript())
        );
        btcTransaction.addOutput(amountToSend, retiringFederation.getAddress());

        FederationTestUtils.addSignatures(unknownFed, signers, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(provider.getOldFederation()).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, times(1)).logRejectedPegin(
            btcTransaction, LEGACY_PEGIN_MULTISIG_SENDER
        );
        //
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(
            eq(rskTx.getHash().getBytes()),
            any(BtcTransaction.class),
            eq(amountToSend)
        );
        Assertions.assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());

        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());
        Assertions.assertTrue(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("existing_retiring_fed_args")
    void pegin_to_retiring_fed_cannot_be_processed_due_to_pegin_v1_undetermined_sender(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, retiringFederation.getAddress());
        btcTransaction.addOutput(
            Coin.ZERO,
            PegTestUtils.createOpReturnScriptForRsk(
                1,
                PrecompiledContracts.BRIDGE_ADDR,
                Optional.empty()
            )
        );

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(provider.getOldFederation()).thenReturn(retiringFederation);

        /*BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(
            BtcLockSender.TxSenderAddressType.UNKNOWN,
            null,
            null
        );*/

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        verify(bridgeEventLogger, times(1)).logUnrefundablePegin(
            btcTransaction, LEGACY_PEGIN_UNDETERMINED_SENDER
        );
        verify(bridgeEventLogger, times(1)).logRejectedPegin(
            btcTransaction, PEGIN_V1_INVALID_PAYLOAD
        );

        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());

        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        Assertions.assertTrue(retiringFederationUtxos.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("existing_retiring_fed_args")
    void pegin_to_retiring_fed_cannot_be_processed_due_to_legacy_undetermined_sender(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, retiringFederation.getAddress());

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        when(provider.getOldFederation()).thenReturn(retiringFederation);

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        verify(bridgeEventLogger, times(1)).logUnrefundablePegin(
            btcTransaction, LEGACY_PEGIN_UNDETERMINED_SENDER
        );
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), anyInt());
        verify(bridgeEventLogger, times(1)).logRejectedPegin(
            eq(btcTransaction), any()
        );
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), any());
        Assertions.assertTrue(retiringFederationUtxos.isEmpty());
    }

    // Pegout tests

    @ParameterizedTest
    @MethodSource("common_args")
    void pegout_no_change_output(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = shouldUsePegoutTxIndex ? blockNumberToStartUsingPegoutIndex : 1;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, activeFederation.getRedeemScript())
        );
        btcTransaction.addOutput(Coin.COIN, userAddress);

        FederationTestUtils.addSignatures(activeFederation, activeFedSigners, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        if (existsRetiringFederation){
            when(provider.getOldFederation()).thenReturn(retiringFederation);
        }

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            activeFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        if (activations.isActive(ConsensusRule.RSKIP379)) {
            when(provider.hasPegoutTxSigHash(firstInputSigHash)).thenReturn(true);
        }

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        Assertions.assertTrue(activeFederationUtxos.isEmpty());
        Assertions.assertTrue(retiringFederationUtxos.isEmpty());
    }

    @Test
    void pegout_no_change_output_sighash_no_exists_in_provider() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        // arrange
        int height = blockNumberToStartUsingPegoutIndex;

        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(
            BitcoinTestUtils.createHash(1),
            FIRST_OUTPUT_INDEX,
            ScriptBuilder.createP2SHMultiSigInputScript(null, activeFederation.getRedeemScript())
        );
        btcTransaction.addOutput(Coin.COIN, userAddress);

        FederationTestUtils.addSignatures(activeFederation, activeFedSigners, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction, height);

        Sha256Hash firstInputSigHash = btcTransaction.hashForSignature(
            FIRST_INPUT_INDEX,
            activeFederation.getRedeemScript(),
            BtcTransaction.SigHash.ALL,
            false
        );

        // act
        BridgeSupport bridgeSupport = buildBridgeSupport(tbd600Activations, btcLockSenderProvider);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        Assertions.assertTrue(activeFederationUtxos.isEmpty());
        Assertions.assertTrue(retiringFederationUtxos.isEmpty());
    }
}
