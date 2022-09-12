package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.PartialMerkleTree;
import co.rsk.bitcoinj.core.ScriptException;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.FastBridgeRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.flyover.FlyoverFederationInformation;
import co.rsk.peg.flyover.FlyoverTxResponseCodes;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.test.builders.BridgeSupportBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.InternalTransaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static co.rsk.peg.PegTestUtils.createBech32Output;
import static co.rsk.peg.PegTestUtils.createP2pkhOutput;
import static co.rsk.peg.PegTestUtils.createP2shOutput;
import static co.rsk.peg.PegTestUtils.createRandomP2PKHBtcAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BridgeSupportFlyoverTest extends BridgeSupportTestBase {
    @BeforeEach
    void setUpOnEachTest() {
        activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP219)).thenReturn(true);
        bridgeSupportBuilder = new BridgeSupportBuilder();
    }

    private BtcTransaction createBtcTransactionWithOutputToAddress(Coin amount, Address btcAddress) {
        return PegTestUtils.createBtcTransactionWithOutputToAddress(btcRegTestParams, amount, btcAddress);
    }

    private BigInteger sendFundsToActiveFederation(
        boolean isRskip293Active,
        Coin valueToSend
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(isRskip293Active);

        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);
        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        doReturn(BridgeRegTestConstants.getInstance().getFederationActivationAge()).when(bridgeConstants).getFederationActivationAge();

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        Federation activeFederation = PegTestUtils.createFederation(bridgeConstants, "fa03", "fa04");
        Repository repository = createRepository();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederation()).thenReturn(activeFederation);

        Address userRefundBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
        Address lpBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();
        Keccak256 flyoverDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address activeFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        btcTx.addOutput(valueToSend, activeFederationAddress);

        btcTx.addInput(
            Sha256Hash.ZERO_HASH,
            0, ScriptBuilder.createInputScript(null, new BtcECKey())
        );

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash());
        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            PegTestUtils.createHash(2),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );
        int height = 1;
        // simulate blockchain
        mockChainOfStoredBlocks(
            btcBlockStore,
            registerHeader,
            height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        co.rsk.core.Coin preCallLbcAddressBalance = repository.getBalance(lbcAddress);

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            true
        );

        // if transaction went OK following assertions should be met
        if (result.signum() == 1) {
            co.rsk.core.Coin expectedBalance = preCallLbcAddressBalance.add(co.rsk.core.Coin.fromBitcoin(
                valueToSend
            ));
            co.rsk.core.Coin postCallLbcAddressBalance = repository.getBalance(lbcAddress);

            // assert the new balance of the Lbc address is correct
            Assertions.assertEquals(
                expectedBalance,
                postCallLbcAddressBalance
            );

            verify(provider, times(1)).markFlyoverDerivationHashAsUsed(
                btcTx.getHash(false),
                flyoverDerivationHash
            );

            verify(provider, times(1)).setFlyoverFederationInformation(
                any()
            );
        }
        return result;
    }

    private BigInteger sendFundsToRetiringFederation(
        boolean isRskip293Active,
        Coin valueToSend
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(isRskip293Active);

        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);
        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        doReturn(BridgeRegTestConstants.getInstance().getFederationActivationAge()).when(bridgeConstants).getFederationActivationAge();

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        Federation activeFederation = PegTestUtils.createFederation(bridgeConstants, "fa03", "fa04");
        Federation retiringFederation = PegTestUtils.createFederation(bridgeConstants, "fa01", "fa02");
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederation()).thenReturn(activeFederation);
        when(provider.getOldFederation()).thenReturn(retiringFederation);

        Address userRefundBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
        Address lpBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());

        Repository repository = createRepository();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();
        Keccak256 flyoverDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address retiringFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstants,
            retiringFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        btcTx.addOutput(valueToSend, retiringFederationAddress);

        btcTx.addInput(
            Sha256Hash.ZERO_HASH,
            0, ScriptBuilder.createInputScript(null, new BtcECKey())
        );

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash());
        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            PegTestUtils.createHash(2),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );
        int height = 1;
        // simulate blockchain
        mockChainOfStoredBlocks(
            btcBlockStore,
            registerHeader,
            height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        co.rsk.core.Coin preCallLbcAddressBalance = repository.getBalance(lbcAddress);

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            true
        );

        // if transaction went OK following assertions should be met
        if (result.signum() == 1) {
            co.rsk.core.Coin expectedBalance = preCallLbcAddressBalance.add(co.rsk.core.Coin.fromBitcoin(
                valueToSend
            ));
            co.rsk.core.Coin postCallLbcAddressBalance = repository.getBalance(lbcAddress);

            // assert the new balance of the Lbc address is correct
            Assertions.assertEquals(
                expectedBalance,
                postCallLbcAddressBalance
            );

            // when RSKIP293 is active, the method markFlyoverFederationDerivationHashAsUsed should be called twice.
            verify(provider, times(isRskip293Active ? 2 : 1)).markFlyoverDerivationHashAsUsed(
                btcTx.getHash(false),
                flyoverDerivationHash
            );

            verify(provider, times(1)).setFlyoverRetiringFederationInformation(
                any()
            );
        }
        return result;
    }

    private BigInteger sendFundsToActiveAndRetiringFederation(
        boolean isRskip293Active,
        Coin valueToSend
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(isRskip293Active);

        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);
        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        doReturn(BridgeRegTestConstants.getInstance().getFederationActivationAge()).when(bridgeConstants).getFederationActivationAge();

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        Federation activeFederation = PegTestUtils.createFederation(bridgeConstants, "fa03", "fa04");
        Federation retiringFederation = PegTestUtils.createFederation(bridgeConstants, "fa01", "fa02");
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederation()).thenReturn(activeFederation);
        when(provider.getOldFederation()).thenReturn(retiringFederation);

        Address userRefundBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
        Address lpBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());

        Repository repository = createRepository();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();
        Keccak256 flyoverDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());

        Address activeFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        btcTx.addOutput(valueToSend, activeFederationAddress);

        Address retiringFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstants,
            retiringFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );
        btcTx.addOutput(valueToSend, retiringFederationAddress);

        btcTx.addInput(
            Sha256Hash.ZERO_HASH,
            0, ScriptBuilder.createInputScript(null, new BtcECKey())
        );

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash());
        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            PegTestUtils.createHash(2),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );
        int height = 1;
        // simulate blockchain
        mockChainOfStoredBlocks(
            btcBlockStore,
            registerHeader,
            height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        co.rsk.core.Coin preCallLbcAddressBalance = repository.getBalance(lbcAddress);

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            true
        );

        // if transaction went OK following assertions should be met
        if (result.signum() == 1) {
            co.rsk.core.Coin expectedBalance = preCallLbcAddressBalance.add(co.rsk.core.Coin.fromBitcoin(
                valueToSend
            ));

            if (isRskip293Active) {
                // should also add the values sent to the retiring federation
                expectedBalance = expectedBalance.add(co.rsk.core.Coin.fromBitcoin(
                    valueToSend
                ));
            }

            co.rsk.core.Coin postCallLbcAddressBalance = repository.getBalance(lbcAddress);
            // assert the new balance of the Lbc address is correct
            Assertions.assertEquals(
                expectedBalance,
                postCallLbcAddressBalance
            );

            // when RSKIP293 is active, the method setFlyoverRetiringFederationInformation should be called twice.
            verify(provider, times(isRskip293Active ? 2 : 1)).markFlyoverDerivationHashAsUsed(
                btcTx.getHash(false),
                flyoverDerivationHash
            );

            verify(provider, times(1)).setFlyoverFederationInformation(
                any()
            );

            // verify method setFlyoverRetiringFederationInformation is called when RSKIP293 is active
            if (isRskip293Active) {
                verify(provider, times(1)).setFlyoverRetiringFederationInformation(
                    any()
                );
            }

        }
        return result;
    }

    private BigInteger sendFundsToAnyAddress(
        BridgeConstants constants,
        boolean isRskip293Active,
        BtcTransactionProvider btcTransactionProvider
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(isRskip293Active);

        BridgeConstants bridgeConstants = spy(constants);
        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        doReturn(BridgeRegTestConstants.getInstance().getFederationActivationAge()).when(bridgeConstants).getFederationActivationAge();

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        Federation activeFederation = PegTestUtils.createFederation(bridgeConstants, "fa03", "fa04");
        Federation retiringFederation = PegTestUtils.createFederation(bridgeConstants, "fa01", "fa02");
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederation()).thenReturn(activeFederation);
        when(provider.getOldFederation()).thenReturn(retiringFederation);

        Address userRefundBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
        Address lpBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());

        Repository repository = createRepository();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();
        Keccak256 flyoverDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address activeFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        Address retiringFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstants,
            retiringFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        BtcTransaction btcTx = btcTransactionProvider.provide(bridgeConstants, activeFederationAddress, retiringFederationAddress);
        btcTx.addInput(
            Sha256Hash.ZERO_HASH,
            0, ScriptBuilder.createInputScript(null, new BtcECKey())
        );

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash());
        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            PegTestUtils.createHash(2),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );
        int height = 1;
        // simulate blockchain
        mockChainOfStoredBlocks(
            btcBlockStore,
            registerHeader,
            height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        return bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            true
        );
    }

    private BigInteger sendFundsSurpassesLockingCapToAnyAddress(
        boolean isRskip293Active,
        Coin lockingCapValue,
        BtcTransactionProvider btcTransactionProvider,
        boolean shouldTransferToContract
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(isRskip293Active);

        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        Federation activeFederation = PegTestUtils.createFederation(bridgeConstants, "fa03", "fa04");
        Federation retiringFederation = PegTestUtils.createFederation(bridgeConstants, "fa01", "fa02");

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederation()).thenReturn(activeFederation);
        when(provider.getOldFederation()).thenReturn(retiringFederation);
        when(provider.getLockingCap()).thenReturn(lockingCapValue);

        ReleaseTransactionSet releaseTransactionSet = new ReleaseTransactionSet(new HashSet<>());
        when(provider.getReleaseTransactionSet()).thenReturn(releaseTransactionSet);

        Address userRefundBtcAddress = createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
        Address lpBtcAddress = createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());

        Repository repository = createRepository();
        // For simplicity of this test, the max rbtc value is set as the current balance for the repository
        // This simulates that no pegin has ever been processed
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, co.rsk.core.Coin.fromBitcoin(bridgeConstantsRegtest.getMaxRbtc()));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();
        Keccak256 flyoverDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address activeFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        Address retiringFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstants,
            retiringFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        BtcTransaction btcTx = btcTransactionProvider.provide(bridgeConstants, activeFederationAddress, retiringFederationAddress);
        btcTx.addInput(
            Sha256Hash.ZERO_HASH,
            0, ScriptBuilder.createInputScript(null, new BtcECKey())
        );

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash());
        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            PegTestUtils.createHash(2),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );
        int height = 1;
        // simulate blockchain
        mockChainOfStoredBlocks(
            btcBlockStore,
            registerHeader,
            height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            shouldTransferToContract
        );

        if (result.longValue() == FlyoverTxResponseCodes.REFUNDED_LP_ERROR.value() || result.longValue() == FlyoverTxResponseCodes.REFUNDED_USER_ERROR.value()){
            List<BtcTransaction> releaseTxs = provider.getReleaseTransactionSet().getEntries()
                .stream()
                .map(ReleaseTransactionSet.Entry::getTransaction)
                .collect(Collectors.toList());

            assertEquals(1, releaseTxs.size());
            BtcTransaction releaseTx = releaseTxs.get(0);
            Assertions.assertEquals(1, releaseTx.getOutputs().size());

            Coin amountSent = BridgeUtils.getAmountSentToAddresses(activations,
                bridgeConstants.getBtcParams(),
                btcContext,
                btcTx,
                isRskip293Active? Arrays.asList(activeFederationAddress, retiringFederationAddress):
                    Collections.singletonList(activeFederationAddress)
            );
            Coin amountToRefund = BridgeUtils.getAmountSentToAddresses(activations,
                bridgeConstants.getBtcParams(),
                btcContext,
                releaseTx,
                Collections.singletonList(
                    result.longValue() == FlyoverTxResponseCodes.REFUNDED_LP_ERROR.value() ? lpBtcAddress : userRefundBtcAddress
                ));

            // For simplicity of this test we are using as estimated fee of 10% of the amount sent in order to check
            // that the amount to refund is at least above the amount sent minus the estimated fee
            Coin estimatedFee = amountSent.divide(10);
            Coin estimatedAmountToRefund = amountSent.minus(estimatedFee);

            Assertions.assertTrue(amountToRefund.isGreaterThan(estimatedAmountToRefund) &&
                amountToRefund.isLessThan(amountSent), "Pegout value should be bigger than the estimated amount to refund(" + estimatedAmountToRefund + ") and " +
                "smaller than the amount sent(" + amountSent + ")"
            );
        }

        return result;
    }

    @Test
    void registerFlyoverBtcTransaction_output_to_bech32_before_RSKIP293_regtest() {
        Assertions.assertThrows(ScriptException.class, () -> sendFundsToAnyAddress(
                bridgeConstantsRegtest,
                false,
                (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                    Coin valueToSend = Coin.COIN;
                    BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                    tx.addOutput(createBech32Output(bridgeConstants.getBtcParams(), valueToSend));
                    return tx;
                }
        ));
    }

    @Test
    void registerFlyoverBtcTransaction_output_to_bech32_before_RSKIP293_mainnet() {
        Assertions.assertThrows(ScriptException.class, () -> sendFundsToAnyAddress(
                bridgeConstantsMainnet,
                false,
                (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                    Coin valueToSend = Coin.COIN;
                    BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                    tx.addOutput(createBech32Output(bridgeConstants.getBtcParams(), valueToSend));
                    return tx;
                }
        ));
    }

    @Test
    void registerFlyoverBtcTransaction_output_to_bech32_after_RSKIP293_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Coin expectedValue = Coin.COIN;
        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsRegtest,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(valueToSend, activeFederationAddress);
                tx.addOutput(createBech32Output(bridgeConstants.getBtcParams(), valueToSend));
                return tx;
            }
        );

        assertEquals(
            co.rsk.core.Coin.fromBitcoin(expectedValue).asBigInteger(),
            result
        );
    }

    @Test
    void registerFlyoverBtcTransaction_output_to_bech32_after_RSKIP293_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Coin expectedValue = Coin.COIN;
        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsMainnet,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(valueToSend, activeFederationAddress);
                tx.addOutput(createBech32Output(bridgeConstants.getBtcParams(), valueToSend));
                return tx;
            }
        );

        assertEquals(
            co.rsk.core.Coin.fromBitcoin(expectedValue).asBigInteger(),
            result
        );
    }

    @Test
    void registerFlyoverBtcTransaction_output_to_P2PKH_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Coin expectedValue = Coin.COIN;

        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsRegtest,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(valueToSend, activeFederationAddress);
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));
                return tx;
            }
        );

        assertEquals(
            co.rsk.core.Coin.fromBitcoin(expectedValue).asBigInteger(),
            result
        );
    }

    @Test
    void registerFlyoverBtcTransaction_output_to_P2PKH_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Coin expectedValue = Coin.COIN;

        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsMainnet,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(valueToSend, activeFederationAddress);
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));
                return tx;
            }
        );

        assertEquals(
            co.rsk.core.Coin.fromBitcoin(expectedValue).asBigInteger(),
            result
        );
    }

    @Test
    void registerFlyoverBtcTransaction_output_to_P2SH_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Coin expectedValue = Coin.COIN;

        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsRegtest,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(valueToSend, activeFederationAddress);
                tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));
                return tx;
            }
        );

        assertEquals(
            co.rsk.core.Coin.fromBitcoin(expectedValue).asBigInteger(),
            result
        );
    }

    @Test
    void registerFlyoverBtcTransaction_output_to_P2SH_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Coin expectedValue = Coin.COIN;

        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsMainnet,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(valueToSend, activeFederationAddress);
                tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));
                return tx;
            }
        );

        assertEquals(
            co.rsk.core.Coin.fromBitcoin(expectedValue).asBigInteger(),
            result
        );
    }

    @Test
    void registerFlyoverBtcTransaction_output_to_different_address_type_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Coin expectedValue = Coin.COIN.multiply(2);

        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsMainnet,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(valueToSend, activeFederationAddress);
                tx.addOutput(valueToSend, retiringFederationAddress);
                tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));
                return tx;
            }
        );

        assertEquals(
            co.rsk.core.Coin.fromBitcoin(expectedValue).asBigInteger(),
            result
        );
    }

    @Test
    void registerFlyoverBtcTransaction_output_to_different_address_type_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Coin expectedValue = Coin.COIN.multiply(2);

        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsMainnet,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(valueToSend, activeFederationAddress);
                tx.addOutput(valueToSend, retiringFederationAddress);
                tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));
                return tx;
            }
        );

        assertEquals(
            co.rsk.core.Coin.fromBitcoin(expectedValue).asBigInteger(),
            result
        );
    }

    @Test
    void registerFlyoverBtcTransaction_multiple_output_to_the_same_address_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Coin expectedValue = Coin.COIN.multiply(4);

        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsRegtest,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(valueToSend, activeFederationAddress);
                tx.addOutput(valueToSend, activeFederationAddress);
                tx.addOutput(valueToSend, activeFederationAddress);
                tx.addOutput(valueToSend, activeFederationAddress);
                return tx;
            }
        );

        assertEquals(
            co.rsk.core.Coin.fromBitcoin(expectedValue).asBigInteger(),
            result
        );
    }

    @Test
    void registerFlyoverBtcTransaction_multiple_output_to_the_same_address_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Coin expectedValue = Coin.COIN.multiply(4);

        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsMainnet,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(valueToSend, retiringFederationAddress);
                tx.addOutput(valueToSend, retiringFederationAddress);
                tx.addOutput(valueToSend, retiringFederationAddress);
                tx.addOutput(valueToSend, retiringFederationAddress);
                return tx;
            }
        );

        assertEquals(
            co.rsk.core.Coin.fromBitcoin(expectedValue).asBigInteger(),
            result
        );
    }

    @Test
    void registerFlyoverBtcTransaction_multiple_output_to_different_addresses_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Coin expectedValue = Coin.COIN.multiply(5);

        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsRegtest,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

                tx.addOutput(valueToSend, activeFederationAddress);
                tx.addOutput(valueToSend, activeFederationAddress);

                tx.addOutput(valueToSend, retiringFederationAddress);
                tx.addOutput(valueToSend, retiringFederationAddress);
                tx.addOutput(valueToSend, retiringFederationAddress);

                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));

                tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));

                return tx;
            }
        );

        assertEquals(
            co.rsk.core.Coin.fromBitcoin(expectedValue).asBigInteger(),
            result
        );
    }

    @Test
    void registerFlyoverBtcTransaction_multiple_output_to_different_addresses_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Coin expectedValue = Coin.COIN.multiply(5);

        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsMainnet,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

                tx.addOutput(valueToSend, activeFederationAddress);
                tx.addOutput(valueToSend, activeFederationAddress);

                tx.addOutput(valueToSend, retiringFederationAddress);
                tx.addOutput(valueToSend, retiringFederationAddress);
                tx.addOutput(valueToSend, retiringFederationAddress);

                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));

                tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));

                return tx;
            }
        );

        assertEquals(
            co.rsk.core.Coin.fromBitcoin(expectedValue).asBigInteger(),
            result
        );
    }

    @Test
    void registerFlyoverBtcTransaction_multiple_output_to_random_addresses_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsRegtest,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));

                tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));

                return tx;
            }
        );

        assertEquals(
            FlyoverTxResponseCodes.UNPROCESSABLE_TX_VALUE_ZERO_ERROR.value(),
            result.longValue()
        );
    }

    @Test
    void registerFlyoverBtcTransaction_multiple_output_to_random_addresses_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsMainnet,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));

                tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));

                return tx;
            }
        );

        assertEquals(
            FlyoverTxResponseCodes.UNPROCESSABLE_TX_VALUE_ZERO_ERROR.value(),
            result.longValue()
        );
    }

    @Test
    void registerFlyoverBtcTransaction_multiple_output_to_all_address_type_before_RSKIP293_regtest() {
        Assertions.assertThrows(ScriptException.class, () -> sendFundsToAnyAddress(
                bridgeConstantsRegtest,
                false,
                (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                    Coin valueToSend = Coin.COIN;
                    BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

                    tx.addOutput(valueToSend, activeFederationAddress);
                    tx.addOutput(createBech32Output(bridgeConstants.getBtcParams(), valueToSend));
                    tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));
                    tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));

                    return tx;
                }
        ));
    }

    @Test
    void registerFlyoverBtcTransaction_multiple_output_to_all_address_type_after_RSKIP293_regtest() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        Coin expectedValue = Coin.COIN;
        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsRegtest,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

                tx.addOutput(valueToSend, activeFederationAddress);
                tx.addOutput(createBech32Output(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));

                return tx;
            }
        );

        assertEquals(
            co.rsk.core.Coin.fromBitcoin(expectedValue).asBigInteger(),
            result
        );
    }

    @Test
    void registerFlyoverBtcTransaction_multiple_output_to_all_address_type_before_RSKIP293_mainnet() {
        Assertions.assertThrows(ScriptException.class, () -> sendFundsToAnyAddress(
                bridgeConstantsMainnet,
                false,
                (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                    Coin valueToSend = Coin.COIN;
                    BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

                    tx.addOutput(valueToSend, activeFederationAddress);
                    tx.addOutput(createBech32Output(bridgeConstants.getBtcParams(), valueToSend));
                    tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));
                    tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));

                    return tx;
                }
        ));
    }

    @Test
    void registerFlyoverBtcTransaction_multiple_output_to_all_address_type_after_RSKIP293_mainnet() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        Coin expectedValue = Coin.COIN;
        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsMainnet,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

                tx.addOutput(valueToSend, activeFederationAddress);
                tx.addOutput(createBech32Output(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend));
                tx.addOutput(createP2shOutput(bridgeConstants.getBtcParams(), valueToSend));

                return tx;
            }
        );

        assertEquals(
            co.rsk.core.Coin.fromBitcoin(expectedValue).asBigInteger(),
            result
        );
    }

    @Test
    void registerFlyoverBtcTransaction_amount_sent_is_below_minimum_before_RSKIP293_activation() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        Coin valueToSend = BridgeUtils.getMinimumPegInTxValue(activations, bridgeConstantsRegtest).minus(Coin.CENT);
        BigInteger result = sendFundsToActiveFederation(
            false,
            valueToSend
        );

        Assertions.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);
    }

    @Test
    void registerFlyoverBtcTransaction_amount_sent_is_below_minimum_after_RSKIP293_activation() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        Coin valueBelowMinimum = BridgeUtils.getMinimumPegInTxValue(activations, bridgeConstantsRegtest).minus(Coin.CENT);
        BigInteger result = sendFundsToActiveFederation(
            true,
            valueBelowMinimum
        );

        Assertions.assertEquals(
            FlyoverTxResponseCodes.UNPROCESSABLE_TX_UTXO_AMOUNT_SENT_BELOW_MINIMUM_ERROR.value()
            , result.longValue());
    }

    @Test
    void registerFlyoverBtcTransaction_sent_multiple_output_and_one_with_amount_below_minimum_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsMainnet,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin minimumPegInTxValue = BridgeUtils.getMinimumPegInTxValue(activations, bridgeConstants);
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), minimumPegInTxValue));
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), Coin.COIN));
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), Coin.COIN));

                tx.addOutput(minimumPegInTxValue, activeFederationAddress);
                tx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), activeFederationAddress);

                tx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), retiringFederationAddress);
                tx.addOutput(minimumPegInTxValue.add(Coin.COIN), retiringFederationAddress);

                return tx;
            }
        );

        assertEquals(
            FlyoverTxResponseCodes.UNPROCESSABLE_TX_UTXO_AMOUNT_SENT_BELOW_MINIMUM_ERROR.value(),
            result.longValue()
        );
    }

    @Test
    void registerFlyoverBtcTransaction_sent_multiple_output_and_one_with_amount_below_minimum_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        BigInteger result = sendFundsToAnyAddress(
            bridgeConstantsRegtest,
            true,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin minimumPegInTxValue = BridgeUtils.getMinimumPegInTxValue(activations, bridgeConstants);
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), minimumPegInTxValue));
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), Coin.COIN));
                tx.addOutput(createP2pkhOutput(bridgeConstants.getBtcParams(), Coin.COIN));

                tx.addOutput(minimumPegInTxValue, activeFederationAddress);
                tx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), activeFederationAddress);

                tx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), retiringFederationAddress);
                tx.addOutput(minimumPegInTxValue.add(Coin.COIN), retiringFederationAddress);

                return tx;
            }
        );

        assertEquals(
            FlyoverTxResponseCodes.UNPROCESSABLE_TX_UTXO_AMOUNT_SENT_BELOW_MINIMUM_ERROR.value(),
            result.longValue()
        );
    }

    @Test
    void registerFlyoverBtcTransaction_amount_sent_is_equal_to_minimum()
        throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        Coin minimumPegInTxValue = BridgeUtils.getMinimumPegInTxValue(activations, bridgeConstantsRegtest);
        BigInteger result = sendFundsToActiveFederation(
            true,
            minimumPegInTxValue
        );

        Assertions.assertEquals(
            co.rsk.core.Coin.fromBitcoin(minimumPegInTxValue).asBigInteger()
            , result);
    }

    @Test
    void registerFlyoverBtcTransaction_amount_sent_is_over_minimum()
        throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        Coin valueOverMinimum = BridgeUtils.getMinimumPegInTxValue(activations, bridgeConstantsRegtest).add(Coin.CENT);
        BigInteger result = sendFundsToActiveFederation(
            true,
            valueOverMinimum
        );

        Assertions.assertEquals(
            co.rsk.core.Coin.fromBitcoin(valueOverMinimum).asBigInteger()
            , result);
    }

    @Test
    void registerFlyoverBtcTransaction_funds_sent_to_current_retiring_fed_before_RSKIP293_activation()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {

        Coin valueToSend = Coin.COIN;
        BigInteger result = sendFundsToRetiringFederation(
            false,
            valueToSend
        );

        Assertions.assertEquals(FlyoverTxResponseCodes.UNPROCESSABLE_TX_VALUE_ZERO_ERROR.value(), result.longValue());
    }

    @Test
    void registerFlyoverBtcTransaction_funds_sent_to_active_and_retiring_fed_before_RSKIP293_activation() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Coin valueToSend = Coin.COIN;
        // should send funds to both federation but only the active federation must receive it
        BigInteger result = sendFundsToActiveAndRetiringFederation(
            false,
            valueToSend
        );

        Assertions.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);
    }

    @Test
    void registerFlyoverBtcTransaction_funds_sent_to_active_and_retiring_fed_after_RSKIP293_activation() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        Coin valueToSend = Coin.COIN;
        // send funds to both federations, the active federation and current retiring federation
        BigInteger result = sendFundsToActiveAndRetiringFederation(
            true,
            valueToSend
        );

        Assertions.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend.multiply(2)).asBigInteger(), result);
    }

    @Test
    void registerFlyoverBtcTransaction_funds_sent_to_active_fed_after_RSKIP293() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        Coin valueToSend = Coin.COIN;
        BigInteger result  = sendFundsToActiveFederation(
            true,
            valueToSend
        );

        Assertions.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);
    }

    @Test
    void registerFlyoverBtcTransaction_funds_sent_to_retiring_fed_after_RSKIP293() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        Coin valueToSend = Coin.COIN;
        BigInteger result = sendFundsToRetiringFederation(
            true,
            valueToSend
        );

        Assertions.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);
    }

    @Test
    void registerFlyoverBtcTransaction_sum_of_funds_sent_to_active_and_retiring_fed_surpasses_locking_cap_before_RSKIP293_no_refund() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        BigInteger result = sendFundsSurpassesLockingCapToAnyAddress(
            false,
            Coin.FIFTY_COINS,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(Coin.FIFTY_COINS.div(2), activeFederationAddress);
                tx.addOutput(Coin.FIFTY_COINS, retiringFederationAddress);
                return tx;
            },
            true
        );
        Assertions.assertEquals(co.rsk.core.Coin.fromBitcoin(Coin.FIFTY_COINS.div(2)).asBigInteger(), result);
    }

    @Test
    void registerFlyoverBtcTransaction_funds_sent_to_active_and_retiring_fed_surpasses_locking_cap_before_RSKIP293_only_funds_sent_to_active_fed_should_be_refunded_to_LP() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        BigInteger result = sendFundsSurpassesLockingCapToAnyAddress(
            false,
            Coin.FIFTY_COINS,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(Coin.FIFTY_COINS.add(Coin.COIN), activeFederationAddress);
                tx.addOutput(Coin.FIFTY_COINS.add(Coin.COIN), retiringFederationAddress);
                return tx;
            },
            true
        );
        Assertions.assertEquals(FlyoverTxResponseCodes.REFUNDED_LP_ERROR.value(), result.longValue());
    }

    @Test
    void registerFlyoverBtcTransaction_funds_sent_to_active_and_retiring_fed_surpasses_locking_cap_before_RSKIP293_only_funds_sent_to_active_fed_should_be_refunded_to_user() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        BigInteger result = sendFundsSurpassesLockingCapToAnyAddress(
            false,
            Coin.FIFTY_COINS,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(Coin.FIFTY_COINS.add(Coin.COIN), activeFederationAddress);
                tx.addOutput(Coin.FIFTY_COINS.add(Coin.COIN), retiringFederationAddress);
                return tx;
            },
            false
        );
        Assertions.assertEquals(FlyoverTxResponseCodes.REFUNDED_USER_ERROR.value(), result.longValue());
    }

    @Test
    void registerFlyoverBtcTransaction_funds_sent_to_retiring_fed_surpasses_locking_cap_should_refund_user_after_RSKIP293() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        BigInteger result = sendFundsSurpassesLockingCapToAnyAddress(
            true,
            Coin.FIFTY_COINS,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(Coin.FIFTY_COINS.add(Coin.COIN), retiringFederationAddress);
                return tx;
            },
            false
        );
        Assertions.assertEquals(FlyoverTxResponseCodes.REFUNDED_USER_ERROR.value(), result.longValue());
    }

    @Test
    void registerFlyoverBtcTransaction_sum_of_funds_sent_to_active_and_retiring_fed_surpasses_locking_cap_should_refund_lp_after_RSKIP293() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        BigInteger result = sendFundsSurpassesLockingCapToAnyAddress(
            true,
            Coin.FIFTY_COINS,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(Coin.FIFTY_COINS, activeFederationAddress);
                tx.addOutput(Coin.FIFTY_COINS, retiringFederationAddress);
                return tx;
            },
            true
        );
        Assertions.assertEquals(FlyoverTxResponseCodes.REFUNDED_LP_ERROR.value(), result.longValue());
    }

    @Test
    void registerFlyoverBtcTransaction_sum_of_all_utxos_surpass_locking_cap_but_not_funds_sent_to_fed_should_not_refund_after_RSKIP293() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        BigInteger result = sendFundsSurpassesLockingCapToAnyAddress(
            true,
            Coin.FIFTY_COINS,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(Coin.COIN, activeFederationAddress);
                tx.addOutput(Coin.COIN, retiringFederationAddress);
                tx.addOutput(Coin.FIFTY_COINS, createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()));
                return tx;
            },
            false
        );
        Assertions.assertEquals(co.rsk.core.Coin.fromBitcoin(Coin.valueOf(2, 0)).asBigInteger(), result);
    }

    @Test
    void registerFlyoverBtcTransaction_funds_sent_to_random_addresses_surpass_locking_cap_should_not_refund_after_RSKIP293() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        BigInteger result = sendFundsSurpassesLockingCapToAnyAddress(
            true,
            Coin.FIFTY_COINS,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(Coin.FIFTY_COINS, createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()));
                tx.addOutput(Coin.COIN, createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()));
                return tx;
            },
            false
        );
        Assertions.assertEquals(FlyoverTxResponseCodes.UNPROCESSABLE_TX_VALUE_ZERO_ERROR.value(), result.longValue());
    }

    @Test
    void registerFlyoverBtcTransaction_failed_when_tx_with_witness_already_saved_in_storage()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);
        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        doReturn(BridgeRegTestConstants.getInstance().getFederationActivationAge()).when(bridgeConstants).getFederationActivationAge();

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        Federation activeFederation = PegTestUtils.createFederation(bridgeConstants, "fa03", "fa04");
        Repository repository = createRepository();

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activations));
        provider.setNewFederation(activeFederation);

        Address userRefundBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
        Address lpBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();
        Keccak256 fastBridgeDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address activeFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
        );

        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        btcTx.addOutput(Coin.COIN, activeFederationAddress);

        btcTx.addInput(
            Sha256Hash.ZERO_HASH,
            0, ScriptBuilder.createInputScript(null, new BtcECKey())
        );
        TransactionWitness txWitness = new TransactionWitness(1);
        txWitness.setPush(0, new byte[]{ 0x1 });
        btcTx.setWitness(0, txWitness);

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash(false));
        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            PegTestUtils.createHash(2),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );
        int height = 1;
        // simulate blockchain
        mockChainOfStoredBlocks(
            btcBlockStore,
            registerHeader,
            height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        // Register block's witnessMerkleRoot to validate wtxId inclusion
        List<Sha256Hash> hashesWithWitness = new ArrayList<>();
        hashesWithWitness.add(btcTx.getHash(true));
        byte[] bitsWithWitness = new byte[1];
        bitsWithWitness[0] = 0x3f;
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bitsWithWitness, hashesWithWitness, 1);
        Sha256Hash witnessMerkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        Keccak256 derivationHash =
            bridgeSupport.getFlyoverDerivationHash(derivationArgumentsHash, userRefundBtcAddress, lpBtcAddress, lbcAddress);

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            false
        );

        assertEquals(co.rsk.core.Coin.fromBitcoin(Coin.COIN).asBigInteger(), result);

        // Transaction includes a witness making its txId != wTxId
        Assertions.assertNotEquals(btcTx.getHash(), btcTx.getHash(true));

        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(true), derivationHash);
        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationHash);

        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(true), derivationArgumentsHash);
        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationArgumentsHash);

        verify(provider).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(true), derivationHash);

        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationArgumentsHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(true), derivationArgumentsHash);

        // Persist provider data
        provider.save();

        // Reset mockito call counter for provider instance
        Mockito.reset(provider);

        // Perform another flyover pegin using the same btcTx expecting as a result the error: UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR
        result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            false
        );

        assertEquals(FlyoverTxResponseCodes.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR.value(), result.longValue());

        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(true), derivationHash);
        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationHash);

        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(true), derivationArgumentsHash);
        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationArgumentsHash);

        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(true), derivationHash);

        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationArgumentsHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(true), derivationArgumentsHash);
    }

    @Test
    void registerFlyoverBtcTransaction_failed_when_tx_with_witness_surpassing_locking_already_saved_in_storage()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);
        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        doReturn(BridgeRegTestConstants.getInstance().getFederationActivationAge()).when(bridgeConstants).getFederationActivationAge();

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        Federation activeFederation = PegTestUtils.createFederation(bridgeConstants, "fa03", "fa04");

        Repository repository = createRepository();
        // For simplicity of this test, the max rbtc value is set as the current balance for the repository
        // This simulates that no pegin has ever been processed
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, co.rsk.core.Coin.fromBitcoin(bridgeConstants.getMaxRbtc()));

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activations));
        provider.setNewFederation(activeFederation);

        Coin lockingCapValue = Coin.COIN;
        provider.setLockingCap(lockingCapValue);

        ReleaseTransactionSet releaseTransactionSet = new ReleaseTransactionSet(new HashSet<>());
        doReturn(releaseTransactionSet).when(provider).getReleaseTransactionSet();

        Address userRefundBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
        Address lpBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();
        Keccak256 fastBridgeDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address activeFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
        );

        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        btcTx.addOutput(Coin.COIN.multiply(2), activeFederationAddress);

        btcTx.addInput(
            Sha256Hash.ZERO_HASH,
            0, ScriptBuilder.createInputScript(null, new BtcECKey())
        );
        TransactionWitness txWitness = new TransactionWitness(1);
        txWitness.setPush(0, new byte[]{ 0x1 });
        btcTx.setWitness(0, txWitness);

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash(false));
        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            PegTestUtils.createHash(2),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );
        int height = 1;
        // simulate blockchain
        mockChainOfStoredBlocks(
            btcBlockStore,
            registerHeader,
            height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        // Register block's witnessMerkleRoot to validate wtxId inclusion
        List<Sha256Hash> hashesWithWitness = new ArrayList<>();
        hashesWithWitness.add(btcTx.getHash(true));
        byte[] bitsWithWitness = new byte[1];
        bitsWithWitness[0] = 0x3f;
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bitsWithWitness, hashesWithWitness, 1);
        Sha256Hash witnessMerkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        Keccak256 derivationHash =
            bridgeSupport.getFlyoverDerivationHash(derivationArgumentsHash, userRefundBtcAddress, lpBtcAddress, lbcAddress);

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            false
        );

        assertEquals(FlyoverTxResponseCodes.REFUNDED_USER_ERROR.value(), result.longValue());

        // Transaction includes a witness making its txId != wTxId
        Assertions.assertNotEquals(btcTx.getHash(), btcTx.getHash(true));

        // The verified derivation hash should be the computed one
        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(true), derivationHash);
        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationHash);

        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(true), derivationArgumentsHash);
        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationArgumentsHash);

        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(true), derivationHash);
        verify(provider).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationHash);

        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationArgumentsHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(true), derivationArgumentsHash);

        // Persist provider data
        provider.save();
        // Reset mockito call counter for provider instance
        Mockito.reset(provider);

        // Perform another flyover pegin using the same BtcTx expecting it get processed successfully
        result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            false
        );

        assertEquals(FlyoverTxResponseCodes.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR.value(), result.longValue());

        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(true), derivationHash);
        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationHash);

        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(true), derivationArgumentsHash);
        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationArgumentsHash);

        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(true), derivationHash);

        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationArgumentsHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(true), derivationArgumentsHash);
    }

    @Test
    void registerFlyoverBtcTransaction_failed_when_tx_with_witness_surpassing_locking_already_saved_in_storage_and_then_send_same_without_witness()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);
        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        doReturn(BridgeRegTestConstants.getInstance().getFederationActivationAge()).when(bridgeConstants).getFederationActivationAge();

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        Federation activeFederation = PegTestUtils.createFederation(bridgeConstants, "fa03", "fa04");

        Repository repository = createRepository();
        // For simplicity of this test, the max rbtc value is set as the current balance for the repository
        // This simulates that no pegin has ever been processed
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, co.rsk.core.Coin.fromBitcoin(bridgeConstants.getMaxRbtc()));

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activations));
        provider.setNewFederation(activeFederation);

        Coin lockingCapValue = Coin.COIN;
        provider.setLockingCap(lockingCapValue);

        ReleaseTransactionSet releaseTransactionSet = new ReleaseTransactionSet(new HashSet<>());
        doReturn(releaseTransactionSet).when(provider).getReleaseTransactionSet();

        Address userRefundBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
        Address lpBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();
        Keccak256 fastBridgeDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address activeFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
        );

        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        btcTx.addOutput(lockingCapValue.multiply(2), activeFederationAddress);

        btcTx.addInput(
            Sha256Hash.ZERO_HASH,
            0, ScriptBuilder.createInputScript(null, new BtcECKey())
        );
        TransactionWitness txWitness = new TransactionWitness(1);
        txWitness.setPush(0, new byte[]{ 0x1 });
        btcTx.setWitness(0, txWitness);

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash(false));
        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            PegTestUtils.createHash(2),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );
        int height = 1;
        // simulate blockchain
        mockChainOfStoredBlocks(
            btcBlockStore,
            registerHeader,
            height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        // Register block's witnessMerkleRoot to validate wtxId inclusion
        List<Sha256Hash> hashesWithWitness = new ArrayList<>();
        hashesWithWitness.add(btcTx.getHash(true));
        byte[] bitsWithWitness = new byte[1];
        bitsWithWitness[0] = 0x3f;
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bitsWithWitness, hashesWithWitness, 1);
        Sha256Hash witnessMerkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        Keccak256 derivationHash =
            bridgeSupport.getFlyoverDerivationHash(derivationArgumentsHash, userRefundBtcAddress, lpBtcAddress, lbcAddress);

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            false
        );

        assertEquals(FlyoverTxResponseCodes.REFUNDED_USER_ERROR.value(), result.longValue());

        // Transaction includes a witness making its txId != wTxId
        Assertions.assertNotEquals(btcTx.getHash(), btcTx.getHash(true));

        // The verified derivation hash should be the computed one
        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(true), derivationHash);
        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationHash);

        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(true), derivationArgumentsHash);
        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationArgumentsHash);

        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(true), derivationHash);
        verify(provider).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationHash);

        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationArgumentsHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(true), derivationArgumentsHash);

        // Persist provider data
        provider.save();
        // Reset mockito call counter for provider instance
        Mockito.reset(provider);

        // Perform another flyover pegin using the same BtcTx but without witness and no surpassing locking cap this time
        // We expect the pegin gets processed successfully
        btcTx.setWitness(0, null);
        provider.setLockingCap(lockingCapValue.multiply(3));

        result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmtWithoutWitness.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            false
        );

        assertEquals(FlyoverTxResponseCodes.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR.value(), result.longValue());

        // Transaction does not include a witness making its txId == wTxId
        Assertions.assertEquals(btcTx.getHash(), btcTx.getHash(true));

        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationArgumentsHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationArgumentsHash);
    }

    @Test
    void registerFlyoverBtcTransaction_failed_when_tx_without_witness_surpassing_locking_already_saved_in_storage()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);
        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        doReturn(BridgeRegTestConstants.getInstance().getFederationActivationAge()).when(bridgeConstants).getFederationActivationAge();

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        Federation activeFederation = PegTestUtils.createFederation(bridgeConstants, "fa03", "fa04");
        Repository repository = createRepository();
        // For simplicity of this test, the max rbtc value is set as the current balance for the repository
        // This simulates that no pegin has ever been processed
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, co.rsk.core.Coin.fromBitcoin(bridgeConstants.getMaxRbtc()));

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activations));
        provider.setNewFederation(activeFederation);

        Coin lockingCapValue = Coin.COIN;
        provider.setLockingCap(lockingCapValue);

        ReleaseTransactionSet releaseTransactionSet = new ReleaseTransactionSet(new HashSet<>());
        doReturn(releaseTransactionSet).when(provider).getReleaseTransactionSet();

        Address userRefundBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
        Address lpBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();
        Keccak256 fastBridgeDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address activeFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
        );

        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        btcTx.addOutput(lockingCapValue.multiply(2), activeFederationAddress);

        btcTx.addInput(
            Sha256Hash.ZERO_HASH,
            0, ScriptBuilder.createInputScript(null, new BtcECKey())
        );

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash(false));
        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            PegTestUtils.createHash(2),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );
        int height = 1;
        // simulate blockchain
        mockChainOfStoredBlocks(
            btcBlockStore,
            registerHeader,
            height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        Keccak256 derivationHash =
            bridgeSupport.getFlyoverDerivationHash(derivationArgumentsHash, userRefundBtcAddress, lpBtcAddress, lbcAddress);

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            false
        );

        assertEquals(FlyoverTxResponseCodes.REFUNDED_USER_ERROR.value(), result.longValue());

        // Transaction does not include a witness making its txId == wTxId
        Assertions.assertEquals(btcTx.getHash(), btcTx.getHash(true));

        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationArgumentsHash);

        verify(provider).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationArgumentsHash);

        // Persist provider data
        provider.save();
        // Reset mockito call counter for provider instance
        Mockito.reset(provider);

        // Perform another flyover pegin using the same BtcTx expecting it failed
        result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            false
        );

        assertEquals(FlyoverTxResponseCodes.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR.value(), result.longValue());

        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationArgumentsHash);

        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationArgumentsHash);
    }

    @Test
    void registerFlyoverBtcTransaction_failed_when_tx_without_witness_surpassing_locking_already_saved_in_storage_and_then_send_same_tx_with_witness()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);
        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        doReturn(BridgeRegTestConstants.getInstance().getFederationActivationAge()).when(bridgeConstants).getFederationActivationAge();

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        Federation activeFederation = PegTestUtils.createFederation(bridgeConstants, "fa03", "fa04");
        Repository repository = createRepository();
        // For simplicity of this test, the max rbtc value is set as the current balance for the repository
        // This simulates that no pegin has ever been processed
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, co.rsk.core.Coin.fromBitcoin(bridgeConstants.getMaxRbtc()));

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activations));
        provider.setNewFederation(activeFederation);

        Coin lockingCapValue = Coin.COIN;
        provider.setLockingCap(lockingCapValue);

        ReleaseTransactionSet releaseTransactionSet = new ReleaseTransactionSet(new HashSet<>());
        doReturn(releaseTransactionSet).when(provider).getReleaseTransactionSet();

        Address userRefundBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
        Address lpBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();
        Keccak256 fastBridgeDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address activeFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
        );

        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        btcTx.addOutput(lockingCapValue.multiply(2), activeFederationAddress);

        btcTx.addInput(
            Sha256Hash.ZERO_HASH,
            0, ScriptBuilder.createInputScript(null, new BtcECKey())
        );

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash(false));
        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            PegTestUtils.createHash(2),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );
        int height = 1;
        // simulate blockchain
        mockChainOfStoredBlocks(
            btcBlockStore,
            registerHeader,
            height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        Keccak256 derivationHash =
            bridgeSupport.getFlyoverDerivationHash(derivationArgumentsHash, userRefundBtcAddress, lpBtcAddress, lbcAddress);

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            false
        );

        assertEquals(FlyoverTxResponseCodes.REFUNDED_USER_ERROR.value(), result.longValue());

        // Transaction does not include a witness making its txId == wTxId
        Assertions.assertEquals(btcTx.getHash(), btcTx.getHash(true));

        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationArgumentsHash);

        verify(provider).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationArgumentsHash);

        // Persist provider data
        provider.save();
        // Reset mockito call counter for provider instance
        Mockito.reset(provider);

        // Perform another flyover pegin using the same BtcTx but this time with witness
        // We expect the flyover pegin fail

        TransactionWitness txWitness = new TransactionWitness(1);
        txWitness.setPush(0, new byte[]{ 0x1 });
        btcTx.setWitness(0, txWitness);

        // Register block's witnessMerkleRoot to validate wtxId inclusion
        List<Sha256Hash> hashesWithWitness = new ArrayList<>();
        hashesWithWitness.add(btcTx.getHash(true));
        byte[] bitsWithWitness = new byte[1];
        bitsWithWitness[0] = 0x3f;
        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(bridgeConstants.getBtcParams(), bitsWithWitness, hashesWithWitness, 1);
        Sha256Hash witnessMerkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmtWithWitness.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            false
        );

        assertEquals(FlyoverTxResponseCodes.UNPROCESSABLE_TX_VALIDATIONS_ERROR.value(), result.longValue());

        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationArgumentsHash);

        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationArgumentsHash);
    }

    @Test
    void registerFlyoverBtcTransaction_failed_when_tx_without_witness_already_saved_in_storage()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);
        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        doReturn(BridgeRegTestConstants.getInstance().getFederationActivationAge()).when(bridgeConstants).getFederationActivationAge();

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        Federation activeFederation = PegTestUtils.createFederation(bridgeConstants, "fa03", "fa04");
        Repository repository = createRepository();

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR, bridgeConstants, activations));
        provider.setNewFederation(activeFederation);

        Address userRefundBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
        Address lpBtcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(0);
        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();
        Keccak256 fastBridgeDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address activeFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
        );

        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
        btcTx.addOutput(Coin.COIN, activeFederationAddress);

        btcTx.addInput(
            Sha256Hash.ZERO_HASH,
            0, ScriptBuilder.createInputScript(null, new BtcECKey())
        );

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash(false));
        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            PegTestUtils.createHash(2),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );
        int height = 1;
        // simulate blockchain
        mockChainOfStoredBlocks(
            btcBlockStore,
            registerHeader,
            height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        Keccak256 derivationHash =
            bridgeSupport.getFlyoverDerivationHash(derivationArgumentsHash, userRefundBtcAddress, lpBtcAddress, lbcAddress);

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            false
        );

        assertEquals(co.rsk.core.Coin.fromBitcoin(Coin.COIN).asBigInteger(), result);

        // Transaction does not include a witness making its txId == wTxId
        Assertions.assertEquals(btcTx.getHash(), btcTx.getHash(true));

        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationArgumentsHash);

        verify(provider).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationArgumentsHash);

        // Persist provider data
        provider.save();

        // Reset mockito call counter for provider instance
        Mockito.reset(provider);

        // Perform another flyover pegin using the same btcTx expecting as result the following error: UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR
        result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize(),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            false
        );

        assertEquals(FlyoverTxResponseCodes.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR.value(), result.longValue());

        verify(provider).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).isFlyoverDerivationHashUsed(btcTx.getHash(false), derivationArgumentsHash);

        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationHash);
        verify(provider, never()).markFlyoverDerivationHashAsUsed(btcTx.getHash(false), derivationArgumentsHash);
    }

    @Test
    void registerFlyoverBtcTransaction_is_not_contract()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        BridgeSupport bridgeSupport = bridgeSupportBuilder.withBridgeConstants(bridgeConstantsRegtest).build();
        Transaction rskTxMock = mock(Transaction.class);
        Keccak256 hash = new Keccak256(HashUtil.keccak256(new byte[]{}));
        when(rskTxMock.getHash()).thenReturn(hash);

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTxMock,
            new byte[]{},
            0,
            new byte[]{},
            PegTestUtils.createHash3(0),
            mock(Address.class),
            mock(RskAddress.class),
            mock(Address.class),
            false
        );

        Assertions.assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_NOT_CONTRACT_ERROR.value()), result);
    }

    @Test
    void registerFlyoverBtcTransaction_sender_is_not_lbc()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction tx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());


        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .withBridgeConstants(bridgeConstantsRegtest)
            .build();

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            Hex.decode("ab"),
            PegTestUtils.createHash3(0),
            mock(Address.class),
            mock(RskAddress.class),
            mock(Address.class),
            false
        );

        Assertions.assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_INVALID_SENDER_ERROR.value()), result);
    }

    @Test
    void registerFlyoverBtcTransaction_validationsForRegisterBtcTransaction_returns_false()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction tx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());

        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BridgeSupport bridgeSupport = spy(bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .withBridgeConstants(bridgeConstantsRegtest)
            .build());

        doReturn(PegTestUtils.createHash3(5))
            .when(bridgeSupport)
            .getFlyoverDerivationHash(any(), any(), any(), any());

        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            Hex.decode("ab"),
            PegTestUtils.createHash3(0),
            mock(Address.class),
            lbcAddress,
            mock(Address.class),
            false
        );

        Assertions.assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_VALIDATIONS_ERROR.value()), result);
    }

    @Test
    void registerFlyoverBtcTransaction_amount_sent_is_0()
        throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstantsRegtest.getBtcParams());

        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstantsRegtest,
            mock(BridgeStorageProvider.class),
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            btcContext,
            mock(FederationSupport.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        ));

        doReturn(bridgeConstantsRegtest.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFlyoverDerivationHash(
            any(Keccak256.class),
            any(Address.class),
            any(Address.class),
            any(RskAddress.class)
        );

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, new BtcECKey().toAddress(btcRegTestParams));
        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            Hex.decode("ab"),
            PegTestUtils.createHash3(0),
            mock(Address.class),
            lbcAddress,
            mock(Address.class),
            false
        );

        Assertions.assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_VALUE_ZERO_ERROR.value()), result);
    }

    @Test
    void registerFlyoverBtcTransaction_surpasses_locking_cap_and_shouldTransfer_is_true()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        ReleaseTransactionSet releaseTransactionSet = new ReleaseTransactionSet(new HashSet<>());
        when(provider.getReleaseTransactionSet()).thenReturn(releaseTransactionSet);

        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        Repository repository = mock(Repository.class);
        when(repository.getBalance(any())).thenReturn(co.rsk.core.Coin.valueOf(1));

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstantsRegtest.getBtcParams());

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstantsRegtest,
            provider,
            mock(BridgeEventLogger.class),
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            repository,
            mock(Block.class),
            btcContext,
            mock(FederationSupport.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        ));

        doReturn(bridgeConstantsRegtest.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(Coin.COIN).when(bridgeSupport).getLockingCap();
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFlyoverDerivationHash(
            any(Keccak256.class),
            any(Address.class),
            any(Address.class),
            any(RskAddress.class)
        );

        Address btcAddress = Address.fromBase58(
            btcRegTestParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, getFlyoverFederationAddress());
        byte[] pmtSerialized = Hex.decode("ab");
        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            pmtSerialized,
            PegTestUtils.createHash3(0),
            btcAddress,
            lbcAddress,
            btcAddress,
            true
        );

        Assertions.assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.REFUNDED_LP_ERROR.value()), result);
    }

    @Test
    void registerFlyoverBtcTransaction_surpasses_locking_cap_and_shouldTransfer_is_false()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);

        ReleaseTransactionSet releaseTransactionSet = new ReleaseTransactionSet(new HashSet<>());

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseTransactionSet()).thenReturn(releaseTransactionSet);
        when(provider.isFlyoverDerivationHashUsed(any(), any())).thenReturn(false);

        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        Repository repository = mock(Repository.class);
        when(repository.getBalance(any())).thenReturn(co.rsk.core.Coin.valueOf(1));

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstantsRegtest.getBtcParams());

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstantsRegtest,
            provider,
            mock(BridgeEventLogger.class),
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            repository,
            mock(Block.class),
            btcContext,
            mock(FederationSupport.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        ));

        doReturn(bridgeConstantsRegtest.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(Coin.COIN).when(bridgeSupport).getLockingCap();
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFlyoverDerivationHash(
            any(Keccak256.class),
            any(Address.class),
            any(Address.class),
            any(RskAddress.class)
        );

        Address btcAddress = Address.fromBase58(
            btcRegTestParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, getFlyoverFederationAddress());
        byte[] pmtSerialized = Hex.decode("ab");
        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            pmtSerialized,
            PegTestUtils.createHash3(0),
            btcAddress,
            lbcAddress,
            btcAddress,
            false
        );

        Assertions.assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.REFUNDED_USER_ERROR.value()), result);
    }

    @Test
    void registerFlyoverBtcTransaction_surpasses_locking_cap_and_tries_to_register_again()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);

        Repository repository = createRepository();
        repository.addBalance(PrecompiledContracts.BRIDGE_ADDR, co.rsk.core.Coin.valueOf(1));

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );

        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstantsRegtest.getBtcParams());

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstantsRegtest,
            provider,
            mock(BridgeEventLogger.class),
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            repository,
            mock(Block.class),
            btcContext,
            mock(FederationSupport.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        ));

        doReturn(bridgeConstantsRegtest.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(
            Coin.COIN, // The first time we simulate a lower locking cap than the value to register, to force the reimburse
            Coin.FIFTY_COINS // The next time we simulate a hight locking cap, to verify the user can't attempt to register the already reimbursed tx
        ).when(bridgeSupport).getLockingCap();
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFlyoverDerivationHash(
            any(Keccak256.class),
            any(Address.class),
            any(Address.class),
            any(RskAddress.class)
        );

        Address btcAddress = Address.fromBase58(
            btcRegTestParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, getFlyoverFederationAddress());
        byte[] pmtSerialized = Hex.decode("ab");
        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        Keccak256 dHash = PegTestUtils.createHash3(0);

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            pmtSerialized,
            dHash,
            btcAddress,
            lbcAddress,
            btcAddress,
            false
        );

        Assertions.assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.REFUNDED_USER_ERROR.value()), result);

        // Update repository
        bridgeSupport.save();

        result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            pmtSerialized,
            dHash,
            btcAddress,
            lbcAddress,
            btcAddress,
            false
        );

        Assertions.assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR.value()), result);
    }

    @Test
    void registerFlyoverBtcTransaction_OK()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstantsRegtest.getBtcParams());

        Repository repository = spy(createRepository());
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );

        FederationSupport federationSupportMock = mock(FederationSupport.class);
        doReturn(provider.getNewFederationBtcUTXOs()).when(federationSupportMock).getActiveFederationBtcUTXOs();

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstantsRegtest,
            provider,
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            repository,
            mock(Block.class),
            btcContext,
            federationSupportMock,
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        ));

        doReturn(bridgeConstantsRegtest.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFlyoverDerivationHash(
            any(Keccak256.class),
            any(Address.class),
            any(Address.class),
            any(RskAddress.class)
        );

        Address btcAddress = Address.fromBase58(
            btcRegTestParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        Coin valueToSend = Coin.COIN;
        BtcTransaction tx = createBtcTransactionWithOutputToAddress(valueToSend, getFlyoverFederationAddress());
        InternalTransaction rskTx = new InternalTransaction(
            Keccak256.ZERO_HASH.getBytes(),
            0,
            0,
            null,
            null,
            null,
            lbcAddress.getBytes(),
            null,
            null,
            null,
            null
        );

        co.rsk.core.Coin preCallLbcAddressBalance = repository.getBalance(lbcAddress);

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            Hex.decode("ab"),
            PegTestUtils.createHash3(0),
            btcAddress,
            lbcAddress,
            btcAddress,
            true
        );

        Assertions.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);

        co.rsk.core.Coin postCallLbcAddressBalance = repository.getBalance(lbcAddress);

        Assertions.assertEquals(
            preCallLbcAddressBalance.add(co.rsk.core.Coin.fromBitcoin(Coin.COIN)),
            postCallLbcAddressBalance
        );

        bridgeSupport.save();

        Assertions.assertTrue(
            provider.isFlyoverDerivationHashUsed(
                tx.getHash(),
                bridgeSupport.getFlyoverDerivationHash(PegTestUtils.createHash3(0), btcAddress, btcAddress, lbcAddress)
            )
        );
        Assertions.assertEquals(1, provider.getNewFederationBtcUTXOs().size());

        // Trying to register the same transaction again fails
        result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            Hex.decode("ab"),
            PegTestUtils.createHash3(0),
            btcAddress,
            lbcAddress,
            btcAddress,
            true
        );

        Assertions.assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR.value()), result);
    }

    @Test
    void createFlyoverFederationInformation_OK() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Federation fed = bridgeConstantsRegtest.getGenesisFederation();
        FederationSupport federationSupport = mock(FederationSupport.class);
        when(federationSupport.getActiveFederation()).thenReturn(fed);

        BridgeSupport bridgeSupport = new BridgeSupport(
            bridgeConstantsRegtest,
            mock(BridgeStorageProvider.class),
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            mock(Context.class),
            federationSupport,
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        );

        Script flyoverRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            bridgeConstantsRegtest.getGenesisFederation().getRedeemScript(),
            PegTestUtils.createHash(1)
        );

        Script flyoverP2SH = ScriptBuilder.createP2SHOutputScript(flyoverRedeemScript);
        Keccak256 derivationHash = PegTestUtils.createHash3(1);

        FlyoverFederationInformation expectedFlyoverFederationInformation =
            new FlyoverFederationInformation(derivationHash,
                fed.getP2SHScript().getPubKeyHash(),
                flyoverP2SH.getPubKeyHash()
            );

        FlyoverFederationInformation obtainedFlyoverFedInfo =
            bridgeSupport.createFlyoverFederationInformation(derivationHash);

        Assertions.assertEquals(
            expectedFlyoverFederationInformation.getFlyoverFederationAddress(bridgeConstantsRegtest.getBtcParams()),
            obtainedFlyoverFedInfo.getFlyoverFederationAddress(bridgeConstantsRegtest.getBtcParams())
        );
    }

    @Test
    void getFlyoverWallet_ok() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstantsRegtest.getBtcParams());

        BridgeSupport bridgeSupport = new BridgeSupport(
            bridgeConstantsRegtest,
            mock(BridgeStorageProvider.class),
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            btcContext,
            mock(FederationSupport.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations
        );

        Federation fed = bridgeConstantsRegtest.getGenesisFederation();
        Keccak256 derivationHash = PegTestUtils.createHash3(1);

        Script flyoverRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            fed.getRedeemScript(),
            Sha256Hash.wrap(derivationHash.getBytes())
        );

        Script flyoverP2SH = ScriptBuilder.createP2SHOutputScript(flyoverRedeemScript);

        FlyoverFederationInformation flyoverFederationInformation =
            new FlyoverFederationInformation(
                derivationHash,
                fed.getP2SHScript().getPubKeyHash(),
                flyoverP2SH.getPubKeyHash()
            );

        BtcTransaction tx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        tx.addOutput(Coin.COIN,
            flyoverFederationInformation.getFlyoverFederationAddress(
                bridgeConstantsRegtest.getBtcParams()
            )
        );

        List<UTXO> utxoList = new ArrayList<>();
        UTXO utxo = new UTXO(tx.getHash(), 0, Coin.COIN, 0, false, flyoverP2SH);
        utxoList.add(utxo);

        Wallet obtainedWallet = bridgeSupport.getFlyoverWallet(btcContext, utxoList, Collections.singletonList(flyoverFederationInformation));

        Assertions.assertEquals(Coin.COIN, obtainedWallet.getBalance());
    }

    @Test
    void getFlyoverDerivationHash_ok() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withActivations(activations)
            .build();

        Address userRefundBtcAddress = Address.fromBase58(
            bridgeConstantsRegtest.getBtcParams(),
            "mgy8yiUZYB7o9vvCu2Yi8GB3Vr32MQsyQJ"
        );
        byte[] userRefundBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(activations, userRefundBtcAddress);

        Address lpBtcAddress = Address.fromBase58(
            bridgeConstantsRegtest.getBtcParams(),
            "mhoDGMzHHDq2ZD6cFrKV9USnMfpxEtLwGm"
        );
        byte[] lpBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(activations, lpBtcAddress);

        byte[] derivationArgumentsHash = ByteUtil.leftPadBytes(new byte[]{0x01}, 32);
        byte[] lbcAddress = ByteUtil.leftPadBytes(new byte[]{0x03}, 20);
        byte[] result = ByteUtil.merge(derivationArgumentsHash, userRefundBtcAddressBytes, lbcAddress, lpBtcAddressBytes);

        Keccak256 flyoverDerivationHash = bridgeSupport.getFlyoverDerivationHash(
            new Keccak256(derivationArgumentsHash),
            userRefundBtcAddress,
            lpBtcAddress,
            new RskAddress(lbcAddress)
        );

        Assertions.assertArrayEquals(HashUtil.keccak256(result), flyoverDerivationHash.getBytes());
    }

    @Test
    void saveFlyoverDataInStorage_OK() throws IOException {
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        Repository repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstantsRegtest,
            activations
        );
        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withActivations(activations)
            .build();

        Sha256Hash btcTxHash = PegTestUtils.createHash(1);
        Keccak256 derivationHash = PegTestUtils.createHash3(1);

        byte[] flyoverScriptHash = new byte[]{0x1};
        FlyoverFederationInformation flyoverFederationInformation = new FlyoverFederationInformation(
            PegTestUtils.createHash3(2),
            new byte[]{0x1},
            flyoverScriptHash
        );

        List<UTXO> utxos = new ArrayList<>();
        Sha256Hash utxoHash = PegTestUtils.createHash(1);
        UTXO utxo = new UTXO(utxoHash, 0, Coin.COIN.multiply(2), 0, false, new Script(new byte[]{}));
        utxos.add(utxo);

        Assertions.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        bridgeSupport.saveFlyoverActiveFederationDataInStorage(btcTxHash, derivationHash, flyoverFederationInformation, utxos);

        bridgeSupport.save();

        Assertions.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        assertEquals(utxo, provider.getNewFederationBtcUTXOs().get(0));
        Assertions.assertTrue(provider.isFlyoverDerivationHashUsed(btcTxHash, derivationHash));
        Optional<FlyoverFederationInformation> optionalFlyoverFederationInformation = provider.getFlyoverFederationInformation(flyoverScriptHash);
        Assertions.assertTrue(optionalFlyoverFederationInformation.isPresent());
        FlyoverFederationInformation obtainedFlyoverFederationInformation = optionalFlyoverFederationInformation.get();
        Assertions.assertEquals(flyoverFederationInformation.getDerivationHash(), obtainedFlyoverFederationInformation.getDerivationHash());
        Assertions.assertArrayEquals(flyoverFederationInformation.getFederationRedeemScriptHash(), obtainedFlyoverFederationInformation.getFederationRedeemScriptHash());
    }

    private Address getFlyoverFederationAddress() {
        Script flyoverRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            bridgeConstantsRegtest.getGenesisFederation().getRedeemScript(),
            PegTestUtils.createHash(1)
        );

        Script flyoverP2SH = ScriptBuilder.createP2SHOutputScript(flyoverRedeemScript);
        return Address.fromP2SHScript(bridgeConstantsRegtest.getBtcParams(), flyoverP2SH);
    }

    private interface BtcTransactionProvider {
        BtcTransaction provide(BridgeConstants bridgeConstants, Address activeFederationAddress, Address retiringFederationAddress);
    }
}
