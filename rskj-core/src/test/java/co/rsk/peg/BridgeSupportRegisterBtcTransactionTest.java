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
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.pegin.PeginProcessAction;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.whitelist.LockWhitelist;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.core.Block;
import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.stream.Stream;

import static co.rsk.peg.BridgeSupportTestUtil.mockChainOfStoredBlocks;
import static co.rsk.peg.PegTestUtils.createFederation;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    private BtcLockSenderProvider btcLockSender;
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
        btcLockSender = new BtcLockSenderProvider();
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

    private PartialMerkleTree createPmtAndMockBlockStore(BtcTransaction btcTransaction) throws BlockStoreException {
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

        int height = 1;
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

    private BridgeSupport getBridgeSupport(ActivationConfig.ForBlock activations) {
        return new BridgeSupportBuilder()
            .withBtcBlockStoreFactory(mockFactory)
            .withBridgeConstants(bridgeMainnetConstants)
            .withProvider(provider)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withEventLogger(bridgeEventLogger)
            .withBtcLockSenderProvider(btcLockSender)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withExecutionBlock(rskExecutionBlock)
            .build();
    }

    // Pegin tests
    private static Stream<Arguments> pegin_to_active_fed_args() {
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

    @ParameterizedTest
    @MethodSource("pegin_to_active_fed_args")
    void registerBtcTransaction_pegin_to_active_fed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        if (activations == tbd600Activations)
            return;

        // arrange
        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, activeFederation.getAddress());

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction);


        if (existsRetiringFederation){
            when(provider.getOldFederation()).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = getBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex: 1,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger,never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(amountToSend), eq(0));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        Assertions.assertEquals(1, activeFederationUtxos.size(), "It should register valid PEGIN sending funds to active fed before and after RSKIP379");
    }

    private static Stream<Arguments> pegin_to_retiring_fed_can_be_registered_args() {
        return Stream.of(
            // before RSKIP379 activation
            Arguments.of(
                fingerrootActivations,
                false,
                true
            ),
            // after RSKIP379 activation but before blockNumber to start using Pegout Index
            Arguments.of(
                tbd600Activations,
                false,
                true
            ),
            // after RSKIP379 activation and after blockNumber to start using Pegout Index
            Arguments.of(
                tbd600Activations,
                true,
                true
            )
        );
    }

    @ParameterizedTest
    @MethodSource("pegin_to_retiring_fed_can_be_registered_args")
    void registerBtcTransaction_pegin_to_retiring_fed_can_be_registered(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        if (activations == tbd600Activations)
            return;
        // arrange
        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, retiringFederation.getAddress());

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction);

        if (existsRetiringFederation){
            when(provider.getOldFederation()).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = getBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex: 1,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger,never()).logRejectedPegin(any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());

        verify(bridgeEventLogger, times(1)).logPeginBtc(any(), eq(btcTransaction), eq(amountToSend), eq(0));
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        Assertions.assertFalse(retiringFederationUtxos.isEmpty());
    }

    private static Stream<Arguments> pegin_to_retiring_fed_can_be_refunded() {
        return Stream.of(
            // before RSKIP379 activation
            Arguments.of(
                fingerrootActivations,
                false,
                true
            ),

            // after RSKIP379 activation but before blockNumber to start using Pegout Index
            Arguments.of(
                tbd600Activations,
                false,
                true
            ),

            // after RSKIP379 activation and after blockNumber to start using Pegout Index
            Arguments.of(
                tbd600Activations,
                true,
                true
            )
        );
    }

    @ParameterizedTest
    @MethodSource("pegin_to_retiring_fed_can_be_refunded")
    void registerBtcTransaction_pegin_to_retiring_fed_can_be_refunded(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        if (activations == tbd600Activations)
            return;
        // arrange
        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, new Script(new byte[]{}));
        btcTransaction.addOutput(amountToSend, retiringFederation.getAddress());

        // since retired fed is a unknown sender for the bridge here we use as multisig address
        FederationTestUtils.addSignatures(retiredFed, retiredFedSigners, btcTransaction);

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction);

        if (existsRetiringFederation){
            when(provider.getOldFederation()).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = getBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex: 1,
            pmt.bitcoinSerialize()
        );

        // assert
        verify(bridgeEventLogger, times(1)).logRejectedPegin(
            eq(btcTransaction), any()
        );
        verify(provider, times(1)).setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(false), rskExecutionBlock.getNumber());
        verify(bridgeEventLogger, times(1)).logReleaseBtcRequested(
            //eq(rskTx.getHash().getBytes()),
            any(),
            any(),
            any()
            //argThat(argument -> argument.isLessThan(amountToSend) )
        );
        Assertions.assertFalse(pegoutsWaitingForConfirmations.getEntries().isEmpty());

        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), any());
        verify(bridgeEventLogger, never()).logUnrefundablePegin(any(), any());
        Assertions.assertTrue(retiringFederationUtxos.isEmpty());
    }

    private static Stream<Arguments> pegin_to_retiring_fed_cannot_be_processed() {
        return Stream.of(
            // before RSKIP379 activation
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
                false,
                PeginProcessAction.CANNOT_BE_PROCESSED
            ),
            Arguments.of(
                tbd600Activations,
                false,
                true,
                PeginProcessAction.CAN_BE_REGISTERED
            ),

            // after RSKIP379 activation and after blockNumber to start using Pegout Index
            Arguments.of(
                tbd600Activations,
                true,
                false,
                PeginProcessAction.CANNOT_BE_PROCESSED
            ),
            Arguments.of(
                tbd600Activations,
                true,
                true,
                PeginProcessAction.CAN_BE_REGISTERED
            )
        );
    }

    @ParameterizedTest
    @MethodSource("pegin_to_retiring_fed_cannot_be_processed")
    void registerBtcTransaction_pegin_to_retiring_fed_cannot_be_processed(
        ActivationConfig.ForBlock activations,
        boolean shouldUsePegoutTxIndex,
        boolean existsRetiringFederation
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        if (activations == tbd600Activations)
            return;
        // arrange
        Coin amountToSend = Coin.COIN;
        BtcTransaction btcTransaction = new BtcTransaction(btcMainnetParams);
        btcTransaction.addInput(BitcoinTestUtils.createHash(1), FIRST_OUTPUT_INDEX, ScriptBuilder.createInputScript(null, new BtcECKey()));
        btcTransaction.addOutput(amountToSend, retiringFederation.getAddress());

        PartialMerkleTree pmt = createPmtAndMockBlockStore(btcTransaction);

        if (existsRetiringFederation){
            when(provider.getOldFederation()).thenReturn(retiringFederation);
        }

        // act
        BridgeSupport bridgeSupport = getBridgeSupport(activations);
        bridgeSupport.registerBtcTransaction(
            rskTx,
            btcTransaction.bitcoinSerialize(),
            shouldUsePegoutTxIndex? blockNumberToStartUsingPegoutIndex: 1,
            pmt.bitcoinSerialize()
        );

        verify(bridgeEventLogger, times(1)).logUnrefundablePegin(
            eq(btcTransaction), any()
        );
        verify(bridgeEventLogger, never()).logPeginBtc(any(), any(), any(), any());
        verify(bridgeEventLogger, times(1)).logUnrefundablePegin(eq(btcTransaction), any());
        verify(bridgeEventLogger, times(1)).logRejectedPegin(
            eq(btcTransaction), any()
        );
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(), any());
        Assertions.assertTrue(retiringFederationUtxos.isEmpty());
    }
}
