/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.peg;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.RskTestUtils.createRskBlock;
import static co.rsk.peg.BridgeSupportTestUtil.*;
import static co.rsk.peg.PegTestUtils.*;
import static co.rsk.peg.PegUtils.getFlyoverFederationAddress;
import static co.rsk.peg.PegUtils.getFlyoverFederationOutputScript;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.bitcoin.CoinbaseInformation;
import co.rsk.peg.bitcoin.FlyoverRedeemScriptBuilderImpl;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.*;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.FeePerKbSupport;
import co.rsk.peg.flyover.FlyoverFederationInformation;
import co.rsk.peg.flyover.FlyoverTxResponseCodes;
import co.rsk.peg.lockingcap.*;
import co.rsk.peg.lockingcap.constants.LockingCapConstants;
import co.rsk.peg.pegininstructions.PeginInstructionsProvider;
import co.rsk.peg.storage.InMemoryStorage;
import co.rsk.peg.storage.StorageAccessor;
import co.rsk.peg.union.UnionBridgeSupport;
import co.rsk.peg.utils.BridgeEventLogger;
import co.rsk.peg.utils.BridgeEventLoggerImpl;
import co.rsk.peg.whitelist.WhitelistSupport;
import co.rsk.test.builders.BridgeSupportBuilder;
import co.rsk.test.builders.FederationSupportBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Stream;

import co.rsk.test.builders.UTXOBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.*;
import org.ethereum.core.*;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.ethereum.vm.program.InternalTransaction;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class BridgeSupportFlyoverTest {
    private final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);

    private final RskAddress bridgeContractAddress = PrecompiledContracts.BRIDGE_ADDR;
    private final BridgeConstants bridgeConstantsMainnet = BridgeMainNetConstants.getInstance();
    private final NetworkParameters btcMainnetParams = bridgeConstantsMainnet.getBtcParams();
    private final FederationConstants federationConstantsMainnet = bridgeConstantsMainnet.getFederationConstants();
    private final LockingCapConstants lockingCapMainnetConstants = bridgeConstantsMainnet.getLockingCapConstants();

    private final BridgeConstants bridgeConstantsRegtest = new BridgeRegTestConstants();
    private final NetworkParameters btcRegTestParams = bridgeConstantsRegtest.getBtcParams();
    private final FederationConstants federationConstantsRegtest = bridgeConstantsRegtest.getFederationConstants();

    private final Keccak256 derivationArgumentsHash = PegTestUtils.createHash3(1);
    private final RskAddress lbcAddress = new RskAddress(new byte[20]);
    private final Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "sender");
    private final Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "liqProvider");

    private final Transaction rskTx = new InternalTransaction(
        Keccak256.ZERO_HASH.getBytes(), 0, 0, null, null, null,
        lbcAddress.getBytes(), null, null, null, null, null
    );

    private final FederationSupportBuilder federationSupportBuilder = FederationSupportBuilder.builder();
    private final SignatureCache signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());

    private Repository repository;
    private FederationStorageProvider federationStorageProvider;
    private BridgeSupportBuilder bridgeSupportBuilder;
    private LockingCapStorageProvider lockingCapStorageProvider;
    private LockingCapSupport lockingCapSupport;
    private WhitelistSupport whitelistSupport;
    private UnionBridgeSupport unionBridgeSupport;
    private FeePerKbSupport feePerKbSupport;
    private ActivationConfig.ForBlock activations;

    private Federation retiringFederation = PegTestUtils.createFederation(bridgeConstantsMainnet, "fa01", "fa02");
    private Federation activeFederation = PegTestUtils.createFederation(bridgeConstantsMainnet, "fa03", "fa04", "fa05");

    @BeforeEach
    void setUpOnEachTest() {
        activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP219)).thenReturn(true);
        bridgeSupportBuilder = BridgeSupportBuilder.builder();
        lockingCapSupport = mock(LockingCapSupport.class);
        whitelistSupport = mock(WhitelistSupport.class);
        unionBridgeSupport = mock(UnionBridgeSupport.class);

        repository = createRepository();

        StorageAccessor bridgeStorageAccessor = new InMemoryStorage();
        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
        lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);
    }

    private BtcTransaction createBtcTransactionWithOutputToAddress(Coin amount, Address btcAddress) {
        return BitcoinTestUtils.createBtcTransactionWithOutputToAddress(btcRegTestParams, amount, btcAddress);
    }

    private BigInteger sendFundsToActiveFederation(
        boolean isRskip293Active,
        Coin valueToSend
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(isRskip293Active);

        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederation(any(), any())).thenReturn(activeFederation);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeConstants.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .withFederationSupport(federationSupport)
            .build();

        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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
            BitcoinTestUtils.createHash(2),
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

        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederation(bridgeConstants.getFederationConstants(), activations)).thenReturn(activeFederation);
        when(federationStorageProvider.getOldFederation(bridgeConstants.getFederationConstants(), activations)).thenReturn(retiringFederation);

        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(bridgeConstants.getBtcParams(), "refund");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(bridgeConstants.getBtcParams(), "lp");

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeConstants.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .withFederationSupport(federationSupport)
            .build();

        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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
            BitcoinTestUtils.createHash(2),
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

        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederation(bridgeConstants.getFederationConstants(), activations)).thenReturn(activeFederation);
        when(federationStorageProvider.getOldFederation(bridgeConstants.getFederationConstants(), activations)).thenReturn(retiringFederation);

        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(bridgeConstants.getBtcParams(), "refund");

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeConstants.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .withFederationSupport(federationSupport)
            .build();

        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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
            BitcoinTestUtils.createHash(2),
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
        FederationConstants federationConstantsSpy = spy(bridgeConstants.getFederationConstants());
        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        long federationActivationAge = federationConstantsRegtest.getFederationActivationAge(activations);
        doReturn(federationActivationAge).when(federationConstantsSpy).getFederationActivationAge(activations);
        when(bridgeConstants.getFederationConstants()).thenReturn(federationConstantsSpy);

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederation(bridgeConstants.getFederationConstants(), activations)).thenReturn(activeFederation);
        when(federationStorageProvider.getOldFederation(bridgeConstants.getFederationConstants(), activations)).thenReturn(retiringFederation);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsSpy)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .withRskExecutionBlock(executionBlock)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withFederationSupport(federationSupport)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .build();

        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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
            BitcoinTestUtils.createHash(2),
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

        Context btcContext = mock(Context.class);
        doReturn(btcRegTestParams).when(btcContext).getParams();

        FederationStorageProvider federationStorageProvider = mock(FederationStorageProvider.class);
        when(federationStorageProvider.getNewFederation(bridgeConstantsRegtest.getFederationConstants(), activations)).thenReturn(activeFederation);
        when(federationStorageProvider.getOldFederation(bridgeConstantsRegtest.getFederationConstants(), activations)).thenReturn(retiringFederation);

        when(lockingCapSupport.getLockingCap()).thenReturn(Optional.of(lockingCapValue));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);

        Address userRefundBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcRegTestParams, "refund");
        Address lpBtcAddress = BitcoinTestUtils.createP2PKHAddress(btcRegTestParams, "lp");

        // For simplicity of this test, the max rbtc value is set as the current balance for the repository
        // This simulates that no pegin has ever been processed
        repository.addBalance(bridgeContractAddress, co.rsk.core.Coin.fromBitcoin(
            this.bridgeConstantsRegtest.getMaxRbtc()));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withFederationSupport(federationSupport)
            .withBridgeConstants(bridgeConstantsRegtest)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .withFeePerKbSupport(feePerKbSupport)
            .withLockingCapSupport(lockingCapSupport)
            .build();

        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
        );

        Address activeFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstantsRegtest,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        Address retiringFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstantsRegtest,
            retiringFederation.getRedeemScript(),
            Sha256Hash.wrap(flyoverDerivationHash.getBytes())
        );

        BtcTransaction btcTx = btcTransactionProvider.provide(bridgeConstantsRegtest, activeFederationAddress, retiringFederationAddress);
        btcTx.addInput(
            Sha256Hash.ZERO_HASH,
            0,
            ScriptBuilder.createInputScript(null, new BtcECKey())
        );

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash());
        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(2),
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
            height + bridgeConstantsRegtest.getBtc2RskMinimumAcceptableConfirmations(),
            height
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
            List<BtcTransaction> pegoutsWaitingForConfirmationsEntries = provider.getPegoutsWaitingForConfirmations().getEntries()
                .stream()
                .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
                .toList();

            assertEquals(1, pegoutsWaitingForConfirmationsEntries.size());
            BtcTransaction releaseTx = pegoutsWaitingForConfirmationsEntries.get(0);
            Assertions.assertEquals(1, releaseTx.getOutputs().size());

            Coin amountSent = BridgeUtils.getAmountSentToAddresses(activations,
                btcRegTestParams,
                btcContext,
                btcTx,
                isRskip293Active? Arrays.asList(activeFederationAddress, retiringFederationAddress):
                    Collections.singletonList(activeFederationAddress)
            );
            Coin amountToRefund = BridgeUtils.getAmountSentToAddresses(activations,
                btcRegTestParams,
                btcContext,
                releaseTx,
                Collections.singletonList(
                    result.longValue() == FlyoverTxResponseCodes.REFUNDED_LP_ERROR.value() ? lpBtcAddress : userRefundBtcAddress
                ));

            // For simplicity of this test we are using as estimated fee of 10% of the amount sent in order to check
            // that the amount to refund is at least above the amount sent minus the estimated fee
            Coin estimatedFee = amountSent.divide(10);
            Coin estimatedAmountToRefund = amountSent.minus(estimatedFee);

            assertTrue(amountToRefund.isGreaterThan(estimatedAmountToRefund) &&
                amountToRefund.isLessThan(amountSent), "Pegout value should be bigger than the estimated amount to refund(" + estimatedAmountToRefund + ") and " +
                "smaller than the amount sent(" + amountSent + ")"
            );
        }

        return result;
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("test flyover pegin that surpasses locking cap different scenarios")
    class FlyoverPeginThatSurpassesLockingCap {
        private final int btcBlockToRegisterHeight = bridgeConstantsMainnet.getBtcHeightWhenPegoutTxIndexActivates() + bridgeConstantsMainnet.getPegoutTxIndexGracePeriodInBtcBlocks(); // we want pegout tx index to be activated

        private final Coin amountRequestedThatSurpassesLockingCap = lockingCapMainnetConstants.getInitialValue().add(Coin.SATOSHI);

        private Block currentBlock;
        private Repository repository;
        private BridgeStorageProvider bridgeStorageProvider;
        private BridgeSupport bridgeSupport;
        private List<LogInfo> logs;

        private BtcBlockStoreWithCache btcBlockStore;

        private BtcTransaction flyoverBtcTx;
        private Keccak256 flyoverDerivationHash;
        private PartialMerkleTree pmtWithTransactions;

        private void setUpWithActivations(ActivationConfig.ForBlock activations) {
            logs = new ArrayList<>();
            BridgeEventLogger bridgeEventLogger = new BridgeEventLoggerImpl(
                bridgeConstantsMainnet,
                allActivations,
                logs
            );

            FederationSupport federationSupport = federationSupportBuilder
                .withFederationConstants(federationConstantsMainnet)
                .withFederationStorageProvider(federationStorageProvider)
                .withRskExecutionBlock(currentBlock)
                .withActivations(activations)
                .build();

            lockingCapSupport = new LockingCapSupportImpl(lockingCapStorageProvider, activations, lockingCapMainnetConstants, signatureCache);

            federationStorageProvider.setNewFederation(activeFederation);

            repository = createRepository();
            bridgeStorageProvider = new BridgeStorageProvider(repository, btcMainnetParams, activations);

            BtcBlockStoreWithCache.Factory btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(btcMainnetParams, 100, 100);
            btcBlockStore = btcBlockStoreFactory.newInstance(repository, bridgeConstantsMainnet, bridgeStorageProvider, activations);

            bridgeSupport = bridgeSupportBuilder
                .withActivations(activations)
                .withExecutionBlock(currentBlock)
                .withBridgeConstants(bridgeConstantsMainnet)
                .withProvider(bridgeStorageProvider)
                .withRepository(repository)
                .withEventLogger(bridgeEventLogger)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withLockingCapSupport(lockingCapSupport)
                .build();
        }

        private static Stream<Arguments> flyoverExpectedResponseCodeArgs() {
            return Stream.of(
                Arguments.of(true, FlyoverTxResponseCodes.REFUNDED_LP_ERROR.value()),
                Arguments.of(false, FlyoverTxResponseCodes.REFUNDED_USER_ERROR.value())
            );
        }

        @ParameterizedTest
        @MethodSource("flyoverExpectedResponseCodeArgs")
        void registerFlyoverBtcTransaction_whenAmountRequestedSurpassesLockingCap_beforeRSKIP305_justEmitsPegoutTransactionCreatedEvent(boolean shouldTransferToContract, long expectedResponseCodeResult) throws Exception {
            // arrange
            ActivationConfig.ForBlock lovellActivations = ActivationConfigsForTest.lovell700().forBlock(0L);
            setUpWithActivations(lovellActivations);
            arrangeFlyoverBtcTransaction(lovellActivations);

            // act
            BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
                rskTx,
                flyoverBtcTx.bitcoinSerialize(),
                btcBlockToRegisterHeight,
                pmtWithTransactions.bitcoinSerialize(),
                derivationArgumentsHash,
                userRefundBtcAddress,
                lbcAddress,
                lpBtcAddress,
                shouldTransferToContract
            );
            bridgeSupport.save();

            // assert
            assertEquals(BigInteger.valueOf(expectedResponseCodeResult), result);
            BtcTransaction releaseTransaction = getReleaseFromPegoutsWFC(bridgeStorageProvider);
            assertLogPegoutTransactionCreated(logs, releaseTransaction, List.of(amountRequestedThatSurpassesLockingCap));
            assertReleaseOutpointsValuesWereNotSavedInStorage(releaseTransaction);
        }

        private void assertReleaseOutpointsValuesWereNotSavedInStorage(BtcTransaction releaseTransaction) {
            byte[] actualReleaseOutpointsValues = repository.getStorageBytes(
                bridgeContractAddress,
                getStorageKeyForReleaseOutpointsValues(releaseTransaction.getHash())
            );
            assertNull(actualReleaseOutpointsValues);
        }

        @ParameterizedTest
        @MethodSource("flyoverExpectedResponseCodeArgs")
        void registerFlyoverBtcTransaction_whenAmountRequestedSurpassesLockingCap_afterRSKIP305_processReleaseRejectionTransactionInfo(
            boolean shouldTransferToContract,
            long expectedResponseCodeResult
        ) throws Exception {
            // arrange
            setUpWithActivations(allActivations);
            arrangeFlyoverBtcTransaction(allActivations);

            // act
            BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
                rskTx,
                flyoverBtcTx.bitcoinSerialize(),
                btcBlockToRegisterHeight,
                pmtWithTransactions.bitcoinSerialize(),
                derivationArgumentsHash,
                userRefundBtcAddress,
                lbcAddress,
                lpBtcAddress,
                shouldTransferToContract
            );
            bridgeSupport.save();

            // assert
            assertEquals(BigInteger.valueOf(expectedResponseCodeResult), result);
            BtcTransaction releaseRejectionTransaction = getReleaseFromPegoutsWFC(bridgeStorageProvider);
            assertReleaseTransactionInfoWasProcessed(repository, bridgeStorageProvider, logs, releaseRejectionTransaction, List.of(amountRequestedThatSurpassesLockingCap));
        }

        @Test
        void registerFlyoverBtcTransaction_fundsThatSurpassLockingCapSentToP2shErpActiveAndP2shErpRetiringFeds_shouldSetRedeemDataInInputsScriptSig() throws Exception {
            // arrange
            retiringFederation = P2shErpFederationBuilder.builder().build();
            federationStorageProvider.setOldFederation(retiringFederation);

            List<BtcECKey> newFedKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
                new String[]{"newMember01", "newMember02", "newMember03", "newMember04", "newMember05", "newMember06", "newMember07", "newMember08", "newMember09"}, true
            );
            activeFederation = P2shErpFederationBuilder.builder()
                .withMembersBtcPublicKeys(newFedKeys)
                .build();

            // Move the required blocks ahead for the new powpeg to become active
            var blockNumber =
                activeFederation.getCreationBlockNumber() + federationConstantsMainnet.getFederationActivationAge(allActivations);
            currentBlock = createRskBlock(blockNumber);

            setUpWithActivations(allActivations);
            createFlyoverBtcTransaction(allActivations);
            // adding output to retiring fed
            var retiringFederationFlyoverAddress = getFlyoverFederationAddress(btcMainnetParams, flyoverDerivationHash, retiringFederation);
            flyoverBtcTx.addOutput(amountRequestedThatSurpassesLockingCap, retiringFederationFlyoverAddress);

            setUpForTransactionRegistration(flyoverBtcTx, btcBlockToRegisterHeight);

            // act
            BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
                rskTx,
                flyoverBtcTx.bitcoinSerialize(),
                btcBlockToRegisterHeight,
                pmtWithTransactions.bitcoinSerialize(),
                derivationArgumentsHash,
                userRefundBtcAddress,
                lbcAddress,
                lpBtcAddress,
                true
            );
            bridgeSupport.save();

            // assert
            BtcTransaction pegout = getReleaseFromPegoutsWFC(bridgeStorageProvider);

            // we should get refund liq provider response
            assertEquals(FlyoverTxResponseCodes.REFUNDED_LP_ERROR.value(), result.longValue());

            // pegout should have two inputs, one related to the active fed and one to the retiring fed
            assertEquals(2, pegout.getInputs().size());
            // since active fed is legacy, redeem data should be in the script sig
            // first input should belong to active fed
            var inputToActiveFlyoverFed = pegout.getInput(0);
            var expectedActiveFederationFlyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder()
                .of(flyoverDerivationHash, activeFederation.getRedeemScript());
            assertScriptSigHasExpectedInputRedeemData(inputToActiveFlyoverFed, expectedActiveFederationFlyoverRedeemScript);
            // second input should belong to retiring fed
            var inputToRetiringFlyoverFed = pegout.getInput(1);
            var expectedRetiringFederationFlyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder()
                .of(flyoverDerivationHash, retiringFederation.getRedeemScript());
            assertScriptSigHasExpectedInputRedeemData(inputToRetiringFlyoverFed, expectedRetiringFederationFlyoverRedeemScript);
        }

        @Test
        void registerFlyoverBtcTransaction_fundsThatSurpassLockingCapSentToP2shP2wshErpActiveAndP2shErpRetiringFeds_shouldSetFixedScriptSig_shouldSetRedeemDataInInputsWitness() throws Exception {
            // arrange
            retiringFederation = P2shErpFederationBuilder.builder().build();
            federationStorageProvider.setOldFederation(retiringFederation);
            activeFederation = P2shP2wshErpFederationBuilder.builder().build();

            // Move the required blocks ahead for the new powpeg to become active
            var blockNumber =
                activeFederation.getCreationBlockNumber() + federationConstantsMainnet.getFederationActivationAge(allActivations);
            currentBlock = createRskBlock(blockNumber);

            setUpWithActivations(allActivations);
            createFlyoverBtcTransaction(allActivations);
            // adding output to retiring fed
            var retiringFederationFlyoverAddress = getFlyoverFederationAddress(btcMainnetParams, flyoverDerivationHash, retiringFederation);
            flyoverBtcTx.addOutput(amountRequestedThatSurpassesLockingCap, retiringFederationFlyoverAddress);

            setUpForTransactionRegistration(flyoverBtcTx, btcBlockToRegisterHeight);

            // act
            BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
                rskTx,
                flyoverBtcTx.bitcoinSerialize(),
                btcBlockToRegisterHeight,
                pmtWithTransactions.bitcoinSerialize(),
                derivationArgumentsHash,
                userRefundBtcAddress,
                lbcAddress,
                lpBtcAddress,
                true
            );
            bridgeSupport.save();

            // assert
            BtcTransaction pegout = getReleaseFromPegoutsWFC(bridgeStorageProvider);

            // we should get refund liq provider response
            assertEquals(FlyoverTxResponseCodes.REFUNDED_LP_ERROR.value(), result.longValue());

            // pegout should have two inputs, one related to the active fed and one to the retiring fed
            assertEquals(2, pegout.getInputs().size());
            // since active fed is segwit compatible, redeem data should be in the witness
            // first input data should belong to active fed
            int activeFlyoverFedInputIndex = 0;
            var expectedActiveFederationFlyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder()
                .of(flyoverDerivationHash, activeFederation.getRedeemScript());
            assertWitnessAndScriptSigHaveExpectedInputRedeemData(
                pegout.getWitness(activeFlyoverFedInputIndex),
                pegout.getInput(activeFlyoverFedInputIndex),
                expectedActiveFederationFlyoverRedeemScript
            );
            // second input data should belong to retiring fed
            int retiringFlyoverFedInputIndex = 1;
            var expectedRetiringFederationFlyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder()
                .of(flyoverDerivationHash, retiringFederation.getRedeemScript());
            assertWitnessAndScriptSigHaveExpectedInputRedeemData(
                pegout.getWitness(retiringFlyoverFedInputIndex),
                pegout.getInput(retiringFlyoverFedInputIndex),
                expectedRetiringFederationFlyoverRedeemScript
            );
        }

        private void arrangeFlyoverBtcTransaction(ActivationConfig.ForBlock activations) throws Exception {
            createFlyoverBtcTransaction(activations);
            setUpForTransactionRegistration(flyoverBtcTx, btcBlockToRegisterHeight);
        }

        private void createFlyoverBtcTransaction(ActivationConfig.ForBlock activations) {
            flyoverBtcTx = new BtcTransaction(btcMainnetParams);
            flyoverBtcTx.addInput(BitcoinTestUtils.createHash(0), 0, new Script(new byte[]{}));

            flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
                derivationArgumentsHash,
                userRefundBtcAddress,
                lpBtcAddress,
                lbcAddress,
                activations
            );

            var flyoverActiveFederationAddress = PegUtils.getFlyoverFederationAddress(btcMainnetParams, flyoverDerivationHash, activeFederation);
            flyoverBtcTx.addOutput(amountRequestedThatSurpassesLockingCap, flyoverActiveFederationAddress);
        }

        private void setUpForTransactionRegistration(BtcTransaction btcTx, int btcBlockWithPmtHeight) throws Exception {
            pmtWithTransactions = createValidPmtForTransactions(List.of(btcTx), btcMainnetParams);
            var chainHeight = btcBlockWithPmtHeight + bridgeConstantsMainnet.getBtc2RskMinimumAcceptableConfirmations();

            recreateChainFromPmt(btcBlockStore, chainHeight, pmtWithTransactions, btcBlockWithPmtHeight, btcMainnetParams);
            bridgeStorageProvider.save();
        }
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
        Coin halfMinimumPeginTxValue = bridgeConstantsRegtest.getMinimumPeginTxValue(activations).div(2);
        BigInteger result = sendFundsToActiveFederation(
            false,
            halfMinimumPeginTxValue
        );

        Assertions.assertEquals(co.rsk.core.Coin.fromBitcoin(halfMinimumPeginTxValue).asBigInteger(), result);
    }

    @Test
    void registerFlyoverBtcTransaction_amount_sent_is_below_minimum_after_RSKIP293_activation() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        Coin halfMinimumPeginTxValue = bridgeConstantsRegtest.getMinimumPeginTxValue(activations).div(2);
        BigInteger result = sendFundsToActiveFederation(
            true,
            halfMinimumPeginTxValue
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
                Coin minimumPegInTxValue = bridgeConstants.getMinimumPeginTxValue(activations);
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
                Coin minimumPegInTxValue = bridgeConstants.getMinimumPeginTxValue(activations);
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
        Coin minimumPegInTxValue = bridgeConstantsRegtest.getMinimumPeginTxValue(activations);
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
        Coin valueOverMinimum = bridgeConstantsRegtest.getMinimumPeginTxValue(activations).add(Coin.CENT);
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
        assertEquals(FlyoverTxResponseCodes.REFUNDED_LP_ERROR.value(), result.longValue());
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
                tx.addOutput(Coin.FIFTY_COINS, BitcoinTestUtils.createP2PKHAddress(bridgeConstants.getBtcParams(), "rndm"));
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
                tx.addOutput(Coin.FIFTY_COINS, BitcoinTestUtils.createP2PKHAddress(bridgeConstants.getBtcParams(), "rndm1"));
                tx.addOutput(Coin.COIN, BitcoinTestUtils.createP2PKHAddress(bridgeConstants.getBtcParams(), "rndm2"));
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

        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        federationStorageProvider.setNewFederation(activeFederation);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, bridgeConstants.getBtcParams(), activations));
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeConstants.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .withFeePerKbSupport(feePerKbSupport)
            .withFederationSupport(federationSupport)
            .build();

        Keccak256 fastBridgeDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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
            BitcoinTestUtils.createHash(2),
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

        Keccak256 derivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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
        assertNotEquals(btcTx.getHash(), btcTx.getHash(true));

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
        FederationConstants federationConstantsSpy = spy(bridgeConstants.getFederationConstants());
        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        long federationActivationAge = federationConstantsRegtest.getFederationActivationAge(activations);
        doReturn(federationActivationAge).when(federationConstantsSpy).getFederationActivationAge(activations);
        when(bridgeConstants.getFederationConstants()).thenReturn(federationConstantsSpy);

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        // For simplicity of this test, the max rbtc value is set as the current balance for the repository
        // This simulates that no pegin has ever been processed
        repository.addBalance(bridgeContractAddress, co.rsk.core.Coin.fromBitcoin(bridgeConstants.getMaxRbtc()));

        federationStorageProvider.setNewFederation(activeFederation);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, bridgeConstants.getBtcParams(), activations));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        doReturn(pegoutsWaitingForConfirmations).when(provider).getPegoutsWaitingForConfirmations();

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        Coin lockingCap = Coin.COIN;
        when(lockingCapSupport.getLockingCap()).thenReturn(Optional.of(lockingCap));

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsSpy)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .withRskExecutionBlock(executionBlock)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withLockingCapSupport(lockingCapSupport)
            .build();

        Keccak256 fastBridgeDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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
            BitcoinTestUtils.createHash(2),
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

        Keccak256 derivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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
        assertNotEquals(btcTx.getHash(), btcTx.getHash(true));

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

        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();


        // This simulates that no pegin has ever been processed
        repository.addBalance(bridgeContractAddress, co.rsk.core.Coin.fromBitcoin(bridgeConstants.getMaxRbtc()));

        federationStorageProvider.setNewFederation(activeFederation);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, bridgeConstants.getBtcParams(), activations));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        doReturn(pegoutsWaitingForConfirmations).when(provider).getPegoutsWaitingForConfirmations();

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        Coin lockingCapValue = Coin.COIN;
        when(lockingCapSupport.getLockingCap()).thenReturn(Optional.of(lockingCapValue));

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeConstants.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .withFeePerKbSupport(feePerKbSupport)
            .withFederationSupport(federationSupport)
            .withLockingCapSupport(lockingCapSupport)
            .build();

        Keccak256 fastBridgeDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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
            BitcoinTestUtils.createHash(2),
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

        Keccak256 derivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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
        assertNotEquals(btcTx.getHash(), btcTx.getHash(true));

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

        when(lockingCapSupport.getLockingCap()).thenReturn(Optional.of(lockingCapValue.multiply(3)));

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
        assertEquals(btcTx.getHash(), btcTx.getHash(true));

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
        FederationConstants federationConstantsSpy = spy(bridgeConstants.getFederationConstants());
        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        long federationActivationAge = federationConstantsRegtest.getFederationActivationAge(activations);
        doReturn(federationActivationAge).when(federationConstantsSpy).getFederationActivationAge(activations);
        when(bridgeConstants.getFederationConstants()).thenReturn(federationConstantsSpy);

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        // For simplicity of this test, the max rbtc value is set as the current balance for the repository
        // This simulates that no pegin has ever been processed
        repository.addBalance(bridgeContractAddress, co.rsk.core.Coin.fromBitcoin(bridgeConstants.getMaxRbtc()));

        federationStorageProvider.setNewFederation(activeFederation);

        Coin lockingCapValue = Coin.COIN;
        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, bridgeConstants.getBtcParams(), activations));

        when(lockingCapSupport.getLockingCap()).thenReturn(Optional.of(lockingCapValue));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        doReturn(pegoutsWaitingForConfirmations).when(provider).getPegoutsWaitingForConfirmations();

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsSpy)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .withRskExecutionBlock(executionBlock)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withLockingCapSupport(lockingCapSupport)
            .build();

        Keccak256 fastBridgeDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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
            BitcoinTestUtils.createHash(2),
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

        Keccak256 derivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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
        FederationConstants federationConstantsSpy = spy(bridgeConstants.getFederationConstants());
        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        long federationActivationAge = federationConstantsRegtest.getFederationActivationAge(activations);
        doReturn(federationActivationAge).when(federationConstantsSpy).getFederationActivationAge(activations);
        when(bridgeConstants.getFederationConstants()).thenReturn(federationConstantsSpy);

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        // For simplicity of this test, the max rbtc value is set as the current balance for the repository
        // This simulates that no pegin has ever been processed
        repository.addBalance(bridgeContractAddress, co.rsk.core.Coin.fromBitcoin(bridgeConstants.getMaxRbtc()));

        federationStorageProvider.setNewFederation(activeFederation);

        Coin lockingCapValue = Coin.COIN;
        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, bridgeConstants.getBtcParams(), activations));

        when(lockingCapSupport.getLockingCap()).thenReturn(Optional.of(lockingCapValue));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        doReturn(pegoutsWaitingForConfirmations).when(provider).getPegoutsWaitingForConfirmations();

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsSpy)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .withRskExecutionBlock(executionBlock)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withLockingCapSupport(lockingCapSupport)
            .build();

        Keccak256 fastBridgeDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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
            BitcoinTestUtils.createHash(2),
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

        Keccak256 derivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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

        // For the sake of simplicity, this set the fed activation age value equal to the value in the bridgeRegTestConstants
        // in order to be able to always get the current retiring federation when it's been mock with no need of creating
        // unnecessary blocks when testing on mainnet.
        BridgeConstants bridgeConstants = spy(this.bridgeConstantsRegtest);

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        federationStorageProvider.setNewFederation(activeFederation);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, bridgeConstants.getBtcParams(), activations));
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);
        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeConstants.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .withBtcBlockStoreFactory(mockFactory)
            .withExecutionBlock(executionBlock)
            .withRepository(repository)
            .withFederationSupport(federationSupport)
            .build();

        Keccak256 fastBridgeDerivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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
            BitcoinTestUtils.createHash(2),
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

        Keccak256 derivationHash = PegUtils.getFlyoverDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress,
            activations
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

        assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_NOT_CONTRACT_ERROR.value()), result);
    }

    @Test
    void registerFlyoverBtcTransaction_sender_is_not_lbc()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction tx = new BtcTransaction(btcRegTestParams);


        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .withBridgeConstants(bridgeConstantsRegtest)
            .build();

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

        assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_INVALID_SENDER_ERROR.value()), result);
    }

    @Test
    void registerFlyoverBtcTransaction_validationsForRegisterBtcTransaction_returns_false()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction tx = new BtcTransaction(btcRegTestParams);
        Address activeFlyoverFederationAddress = PegTestUtils.getFlyoverAddressFromRedeemScript(
            bridgeConstantsRegtest,
            activeFederation.getRedeemScript(),
            Sha256Hash.wrap(derivationArgumentsHash.getBytes())
        );
        tx.addOutput(Coin.COIN, activeFlyoverFederationAddress);

        BridgeSupport bridgeSupport = spy(bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .withBridgeConstants(bridgeConstantsRegtest)
            .build());

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            Hex.decode("ab"),
            derivationArgumentsHash,
            userRefundBtcAddress,
            lbcAddress,
            lpBtcAddress,
            false
        );

        assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_VALIDATIONS_ERROR.value()), result);
    }

    @Test
    void registerFlyoverBtcTransaction_amount_sent_is_0()
        throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(btcMainnetParams);

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstantsMainnet,
            mock(BridgeStorageProvider.class),
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            btcContext,
            feePerKbSupport,
            whitelistSupport,
            mock(FederationSupport.class),
            lockingCapSupport,
            unionBridgeSupport,
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        ));

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(bridgeConstantsMainnet.getFederationConstants());

        doReturn(genesisFederation).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());

        Address btcAddress = Address.fromBase58(
            btcRegTestParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );
        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            RskTestUtils.createHash(0),
            btcAddress,
            btcAddress,
            lbcAddress,
            activations
        );
        Address activeFlyoverFederationAddress = PegUtils.getFlyoverFederationAddress(btcRegTestParams, flyoverDerivationHash, genesisFederation);

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.ZERO, activeFlyoverFederationAddress);

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            Hex.decode("ab"),
            PegTestUtils.createHash3(0),
            btcAddress,
            lbcAddress,
            btcAddress,
            false
        );

        assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_VALUE_ZERO_ERROR.value()), result);
    }

    @Test
    void registerFlyoverBtcTransaction_surpasses_locking_cap_and_shouldTransfer_is_true()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);

        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        Repository repository = mock(Repository.class);
        when(repository.getBalance(any())).thenReturn(co.rsk.core.Coin.valueOf(1));

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(btcRegTestParams);

        when(lockingCapSupport.getLockingCap()).thenReturn(Optional.of(Coin.COIN));

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);
        FederationSupport federationSupport = mock(FederationSupport.class);
        when(federationSupport.getLiveFederations()).thenReturn(Collections.singletonList(genesisFederation));

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstantsRegtest,
            provider,
            mock(BridgeEventLogger.class),
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            repository,
            mock(Block.class),
            btcContext,
            feePerKbSupport,
            whitelistSupport,
            federationSupport,
            lockingCapSupport,
            unionBridgeSupport,
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        ));

        when(bridgeSupport.getActiveFederation()).thenReturn(genesisFederation);
        when(bridgeSupport.validationsForRegisterBtcTransaction(any(), anyInt(), any(), any())).thenReturn(true);

        Address btcAddress = Address.fromBase58(
            btcRegTestParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            RskTestUtils.createHash(0),
            btcAddress,
            btcAddress,
            lbcAddress,
            activations
        );
        Address activeFlyoverFederationAddress = PegUtils.getFlyoverFederationAddress(btcRegTestParams, flyoverDerivationHash, genesisFederation);

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, activeFlyoverFederationAddress);
        byte[] pmtSerialized = Hex.decode("ab");

        BigInteger result = bridgeSupport.registerFlyoverBtcTransaction(
            rskTx,
            tx.bitcoinSerialize(),
            100,
            pmtSerialized,
            RskTestUtils.createHash(0),
            btcAddress,
            lbcAddress,
            btcAddress,
            true
        );

        assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.REFUNDED_LP_ERROR.value()), result);
    }

    @Test
    void registerFlyoverBtcTransaction_surpasses_locking_cap_and_shouldTransfer_is_false()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);
        when(provider.isFlyoverDerivationHashUsed(any(), any())).thenReturn(false);

        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        Repository repository = mock(Repository.class);
        when(repository.getBalance(any())).thenReturn(co.rsk.core.Coin.valueOf(1));

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(btcRegTestParams);

        when(lockingCapSupport.getLockingCap()).thenReturn(Optional.of(Coin.COIN));

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);
        FederationSupport federationSupport = mock(FederationSupport.class);
        when(federationSupport.getLiveFederations()).thenReturn(Collections.singletonList(genesisFederation));

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstantsRegtest,
            provider,
            mock(BridgeEventLogger.class),
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            repository,
            mock(Block.class),
            btcContext,
            feePerKbSupport,
            whitelistSupport,
            federationSupport,
            lockingCapSupport,
            unionBridgeSupport,
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        ));

        doReturn(genesisFederation).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());

        Address btcAddress = Address.fromBase58(
            btcRegTestParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );
        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            RskTestUtils.createHash(0),
            btcAddress,
            btcAddress,
            lbcAddress,
            activations
        );
        Address activeFlyoverFederationAddress = PegUtils.getFlyoverFederationAddress(btcRegTestParams, flyoverDerivationHash, genesisFederation);

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, activeFlyoverFederationAddress);
        byte[] pmtSerialized = Hex.decode("ab");

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

        assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.REFUNDED_USER_ERROR.value()), result);
    }

    @Test
    void registerFlyoverBtcTransaction_surpasses_locking_cap_and_tries_to_register_again()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(true);

        repository.addBalance(bridgeContractAddress, co.rsk.core.Coin.valueOf(1));

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            btcRegTestParams,
            activations
        );

        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(btcRegTestParams);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);
        FederationSupport federationSupport = mock(FederationSupport.class);
        when(federationSupport.getLiveFederations()).thenReturn(Collections.singletonList(genesisFederation));

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstantsRegtest,
            provider,
            mock(BridgeEventLogger.class),
            btcLockSenderProvider,
            new PeginInstructionsProvider(),
            repository,
            mock(Block.class),
            btcContext,
            feePerKbSupport,
            whitelistSupport,
            federationSupport,
            lockingCapSupport,
            unionBridgeSupport,
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        ));

        doReturn(genesisFederation).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(
            Optional.of(Coin.COIN), // The first time we simulate a lower locking cap than the value to register, to force the reimburse
            Optional.of(Coin.FIFTY_COINS) // The next time we simulate a height locking cap, to verify the user can't attempt to register the already reimbursed tx
        ).when(lockingCapSupport).getLockingCap();

        Address btcAddress = Address.fromBase58(
            btcRegTestParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            RskTestUtils.createHash(0),
            btcAddress,
            btcAddress,
            lbcAddress,
            activations
        );
        Address activeFlyoverFederationAddress = PegUtils.getFlyoverFederationAddress(btcRegTestParams, flyoverDerivationHash, genesisFederation);

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, activeFlyoverFederationAddress);
        byte[] pmtSerialized = Hex.decode("ab");

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

        assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.REFUNDED_USER_ERROR.value()), result);

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

        assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR.value()), result);
    }

    @Test
    void registerFlyoverBtcTransaction_OK()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(btcRegTestParams);

        Repository repository = spy(createRepository());
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            btcRegTestParams,
            activations
        );

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstantsRegtest,
            provider,
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            repository,
            mock(Block.class),
            btcContext,
            feePerKbSupport,
            whitelistSupport,
            federationSupport,
            lockingCapSupport,
            unionBridgeSupport,
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        ));

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);

        doReturn(genesisFederation).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());

        Address btcAddress = Address.fromBase58(
            btcRegTestParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        Coin valueToSend = Coin.COIN;
        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            RskTestUtils.createHash(0),
            btcAddress,
            btcAddress,
            lbcAddress,
            activations
        );
        Address activeFlyoverFederationAddress = PegUtils.getFlyoverFederationAddress(btcRegTestParams, flyoverDerivationHash, genesisFederation);

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(valueToSend, activeFlyoverFederationAddress);

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

        assertTrue(
            provider.isFlyoverDerivationHashUsed(
                tx.getHash(),
                flyoverDerivationHash
            )
        );
        assertEquals(1, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());

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

        assertEquals(BigInteger.valueOf(FlyoverTxResponseCodes.UNPROCESSABLE_TX_ALREADY_PROCESSED_ERROR.value()), result);
    }

    @Test
    void createFlyoverFederationInformation_OK() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Federation activeFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);
        FederationSupport federationSupport = mock(FederationSupport.class);
        when(federationSupport.getActiveFederation()).thenReturn(activeFederation);

        BridgeSupport bridgeSupport = new BridgeSupport(
            bridgeConstantsRegtest,
            mock(BridgeStorageProvider.class),
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            mock(Context.class),
            feePerKbSupport,
            whitelistSupport,
            federationSupport,
            lockingCapSupport,
            unionBridgeSupport,
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);
        Script federationRedeemScript = genesisFederation.getRedeemScript();

        Keccak256 flyoverDerivationHash = RskTestUtils.createHash(1);

        Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
            flyoverDerivationHash,
            federationRedeemScript
        );

        Script flyoverP2SHScript = getFlyoverFederationOutputScript(flyoverRedeemScript, genesisFederation.getFormatVersion());

        FlyoverFederationInformation expectedFlyoverFederationInformation =
            new FlyoverFederationInformation(flyoverDerivationHash,
                activeFederation.getP2SHScript().getPubKeyHash(),
                flyoverP2SHScript.getPubKeyHash()
            );

        FlyoverFederationInformation obtainedFlyoverFedInfo =
            bridgeSupport.createFlyoverFederationInformation(flyoverDerivationHash);

        assertEquals(
            expectedFlyoverFederationInformation.getFlyoverFederationAddress(btcRegTestParams),
            obtainedFlyoverFedInfo.getFlyoverFederationAddress(btcRegTestParams)
        );
    }

    @Test
    void getFlyoverWallet_ok() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(btcRegTestParams);

        BridgeSupport bridgeSupport = new BridgeSupport(
            bridgeConstantsRegtest,
            mock(BridgeStorageProvider.class),
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            btcContext,
            feePerKbSupport,
            whitelistSupport,
            mock(FederationSupport.class),
            lockingCapSupport,
            unionBridgeSupport,
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);
        Keccak256 derivationHash = PegTestUtils.createHash3(1);

        Script flyoverRedeemScript = FlyoverRedeemScriptBuilderImpl.builder().of(
            derivationHash,
            genesisFederation.getRedeemScript()
        );

        Script flyoverP2SH = getFlyoverFederationOutputScript(flyoverRedeemScript, genesisFederation.getFormatVersion());

        FlyoverFederationInformation flyoverFederationInformation =
            new FlyoverFederationInformation(
                derivationHash,
                genesisFederation.getP2SHScript().getPubKeyHash(),
                flyoverP2SH.getPubKeyHash()
            );

        BtcTransaction tx = new BtcTransaction(btcRegTestParams);
        tx.addOutput(Coin.COIN,
            flyoverFederationInformation.getFlyoverFederationAddress(
                btcRegTestParams
            )
        );

        UTXO utxo = UTXOBuilder.builder()
            .withTransactionHash(tx.getHash())
            .withOutputScript(flyoverP2SH)
            .build();
        List<UTXO> utxoList = Collections.singletonList(utxo);

        Wallet obtainedWallet = bridgeSupport.getFlyoverWallet(btcContext, utxoList, Collections.singletonList(flyoverFederationInformation));

        Assertions.assertEquals(Coin.COIN, obtainedWallet.getBalance());
    }

    @Test
    void getFlyoverDerivationHash_ok() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        Address userRefundBtcAddress = Address.fromBase58(
            btcRegTestParams,
            "mgy8yiUZYB7o9vvCu2Yi8GB3Vr32MQsyQJ"
        );
        byte[] userRefundBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(activations, userRefundBtcAddress);

        Address lpBtcAddress = Address.fromBase58(
            btcRegTestParams,
            "mhoDGMzHHDq2ZD6cFrKV9USnMfpxEtLwGm"
        );
        byte[] lpBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(activations, lpBtcAddress);

        byte[] derivationArgumentsHash = ByteUtil.leftPadBytes(new byte[]{0x01}, 32);
        byte[] lbcAddress = ByteUtil.leftPadBytes(new byte[]{0x03}, 20);
        byte[] result = ByteUtil.merge(derivationArgumentsHash, userRefundBtcAddressBytes, lbcAddress, lpBtcAddressBytes);

        Keccak256 flyoverDerivationHash = PegUtils.getFlyoverDerivationHash(
            new Keccak256(derivationArgumentsHash),
            userRefundBtcAddress,
            lpBtcAddress,
            new RskAddress(lbcAddress),
            activations
        );

        Assertions.assertArrayEquals(HashUtil.keccak256(result), flyoverDerivationHash.getBytes());
    }

    @Test
    void saveFlyoverDataInStorage_OK() {
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            btcRegTestParams,
            activations
        );

        FederationSupport federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeConstantsRegtest.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();

        Sha256Hash btcTxHash = BitcoinTestUtils.createHash(1);
        Keccak256 derivationHash = PegTestUtils.createHash3(1);

        byte[] flyoverScriptHash = new byte[]{0x1};
        FlyoverFederationInformation flyoverFederationInformation = new FlyoverFederationInformation(
            PegTestUtils.createHash3(2),
            new byte[]{0x1},
            flyoverScriptHash
        );

        List<UTXO> utxos = new ArrayList<>();
        Sha256Hash utxoHash = BitcoinTestUtils.createHash(1);
        Coin value = Coin.COIN.multiply(2);
        Script outputScript = new Script(EMPTY_BYTE_ARRAY);
        UTXO utxo = UTXOBuilder.builder()
            .withTransactionHash(utxoHash)
            .withValue(value)
            .withOutputScript(outputScript)
            .build();
        utxos.add(utxo);

        Assertions.assertEquals(0, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        bridgeSupport.saveFlyoverActiveFederationDataInStorage(btcTxHash, derivationHash, flyoverFederationInformation, utxos);

        bridgeSupport.save();

        Assertions.assertEquals(1, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(utxo, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).get(0));
        assertTrue(provider.isFlyoverDerivationHashUsed(btcTxHash, derivationHash));
        Optional<FlyoverFederationInformation> optionalFlyoverFederationInformation = provider.getFlyoverFederationInformation(flyoverScriptHash);
        assertTrue(optionalFlyoverFederationInformation.isPresent());
        FlyoverFederationInformation obtainedFlyoverFederationInformation = optionalFlyoverFederationInformation.get();
        Assertions.assertEquals(flyoverFederationInformation.getDerivationHash(), obtainedFlyoverFederationInformation.getDerivationHash());
        Assertions.assertArrayEquals(flyoverFederationInformation.getFederationRedeemScriptHash(), obtainedFlyoverFederationInformation.getFederationRedeemScriptHash());
    }

    private interface BtcTransactionProvider {
        BtcTransaction provide(BridgeConstants bridgeConstants, Address activeFederationAddress, Address retiringFederationAddress);
    }
}
