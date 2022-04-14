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
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.fastbridge.FastBridgeFederationInformation;
import co.rsk.peg.fastbridge.FastBridgeTxResponseCodes;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
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
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BridgeSupportFlyoverTest extends BridgeSupportTestBase {
    @Before
    public void setUpOnEachTest() {
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

        BridgeConstants bridgeConstants = spy(bridgeConstantsRegtest);
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
        Keccak256 fastBridgeDerivationHash = bridgeSupport.getFastBridgeDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address activeFederationAddress = PegTestUtils.getFastBridgeAddressFromRedeemScript(
            bridgeConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
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

        BigInteger result = bridgeSupport.registerFastBridgeBtcTransaction(
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
            Assert.assertEquals(
                expectedBalance,
                postCallLbcAddressBalance
            );

            verify(provider, times(1)).markFastBridgeFederationDerivationHashAsUsed(
                btcTx.getHash(false),
                fastBridgeDerivationHash
            );

            verify(provider, times(1)).setFastBridgeFederationInformation(
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

        BridgeConstants bridgeConstants = spy(bridgeConstantsRegtest);
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
        Keccak256 fastBridgeDerivationHash = bridgeSupport.getFastBridgeDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address retiringFederationAddress = PegTestUtils.getFastBridgeAddressFromRedeemScript(
            bridgeConstants,
            retiringFederation.getRedeemScript(),
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
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

        BigInteger result = bridgeSupport.registerFastBridgeBtcTransaction(
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
            Assert.assertEquals(
                expectedBalance,
                postCallLbcAddressBalance
            );

            // when RSKIP293 is active, the method markFastBridgeFederationDerivationHashAsUsed should be called twice.
            verify(provider, times(isRskip293Active ? 2 : 1)).markFastBridgeFederationDerivationHashAsUsed(
                btcTx.getHash(false),
                fastBridgeDerivationHash
            );

            verify(provider, times(1)).setFastBridgeRetiringFederationInformation(
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

        BridgeConstants bridgeConstants = spy(bridgeConstantsRegtest);
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
        Keccak256 fastBridgeDerivationHash = bridgeSupport.getFastBridgeDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());

        Address activeFederationAddress = PegTestUtils.getFastBridgeAddressFromRedeemScript(
            bridgeConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
        );

        btcTx.addOutput(valueToSend, activeFederationAddress);

        Address retiringFederationAddress = PegTestUtils.getFastBridgeAddressFromRedeemScript(
            bridgeConstants,
            retiringFederation.getRedeemScript(),
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
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

        BigInteger result = bridgeSupport.registerFastBridgeBtcTransaction(
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
            Assert.assertEquals(
                expectedBalance,
                postCallLbcAddressBalance
            );

            // when RSKIP293 is active, the method setFastBridgeRetiringFederationInformation should be called twice.
            verify(provider, times(isRskip293Active ? 2 : 1)).markFastBridgeFederationDerivationHashAsUsed(
                btcTx.getHash(false),
                fastBridgeDerivationHash
            );

            verify(provider, times(1)).setFastBridgeFederationInformation(
                any()
            );

            // verify method setFastBridgeRetiringFederationInformation is called when RSKIP293 is active
            if (isRskip293Active) {
                verify(provider, times(1)).setFastBridgeRetiringFederationInformation(
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
        Keccak256 fastBridgeDerivationHash = bridgeSupport.getFastBridgeDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address activeFederationAddress = PegTestUtils.getFastBridgeAddressFromRedeemScript(
            bridgeConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
        );

        Address retiringFederationAddress = PegTestUtils.getFastBridgeAddressFromRedeemScript(
            bridgeConstants,
            retiringFederation.getRedeemScript(),
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
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

        return bridgeSupport.registerFastBridgeBtcTransaction(
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

        BridgeConstants bridgeConstants = spy(bridgeConstantsRegtest);

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
        Keccak256 fastBridgeDerivationHash = bridgeSupport.getFastBridgeDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        Address activeFederationAddress = PegTestUtils.getFastBridgeAddressFromRedeemScript(
            bridgeConstants,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
        );

        Address retiringFederationAddress = PegTestUtils.getFastBridgeAddressFromRedeemScript(
            bridgeConstants,
            retiringFederation.getRedeemScript(),
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
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

        BigInteger result = bridgeSupport.registerFastBridgeBtcTransaction(
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

        if (result.longValue() == FastBridgeTxResponseCodes.REFUNDED_LP_ERROR.value() || result.longValue() == FastBridgeTxResponseCodes.REFUNDED_USER_ERROR.value()){
            List<BtcTransaction> releaseTxs = provider.getReleaseTransactionSet().getEntries()
                .stream()
                .map(ReleaseTransactionSet.Entry::getTransaction)
                .collect(Collectors.toList());

            assertEquals(1, releaseTxs.size());
            BtcTransaction releaseTx = releaseTxs.get(0);
            Assert.assertEquals(1, releaseTx.getOutputs().size());

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
                    result.longValue() == FastBridgeTxResponseCodes.REFUNDED_LP_ERROR.value() ? lpBtcAddress : userRefundBtcAddress
                ));

            // For simplicity of this test we are using as estimated fee of 10% of the amount sent in order to check
            // that the amount to refund is at least above the amount sent minus the estimated fee
            Coin estimatedFee = amountSent.divide(10);
            Coin estimatedAmountToRefund = amountSent.minus(estimatedFee);

            Assert.assertTrue("Pegout value should be bigger than the estimated amount to refund(" + estimatedAmountToRefund + ") and " +
                "smaller than the amount sent(" + amountSent + ")", amountToRefund.isGreaterThan(estimatedAmountToRefund) &&
                amountToRefund.isLessThan(amountSent)
            );
        }

        return result;
    }

    @Test(expected = ScriptException.class)
    public void registerFastBridgeBtcTransaction_output_to_bech32_before_RSKIP293_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        sendFundsToAnyAddress(
            bridgeConstantsRegtest,
            false,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(createBech32Output(bridgeConstants.getBtcParams(), valueToSend));
                return tx;
            }
        );
    }

    @Test(expected = ScriptException.class)
    public void registerFastBridgeBtcTransaction_output_to_bech32_before_RSKIP293_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        sendFundsToAnyAddress(
            bridgeConstantsMainnet,
            false,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                Coin valueToSend = Coin.COIN;
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(createBech32Output(bridgeConstants.getBtcParams(), valueToSend));
                return tx;
            }
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_output_to_bech32_after_RSKIP293_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
    public void registerFastBridgeBtcTransaction_output_to_bech32_after_RSKIP293_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
    public void registerFastBridgeBtcTransaction_output_to_P2PKH_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
    public void registerFastBridgeBtcTransaction_output_to_P2PKH_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
    public void registerFastBridgeBtcTransaction_output_to_P2SH_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
    public void registerFastBridgeBtcTransaction_output_to_P2SH_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
    public void registerFastBridgeBtcTransaction_output_to_different_address_type_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
    public void registerFastBridgeBtcTransaction_output_to_different_address_type_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
    public void registerFastBridgeBtcTransaction_multiple_output_to_the_same_address_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
    public void registerFastBridgeBtcTransaction_multiple_output_to_the_same_address_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
    public void registerFastBridgeBtcTransaction_multiple_output_to_different_addresses_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
    public void registerFastBridgeBtcTransaction_multiple_output_to_different_addresses_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
    public void registerFastBridgeBtcTransaction_multiple_output_to_random_addresses_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
            FastBridgeTxResponseCodes.UNPROCESSABLE_TX_VALUE_ZERO_ERROR.value(),
            result.longValue()
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_multiple_output_to_random_addresses_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
            FastBridgeTxResponseCodes.UNPROCESSABLE_TX_VALUE_ZERO_ERROR.value(),
            result.longValue()
        );
    }

    @Test(expected = ScriptException.class)
    public void registerFastBridgeBtcTransaction_multiple_output_to_all_address_type_before_RSKIP293_regtest() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        sendFundsToAnyAddress(
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
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_multiple_output_to_all_address_type_after_RSKIP293_regtest() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
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

    @Test(expected = ScriptException.class)
    public void registerFastBridgeBtcTransaction_multiple_output_to_all_address_type_before_RSKIP293_mainnet() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        sendFundsToAnyAddress(
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
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_multiple_output_to_all_address_type_after_RSKIP293_mainnet() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
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
    public void registerFastBridgeBtcTransaction_amount_sent_is_below_minimum_before_RSKIP293_activation() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        Coin valueToSend = BridgeUtils.getMinimumPegInTxValue(activations, bridgeConstantsRegtest).minus(Coin.CENT);
        BigInteger result = sendFundsToActiveFederation(
            false,
            valueToSend
        );

        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_amount_sent_is_below_minimum_after_RSKIP293_activation() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        Coin valueBelowMinimum = BridgeUtils.getMinimumPegInTxValue(activations, bridgeConstantsRegtest).minus(Coin.CENT);
        BigInteger result = sendFundsToActiveFederation(
            true,
            valueBelowMinimum
        );

        Assert.assertEquals(
            FastBridgeTxResponseCodes.UNPROCESSABLE_TX_AMOUNT_SENT_BELOW_MINIMUM_ERROR.value()
            , result.longValue());
    }

    @Test
    public void registerFastBridgeBtcTransaction_sent_multiple_output_and_one_with_amount_below_minimum_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
            FastBridgeTxResponseCodes.UNPROCESSABLE_TX_UTXO_AMOUNT_SENT_BELOW_MINIMUM_ERROR.value(),
            result.longValue()
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_sent_multiple_output_and_one_with_amount_below_minimum_regtest() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
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
            FastBridgeTxResponseCodes.UNPROCESSABLE_TX_UTXO_AMOUNT_SENT_BELOW_MINIMUM_ERROR.value(),
            result.longValue()
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_amount_sent_is_equal_to_minimum()
        throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        Coin minimumPegInTxValue = BridgeUtils.getMinimumPegInTxValue(activations, bridgeConstantsRegtest);
        BigInteger result = sendFundsToActiveFederation(
            true,
            minimumPegInTxValue
        );

        Assert.assertEquals(
            co.rsk.core.Coin.fromBitcoin(minimumPegInTxValue).asBigInteger()
            , result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_amount_sent_is_over_minimum()
        throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        Coin valueOverMinimum = BridgeUtils.getMinimumPegInTxValue(activations, bridgeConstantsRegtest).add(Coin.CENT);
        BigInteger result = sendFundsToActiveFederation(
            true,
            valueOverMinimum
        );

        Assert.assertEquals(
            co.rsk.core.Coin.fromBitcoin(valueOverMinimum).asBigInteger()
            , result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_funds_sent_to_current_retiring_fed_before_RSKIP293_activation()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {

        Coin valueToSend = Coin.COIN;
        BigInteger result = sendFundsToRetiringFederation(
            false,
            valueToSend
        );

        Assert.assertEquals(FastBridgeTxResponseCodes.UNPROCESSABLE_TX_VALUE_ZERO_ERROR.value(), result.longValue());
    }

    @Test
    public void registerFastBridgeBtcTransaction_funds_sent_to_active_and_retiring_fed_before_RSKIP293_activation() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Coin valueToSend = Coin.COIN;
        // should send funds to both federation but only the active federation must receive it
        BigInteger result = sendFundsToActiveAndRetiringFederation(
            false,
            valueToSend
        );

        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_funds_sent_to_active_and_retiring_fed_after_RSKIP293_activation() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        Coin valueToSend = Coin.COIN;
        // send funds to both federations, the active federation and current retiring federation
        BigInteger result = sendFundsToActiveAndRetiringFederation(
            true,
            valueToSend
        );

        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend.multiply(2)).asBigInteger(), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_funds_sent_to_active_fed_after_RSKIP293() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        Coin valueToSend = Coin.COIN;
        BigInteger result  = sendFundsToActiveFederation(
            true,
            valueToSend
        );

        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_funds_sent_to_retiring_fed_after_RSKIP293() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        Coin valueToSend = Coin.COIN;
        BigInteger result = sendFundsToRetiringFederation(
            true,
            valueToSend
        );

        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_sum_of_funds_sent_to_active_and_retiring_fed_surpasses_locking_cap_before_RSKIP293_no_refund() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
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
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(Coin.FIFTY_COINS.div(2)).asBigInteger(), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_funds_sent_to_active_and_retiring_fed_surpasses_locking_cap_before_RSKIP293_only_funds_sent_to_active_fed_should_be_refunded_to_LP() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
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
        Assert.assertEquals(FastBridgeTxResponseCodes.REFUNDED_LP_ERROR.value(), result.longValue());
    }

    @Test
    public void registerFastBridgeBtcTransaction_funds_sent_to_active_and_retiring_fed_surpasses_locking_cap_before_RSKIP293_only_funds_sent_to_active_fed_should_be_refunded_to_user() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
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
        Assert.assertEquals(FastBridgeTxResponseCodes.REFUNDED_USER_ERROR.value(), result.longValue());
    }

    @Test
    public void registerFastBridgeBtcTransaction_funds_sent_to_retiring_fed_surpasses_locking_cap_should_refund_user_after_RSKIP293() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
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
        Assert.assertEquals(FastBridgeTxResponseCodes.REFUNDED_USER_ERROR.value(), result.longValue());
    }

    @Test
    public void registerFastBridgeBtcTransaction_sum_of_funds_sent_to_active_and_retiring_fed_surpasses_locking_cap_should_refund_lp_after_RSKIP293() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        BigInteger result = sendFundsSurpassesLockingCapToAnyAddress(
            true,
            Coin.FIFTY_COINS,
            (bridgeConstants, activeFederationAddress, retiringFederationAddress) -> {
                BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
                tx.addOutput(Coin.FIFTY_COINS, activeFederationAddress);
                tx.addOutput(Coin.COIN, retiringFederationAddress);
                return tx;
            },
            true
        );
        Assert.assertEquals(FastBridgeTxResponseCodes.REFUNDED_LP_ERROR.value(), result.longValue());
    }

    @Test
    public void registerFastBridgeBtcTransaction_sum_of_all_utxos_surpass_locking_cap_but_not_funds_sent_to_fed_should_not_refund_after_RSKIP293() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
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
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(Coin.valueOf(2, 0)).asBigInteger(), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_funds_sent_to_random_addresses_surpass_locking_cap_should_not_refund_after_RSKIP293() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
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
        Assert.assertEquals(FastBridgeTxResponseCodes.UNPROCESSABLE_TX_VALUE_ZERO_ERROR.value(), result.longValue());
    }

    @Test
    public void registerFastBridgeBtcTransaction_is_not_contract()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        BridgeSupport bridgeSupport = bridgeSupportBuilder.withBridgeConstants(bridgeConstantsRegtest).build();
        Transaction rskTxMock = mock(Transaction.class);
        Keccak256 hash = new Keccak256(HashUtil.keccak256(new byte[]{}));
        when(rskTxMock.getHash()).thenReturn(hash);

        BigInteger result = bridgeSupport.registerFastBridgeBtcTransaction(
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

        Assert.assertEquals(BigInteger.valueOf(FastBridgeTxResponseCodes.UNPROCESSABLE_TX_NOT_CONTRACT_ERROR.value()), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_sender_is_not_lbc()
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

        BigInteger result = bridgeSupport.registerFastBridgeBtcTransaction(
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

        Assert.assertEquals(BigInteger.valueOf(FastBridgeTxResponseCodes.UNPROCESSABLE_TX_INVALID_SENDER_ERROR.value()), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_TxAlreadySavedInStorage_returnsError()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.isFastBridgeFederationDerivationHashUsed(any(), any())).thenReturn(true);
        BtcTransaction tx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());

        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BridgeSupport bridgeSupport = spy(bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .withBridgeConstants(bridgeConstantsRegtest)
            .build());

        doReturn(PegTestUtils.createHash3(5))
            .when(bridgeSupport)
            .getFastBridgeDerivationHash(any(), any(), any(), any());

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

        BigInteger result = bridgeSupport.registerFastBridgeBtcTransaction(
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

        Assert.assertEquals(BigInteger.valueOf(FastBridgeTxResponseCodes.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR.value()), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_validationsForRegisterBtcTransaction_returns_false()
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
            .getFastBridgeDerivationHash(any(), any(), any(), any());

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

        BigInteger result = bridgeSupport.registerFastBridgeBtcTransaction(
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

        Assert.assertEquals(BigInteger.valueOf(FastBridgeTxResponseCodes.UNPROCESSABLE_TX_VALIDATIONS_ERROR.value()), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_amount_sent_is_0()
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
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
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

        BigInteger result = bridgeSupport.registerFastBridgeBtcTransaction(
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

        Assert.assertEquals(BigInteger.valueOf(FastBridgeTxResponseCodes.UNPROCESSABLE_TX_VALUE_ZERO_ERROR.value()), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_TxWitnessAlreadySavedInStorage_returnsError()
        throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstantsRegtest.getBtcParams());

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.isFastBridgeFederationDerivationHashUsed(any(), any())).thenReturn(false).thenReturn(true);

        RskAddress lbcAddress = PegTestUtils.createRandomRskAddress();

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstantsRegtest,
            provider,
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
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
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

        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx.setWitness(0, txWit);

        BigInteger result = bridgeSupport.registerFastBridgeBtcTransaction(
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

        Assert.assertEquals(BigInteger.valueOf(FastBridgeTxResponseCodes.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR.value()), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_surpasses_locking_cap_and_shouldTransfer_is_true()
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
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
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

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, getFastBridgeFederationAddress());
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

        BigInteger result = bridgeSupport.registerFastBridgeBtcTransaction(
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

        Assert.assertEquals(BigInteger.valueOf(FastBridgeTxResponseCodes.REFUNDED_LP_ERROR.value()), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_surpasses_locking_cap_and_shouldTransfer_is_false()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);

        ReleaseTransactionSet releaseTransactionSet = new ReleaseTransactionSet(new HashSet<>());

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseTransactionSet()).thenReturn(releaseTransactionSet);
        when(provider.isFastBridgeFederationDerivationHashUsed(any(), any())).thenReturn(false);

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
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
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

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, getFastBridgeFederationAddress());
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

        BigInteger result = bridgeSupport.registerFastBridgeBtcTransaction(
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

        Assert.assertEquals(BigInteger.valueOf(FastBridgeTxResponseCodes.REFUNDED_USER_ERROR.value()), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_surpasses_locking_cap_and_tries_to_register_again()
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
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
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

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, getFastBridgeFederationAddress());
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

        BigInteger result = bridgeSupport.registerFastBridgeBtcTransaction(
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

        Assert.assertEquals(BigInteger.valueOf(FastBridgeTxResponseCodes.REFUNDED_USER_ERROR.value()), result);

        // Update repository
        bridgeSupport.save();

        result = bridgeSupport.registerFastBridgeBtcTransaction(
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

        Assert.assertEquals(BigInteger.valueOf(FastBridgeTxResponseCodes.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR.value()), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_OK()
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
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
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
        BtcTransaction tx = createBtcTransactionWithOutputToAddress(valueToSend, getFastBridgeFederationAddress());
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

        BigInteger result = bridgeSupport.registerFastBridgeBtcTransaction(
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

        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);

        co.rsk.core.Coin postCallLbcAddressBalance = repository.getBalance(lbcAddress);

        Assert.assertEquals(
            preCallLbcAddressBalance.add(co.rsk.core.Coin.fromBitcoin(Coin.COIN)),
            postCallLbcAddressBalance
        );

        bridgeSupport.save();

        Assert.assertTrue(
            provider.isFastBridgeFederationDerivationHashUsed(
                tx.getHash(),
                bridgeSupport.getFastBridgeDerivationHash(PegTestUtils.createHash3(0), btcAddress, btcAddress, lbcAddress)
            )
        );
        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());

        // Trying to register the same transaction again fails
        result = bridgeSupport.registerFastBridgeBtcTransaction(
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

        Assert.assertEquals(BigInteger.valueOf(FastBridgeTxResponseCodes.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR.value()), result);
    }

    @Test
    public void createFastBridgeFederationInformation_OK() {
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

        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            bridgeConstantsRegtest.getGenesisFederation().getRedeemScript(),
            PegTestUtils.createHash(1)
        );

        Script fastBridgeP2SH = ScriptBuilder.createP2SHOutputScript(fastBridgeRedeemScript);
        Keccak256 derivationHash = PegTestUtils.createHash3(1);

        FastBridgeFederationInformation expectedFastBridgeFederationInformation =
            new FastBridgeFederationInformation(derivationHash,
                fed.getP2SHScript().getPubKeyHash(),
                fastBridgeP2SH.getPubKeyHash()
            );

        FastBridgeFederationInformation obtainedFastBridgeFedInfo =
            bridgeSupport.createFastBridgeFederationInformation(derivationHash);

        Assert.assertEquals(
            expectedFastBridgeFederationInformation.getFastBridgeFederationAddress(bridgeConstantsRegtest.getBtcParams()),
            obtainedFastBridgeFedInfo.getFastBridgeFederationAddress(bridgeConstantsRegtest.getBtcParams())
        );
    }

    @Test
    public void getFastBridgeWallet_ok() {
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

        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            fed.getRedeemScript(),
            Sha256Hash.wrap(derivationHash.getBytes())
        );

        Script fastBridgeP2SH = ScriptBuilder.createP2SHOutputScript(fastBridgeRedeemScript);

        FastBridgeFederationInformation fastBridgeFederationInformation =
            new FastBridgeFederationInformation(
                derivationHash,
                fed.getP2SHScript().getPubKeyHash(),
                fastBridgeP2SH.getPubKeyHash()
            );

        BtcTransaction tx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        tx.addOutput(Coin.COIN,
            fastBridgeFederationInformation.getFastBridgeFederationAddress(
                bridgeConstantsRegtest.getBtcParams()
            )
        );

        List<UTXO> utxoList = new ArrayList<>();
        UTXO utxo = new UTXO(tx.getHash(), 0, Coin.COIN, 0, false, fastBridgeP2SH);
        utxoList.add(utxo);

        Wallet obtainedWallet = bridgeSupport.getFastBridgeWallet(btcContext, utxoList, fastBridgeFederationInformation);

        Assert.assertEquals(Coin.COIN, obtainedWallet.getBalance());
    }

    @Test
    public void getFastBridgeDerivationHash_ok() {
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

        Keccak256 fastBridgeDerivationHash = bridgeSupport.getFastBridgeDerivationHash(
            new Keccak256(derivationArgumentsHash),
            userRefundBtcAddress,
            lpBtcAddress,
            new RskAddress(lbcAddress)
        );

        Assert.assertArrayEquals(HashUtil.keccak256(result), fastBridgeDerivationHash.getBytes());
    }

    @Test
    public void saveFastBridgeDataInStorage_OK() throws IOException {
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

        byte[] fastBridgeScriptHash = new byte[]{0x1};
        FastBridgeFederationInformation fastBridgeFederationInformation = new FastBridgeFederationInformation(
            PegTestUtils.createHash3(2),
            new byte[]{0x1},
            fastBridgeScriptHash
        );

        List<UTXO> utxos = new ArrayList<>();
        Sha256Hash utxoHash = PegTestUtils.createHash(1);
        UTXO utxo = new UTXO(utxoHash, 0, Coin.COIN.multiply(2), 0, false, new Script(new byte[]{}));
        utxos.add(utxo);

        Assert.assertEquals(0, provider.getNewFederationBtcUTXOs().size());
        bridgeSupport.saveFastBridgeActiveFederationDataInStorage(btcTxHash, derivationHash, fastBridgeFederationInformation, utxos);

        bridgeSupport.save();

        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        assertEquals(utxo, provider.getNewFederationBtcUTXOs().get(0));
        Assert.assertTrue(provider.isFastBridgeFederationDerivationHashUsed(btcTxHash, derivationHash));
        Optional<FastBridgeFederationInformation> optionalFastBridgeFederationInformation = provider.getFastBridgeFederationInformation(fastBridgeScriptHash);
        Assert.assertTrue(optionalFastBridgeFederationInformation.isPresent());
        FastBridgeFederationInformation obtainedFastBridgeFederationInformation = optionalFastBridgeFederationInformation.get();
        Assert.assertEquals(fastBridgeFederationInformation.getDerivationHash(), obtainedFastBridgeFederationInformation.getDerivationHash());
        Assert.assertArrayEquals(fastBridgeFederationInformation.getFederationRedeemScriptHash(), obtainedFastBridgeFederationInformation.getFederationRedeemScriptHash());
    }

    private Address getFastBridgeFederationAddress() {
        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            bridgeConstantsRegtest.getGenesisFederation().getRedeemScript(),
            PegTestUtils.createHash(1)
        );

        Script fastBridgeP2SH = ScriptBuilder.createP2SHOutputScript(fastBridgeRedeemScript);
        return Address.fromP2SHScript(bridgeConstantsRegtest.getBtcParams(), fastBridgeP2SH);
    }

    private interface BtcTransactionProvider {
        BtcTransaction provide(BridgeConstants bridgeConstants, Address activeFederationAddress, Address retiringFederationAddress);
    }
}
