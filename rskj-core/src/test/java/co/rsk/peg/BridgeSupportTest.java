/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
import static co.rsk.peg.BridgeStorageIndexKey.RELEASE_REQUEST_QUEUE_WITH_TXHASH;
import static co.rsk.peg.BridgeSupport.BTC_TRANSACTION_CONFIRMATION_INCONSISTENT_BLOCK_ERROR_CODE;
import static co.rsk.peg.BridgeSupportTestUtil.*;
import static co.rsk.peg.bitcoin.BitcoinTestUtils.*;
import static co.rsk.peg.federation.FederationStorageIndexKey.NEW_FEDERATION_BTC_UTXOS_KEY;
import static co.rsk.peg.federation.FederationStorageIndexKey.OLD_FEDERATION_BTC_UTXOS_KEY;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.ethereum.vm.PrecompiledContracts.BRIDGE_ADDR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import co.rsk.RskTestUtils;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import co.rsk.net.utils.TransactionUtils;
import co.rsk.peg.bitcoin.*;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.peg.btcLockSender.BtcLockSenderProvider;
import co.rsk.peg.constants.*;
import co.rsk.peg.federation.*;
import co.rsk.peg.federation.constants.FederationConstants;
import co.rsk.peg.feeperkb.*;
import co.rsk.peg.lockingcap.*;
import co.rsk.peg.lockingcap.constants.*;
import co.rsk.peg.pegin.RejectedPeginReason;
import co.rsk.peg.pegininstructions.*;
import co.rsk.peg.storage.*;
import co.rsk.peg.union.*;
import co.rsk.peg.union.constants.*;
import co.rsk.peg.utils.*;
import co.rsk.peg.utils.NonRefundablePeginReason;
import co.rsk.peg.vote.ABICallSpec;
import co.rsk.peg.whitelist.*;
import co.rsk.peg.whitelist.constants.WhitelistMainNetConstants;
import co.rsk.test.builders.*;
import co.rsk.util.HexUtils;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.TestUtils;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.*;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.*;
import org.ethereum.vm.exception.VMException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class BridgeSupportTest {
    private static final String TO_ADDRESS = "0000000000000000000000000000000000000006";
    private static final BigInteger DUST_AMOUNT = new BigInteger("1");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";
    private static final Transaction tx = Transaction
        .builder()
        .nonce(NONCE)
        .gasPrice(GAS_PRICE)
        .gasLimit(GAS_LIMIT)
        .destination(Hex.decode(TO_ADDRESS))
        .data(Hex.decode(DATA))
        .chainId(Constants.MAINNET_CHAIN_ID)
        .value(DUST_AMOUNT)
        .build();
    private static final ECKey senderKey = RskTestUtils.getEcKeyFromSeed("sender");
    private static final co.rsk.core.Coin LIMIT_MONETARY_BASE = new co.rsk.core.Coin(new BigInteger("21000000000000000000000000"));
    private static final RskAddress contractAddress = PrecompiledContracts.BRIDGE_ADDR;

    private static final ActivationConfig.ForBlock genesisActivations = ActivationConfigsForTest.genesis().forBlock(0);
    private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);
    private static Federation activeFederation = P2shErpFederationBuilder.builder().build();

    private final BridgeConstants bridgeConstantsRegtest = new BridgeRegTestConstants();
    private final NetworkParameters btcRegTestParams = bridgeConstantsRegtest.getBtcParams();
    private final FederationConstants federationConstantsRegtest = bridgeConstantsRegtest.getFederationConstants();

    private final BridgeConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();
    private final NetworkParameters btcMainnetParams = bridgeMainNetConstants.getBtcParams();
    private final FederationConstants federationConstantsMainnet = bridgeMainNetConstants.getFederationConstants();
    private final LockingCapConstants lockingCapMainnetConstants = bridgeMainNetConstants.getLockingCapConstants();

    private final BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();
    private final FederationSupportBuilder federationSupportBuilder = FederationSupportBuilder.builder();
    private final int pegoutTxIndexActivationHeight = bridgeMainNetConstants.getBtcHeightWhenPegoutTxIndexActivates() + bridgeMainNetConstants.getPegoutTxIndexGracePeriodInBtcBlocks();

    private Repository repository;
    private BridgeStorageProvider bridgeStorageProvider;
    private WhitelistStorageProvider whitelistStorageProvider;
    private WhitelistSupport whitelistSupport;
    private LockingCapSupport lockingCapSupport;
    private StorageAccessor bridgeStorageAccessor;
    private FederationStorageProvider federationStorageProvider;
    private FederationSupport federationSupport;
    private FeePerKbSupport feePerKbSupport;
    private UnionBridgeSupport unionBridgeSupport;
    private SignatureCache signatureCache;
    private PartialMerkleTree pmtWithTransactions;
    private BtcBlockStoreWithCache.Factory btcBlockStoreFactory;
    private BridgeSupport bridgeSupport;

    @BeforeEach
    void setUpOnEachTest() {
        signatureCache = new BlockTxSignatureCache(new ReceivedTxSignatureCache());
        tx.sign(senderKey.getPrivKeyBytes());

        bridgeStorageAccessor = new InMemoryStorage();
        federationStorageProvider = new FederationStorageProviderImpl(bridgeStorageAccessor);
        whitelistStorageProvider = new WhitelistStorageProviderImpl(bridgeStorageAccessor);
        LockingCapStorageProvider lockingCapStorageProvider = new LockingCapStorageProviderImpl(bridgeStorageAccessor);

        feePerKbSupport = mock(FeePerKbSupport.class);
        Coin feePerKb = Coin.valueOf(1000L);
        when(feePerKbSupport.getFeePerKb()).thenReturn(feePerKb);
        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsMainnet)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(allActivations)
            .build();
        whitelistSupport = new WhitelistSupportImpl(
            WhitelistMainNetConstants.getInstance(),
            whitelistStorageProvider,
            allActivations,
            signatureCache
        );
        lockingCapSupport = new LockingCapSupportImpl(
            lockingCapStorageProvider,
            allActivations,
            lockingCapMainnetConstants,
            signatureCache
        );

        repository = createRepository();
        bridgeStorageProvider = new BridgeStorageProvider(repository, btcMainnetParams, allActivations);
    }

    @Test
    void getFeePerKb() {
        Coin feePerKb = Coin.valueOf(10_000L);
        when(feePerKbSupport.getFeePerKb()).thenReturn(feePerKb);

        bridgeSupport = bridgeSupportBuilder
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        Coin result = bridgeSupport.getFeePerKb();

        assertEquals(feePerKb, result);
    }

    @Test
    void voteFeePerKbChange_success() {
        when(feePerKbSupport.voteFeePerKbChange(any(), any(), any())).thenReturn(1);

        bridgeSupport = bridgeSupportBuilder
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        Transaction feePerKbTx = mock(Transaction.class);
        Coin feePerKbVote = Coin.CENT;
        int result = bridgeSupport.voteFeePerKbChange(feePerKbTx, feePerKbVote);

        assertEquals(FeePerKbResponseCode.SUCCESSFUL_VOTE.getCode(), result);
    }

    @Nested
    @Tag("Whitelist")
    class WhitelistTest {
        private WhitelistSupport whitelistSupport;
        private BridgeSupport bridgeSupport;

        @BeforeEach
        void setUp() {
            whitelistSupport = mock(WhitelistSupportImpl.class);
            bridgeSupport = bridgeSupportBuilder
                .withWhitelistSupport(whitelistSupport)
                .build();
        }

        @Test
        void getLockWhitelistSize() {
            when(whitelistSupport.getLockWhitelistSize()).thenReturn(10);

            assertEquals(10, bridgeSupport.getLockWhitelistSize());
        }

        @Test
        void getLockWhitelistEntryByIndex() {
            LockWhitelistEntry entry = mock(LockWhitelistEntry.class);
            when(whitelistSupport.getLockWhitelistEntryByIndex(0)).thenReturn(Optional.of(entry));

            Optional<LockWhitelistEntry> lockWhitelistEntryByIndex = bridgeSupport.getLockWhitelistEntryByIndex(0);
            assertTrue(lockWhitelistEntryByIndex.isPresent());
            assertEquals(entry, lockWhitelistEntryByIndex.get());
        }

        @Test
        void getLockWhitelistEntryByAddress() {
            LockWhitelistEntry entry = mock(LockWhitelistEntry.class);
            when(whitelistSupport.getLockWhitelistEntryByAddress("address")).thenReturn(Optional.of(entry));

            Optional<LockWhitelistEntry> lockWhitelistEntry = bridgeSupport.getLockWhitelistEntryByAddress("address");
            assertTrue(lockWhitelistEntry.isPresent());
            assertEquals(entry, lockWhitelistEntry.get());
        }

        @Test
        void addOneOffLockWhitelistAddress() {
            Transaction whitelistTx = mock(Transaction.class);
            String address = "address";
            BigInteger maxTransferValue = BigInteger.ONE;
            when(whitelistSupport.addOneOffLockWhitelistAddress(
                whitelistTx,
                address,
                maxTransferValue
            )).thenReturn(WhitelistResponseCode.SUCCESS.getCode());

            int result = bridgeSupport.addOneOffLockWhitelistAddress(whitelistTx, address, maxTransferValue);

            assertEquals(WhitelistResponseCode.SUCCESS.getCode(), result);
        }

        @Test
        void addUnlimitedLockWhitelistAddress() {
            Transaction whitelistTx = mock(Transaction.class);
            String address = "address";
            when(whitelistSupport.addUnlimitedLockWhitelistAddress(
                whitelistTx,
                address
            )).thenReturn(WhitelistResponseCode.SUCCESS.getCode());

            int result = bridgeSupport.addUnlimitedLockWhitelistAddress(whitelistTx, address);

            assertEquals(WhitelistResponseCode.SUCCESS.getCode(), result);
        }

        @Test
        void removeLockWhitelistAddress() {
            Transaction whitelistTx = mock(Transaction.class);
            String address = "address";
            when(whitelistSupport.removeLockWhitelistAddress(
                whitelistTx,
                address
            )).thenReturn(WhitelistResponseCode.SUCCESS.getCode());

            int result = bridgeSupport.removeLockWhitelistAddress(whitelistTx, address);

            assertEquals(WhitelistResponseCode.SUCCESS.getCode(), result);
        }

        @Test
        void setLockWhitelistDisableBlockDelay() throws BlockStoreException, IOException {
            // Set of Variables to be use in setLockWhitelistDisableBlockDelay
            Transaction whitelistTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                WhitelistCaller.AUTHORIZED.getRskAddress()
            );
            BigInteger disableBlockDelayBI = BigInteger.ONE;
            when(whitelistSupport.setLockWhitelistDisableBlockDelay(
                whitelistTx,
                disableBlockDelayBI,
                0
            )).thenReturn(WhitelistResponseCode.SUCCESS.getCode());

            // Set of variables to be use in bridgeSupportBuilder
            BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
            Repository rskRepository = mock(Repository.class);
            BridgeConstants bridgeConstantsMainNet = BridgeMainNetConstants.getInstance();
            BtcBlockStoreWithCache.Factory blockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);
            ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

            bridgeSupport = bridgeSupportBuilder
                .withWhitelistSupport(whitelistSupport)
                .withProvider(provider)
                .withRepository(rskRepository)
                .withBridgeConstants(bridgeConstantsMainNet)
                .withBtcBlockStoreFactory(blockStoreFactory)
                .withActivations(activations)
                .withFederationSupport(federationSupport)
                .build();

            // Set of variables to be used mocking
            BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
            StoredBlock storedBlock = mock(StoredBlock.class);
            BtcBlock btcBlock = mock(BtcBlock.class);
            NetworkParameters btcParams = bridgeConstantsMainNet.getBtcParams();
            BtcBlock genesisBtcBlock = btcParams.getGenesisBlock();
            Sha256Hash genesisBtcBlockHash = genesisBtcBlock.getHash();

            when(btcBlockStore.getChainHead()).thenReturn(storedBlock);
            when(storedBlock.getHeader()).thenReturn(btcBlock);
            when(btcBlock.getHash()).thenReturn(genesisBtcBlockHash);
            when(blockStoreFactory.newInstance(
                rskRepository,
                bridgeConstantsMainNet,
                provider,
                activations
            )).thenReturn(btcBlockStore);

            int result = bridgeSupport.setLockWhitelistDisableBlockDelay(whitelistTx, disableBlockDelayBI);

            assertEquals(WhitelistResponseCode.SUCCESS.getCode(), result);
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("federation support tests")
    class FederationSupportTests {
        FederationSupport federationSupport;
        BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
        BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();
        BridgeSupport bridgeSupport;

        Federation federation = P2shErpFederationBuilder.builder().build();

        @BeforeEach
        void setUp() {
            federationSupport = mock(FederationSupportImpl.class);

            bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeMainnetConstants)
                .withFederationSupport(federationSupport)
                .build();
        }

        @Test
        void getActiveFederation() {
            when(federationSupport.getActiveFederation()).thenReturn(federation);
            assertThat(bridgeSupport.getActiveFederation(), is(federation));
        }

        @Test
        void getActiveFederationRedeemScript() {
            Optional<Script> redeemScript = Optional.ofNullable(federation.getRedeemScript());

            when(federationSupport.getActiveFederationRedeemScript()).thenReturn(redeemScript);
            assertThat(bridgeSupport.getActiveFederationRedeemScript(), is(redeemScript));
        }

        @Test
        void getActiveFederationAddress() {
            Address address = federation.getAddress();

            when(federationSupport.getActiveFederationAddress()).thenReturn(address);
            assertThat(bridgeSupport.getActiveFederationAddress(), is(address));
        }

        @Test
        void getActiveFederationSize() {
            int size = federation.getSize();

            when(federationSupport.getActiveFederationSize()).thenReturn(size);
            assertThat(bridgeSupport.getActiveFederationSize(), is(size));
        }

        @Test
        void getActiveFederationThreshold() {
            int threshold = federation.getNumberOfSignaturesRequired();

            when(federationSupport.getActiveFederationThreshold()).thenReturn(threshold);
            assertThat(bridgeSupport.getActiveFederationThreshold(), is(threshold));
        }

        @Test
        void getActiveFederationCreationTime() {
            Instant creationTime = federation.getCreationTime();

            when(federationSupport.getActiveFederationCreationTime()).thenReturn(creationTime);
            assertThat(bridgeSupport.getActiveFederationCreationTime(), is(creationTime));
        }

        @Test
        void getActiveFederationCreationBlockNumber() {
            long creationBlockNumber = federation.getCreationBlockNumber();

            when(federationSupport.getActiveFederationCreationBlockNumber()).thenReturn(creationBlockNumber);
            assertThat(bridgeSupport.getActiveFederationCreationBlockNumber(), is(creationBlockNumber));
        }

        @Test
        void getActiveFederationCreationBlockHeight() {
            long creationBlockHeight = 100L;

            when(federationSupport.getActiveFederationCreationBlockHeight()).thenReturn(creationBlockHeight);
            assertThat(bridgeSupport.getActiveFederationCreationBlockHeight(), is(creationBlockHeight));
        }

        @Test
        void getActiveFederatorBtcPublicKey() {
            BtcECKey publicKey = federation.getBtcPublicKeys().get(0);

            when(federationSupport.getActiveFederatorBtcPublicKey(0)).thenReturn(publicKey.getPubKey());
            assertThat(bridgeSupport.getActiveFederatorBtcPublicKey(0), is(publicKey.getPubKey()));
        }

        @Test
        void getActiveFederatorPublicKeyOfType() {
            FederationMember member = federation.getMembers().get(0);
            BtcECKey btcKey = member.getBtcPublicKey();
            ECKey rskKey = member.getRskPublicKey();
            ECKey mstKey = member.getMstPublicKey();

            when(federationSupport.getActiveFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC)).thenReturn(btcKey.getPubKey());
            assertThat(bridgeSupport.getActiveFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC), is(btcKey.getPubKey()));

            when(federationSupport.getActiveFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK)).thenReturn(rskKey.getPubKey());
            assertThat(bridgeSupport.getActiveFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK), is(rskKey.getPubKey()));

            when(federationSupport.getActiveFederatorPublicKeyOfType(0, FederationMember.KeyType.MST)).thenReturn(mstKey.getPubKey());
            assertThat(bridgeSupport.getActiveFederatorPublicKeyOfType(0, FederationMember.KeyType.MST), is(mstKey.getPubKey()));
        }

        @Test
        void getRetiringFederation() {
            when(federationSupport.getRetiringFederation()).thenReturn(Optional.of(federation));

            Optional<Federation> actualRetiringFederation = bridgeSupport.getRetiringFederation();
            assertTrue(actualRetiringFederation.isPresent());
            assertThat(actualRetiringFederation.get(), is(federation));
        }

        @Test
        void getRetiringFederationAddress() {
            Address address = federation.getAddress();

            Optional<Address> retiringFederationAddressOptional = Optional.of(address);
            when(federationSupport.getRetiringFederationAddress()).thenReturn(retiringFederationAddressOptional);
            assertThat(bridgeSupport.getRetiringFederationAddress(), is(retiringFederationAddressOptional));
        }

        @Test
        void getRetiringFederationSize() {
            int size = federation.getSize();

            Optional<Integer> federationSizeOptional = Optional.of(size);
            when(federationSupport.getRetiringFederationSize()).thenReturn(federationSizeOptional);
            assertThat(bridgeSupport.getRetiringFederationSize(), is(federationSizeOptional));
        }

        @Test
        void getRetiringFederationThreshold() {
            int threshold = federation.getNumberOfSignaturesRequired();

            Optional<Integer> thresholdOptional = Optional.of(threshold);
            when(federationSupport.getRetiringFederationThreshold()).thenReturn(thresholdOptional);

            Optional<Integer> retiringFederationThreshold = bridgeSupport.getRetiringFederationThreshold();
            assertTrue(retiringFederationThreshold.isPresent());
            assertThat(retiringFederationThreshold, is(thresholdOptional));
        }

        @Test
        void getRetiringFederationCreationTime() {
            Instant creationTime = federation.getCreationTime();

            Optional<Instant> creationTimeOptional = Optional.of(creationTime);
            when(federationSupport.getRetiringFederationCreationTime()).thenReturn(creationTimeOptional);
            assertThat(bridgeSupport.getRetiringFederationCreationTime(), is(creationTimeOptional));
        }

        @Test
        void getRetiringFederationCreationBlockNumber() {
            long creationBlockNumber = federation.getCreationBlockNumber();

            Optional<Long> creationBlockNumberOptional = Optional.of(creationBlockNumber);
            when(federationSupport.getRetiringFederationCreationBlockNumber()).thenReturn(creationBlockNumberOptional);
            assertThat(bridgeSupport.getRetiringFederationCreationBlockNumber(), is(creationBlockNumberOptional));
        }

        @Test
        void getRetiringFederatorBtcPublicKey() {
            BtcECKey publicKey = federation.getBtcPublicKeys().get(0);
            when(federationSupport.getRetiringFederatorBtcPublicKey(0)).thenReturn(Optional.of(publicKey));

            Optional<BtcECKey> retiringFederatorBtcPublicKey = bridgeSupport.getRetiringFederatorBtcPublicKey(0);
            assertTrue(retiringFederatorBtcPublicKey.isPresent());
            assertEquals(retiringFederatorBtcPublicKey.get(), publicKey);
        }

        @Test
        void getRetiringFederatorPublicKeyOfType() {
            FederationMember member = federation.getMembers().get(0);
            BtcECKey btcKey = member.getBtcPublicKey();
            ECKey rskKey = member.getRskPublicKey();
            ECKey mstKey = member.getMstPublicKey();

            Optional<byte[]> retiringFederatorBtcPubKey = Optional.of(btcKey.getPubKey());
            when(federationSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC)).thenReturn(retiringFederatorBtcPubKey);
            assertThat(bridgeSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC), is(retiringFederatorBtcPubKey));

            Optional<byte[]> retiringFederatorRskPubKey = Optional.of(rskKey.getPubKey());
            when(federationSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK)).thenReturn(retiringFederatorRskPubKey);
            assertThat(bridgeSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK), is(retiringFederatorRskPubKey));

            Optional<byte[]> retiringFederatorMstPubKey = Optional.of(mstKey.getPubKey());
            when(federationSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.MST)).thenReturn(retiringFederatorMstPubKey);
            assertThat(bridgeSupport.getRetiringFederatorPublicKeyOfType(0, FederationMember.KeyType.MST), is(retiringFederatorMstPubKey));
        }

        @Test
        void getPendingFederationHash() {
            PendingFederation pendingFederation = PendingFederationBuilder.builder().build();
            Keccak256 hash = pendingFederation.getHash();

            when(federationSupport.getPendingFederationHash()).thenReturn(hash);
            assertThat(bridgeSupport.getPendingFederationHash(), is(hash));
        }

        @Test
        void getPendingFederationSize() {
            int size = federation.getSize();

            when(federationSupport.getPendingFederationSize()).thenReturn(size);
            assertThat(bridgeSupport.getPendingFederationSize(), is(size));
        }

        @Test
        void getPendingFederatorBtcPublicKey() {
            BtcECKey publicKey = federation.getBtcPublicKeys().get(0);

            when(federationSupport.getPendingFederatorBtcPublicKey(0)).thenReturn(publicKey.getPubKey());
            assertThat(bridgeSupport.getPendingFederatorBtcPublicKey(0), is(publicKey.getPubKey()));
        }

        @Test
        void getPendingFederatorPublicKeyOfType() {
            FederationMember member = federation.getMembers().get(0);
            BtcECKey btcKey = member.getBtcPublicKey();
            ECKey rskKey = member.getRskPublicKey();
            ECKey mstKey = member.getMstPublicKey();

            when(federationSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC)).thenReturn(btcKey.getPubKey());
            assertThat(bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.BTC), is(btcKey.getPubKey()));

            when(federationSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK)).thenReturn(rskKey.getPubKey());
            assertThat(bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.RSK), is(rskKey.getPubKey()));

            when(federationSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.MST)).thenReturn(mstKey.getPubKey());
            assertThat(bridgeSupport.getPendingFederatorPublicKeyOfType(0, FederationMember.KeyType.MST), is(mstKey.getPubKey()));
        }

        @Test
        void getProposedFederation_whenFederationSupportReturnsProposedFederation_shouldReturnProposedFed() {
            when(federationSupport.getProposedFederation()).thenReturn(Optional.of(federation));

            Optional<Federation> proposedFed = bridgeSupport.getProposedFederation();
            assertTrue(proposedFed.isPresent());
            assertThat(proposedFed.get(), is(federation));
        }

        @Test
        void getProposedFederation_whenNoProposedFederation_shouldReturnEmpty() {
            // Act
            var actualProposedFederation = bridgeSupport.getProposedFederation();

            // Assert
            assertFalse(actualProposedFederation.isPresent());
        }

        @Test
        void getProposedFederationAddress_whenNoProposedFederationAddress_shouldReturnEmpty() {
            // Act
            var actualProposedFederationAddress = bridgeSupport.getProposedFederationAddress();

            // Assert
            assertFalse(actualProposedFederationAddress.isPresent());
        }

        @Test
        void getProposedFederationAddress_whenFederationSupportReturnsAddress_shouldReturnAddress() {
            // Arrange
            var expectedAddress = federation.getAddress();
            when(federationSupport.getProposedFederationAddress()).thenReturn(Optional.of(expectedAddress));

            // Act
            var actualProposedFederationAddress = bridgeSupport.getProposedFederationAddress();

            // Assert
            assertTrue(actualProposedFederationAddress.isPresent());
            assertEquals(expectedAddress, actualProposedFederationAddress.get());
        }

        @ParameterizedTest
        @EnumSource(FederationMember.KeyType.class)
        void getProposedFederatorPublicKeyOfType_whenBridgeSupportReturnsEmpty_shouldReturnEmpty(FederationMember.KeyType keyType) {
            // Arrange
            var index = 1;

            // Act
            var actualProposedFederatorPublicKey = bridgeSupport.getProposedFederatorPublicKeyOfType(index, keyType);

            // Assert
            assertFalse(actualProposedFederatorPublicKey.isPresent());
        }

        @ParameterizedTest
        @EnumSource(FederationMember.KeyType.class)
        void getProposedFederatorPublicKeyOfType_whenProposedFederationExists_shouldReturnExpectedPublicKey(FederationMember.KeyType keyType) {
            var index = 0;
            var member = federation.getMembers().get(index);

            // Set up public keys based on the keyType
            byte[] expectedPublicKey;
            switch (keyType) {
                case BTC:
                    expectedPublicKey = member.getBtcPublicKey().getPubKey();
                    when(federationSupport.getProposedFederatorPublicKeyOfType(index, FederationMember.KeyType.BTC))
                        .thenReturn(Optional.of(expectedPublicKey));
                    break;
                case RSK:
                    expectedPublicKey = member.getRskPublicKey().getPubKey();
                    when(federationSupport.getProposedFederatorPublicKeyOfType(index, FederationMember.KeyType.RSK))
                        .thenReturn(Optional.of(expectedPublicKey));
                    break;
                case MST:
                    expectedPublicKey = member.getMstPublicKey().getPubKey();
                    when(federationSupport.getProposedFederatorPublicKeyOfType(index, FederationMember.KeyType.MST))
                        .thenReturn(Optional.of(expectedPublicKey));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown KeyType");
            }

            Optional<byte[]> key = bridgeSupport.getProposedFederatorPublicKeyOfType(index, keyType);
            assertTrue(key.isPresent());
            assertArrayEquals(expectedPublicKey, key.get());
        }

        @Test
        void getProposedFederationSize_whenBridgeSupportReturnsEmpty_shouldReturnEmpty() {
            // Act
            var actualProposedFederationSize = bridgeSupport.getProposedFederationSize();

            // Assert
            assertFalse(actualProposedFederationSize.isPresent());
        }

        @Test
        void getProposedFederationSize_whenProposedFederationExists_shouldReturnSize() {
            // Arrange
            var expectedSize = federation.getSize();
            when(federationSupport.getProposedFederationSize()).thenReturn(Optional.of(expectedSize));

            // Act
            var actualProposedFederationSize = bridgeSupport.getProposedFederationSize();

            // Assert
            assertTrue(actualProposedFederationSize.isPresent());
            assertEquals(expectedSize, actualProposedFederationSize.get());
        }

        @Test
        void getProposedFederationCreationTime_whenBridgeSupportReturnsEmpty_shouldReturnEmpty() {
            // Act
            var actualProposedFederationCreationTime = bridgeSupport.getProposedFederationCreationTime();

            // Assert
            assertFalse(actualProposedFederationCreationTime.isPresent());
        }

        @Test
        void getProposedFederationCreationTime_whenProposedFederationExists_shouldReturnCreationTime() {
            // Arrange
            var expectedCreationTime = federation.getCreationTime();
            when(federationSupport.getProposedFederationCreationTime()).thenReturn(Optional.of(expectedCreationTime));

            // Act
            var actualProposedFederationCreationTime = bridgeSupport.getProposedFederationCreationTime();

            // Assert
            assertTrue(actualProposedFederationCreationTime.isPresent());
            assertEquals(expectedCreationTime, actualProposedFederationCreationTime.get());
        }

        @Test
        void getProposedFederationCreationBlockNumber_whenBridgeSupportReturnsEmpty_shouldReturnEmpty() {
            // Act
            var actualProposedFederationCreationBlockNumber = bridgeSupport.getProposedFederationCreationBlockNumber();

            // Assert
            assertFalse(actualProposedFederationCreationBlockNumber.isPresent());
        }

        @Test
        void getProposedFederationCreationBlockNumber_whenProposedFederationExists_shouldReturnCreationBlockNumber() {
            // Arrange
            var expectedCreationBlockNumber = federation.getCreationBlockNumber();
            when(federationSupport.getProposedFederationCreationBlockNumber()).thenReturn(Optional.of(expectedCreationBlockNumber));

            // Act
            var actualProposedFederationCreationBlockNumber = bridgeSupport.getProposedFederationCreationBlockNumber();

            // Assert
            assertTrue(actualProposedFederationCreationBlockNumber.isPresent());
            assertEquals(expectedCreationBlockNumber, actualProposedFederationCreationBlockNumber.get());
        }

        @Test
        void getProposedFederatorPublicKeyOfType_whenIndexOutOfBoundsForMemberList_shouldThrowException() {
            // Arrange
            var index = 1;
            var keyType = FederationMember.KeyType.BTC;
            when(federationSupport.getProposedFederatorPublicKeyOfType(index, keyType))
                .thenThrow(new IndexOutOfBoundsException());

            // Act & Assert
            assertThrows(IndexOutOfBoundsException.class,
                () -> bridgeSupport.getProposedFederatorPublicKeyOfType(index, keyType));
        }

        @Test
        void voteFederationChange() {
            Transaction federationChangeTx = mock(Transaction.class);
            ABICallSpec callSpec = mock(ABICallSpec.class);
            int result = 1;

            when(federationSupport.voteFederationChange(any(), any(), any(), any())).thenReturn(result);
            assertThat(bridgeSupport.voteFederationChange(federationChangeTx, callSpec), is(result));
        }

        @Test
        void updateFederationCreationBlockHeights_callsFederationSupportUpdateFederationCreationBlockHeights() {
            bridgeSupport.updateFederationCreationBlockHeights();
            verify(federationSupport).updateFederationCreationBlockHeights();
        }

        @Test
        void save_callsFederationSupportSave() {
            bridgeSupport.save();
            verify(federationSupport).save();
        }
    }

    @Nested
    @Tag("LockingCap")
    class LockingCapTest {

        private LockingCapSupport lockingCapSupport;
        private BridgeSupport bridgeSupport;
        private final LockingCapConstants constants = LockingCapMainNetConstants.getInstance();

        @BeforeEach
        void setUp() {
            lockingCapSupport = mock(LockingCapSupportImpl.class);
            bridgeSupport = bridgeSupportBuilder
                .withLockingCapSupport(lockingCapSupport)
                .build();
        }

        @Test
        void getLockingCap_whenNoValueExistsInStorage_shouldReturnInitialValue() {
            // Arrange
            Optional<Coin> expectedLockingCap = Optional.of(constants.getInitialValue());
            when(lockingCapSupport.getLockingCap()).thenReturn(expectedLockingCap);

            // Act
            Optional<Coin> actualLockingCap = Optional.of(bridgeSupport.getLockingCap());

            // Assert
            assertEquals(expectedLockingCap, actualLockingCap);
        }

        @Test
        void getLockingCap_whenLockingCapIsEmpty_shouldReturnNull() {
            // Arrange
            when(lockingCapSupport.getLockingCap()).thenReturn(Optional.empty());

            // Act
            Coin actualLockingCap = bridgeSupport.getLockingCap();

            // Assert
            assertNull(actualLockingCap);
        }

        @Test
        void increaseLockingCap() throws VMException {
            // Arrange
            Coin newLockingCap = constants.getInitialValue().add(Coin.SATOSHI);
            Transaction lockingCapTx = TransactionUtils.getTransactionFromCaller(
                signatureCache,
                LockingCapCaller.FIRST_AUTHORIZED.getRskAddress()
            );
            when(lockingCapSupport.increaseLockingCap(lockingCapTx, newLockingCap)).thenReturn(true);
            when(lockingCapSupport.getLockingCap()).thenReturn(Optional.of(newLockingCap));

            // Act
            boolean actualResult = bridgeSupport.increaseLockingCap(lockingCapTx, newLockingCap);

            // Assert
            assertTrue(actualResult);
            assertEquals(newLockingCap, bridgeSupport.getLockingCap());
        }
    }

    @Nested
    @Tag("unionBridge")
    class UnionBridgeTest {
        private static final ActivationConfig.ForBlock allActivations = ActivationConfigsForTest.all().forBlock(0);
        private static final BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        private static final UnionBridgeConstants unionBridgeConstants = bridgeConstants.getUnionBridgeConstants();
        private static final UnionBridgeSupportBuilder unionBridgeSupportBuilder = UnionBridgeSupportBuilder.builder();
        private static final RskAddress newUnionBridgeContractAddress = RskTestUtils.generateAddress("newUnionBridgeContractAddress");

        private Repository repository;
        private UnionBridgeStorageProvider unionBridgeStorageProvider;
        private BridgeEventLogger bridgeEventLogger;
        private BridgeSupport bridgeSupport;
        private SignatureCache signatureCache;
        private Transaction rskTx;

        @BeforeEach
        void setUp() {
            signatureCache = mock(SignatureCache.class);
            unionBridgeStorageProvider = mock(UnionBridgeStorageProvider.class);
            unionBridgeSupport = unionBridgeSupportBuilder
                .withSignatureCache(signatureCache)
                .withConstants(unionBridgeConstants)
                .withStorageProvider(unionBridgeStorageProvider)
                .build();

            repository = mock(Repository.class);
            BridgeStorageProvider bridgeStorageProvider = mock(BridgeStorageProvider.class);
            bridgeEventLogger = mock(BridgeEventLogger.class);

            bridgeSupport = bridgeSupportBuilder
                .withUnionBridgeSupport(unionBridgeSupport)
                .withBridgeConstants(bridgeConstants)
                .withProvider(bridgeStorageProvider)
                .withRepository(repository)
                .withEventLogger(bridgeEventLogger)
                .build();

            rskTx = mock(Transaction.class);
        }

        @ParameterizedTest
        @MethodSource("unionResponseCodeProvider")
        void setUnionBridgeContractAddress_shouldReturnResultedResponseCode(UnionResponseCode expectedUnionResponseCode) {
            // arrange
            unionBridgeSupport = mock(UnionBridgeSupport.class);
            when(unionBridgeSupport.setUnionBridgeContractAddressForTestnet(any())).thenReturn(
                expectedUnionResponseCode
            );
            bridgeSupport = bridgeSupportBuilder
                .withUnionBridgeSupport(unionBridgeSupport)
                .build();

            // act
            UnionResponseCode actualResponseCode = bridgeSupport.setUnionBridgeContractAddressForTestnet(newUnionBridgeContractAddress);

            // assert
            assertEquals(
                expectedUnionResponseCode,
                actualResponseCode
            );
            verify(unionBridgeSupport).setUnionBridgeContractAddressForTestnet(newUnionBridgeContractAddress);
            // Verify save method is not called when setAddress is called
            verify(unionBridgeSupport, never()).save();
        }

        private static Stream<Arguments> unionResponseCodeProvider() {
            return Stream.of(
                Arguments.of(UnionResponseCode.SUCCESS),
                Arguments.of(UnionResponseCode.GENERIC_ERROR)
            );
        }

        @ParameterizedTest
        @MethodSource("unionBridgeConstantsProvider")
        void getUnionBridgeContractAddress_shouldReturnInitialUnionBridgeContractAddress(UnionBridgeConstants unionBridgeConstants) {
            unionBridgeSupport = unionBridgeSupportBuilder
                .withConstants(unionBridgeConstants)
                .build();

            bridgeSupport = bridgeSupportBuilder
                .withUnionBridgeSupport(unionBridgeSupport)
                .build();

            // act
            RskAddress actualUnionBridgeContractAddress = bridgeSupport.getUnionBridgeContractAddress();

            // assert
            RskAddress expectedUnionBridgeContractAddress = unionBridgeConstants.getAddress();
            assertEquals(expectedUnionBridgeContractAddress, actualUnionBridgeContractAddress);
        }

        private static Stream<Arguments> unionBridgeConstantsProvider() {
            return Stream.of(
                Arguments.of(UnionBridgeMainNetConstants.getInstance()),
                Arguments.of(UnionBridgeTestNetConstants.getInstance()),
                Arguments.of(UnionBridgeRegTestConstants.getInstance())
            );
        }

        private static Stream<Arguments> unionBridgeTestnetAndRegTestConstantsProvider() {
            return Stream.of(
                Arguments.of(UnionBridgeTestNetConstants.getInstance()),
                Arguments.of(UnionBridgeRegTestConstants.getInstance())
            );
        }

        @ParameterizedTest
        @MethodSource("unionBridgeTestnetAndRegTestConstantsProvider")
        void getUnionBridgeContractAddress_whenStoredAddress_shouldReturnUnionBridgeContractAddress(UnionBridgeConstants unionBridgeConstants) {
            // arrange
            unionBridgeSupport = unionBridgeSupportBuilder
                .withConstants(unionBridgeConstants)
                .build();

            bridgeSupport = bridgeSupportBuilder
                .withUnionBridgeSupport(unionBridgeSupport)
                .build();

            when(unionBridgeStorageProvider.getAddress()).thenReturn(Optional.of(newUnionBridgeContractAddress));

            // act
            RskAddress actualUnionBridgeContractAddress = bridgeSupport.getUnionBridgeContractAddress();

            // assert
            assertEquals(newUnionBridgeContractAddress, actualUnionBridgeContractAddress);
        }


        @Test
        void getUnionBridgeLockingCap_whenNoLockingCapIsStored_shouldReturnInitialConstantLockingCapValue() {
            // act
            co.rsk.core.Coin actualUnionBridgeLockingCap = bridgeSupport.getUnionBridgeLockingCap();

            // assert
            co.rsk.core.Coin expectedLockingCap = unionBridgeConstants.getInitialLockingCap();
            assertEquals(expectedLockingCap, actualUnionBridgeLockingCap);
        }

        @Test
        void getUnionBridgeLockingCap_whenStoredLockingCap_shouldReturnStoredLockingCap() {
            // arrange
            BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
            co.rsk.core.Coin storedLockingCap = new co.rsk.core.Coin(oneEth.multiply(BigInteger.valueOf(500))); // 500 RBTC
            when(unionBridgeStorageProvider.getLockingCap()).thenReturn(Optional.of(storedLockingCap));

            bridgeSupport = bridgeSupportBuilder
                .withActivations(allActivations)
                .withUnionBridgeSupport(
                    UnionBridgeSupportBuilder
                        .builder()
                        .withStorageProvider(unionBridgeStorageProvider)
                        .build()
                )
                .build();

            // act
            co.rsk.core.Coin actualUnionBridgeLockingCap = bridgeSupport.getUnionBridgeLockingCap();

            // assert
            assertEquals(storedLockingCap, actualUnionBridgeLockingCap);
        }

        @ParameterizedTest
        @MethodSource("increaseUnionBridgeLockingCapResponseCodeProvider")
        void increaseUnionBridgeLockingCap_shouldReturnResultedResponseCode(UnionResponseCode expectedUnionResponseCode) {
            // arrange
            unionBridgeSupport = mock(UnionBridgeSupport.class);
            when(unionBridgeSupport.increaseLockingCap(any(),
                any())).thenReturn(
                expectedUnionResponseCode
            );
            bridgeSupport = bridgeSupportBuilder
                .withUnionBridgeSupport(unionBridgeSupport)
                .build();

            co.rsk.core.Coin initialLockingCap = unionBridgeConstants.getInitialLockingCap();
            co.rsk.core.Coin newLockingCap = initialLockingCap.multiply(
                BigInteger.valueOf(unionBridgeConstants.getLockingCapIncrementsMultiplier()));

            // act
            UnionResponseCode actualResult = bridgeSupport.increaseUnionBridgeLockingCap(
                rskTx, newLockingCap);

            // assert
            assertEquals(expectedUnionResponseCode, actualResult);
            verify(unionBridgeSupport).increaseLockingCap(rskTx, newLockingCap);
        }

        private static Stream<Arguments> increaseUnionBridgeLockingCapResponseCodeProvider() {
            return Stream.of(
                Arguments.of(UnionResponseCode.SUCCESS),
                Arguments.of(UnionResponseCode.GENERIC_ERROR),
                Arguments.of(UnionResponseCode.INVALID_VALUE)
            );
        }

        @ParameterizedTest
        @MethodSource("requestUnionBridgeRbtcFailingResponseCodes")
        void requestUnionBridgeRbtc_whenFail_shouldReturnFailingResponseCode(UnionResponseCode expectedUnionResponseCode) {
            // arrange
            BigInteger oneEth = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
            co.rsk.core.Coin amountRequested = new co.rsk.core.Coin(oneEth);
            unionBridgeSupport = mock(UnionBridgeSupport.class);
            when(unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested)).thenReturn(expectedUnionResponseCode);
            bridgeSupport = bridgeSupportBuilder.withUnionBridgeSupport(unionBridgeSupport).build();

            // act
            UnionResponseCode actualResponseCode = bridgeSupport.requestUnionBridgeRbtc(rskTx, amountRequested);

            // assert
            verify(unionBridgeSupport).requestUnionRbtc(rskTx, amountRequested);

            verify(repository, never()).transfer(
                any(RskAddress.class),
                any(RskAddress.class),
                any(co.rsk.core.Coin.class)
            );
            verify(bridgeEventLogger, never()).logUnionRbtcRequested(
                any(RskAddress.class),
                any(co.rsk.core.Coin.class)
            );

            assertEquals(expectedUnionResponseCode, actualResponseCode);
        }

        private static Stream<Arguments> requestUnionBridgeRbtcFailingResponseCodes() {
            return Stream.of(
                Arguments.of(UnionResponseCode.UNAUTHORIZED_CALLER),
                Arguments.of(UnionResponseCode.INVALID_VALUE),
                Arguments.of(UnionResponseCode.REQUEST_DISABLED),
                Arguments.of(UnionResponseCode.GENERIC_ERROR)
            );
        }

        @Test
        void requestUnionBridgeRbtc_whenSuccess_shouldReturnSuccessResponseCode() {
            // arrange
            BigInteger weiPerEther = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
            co.rsk.core.Coin amountRequested = new co.rsk.core.Coin(weiPerEther);
            unionBridgeSupport = mock(UnionBridgeSupport.class);
            when(unionBridgeSupport.requestUnionRbtc(rskTx, amountRequested)).thenReturn(UnionResponseCode.SUCCESS);
            when(unionBridgeSupport.getUnionBridgeContractAddress()).thenReturn(newUnionBridgeContractAddress);

            bridgeSupport = bridgeSupportBuilder.withUnionBridgeSupport(unionBridgeSupport).build();

            // act
            UnionResponseCode actualResponseCode = bridgeSupport.requestUnionBridgeRbtc(rskTx, amountRequested);

            // assert
            verify(unionBridgeSupport).requestUnionRbtc(rskTx, amountRequested);

            verify(repository).transfer(
                BRIDGE_ADDR,
                newUnionBridgeContractAddress,
                amountRequested
            );
            verify(bridgeEventLogger).logUnionRbtcRequested(
                newUnionBridgeContractAddress,
                amountRequested
            );

            assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        }

        @ParameterizedTest
        @MethodSource("releaseUnionBridgeRbtcFailingResponseCodes")
        void releaseUnionBridgeRbtc_whenFail_shouldReturnFailingResponseCode(UnionResponseCode expectedUnionResponseCode) {
            // arrange
            unionBridgeSupport = mock(UnionBridgeSupport.class);
            when(unionBridgeSupport.releaseUnionRbtc(any())).thenReturn(expectedUnionResponseCode);
            bridgeSupport = bridgeSupportBuilder
                .withSignatureCache(signatureCache)
                .withUnionBridgeSupport(unionBridgeSupport).build();

            BigInteger weiPerEther = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
            co.rsk.core.Coin amountToRelease = new co.rsk.core.Coin(weiPerEther.multiply(BigInteger.TEN)); // 10 RBTC

            RskAddress unionBridgeContractAddress = unionBridgeConstants.getAddress();
            when(rskTx.getSender(signatureCache)).thenReturn(unionBridgeContractAddress);
            when(rskTx.getValue()).thenReturn(amountToRelease);

            // act
            UnionResponseCode actualResponseCode = bridgeSupport.releaseUnionBridgeRbtc(rskTx);

            // assert
            verify(unionBridgeSupport).releaseUnionRbtc(rskTx);

            verify(repository).transfer(
                BRIDGE_ADDR,
                unionBridgeContractAddress,
                amountToRelease
            );
            verify(bridgeEventLogger, never()).logUnionRbtcReleased(
                any(RskAddress.class),
                any(co.rsk.core.Coin.class)
            );

            assertEquals(expectedUnionResponseCode, actualResponseCode);
        }

        private static Stream<Arguments> releaseUnionBridgeRbtcFailingResponseCodes() {
            return Stream.of(
                Arguments.of(UnionResponseCode.UNAUTHORIZED_CALLER),
                Arguments.of(UnionResponseCode.RELEASE_DISABLED),
                Arguments.of(UnionResponseCode.INVALID_VALUE),
                Arguments.of(UnionResponseCode.GENERIC_ERROR)
            );
        }

        @Test
        void releaseUnionBridgeRbtc_whenSuccess_shouldReturnSuccessResponseCode() {
            // arrange
            unionBridgeSupport = mock(UnionBridgeSupport.class);
            when(unionBridgeSupport.releaseUnionRbtc(any())).thenReturn(UnionResponseCode.SUCCESS);
            bridgeSupport = bridgeSupportBuilder
                .withSignatureCache(signatureCache)
                .withUnionBridgeSupport(unionBridgeSupport).build();

            BigInteger weiPerEther = BigInteger.TEN.pow(18); // 1 ETH = 1000000000000000000 wei
            co.rsk.core.Coin amountToRelease = new co.rsk.core.Coin(weiPerEther.multiply(BigInteger.TEN)); // 10 RBTC
            when(rskTx.getSender(signatureCache)).thenReturn(newUnionBridgeContractAddress);
            when(rskTx.getValue()).thenReturn(amountToRelease);

            // act
            UnionResponseCode actualResponseCode = bridgeSupport.releaseUnionBridgeRbtc(rskTx);

            // assert
            verify(unionBridgeSupport).releaseUnionRbtc(rskTx);
            verify(repository, never()).transfer(
                any(),
                any(),
                any()
            );

            assertEquals(UnionResponseCode.SUCCESS, actualResponseCode);
        }

        @Test
        void setTransferPermissions_whenSuccess_shouldReturnSuccessResponseCode() {
            // arrange
            unionBridgeSupport = mock(UnionBridgeSupport.class);
            when(unionBridgeSupport.setTransferPermissions(any(), anyBoolean(), anyBoolean()))
                .thenReturn(UnionResponseCode.SUCCESS);
            bridgeSupport = bridgeSupportBuilder.withUnionBridgeSupport(unionBridgeSupport).build();

            // act
            UnionResponseCode responseCode = bridgeSupport.setUnionBridgeTransferPermissions(rskTx, true, false);

            // assert
            assertEquals(UnionResponseCode.SUCCESS, responseCode);
            verify(unionBridgeSupport).setTransferPermissions(rskTx, true, false);
        }

        @Test
        void setSuperEvent_shouldSetSuperEvent() {
            // arrange
            unionBridgeSupport = mock(UnionBridgeSupport.class);
            bridgeSupport = bridgeSupportBuilder
                .withSignatureCache(signatureCache)
                .withUnionBridgeSupport(unionBridgeSupport)
                .build();

            // act
            byte[] superEvent = new byte[]{(byte) 0x123456};
            bridgeSupport.setSuperEvent(rskTx, superEvent);

            // assert
            verify(unionBridgeSupport).setSuperEvent(rskTx, superEvent);
        }

        @Test
        void clearSuperEvent_shouldClearSuperEvent() {
            // arrange
            unionBridgeSupport = mock(UnionBridgeSupport.class);
            bridgeSupport = bridgeSupportBuilder
                .withSignatureCache(signatureCache)
                .withUnionBridgeSupport(unionBridgeSupport)
                .build();

            // act
            bridgeSupport.clearSuperEvent(rskTx);

            // assert
            verify(unionBridgeSupport).clearSuperEvent(rskTx);
        }

        @Test
        void getSuperEvent_shouldGetSuperEvent() {
            // arrange
            unionBridgeSupport = mock(UnionBridgeSupport.class);
            bridgeSupport = bridgeSupportBuilder
                .withSignatureCache(signatureCache)
                .withUnionBridgeSupport(unionBridgeSupport)
                .build();

            // act
            bridgeSupport.getSuperEvent();

            // assert
            verify(unionBridgeSupport).getSuperEvent();
        }

        @Test
        void setBaseEvent_shouldSetBaseEvent() {
            // arrange
            unionBridgeSupport = mock(UnionBridgeSupport.class);
            bridgeSupport = bridgeSupportBuilder
                .withSignatureCache(signatureCache)
                .withUnionBridgeSupport(unionBridgeSupport)
                .build();

            // act
            byte[] baseEvent = new byte[]{(byte) 0x123456};
            bridgeSupport.setBaseEvent(rskTx, baseEvent);

            // assert
            verify(unionBridgeSupport).setBaseEvent(rskTx, baseEvent);
        }

        @Test
        void clearBaseEvent_shouldClearBaseEvent() {
            // arrange
            unionBridgeSupport = mock(UnionBridgeSupport.class);
            bridgeSupport = bridgeSupportBuilder
                .withSignatureCache(signatureCache)
                .withUnionBridgeSupport(unionBridgeSupport)
                .build();

            // act
            bridgeSupport.clearBaseEvent(rskTx);

            // assert
            verify(unionBridgeSupport).clearBaseEvent(rskTx);
        }

        @Test
        void getBaseEvent_shouldGetBaseEvent() {
            // arrange
            unionBridgeSupport = mock(UnionBridgeSupport.class);
            bridgeSupport = bridgeSupportBuilder
                .withSignatureCache(signatureCache)
                .withUnionBridgeSupport(unionBridgeSupport)
                .build();

            // act
            bridgeSupport.getBaseEvent();

            // assert
            verify(unionBridgeSupport).getBaseEvent();
        }

        @Test
        void save() {
            // arrange
            unionBridgeSupport = mock(UnionBridgeSupport.class);
            bridgeSupport = bridgeSupportBuilder.withUnionBridgeSupport(unionBridgeSupport).build();

            // act
            bridgeSupport.save();

            // assert
            verify(unionBridgeSupport).save();
        }
    }

    @Test
    void registerBtcTransaction_before_RSKIP134_activation_sends_above_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        // Sending above locking cap evaluating different conditions (sending to both fed, to one, including funds in wallet and in utxos waiting for signatures...)
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.ZERO, Coin.COIN.multiply(2), Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN.multiply(2), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN.multiply(2), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN.multiply(2), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN.multiply(2), Coin.ZERO, Coin.COIN.multiply(2), Coin.ZERO);
        assertLockingCap(true, false, Coin.COIN.multiply(3), Coin.COIN.multiply(2), Coin.ZERO, Coin.ZERO, Coin.COIN.multiply(2));

        // Right above locking cap
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN.multiply(5), Coin.ZERO, Coin.ZERO, Coin.SATOSHI);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN.multiply(5), Coin.ZERO, Coin.SATOSHI, Coin.ZERO);
    }

    @Test
    void registerBtcTransaction_before_RSKIP134_activation_sends_exactly_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.ZERO, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.ZERO, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.ZERO);
    }

    @Test
    void registerBtcTransaction_before_RSKIP134_activation_sends_below_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.ZERO, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.ZERO);
        assertLockingCap(true, false, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.ZERO, Coin.COIN);
    }

    @Test
    void registerBtcTransaction_after_RSKIP134_activation_sends_above_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        // Sending above locking cap evaluating different conditions (sending to both fed, to one, including funds in wallet and in utxos waiting for signatures...)
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN, Coin.COIN.multiply(2), Coin.COIN, Coin.COIN);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.ZERO, Coin.COIN.multiply(3), Coin.COIN, Coin.COIN);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN.multiply(3), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN.multiply(3), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN.multiply(3), Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN.multiply(3), Coin.ZERO, Coin.COIN.multiply(2), Coin.ZERO);
        assertLockingCap(false, true, Coin.COIN.multiply(4), Coin.COIN.multiply(3), Coin.ZERO, Coin.ZERO, Coin.COIN.multiply(2));

        // Right above locking cap
        assertLockingCap(false, true, Coin.COIN.multiply(5), Coin.COIN.multiply(5), Coin.ZERO, Coin.ZERO, Coin.SATOSHI);
        assertLockingCap(false, true, Coin.COIN.multiply(5), Coin.COIN.multiply(5), Coin.ZERO, Coin.SATOSHI, Coin.ZERO);
        assertLockingCap(false, true, Coin.COIN.multiply(5), Coin.COIN.multiply(5), Coin.SATOSHI, Coin.ZERO, Coin.ZERO);
        assertLockingCap(false, true, Coin.COIN.multiply(5), Coin.COIN.multiply(5).add(Coin.SATOSHI), Coin.ZERO, Coin.ZERO, Coin.ZERO);
    }

    @Test
    void registerBtcTransaction_after_RSKIP134_activation_sends_exactly_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.ZERO, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.ZERO, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(5), Coin.COIN, Coin.COIN, Coin.COIN, Coin.ZERO);
    }

    @Test
    void registerBtcTransaction_after_RSKIP134_activation_sends_below_lockingcap() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.ZERO, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.COIN, Coin.ZERO, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.COIN);
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.COIN, Coin.ZERO);
        assertLockingCap(true, true, Coin.COIN.multiply(6), Coin.COIN, Coin.COIN, Coin.ZERO, Coin.COIN);
    }

    @Test
    void isBtcTxHashAlreadyProcessed() throws IOException {
        BridgeConstants bridgeConstants = new BridgeRegTestConstants();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);

        Sha256Hash hash1 = Sha256Hash.ZERO_HASH;
        Sha256Hash hash2 = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001");

        BridgeStorageProvider bridgeStorageProviderMock = mock(BridgeStorageProvider.class);
        when(bridgeStorageProviderMock.getHeightIfBtcTxhashIsAlreadyProcessed(hash1)).thenReturn(Optional.of(1L));

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(bridgeStorageProviderMock)
            .withActivations(activations)
            .build();

        assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(hash1));
        assertFalse(bridgeSupport.isBtcTxHashAlreadyProcessed(hash2));
    }

    @Test
    void getBtcTxHashProcessedHeight() throws IOException {
        BridgeConstants bridgeConstants = new BridgeRegTestConstants();
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);

        Sha256Hash hash1 = Sha256Hash.ZERO_HASH;
        Sha256Hash hash2 = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000001");

        BridgeStorageProvider bridgeStorageProviderMock = mock(BridgeStorageProvider.class);
        when(bridgeStorageProviderMock.getHeightIfBtcTxhashIsAlreadyProcessed(hash1)).thenReturn(Optional.of(1L));

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(bridgeStorageProviderMock)
            .withActivations(activations)
            .build();

        assertEquals(Long.valueOf(1), bridgeSupport.getBtcTxHashProcessedHeight(hash1));
        assertEquals(Long.valueOf(-1), bridgeSupport.getBtcTxHashProcessedHeight(hash2));
    }

    @Test
    void eventLoggerLogLockBtc_before_rskip_146_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);
        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsMainnet);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);
        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(genesisFederation);

        Block executionBlock = mock(Block.class);
        btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction btcTx = new BtcTransaction(bridgeMainNetConstants.getBtcParams());
        btcTx.addOutput(lockValue, genesisFederation.getAddress());
        BtcECKey srcKey = new BtcECKey();
        btcTx.addInput(
            BitcoinTestUtils.createHash(1),
            0,
            ScriptBuilder.createInputScript(null, srcKey)
        );

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeMainNetConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeMainNetConstants.getBtcParams(),
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 1;

        mockChainOfStoredBlocks(
            btcBlockStore,
            btcBlock,
            height + bridgeMainNetConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        feePerKbSupport = new FeePerKbSupportImpl(
            bridgeMainNetConstants.getFeePerKbConstants(),
            mock(FeePerKbStorageProvider.class)
        );
        when(mockBridgeStorageProvider.getPegoutsWaitingForConfirmations()).thenReturn(mock(PegoutsWaitingForConfirmations.class));

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsMainnet)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withProvider(mockBridgeStorageProvider)
            .withEventLogger(mockedEventLogger)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withFeePerKbSupport(feePerKbSupport)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();

        bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            btcTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        verify(mockedEventLogger, never()).logLockBtc(
            any(RskAddress.class),
            any(BtcTransaction.class),
            any(Address.class),
            any(Coin.class)
        );
    }

    @Test
    void eventLoggerLogLockBtc_after_rskip_146_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);

        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);
        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(genesisFederation);

        Block executionBlock = mock(Block.class);
        btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction peginTx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        peginTx.addOutput(lockValue, federationStorageProviderMock.getNewFederation(any(), any()).getAddress());
        BtcECKey srcKey = new BtcECKey();
        peginTx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Get the tx sender public key
        byte[] data = peginTx.getInput(0).getScriptSig().getChunks().get(1).data;
        BtcECKey senderBtcKey = BtcECKey.fromPublicOnly(data);

        // Whitelist the address
        LockWhitelist whitelist = whitelistStorageProvider.getLockWhitelist(activations, bridgeMainNetConstants.getBtcParams());
        Address address = senderBtcKey.toAddress(bridgeMainNetConstants.getBtcParams());
        whitelist.put(address, new OneOffWhiteListEntry(address, lockValue));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(peginTx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeMainNetConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeMainNetConstants.getBtcParams(),
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 1;

        mockChainOfStoredBlocks(
            btcBlockStore,
            btcBlock,
            height + bridgeMainNetConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        feePerKbSupport = new FeePerKbSupportImpl(
            bridgeMainNetConstants.getFeePerKbConstants(),
            mock(FeePerKbStorageProvider.class)
        );
        when(mockBridgeStorageProvider.getPegoutsWaitingForConfirmations()).thenReturn(mock(PegoutsWaitingForConfirmations.class));
        Transaction rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(Keccak256.ZERO_HASH);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withProvider(mockBridgeStorageProvider)
            .withFeePerKbSupport(feePerKbSupport)
            .withWhitelistSupport(whitelistSupport)
            .withEventLogger(mockedEventLogger)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();

        bridgeSupport.registerBtcTransaction(rskTx, peginTx.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        verify(mockedEventLogger, atLeastOnce()).logLockBtc(
            any(RskAddress.class),
            any(BtcTransaction.class),
            any(Address.class),
            any(Coin.class)
        );
    }

    @Test
    void eventLoggerLogPeginRejectionEvents_before_rskip_181_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP181)).thenReturn(false);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        WhitelistStorageProvider whitelistProvider = mock(WhitelistStorageProvider.class);
        when(whitelistProvider.getLockWhitelist(activations, btcRegTestParams)).thenReturn(lockWhitelist);

        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);
        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(genesisFederation);

        Block executionBlock = mock(Block.class);
        btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction peginTx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        peginTx.addOutput(lockValue, federationStorageProviderMock.getNewFederation(any(), any()).getAddress());
        BtcECKey srcKey = new BtcECKey();
        peginTx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(peginTx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstantsRegtest.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstantsRegtest.getBtcParams(),
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 1;

        mockChainOfStoredBlocks(
            btcBlockStore,
            btcBlock,
            height + bridgeConstantsRegtest.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any(BtcTransaction.class))).thenReturn(Optional.empty());

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any(BtcTransaction.class))).thenReturn(Optional.empty());

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(mockBridgeStorageProvider)
            .withEventLogger(mockedEventLogger)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), peginTx.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        verify(mockedEventLogger, never()).logRejectedPegin(any(BtcTransaction.class), any(RejectedPeginReason.class));
        verify(mockedEventLogger, never()).logNonRefundablePegin(any(BtcTransaction.class), any(
            NonRefundablePeginReason.class));
    }

    @Test
    void eventLoggerLogPeginRejectionEvents_after_rskip_181_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP181)).thenReturn(true);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);
        WhitelistStorageProvider whitelistProvider = mock(WhitelistStorageProvider.class);
        when(whitelistProvider.getLockWhitelist(activations, btcRegTestParams)).thenReturn(lockWhitelist);

        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);
        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(genesisFederation);

        Block executionBlock = mock(Block.class);
        btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction peginTx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        peginTx.addOutput(lockValue, federationStorageProviderMock.getNewFederation(any(), any()).getAddress());
        BtcECKey srcKey = new BtcECKey();
        peginTx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(peginTx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstantsRegtest.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstantsRegtest.getBtcParams(),
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 1;

        mockChainOfStoredBlocks(
            btcBlockStore,
            btcBlock,
            height + bridgeConstantsRegtest.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any(BtcTransaction.class))).thenReturn(Optional.empty());

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any(BtcTransaction.class))).thenReturn(Optional.empty());

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(mockBridgeStorageProvider)
            .withEventLogger(mockedEventLogger)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();

        bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            peginTx.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        verify(mockedEventLogger, atLeastOnce()).logRejectedPegin(any(BtcTransaction.class), any(RejectedPeginReason.class));
        verify(mockedEventLogger, atLeastOnce()).logNonRefundablePegin(any(BtcTransaction.class), any(
            NonRefundablePeginReason.class));
    }

    @Test
    void eventLoggerLogPeginBtc_before_rskip_170_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(false);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);

        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);
        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(genesisFederation);

        Block executionBlock = mock(Block.class);
        btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction peginTx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        peginTx.addOutput(lockValue, federationStorageProviderMock.getNewFederation(any(), any()).getAddress());
        BtcECKey srcKey = new BtcECKey();
        peginTx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Get the tx sender public key
        byte[] data = peginTx.getInput(0).getScriptSig().getChunks().get(1).data;
        BtcECKey senderBtcKey = BtcECKey.fromPublicOnly(data);

        // Whitelist the address
        LockWhitelist whitelist = whitelistStorageProvider.getLockWhitelist(activations, bridgeMainNetConstants.getBtcParams());
        Address address = senderBtcKey.toAddress(bridgeMainNetConstants.getBtcParams());
        whitelist.put(address, new OneOffWhiteListEntry(address, lockValue));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(peginTx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeMainNetConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeMainNetConstants.getBtcParams(),
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 1;

        mockChainOfStoredBlocks(
            btcBlockStore,
            btcBlock,
            height + bridgeMainNetConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        feePerKbSupport = new FeePerKbSupportImpl(bridgeMainNetConstants.getFeePerKbConstants(), mock(FeePerKbStorageProvider.class));
        when(mockBridgeStorageProvider.getPegoutsWaitingForConfirmations()).thenReturn(mock(PegoutsWaitingForConfirmations.class));
        Transaction rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(Keccak256.ZERO_HASH);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withProvider(mockBridgeStorageProvider)
            .withFeePerKbSupport(feePerKbSupport)
            .withWhitelistSupport(whitelistSupport)
            .withEventLogger(mockedEventLogger)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();

        bridgeSupport.registerBtcTransaction(rskTx, peginTx.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        verify(mockedEventLogger, atLeastOnce()).logLockBtc(any(RskAddress.class), any(BtcTransaction.class), any(Address.class), any(Coin.class));
        verify(mockedEventLogger, never()).logPeginBtc(any(RskAddress.class), any(BtcTransaction.class), any(Coin.class), anyInt());
    }

    @Test
    void eventLoggerLogPeginBtc_after_rskip_170_activation() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        BridgeEventLogger mockedEventLogger = mock(BridgeEventLogger.class);
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);

        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(any(Address.class), any(Coin.class), any(int.class))).thenReturn(true);

        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);
        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(genesisFederation);

        Block executionBlock = mock(Block.class);
        btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction peginTx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        peginTx.addOutput(lockValue, federationStorageProviderMock.getNewFederation(any(), any()).getAddress());
        BtcECKey srcKey = new BtcECKey();
        peginTx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Get the tx sender public key
        byte[] data = peginTx.getInput(0).getScriptSig().getChunks().get(1).data;
        BtcECKey senderBtcKey = BtcECKey.fromPublicOnly(data);

        // Whitelist the address
        LockWhitelist whitelist = whitelistStorageProvider.getLockWhitelist(activations, bridgeMainNetConstants.getBtcParams());
        Address address = senderBtcKey.toAddress(bridgeMainNetConstants.getBtcParams());
        whitelist.put(address, new OneOffWhiteListEntry(address, lockValue));

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(peginTx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeMainNetConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
            new co.rsk.bitcoinj.core.BtcBlock(bridgeMainNetConstants.getBtcParams(), 1, BitcoinTestUtils.createHash(1), merkleRoot,
                1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock,
            height + bridgeMainNetConstants.getBtc2RskMinimumAcceptableConfirmations(), height);

        feePerKbSupport = new FeePerKbSupportImpl(bridgeMainNetConstants.getFeePerKbConstants(), mock(FeePerKbStorageProvider.class));
        when(mockBridgeStorageProvider.getPegoutsWaitingForConfirmations()).thenReturn(mock(PegoutsWaitingForConfirmations.class));
        Transaction rskTx = mock(Transaction.class);
        when(rskTx.getHash()).thenReturn(Keccak256.ZERO_HASH);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withProvider(mockBridgeStorageProvider)
            .withEventLogger(mockedEventLogger)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withFeePerKbSupport(feePerKbSupport)
            .withWhitelistSupport(whitelistSupport)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();

        bridgeSupport.registerBtcTransaction(rskTx, peginTx.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        verify(mockedEventLogger, never()).logLockBtc(any(RskAddress.class), any(BtcTransaction.class), any(Address.class), any(Coin.class));
        verify(mockedEventLogger, atLeastOnce()).logPeginBtc(any(RskAddress.class), any(BtcTransaction.class), any(Coin.class), anyInt());
    }

    @Test
    @SuppressWarnings("squid:S5961")
    void registerBtcTransactionLockTxNotWhitelisted_before_rskip_146_activation() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        NetworkParameters btcParams = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        // Creates active federation
        List<BtcECKey> newFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );

        FederationArgs newFederationArgs = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(newFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            btcParams
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(newFederationArgs);

        // Creates retiring federation
        List<BtcECKey> retiringFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fb01")),
            BtcECKey.fromPrivate(Hex.decode("fb02")),
            BtcECKey.fromPrivate(Hex.decode("fb03"))
        );

        FederationArgs retiringFederationArgs = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiringFederationKeys),
            Instant.ofEpochMilli(2000L),
            0L,
            btcParams
        );
        Federation retiringFederation = FederationFactory.buildStandardMultiSigFederation(retiringFederationArgs);

        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);
        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(5), newFederation.getAddress());
        BtcECKey srcKey1 = new BtcECKey();
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        // Second transaction goes only to the second federation
        BtcTransaction tx2 = new BtcTransaction(btcRegTestParams);
        tx2.addOutput(Coin.COIN.multiply(10), retiringFederation.getAddress());
        BtcECKey srcKey2 = new BtcECKey();
        tx2.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey2));

        // Third transaction has one output to each federation
        // Lock is expected to be done accordingly and utxos assigned accordingly as well
        BtcTransaction tx3 = new BtcTransaction(btcRegTestParams);
        tx3.addOutput(Coin.COIN.multiply(3), newFederation.getAddress());
        tx3.addOutput(Coin.COIN.multiply(4), retiringFederation.getAddress());
        BtcECKey srcKey3 = new BtcECKey();
        tx3.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey3));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);

        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(newFederation);
        federationStorageProvider.setOldFederation(retiringFederation);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(executionBlock)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(tx2.getHash());
        hashes.add(tx3.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 3);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        Transaction rskTx1 = PegTestUtils.getMockedRskTxWithHash("aa");
        Transaction rskTx2 = PegTestUtils.getMockedRskTxWithHash("bb");
        Transaction rskTx3 = PegTestUtils.getMockedRskTxWithHash("cc");

        bridgeSupport.registerBtcTransaction(rskTx1, tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(rskTx2, tx2.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(rskTx3, tx3.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

        RskAddress srcKey1RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey1.getPrivKey()).getAddress());
        RskAddress srcKey2RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey2.getPrivKey()).getAddress());
        RskAddress srcKey3RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey3.getPrivKey()).getAddress());

        assertEquals(0, repository.getBalance(srcKey1RskAddress).asBigInteger().intValue());
        assertEquals(0, repository.getBalance(srcKey2RskAddress).asBigInteger().intValue());
        assertEquals(0, repository.getBalance(srcKey3RskAddress).asBigInteger().intValue());
        assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(BRIDGE_ADDR));

        assertEquals(0, federationStorageProvider.getNewFederationBtcUTXOs(btcParams, activations).size());
        assertEquals(0, federationStorageProvider.getOldFederationBtcUTXOs().size());

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(3, provider.getPegoutsWaitingForConfirmations().getEntriesWithoutHash().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().size());

        List<BtcTransaction> pegoutBtcTxs = provider.getPegoutsWaitingForConfirmations().getEntries()
            .stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .sorted(Comparator.comparing(BtcTransaction::getOutputSum))
            .toList();

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction pegoutBtcTx = pegoutBtcTxs.get(0);
        assertEquals(1, pegoutBtcTx.getOutputs().size());
        assertThat(Coin.COIN.multiply(5).subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        assertEquals(srcKey1.toAddress(btcRegTestParams), pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        assertEquals(1, pegoutBtcTx.getInputs().size());
        assertEquals(tx1.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());

        // Second release tx should correspond to the 7 (3+4) BTC lock tx
        pegoutBtcTx = pegoutBtcTxs.get(1);
        assertEquals(1, pegoutBtcTx.getOutputs().size());
        assertThat(Coin.COIN.multiply(7).subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        assertEquals(srcKey3.toAddress(btcRegTestParams), pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        assertEquals(2, pegoutBtcTx.getInputs().size());
        List<TransactionOutPoint> releaseOutpoints = pegoutBtcTx.getInputs().stream().map(TransactionInput::getOutpoint).sorted(Comparator.comparing(TransactionOutPoint::getIndex)).toList();
        assertEquals(tx3.getHash(), releaseOutpoints.get(0).getHash());
        assertEquals(tx3.getHash(), releaseOutpoints.get(1).getHash());
        assertEquals(0, releaseOutpoints.get(0).getIndex());
        assertEquals(1, releaseOutpoints.get(1).getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx3.getHash()).isPresent());

        // Third release tx should correspond to the 10 BTC lock tx
        pegoutBtcTx = pegoutBtcTxs.get(2);
        assertEquals(1, pegoutBtcTx.getOutputs().size());
        assertThat(Coin.COIN.multiply(10).subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        assertEquals(srcKey2.toAddress(btcRegTestParams), pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        assertEquals(1, pegoutBtcTx.getInputs().size());
        assertEquals(tx2.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx2.getHash()).isPresent());

        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
    }

    @Test
    @SuppressWarnings("squid:S5961")
    void registerBtcTransactionLockTxNotWhitelisted_after_rskip_146_activation() throws BlockStoreException, AddressFormatException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        NetworkParameters btcParams = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        //Creates federation 1
        List<BtcECKey> federation1Keys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );

        FederationArgs federation1Args = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
            Instant.ofEpochMilli(1000L),
            0L,
            btcParams
        );
        Federation federation1 = FederationFactory.buildStandardMultiSigFederation(federation1Args);

        //Creates federation 2
        List<BtcECKey> federation2Keys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fb01")),
            BtcECKey.fromPrivate(Hex.decode("fb02")),
            BtcECKey.fromPrivate(Hex.decode("fb03"))
        );

        FederationArgs federation2Args = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(federation2Keys),
            Instant.ofEpochMilli(2000L),
            0L,
            btcParams
        );
        Federation federation2 = FederationFactory.buildStandardMultiSigFederation(federation2Args);

        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        BtcECKey srcKey1 = new BtcECKey();
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        // Second transaction goes only to the second federation
        BtcTransaction tx2 = new BtcTransaction(btcRegTestParams);
        tx2.addOutput(Coin.COIN.multiply(10), federation2.getAddress());
        BtcECKey srcKey2 = new BtcECKey();
        tx2.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey2));

        // Third transaction has one output to each federation
        // Lock is expected to be done accordingly and utxos assigned accordingly as well
        BtcTransaction tx3 = new BtcTransaction(btcRegTestParams);
        tx3.addOutput(Coin.COIN.multiply(3), federation1.getAddress());
        tx3.addOutput(Coin.COIN.multiply(4), federation2.getAddress());
        BtcECKey srcKey3 = new BtcECKey();
        tx3.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey3));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);
        federationStorageProvider.setOldFederation(federation2);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withEventLogger(bridgeEventLogger)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(tx2.getHash());
        hashes.add(tx3.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 3);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        Transaction rskTx1 = PegTestUtils.getMockedRskTxWithHash("aa");
        Transaction rskTx2 = PegTestUtils.getMockedRskTxWithHash("bb");
        Transaction rskTx3 = PegTestUtils.getMockedRskTxWithHash("cc");

        bridgeSupport.registerBtcTransaction(rskTx1, tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(rskTx2, tx2.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.registerBtcTransaction(rskTx3, tx3.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

        RskAddress srcKey1RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey1.getPrivKey()).getAddress());
        RskAddress srcKey2RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey2.getPrivKey()).getAddress());
        RskAddress srcKey3RskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey3.getPrivKey()).getAddress());

        assertEquals(0, repository.getBalance(srcKey1RskAddress).asBigInteger().intValue());
        assertEquals(0, repository.getBalance(srcKey2RskAddress).asBigInteger().intValue());
        assertEquals(0, repository.getBalance(srcKey3RskAddress).asBigInteger().intValue());
        assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(BRIDGE_ADDR));

        assertEquals(0, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(0, federationStorageProvider.getOldFederationBtcUTXOs().size());

        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntriesWithoutHash().size());
        assertEquals(3, provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().size());

        List<BtcTransaction> pegoutBtcTxs = provider.getPegoutsWaitingForConfirmations().getEntries()
            .stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .sorted(Comparator.comparing(BtcTransaction::getOutputSum))
            .toList();

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction pegoutBtcTx = pegoutBtcTxs.get(0);
        assertEquals(1, pegoutBtcTx.getOutputs().size());
        assertThat(Coin.COIN.multiply(5).subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        assertEquals(srcKey1.toAddress(btcRegTestParams), pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        assertEquals(1, pegoutBtcTx.getInputs().size());
        assertEquals(tx1.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
        // First Rsk tx corresponds to this release
        verify(bridgeEventLogger).logReleaseBtcRequested(
            rskTx1.getHash().getBytes(),
            pegoutBtcTx,
            Coin.COIN.multiply(5)
        );

        // Second release tx should correspond to the 7 (3+4) BTC lock tx
        pegoutBtcTx = pegoutBtcTxs.get(1);
        assertEquals(1, pegoutBtcTx.getOutputs().size());
        assertThat(Coin.COIN.multiply(7).subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        assertEquals(srcKey3.toAddress(btcRegTestParams), pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        assertEquals(2, pegoutBtcTx.getInputs().size());
        List<TransactionOutPoint> releaseOutpoints = pegoutBtcTx.getInputs().stream().map(TransactionInput::getOutpoint).sorted(Comparator.comparing(TransactionOutPoint::getIndex)).toList();
        assertEquals(tx3.getHash(), releaseOutpoints.get(0).getHash());
        assertEquals(tx3.getHash(), releaseOutpoints.get(1).getHash());
        assertEquals(0, releaseOutpoints.get(0).getIndex());
        assertEquals(1, releaseOutpoints.get(1).getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx3.getHash()).isPresent());
        // third Rsk tx corresponds to this release
        verify(bridgeEventLogger).logReleaseBtcRequested(
            rskTx3.getHash().getBytes(),
            pegoutBtcTx,
            Coin.COIN.multiply(7)
        );

        // Third release tx should correspond to the 10 BTC lock tx
        pegoutBtcTx = pegoutBtcTxs.get(2);
        assertEquals(1, pegoutBtcTx.getOutputs().size());
        assertThat(Coin.COIN.multiply(10).subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        assertEquals(srcKey2.toAddress(btcRegTestParams), pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        assertEquals(1, pegoutBtcTx.getInputs().size());
        assertEquals(tx2.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx2.getHash()).isPresent());
        // Second Rsk tx corresponds to this release
        verify(bridgeEventLogger).logReleaseBtcRequested(rskTx2.getHash().getBytes(), pegoutBtcTx, Coin.COIN.multiply(10));

        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
    }

    @Test
    void registerBtcTransaction_sending_segwit_tx_twice_locks_just_once() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock mockedActivations = mock(ActivationConfig.ForBlock.class);
        when(mockedActivations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        BtcTransaction txWithWitness = new BtcTransaction(btcRegTestParams);

        // P2SH-P2WPKH tx
        BtcECKey srcKey1 = getBtcEcKeyFromSeed("p2shp2wpkh_sender");
        byte[] redeemScript = ByteUtil.merge(new byte[]{0x00, 0x14}, srcKey1.getPubKeyHash());

        Script witnessScript = new ScriptBuilder()
            .data(redeemScript)
            .build();
        txWithWitness.addInput(BitcoinTestUtils.createHash(1), 0, witnessScript);

        TransactionWitness txWit = new TransactionWitness(2);
        txWit.setPush(0, new byte[72]); // push for signatures
        txWit.setPush(1, srcKey1.getPubKey());
        txWithWitness.setWitness(0, txWit);

        List<BtcECKey> fedKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );

        FederationArgs federationArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(fedKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            btcRegTestParams
        );
        Federation fed = FederationFactory.buildStandardMultiSigFederation(
            federationArgs
        );

        txWithWitness.addOutput(Coin.COIN.multiply(5), fed.getAddress());

        // Create the pmt without witness and calculate the block merkle root
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits,
            Collections.singletonList(txWithWitness.getHash()), 1);
        Sha256Hash merkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(new ArrayList<>());

        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcRegTestParams, bits,
            Collections.singletonList(txWithWitness.getHash(true)), 1);

        Sha256Hash witnessMerkleRoot = pmtWithWitness.getTxnHashAndMerkleRoot(new ArrayList<>());

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);

        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(fed);
        when(provider.getCoinbaseInformation(registerHeader.getHash())).thenReturn(new CoinbaseInformation(witnessMerkleRoot));
        WhitelistStorageProvider whitelistProvider = mock(WhitelistStorageProvider.class);
        when(whitelistProvider.getLockWhitelist(genesisActivations, btcRegTestParams)).thenReturn(new LockWhitelist(new HashMap<>(), 0));
        LockingCapSupport lockingCapSupportMock = mock(LockingCapSupport.class);
        when(lockingCapSupportMock.getLockingCap()).thenReturn(Optional.of(Coin.FIFTY_COINS));
        // mock an actual store for the processed txs
        HashMap<Sha256Hash, Long> processedTxs = new HashMap<>();
        doAnswer(a -> {
            processedTxs.put(a.getArgument(0), a.getArgument(1));
            return null;
        }).when(provider).setHeightBtcTxhashAlreadyProcessed(any(), anyLong());
        doAnswer(a -> Optional.ofNullable(processedTxs.get(a.getArgument(0))))
            .when(provider).getHeightIfBtcTxhashIsAlreadyProcessed(any());

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(666L);

        feePerKbSupport = new FeePerKbSupportImpl(
            bridgeConstantsRegtest.getFeePerKbConstants(),
            mock(FeePerKbStorageProvider.class)
        );
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(mock(PegoutsWaitingForConfirmations.class));

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withRskExecutionBlock(executionBlock)
            .withActivations(mockedActivations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(mockFactory)
            .withFeePerKbSupport(feePerKbSupport)
            .withActivations(mockedActivations)
            .withFederationSupport(federationSupport)
            .withLockingCapSupport(lockingCapSupport)
            .build();

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        // Tx is locked
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), txWithWitness.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(txWithWitness.getHash(true), executionBlock.getNumber());
        verify(provider).setHeightBtcTxhashAlreadyProcessed(txWithWitness.getHash(false), executionBlock.getNumber());

        BtcTransaction txWithoutWitness = new BtcTransaction(btcRegTestParams, txWithWitness.bitcoinSerialize());
        txWithoutWitness.setWitness(0, null);
        assertFalse(txWithoutWitness.hasWitness());

        // Tx is NOT locked again!
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), txWithoutWitness.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());
        verify(provider).setHeightBtcTxhashAlreadyProcessed(txWithoutWitness.getHash(), executionBlock.getNumber());

        assertNotEquals(txWithWitness.getHash(true), txWithoutWitness.getHash());
    }

    @Test
    void callProcessFundsMigration_is_migrating_before_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = FederationTestUtils.getGenesisFederation(federationConstantsMainnet);

        FederationArgs newFederationArgs = new FederationArgs(FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            btcMainnetParams
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(newFederationArgs);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);

        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations())
            .thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));
        when(federationStorageProviderMock.getOldFederation(any(), any()))
            .thenReturn(oldFederation);
        when(federationStorageProviderMock.getNewFederation(any(), any()))
            .thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 18500 since we are using mainnet constants
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(18510, 1);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsMainnet)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withRskExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        Script outputScript = ScriptBuilder.createOutputScript(oldFederation.getAddress());
        UTXO utxo = UTXOBuilder.builder()
            .withOutputScript(outputScript)
            .withHeight(10)
            .build();
        sufficientUTXOsForMigration1.add(utxo);
        when(federationStorageProviderMock.getOldFederationBtcUTXOs())
            .thenReturn(sufficientUTXOsForMigration1);

        bridgeSupport.updateCollections(tx);

        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntriesWithoutHash().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().size());

        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
    }

    @Test
    void callProcessFundsMigration_is_migrating_after_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = FederationTestUtils.getGenesisFederation(federationConstantsMainnet);

        FederationArgs newFederationArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            btcMainnetParams
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(newFederationArgs);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);

        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));
        when(federationStorageProviderMock.getOldFederation(any(), any())).thenReturn(oldFederation);
        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 18500 since we are using mainnet constants
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(18510, 1);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsMainnet)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withRskExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        Script outputScript = ScriptBuilder.createOutputScript(oldFederation.getAddress());
        UTXO utxo = UTXOBuilder.builder()
            .withOutputScript(outputScript)
            .withHeight(10)
            .build();
        sufficientUTXOsForMigration1.add(utxo);
        when(federationStorageProviderMock.getOldFederationBtcUTXOs()).thenReturn(sufficientUTXOsForMigration1);

        bridgeSupport.updateCollections(tx);

        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntriesWithoutHash().size());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().size());
        PegoutsWaitingForConfirmations.Entry entry = (PegoutsWaitingForConfirmations.Entry) provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().toArray()[0];
        // Should have been logged with the migrated UTXO
        verify(bridgeEventLogger).logReleaseBtcRequested(
            tx.getHash().getBytes(),
            entry.getBtcTransaction(),
            Coin.COIN
        );
    }

    @Test
    void callProcessFundsMigration_is_migrated_before_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = FederationTestUtils.getGenesisFederation(federationConstantsMainnet);

        FederationArgs newFederationArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            btcMainnetParams
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(
            newFederationArgs
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);

        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));
        when(federationStorageProviderMock.getOldFederation(any(), any())).thenReturn(oldFederation);
        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 18500 since we are using mainnet constants
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(18510, 1);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsMainnet)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withRskExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        Script outputScript = ScriptBuilder.createOutputScript(oldFederation.getAddress());
        UTXO utxo = UTXOBuilder.builder()
            .withOutputScript(outputScript)
            .withHeight(10)
            .build();
        sufficientUTXOsForMigration1.add(utxo);
        when(federationStorageProviderMock.getOldFederationBtcUTXOs())
            .thenReturn(sufficientUTXOsForMigration1);

        bridgeSupport.updateCollections(tx);

        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntriesWithoutHash().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().size());

        verify(bridgeEventLogger, never()).logReleaseBtcRequested(any(byte[].class), any(BtcTransaction.class), any(Coin.class));
    }

    @Test
    void callProcessFundsMigration_is_migrated_after_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = FederationTestUtils.getGenesisFederation(federationConstantsMainnet);

        FederationArgs newFederationArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            btcMainnetParams
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(newFederationArgs);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);

        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));
        when(federationStorageProviderMock.getOldFederation(any(), any())).thenReturn(oldFederation);
        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 18500 since we are using mainnet constants
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(18510, 1);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsMainnet)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withRskExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        Script outputScript = ScriptBuilder.createOutputScript(oldFederation.getAddress());
        UTXO utxo = UTXOBuilder.builder()
            .withOutputScript(outputScript)
            .withHeight(10)
            .build();
        sufficientUTXOsForMigration1.add(utxo);
        when(federationStorageProviderMock.getOldFederationBtcUTXOs())
            .thenReturn(sufficientUTXOsForMigration1);

        bridgeSupport.updateCollections(tx);

        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntriesWithoutHash().size());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().size());
        PegoutsWaitingForConfirmations.Entry entry = (PegoutsWaitingForConfirmations.Entry) provider.getPegoutsWaitingForConfirmations().getEntriesWithHash().toArray()[0];
        // Should have been logged with the migrated UTXO
        verify(bridgeEventLogger).logReleaseBtcRequested(
            tx.getHash().getBytes(),
            entry.getBtcTransaction(),
            Coin.COIN
        );
    }

    @Test
    void updateFederationCreationBlockHeights_before_rskip_186_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(false);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);

        FederationArgs newFederationArgs = new FederationArgs(
            FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            btcMainnetParams
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(newFederationArgs);

        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueue())
            .thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations())
            .thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);
        when(federationStorageProviderMock.getOldFederation(any(), any()))
            .thenReturn(oldFederation);
        when(federationStorageProviderMock.getNewFederation(any(), any()))
            .thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 18500 since we are using mainnet constants
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(18510, 1);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withRskExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        Script outputScript = ScriptBuilder.createOutputScript(oldFederation.getAddress());
        UTXO utxo = UTXOBuilder.builder()
            .withOutputScript(outputScript)
            .withHeight(10)
            .build();
        sufficientUTXOsForMigration1.add(utxo);

        when(federationStorageProviderMock.getOldFederationBtcUTXOs()).thenReturn(sufficientUTXOsForMigration1);
        when(federationStorageProviderMock.getNextFederationCreationBlockHeight(any())).thenReturn(Optional.of(1L));

        bridgeSupport.updateCollections(tx);

        verify(federationStorageProviderMock, never()).getNextFederationCreationBlockHeight(activations);
        verify(federationStorageProviderMock, never()).setActiveFederationCreationBlockHeight(any(Long.class));
        verify(federationStorageProviderMock, never()).clearNextFederationCreationBlockHeight();
    }

    @Test
    void updateFederationCreationBlockHeights_after_rskip_186_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP186)).thenReturn(true);

        BridgeEventLogger bridgeEventLogger = mock(BridgeEventLogger.class);

        Federation oldFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);

        FederationArgs newFederationArgs = new FederationArgs(FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            btcMainnetParams
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(newFederationArgs);

        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(Collections.emptySet()));

        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);
        when(federationStorageProviderMock.getOldFederation(any(), any())).thenReturn(oldFederation);
        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(newFederation);

        BlockGenerator blockGenerator = new BlockGenerator();
        // Old federation will be in migration age at block 18500 since we are using mainnet constants
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(18510, 1);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withRskExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeMainNetConstants)
            .withProvider(provider)
            .withEventLogger(bridgeEventLogger)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        List<UTXO> sufficientUTXOsForMigration1 = new ArrayList<>();
        Script outputScript = ScriptBuilder.createOutputScript(oldFederation.getAddress());
        UTXO utxo = UTXOBuilder.builder()
            .withOutputScript(outputScript)
            .withHeight(10)
            .build();
        sufficientUTXOsForMigration1.add(utxo);
        when(federationStorageProviderMock.getOldFederationBtcUTXOs())
            .thenReturn(sufficientUTXOsForMigration1);

        when(federationStorageProviderMock.getNextFederationCreationBlockHeight(any())).thenReturn(Optional.empty());

        bridgeSupport.updateCollections(tx);

        verify(federationStorageProviderMock).getNextFederationCreationBlockHeight(any());
        verify(federationStorageProviderMock, never()).setActiveFederationCreationBlockHeight(any(Long.class));
        verify(federationStorageProviderMock, never()).clearNextFederationCreationBlockHeight();

        when(federationStorageProviderMock.getNextFederationCreationBlockHeight(any())).thenReturn(Optional.of(1L));

        bridgeSupport.updateCollections(tx);

        verify(federationStorageProviderMock, times(2)).getNextFederationCreationBlockHeight(any());
        verify(federationStorageProviderMock).setActiveFederationCreationBlockHeight(1L);
        verify(federationStorageProviderMock).clearNextFederationCreationBlockHeight();
    }

    @Test
    void rskTxWaitingForSignature_uses_updateCollection_rskTxHash_before_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(false);

        BridgeConstants spiedBridgeConstants = spy(new BridgeRegTestConstants());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, 1L)); // no rsk tx hash
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(spiedBridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();
        bridgeSupport.updateCollections(tx);

        assertEquals(btcTx, provider.getPegoutsWaitingForSignatures().get(tx.getHash()));
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void rskTxWaitingForSignature_postRSKIP326_emitNewPegoutConfirmedEvent() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP326)).thenReturn(true);

        BridgeConstants spiedBridgeConstants = spy(new BridgeRegTestConstants());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        long rskBlockNumber = 1L;
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, rskBlockNumber)); // no rsk tx hash
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        BridgeEventLogger eventLogger = mock(BridgeEventLogger.class);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(spiedBridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withEventLogger(eventLogger)
            .withFederationSupport(federationSupport)
            .build();
        bridgeSupport.updateCollections(tx);

        verify(eventLogger).logPegoutConfirmed(btcTx.getHash(), rskBlockNumber);

    }

    @Test
    void rskTxWaitingForSignature_preRSKIP326_noPegoutConfirmedEventEmitted() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP326)).thenReturn(false);

        BridgeConstants spiedBridgeConstants = spy(new BridgeRegTestConstants());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        long rskBlockNumber = 1L;
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, rskBlockNumber)); // no rsk tx hash
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        BridgeEventLogger eventLogger = mock(BridgeEventLogger.class);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(spiedBridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withEventLogger(eventLogger)
            .withFederationSupport(federationSupport)
            .build();
        bridgeSupport.updateCollections(tx);

        verify(eventLogger, times(0)).logPegoutConfirmed(btcTx.getHash(), rskBlockNumber);

    }

    @Test
    void rskTxWaitingForSignature_postRSKIP326NoTxWithEnoughConfirmation_pegoutConfirmedEventNotEmitted() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP326)).thenReturn(true);

        BridgeConstants spiedBridgeConstants = spy(new BridgeRegTestConstants());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(new HashSet<>()));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        BridgeEventLogger eventLogger = mock(BridgeEventLogger.class);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(spiedBridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withEventLogger(eventLogger)
            .withFederationSupport(federationSupport)
            .build();
        bridgeSupport.updateCollections(tx);

        verify(eventLogger, times(0)).logPegoutConfirmed(btcTx.getHash(), 1L);
    }

    @Test
    void rskTxWaitingForSignature_uses_updateCollection_rskTxHash_after_rskip_146_activation_if_release_transaction_doesnt_have_rstTxHash() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeConstants spiedBridgeConstants = spy(new BridgeRegTestConstants());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, 1L)); // no rsk tx hash
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(spiedBridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();
        bridgeSupport.updateCollections(tx);

        assertEquals(btcTx, provider.getPegoutsWaitingForSignatures().get(tx.getHash()));
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void rskTxWaitingForSignature_uses_release_transaction_rstTxHash_after_rskip_146_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);

        BridgeConstants spiedBridgeConstants = spy(new BridgeRegTestConstants());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        Keccak256 rskTxHash = Keccak256.ZERO_HASH;
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, 1L, rskTxHash)); // HAS rsk tx hash
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(spiedBridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();
        bridgeSupport.updateCollections(tx);

        assertEquals(btcTx, provider.getPegoutsWaitingForSignatures().get(rskTxHash));
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test
    void rskTxWaitingForSignature_uses_updateCollection_rskTxHash_after_rskip_176_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BridgeConstants spiedBridgeConstants = spy(new BridgeRegTestConstants());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        Keccak256 rskTxHash = Keccak256.ZERO_HASH;
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, 1L, rskTxHash)); // HAS rsk tx hash
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(spiedBridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();
        bridgeSupport.updateCollections(tx);

        assertEquals(btcTx, provider.getPegoutsWaitingForSignatures().get(tx.getHash()));
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    private static Stream<BridgeConstants> provideBridgeConstants() {
        return Stream.of(new BridgeRegTestConstants(), BridgeTestNetConstants.getInstance(), BridgeMainNetConstants.getInstance());
    }

    @Test
    void rskTxWaitingForSignature_fail_adding_an_already_existing_key_after_rskip_375() throws IOException {
        // Arrange
        BridgeConstants bridgeConstants = new BridgeRegTestConstants();
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0);

        // Set state to make concur a pegout migration tx and pegout batch creation on the same updateCollection
        Federation oldFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);
        FederationArgs newFederationArgs = new FederationArgs(FederationTestUtils.getFederationMembers(1),
            Instant.EPOCH,
            5L,
            bridgeConstants.getBtcParams()
        );
        Federation newFederation = FederationFactory.buildStandardMultiSigFederation(
            newFederationArgs
        );

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);

        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(PegTestUtils.createReleaseRequestQueueEntries(3)));

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);

        SortedMap<Keccak256, BtcTransaction> pegoutWaitingForSignatures = new TreeMap<>();
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(pegoutWaitingForSignatures);

        // Federation change on going
        when(federationStorageProviderMock.getOldFederation(any(), any()))
            .thenReturn(oldFederation);
        when(federationStorageProviderMock.getNewFederation(any(), any()))
            .thenReturn(newFederation);

        // Utxos to migrate
        List<UTXO> utxos = BitcoinTestUtils.createUTXOs(10, oldFederation.getAddress());
        when(federationStorageProviderMock.getOldFederationBtcUTXOs())
            .thenReturn(utxos);

        List<UTXO> utxosNew = BitcoinTestUtils.createUTXOs(10, newFederation.getAddress());
        when(federationStorageProviderMock.getNewFederationBtcUTXOs(any(), any()))
            .thenReturn(utxosNew);

        // Advance blockchain to migration phase
        BlockGenerator blockGenerator = new BlockGenerator();
        org.ethereum.core.Block rskCurrentBlock = blockGenerator.createBlock(180, 1);

        federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeConstants.getFederationConstants())
            .withFederationStorageProvider(federationStorageProviderMock)
            .withRskExecutionBlock(rskCurrentBlock)
            .withActivations(fingerrootActivations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(fingerrootActivations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();
        bridgeSupport.updateCollections(tx);

        // Assert two transactions are added to pegoutsWaitingForConfirmations, one pegout batch and one migration tx
        assertEquals(2, pegoutsWaitingForConfirmations.getEntries().size());

        // Get new fed wallet to identify the migration tx
        Wallet newFedWallet = BridgeUtils.getFederationNoSpendWallet(
            new Context(bridgeConstants.getBtcParams()),
            newFederation,
            false,
            null
        );

        PegoutsWaitingForConfirmations.Entry migrationTx = null;
        PegoutsWaitingForConfirmations.Entry pegoutBatchTx = null;

        // If all outputs are sent to the active fed then it's the migration tx; if not, it's the peg-out batch
        for (PegoutsWaitingForConfirmations.Entry entry : pegoutsWaitingForConfirmations.getEntries()) {
            List<TransactionOutput> walletOutputs = entry.getBtcTransaction().getWalletOutputs(newFedWallet);
            if (walletOutputs.size() == entry.getBtcTransaction().getOutputs().size()) {
                migrationTx = entry;
            } else {
                pegoutBatchTx = entry;
            }
        }

        // Assert the two added pegouts have the same pegoutCreationRskTxHash
        assertEquals(migrationTx.getPegoutCreationRskTxHash(), pegoutBatchTx.getPegoutCreationRskTxHash());

        // Assert no pegouts were moved to pegoutsWaitingForSignatures
        assertEquals(0, pegoutWaitingForSignatures.size());

        // Advance blockchain to the height where both pegouts have enough confirmations to be moved to waitingForSignature
        rskCurrentBlock = blockGenerator.createBlock(184, 1);
        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(fingerrootActivations)
            .withFeePerKbSupport(feePerKbSupport)
            .build();
        bridgeSupport.updateCollections(tx);

        // Get the transaction that was confirmed and the one that stayed unconfirmed
        PegoutsWaitingForConfirmations.Entry unconfirmedEntry = pegoutsWaitingForConfirmations.getEntries().iterator().next();
        BtcTransaction firstTransactionConfirmed = pegoutWaitingForSignatures.get(pegoutWaitingForSignatures.firstKey());

        // Since only one pegout per updateCollection call is move to pegoutsWaitingForSignatures
        // assert one of the two pegout get moved to pegoutsWaitingForSignatures and one is left on pegoutsWaitingForConfirmation
        assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());
        assertEquals(1, pegoutWaitingForSignatures.size());

        /*
            Advance blockchain one block so the bridge now will attempt to move the pegoutBatchTx to pegoutsWaitingForSignatures
            but will fail due to there's already an entry using that pegoutCreationRskTx as key. It's not allowed to
            overwrite pegoutsWaitingForSignatures entries.
         */
        rskCurrentBlock = blockGenerator.createBlock(185, 1);
        BridgeSupport bridgeSupportForFailingTx = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(fingerrootActivations)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        Transaction throwsExceptionTx = Transaction
            .builder()
            .nonce(NONCE)
            .gasPrice(GAS_PRICE)
            .gasLimit(GAS_LIMIT)
            .destination(Hex.decode(TO_ADDRESS))
            .data(Hex.decode(DATA))
            .chainId(Constants.REGTEST_CHAIN_ID)
            .value(DUST_AMOUNT)
            .build();
        throwsExceptionTx.sign(senderKey.getPrivKeyBytes());

        assertThrows(IllegalStateException.class, () -> bridgeSupportForFailingTx.updateCollections(throwsExceptionTx));

        // assert both collections still without any change
        assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());
        assertTrue(pegoutsWaitingForConfirmations.getEntries().contains(unconfirmedEntry));
        assertEquals(1, pegoutWaitingForSignatures.size());
        assertEquals(firstTransactionConfirmed, pegoutWaitingForSignatures.get(pegoutWaitingForSignatures.firstKey()));

        // Now we remove the confirmed transaction from pegoutsWaitingForSignatures pretending it was signed,
        // to assert that in the next updateCollections call the unconfirmed transaction will be confirmed
        pegoutWaitingForSignatures.remove(pegoutWaitingForSignatures.firstKey());

        rskCurrentBlock = blockGenerator.createBlock(186, 1);
        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(rskCurrentBlock)
            .withActivations(fingerrootActivations)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        bridgeSupport.updateCollections(tx);

        assertEquals(0, pegoutsWaitingForConfirmations.getEntries().size());
        assertEquals(1, pegoutWaitingForSignatures.size());
        assertEquals(unconfirmedEntry.getBtcTransaction(), pegoutWaitingForSignatures.get(pegoutWaitingForSignatures.firstKey()));
    }

    @ParameterizedTest
    @MethodSource("provideBridgeConstants")
    void rskTxWaitingForSignature_uses_pegoutCreation_rskTxHash_after_rskip_375_activation(BridgeConstants bridgeConstants) throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP375)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        Keccak256 pegoutCreationRskTxHash = Keccak256.ZERO_HASH;
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, 1L, pegoutCreationRskTxHash));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(new TreeMap<>());

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L + bridgeConstants.getRsk2BtcMinimumAcceptableConfirmations());

        federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeConstants.getFederationConstants())
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();
        bridgeSupport.updateCollections(tx);

        assertNull(provider.getPegoutsWaitingForSignatures().get(tx.getHash()));
        assertEquals(btcTx, provider.getPegoutsWaitingForSignatures().get(pegoutCreationRskTxHash));
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @ParameterizedTest
    @MethodSource("provideBridgeConstants")
    void rskTxWaitingForSignature_override_entry_is_allowed_before_rskip_375_activation(BridgeConstants bridgeConstants) throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP375)).thenReturn(false);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTxNewEntryValue = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        Keccak256 pegoutCreationRskTxHash = Keccak256.ZERO_HASH;
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTxNewEntryValue, 1L, pegoutCreationRskTxHash));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L + bridgeConstants.getRsk2BtcMinimumAcceptableConfirmations());

        federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeConstants.getFederationConstants())
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();

        TreeMap<Keccak256, BtcTransaction> txsWaitingForSignatures = new TreeMap<>();
        BtcTransaction existingBtcTxEntryValue = mock(BtcTransaction.class);
        txsWaitingForSignatures.put(tx.getHash(), existingBtcTxEntryValue);
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(txsWaitingForSignatures);

        bridgeSupport.updateCollections(tx);

        BtcTransaction updatedBtcTxEntryValue = provider.getPegoutsWaitingForSignatures().get(tx.getHash());

        assertEquals(btcTxNewEntryValue, updatedBtcTxEntryValue);
        assertNotEquals(existingBtcTxEntryValue, updatedBtcTxEntryValue);
        assertNull(provider.getPegoutsWaitingForSignatures().get(pegoutCreationRskTxHash));
        assertEquals(1, provider.getPegoutsWaitingForSignatures().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
    }

    @Test()
    void rskTxWaitingForSignature_override_entry_attempt_after_rskip_375_activation() throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP146)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP375)).thenReturn(true);

        BridgeConstants spiedBridgeConstants = spy(new BridgeRegTestConstants());
        doReturn(1).when(spiedBridgeConstants).getRsk2BtcMinimumAcceptableConfirmations();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcTransaction btcTx = mock(BtcTransaction.class);
        Set<PegoutsWaitingForConfirmations.Entry> pegoutsWaitingForConfirmations = new HashSet<>();
        Keccak256 pegoutCreationRskTxHash = Keccak256.ZERO_HASH;
        pegoutsWaitingForConfirmations.add(new PegoutsWaitingForConfirmations.Entry(btcTx, 1L, pegoutCreationRskTxHash));
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(new PegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations));
        when(provider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(Collections.emptyList()));

        TreeMap<Keccak256, BtcTransaction> txsWaitingForSignatures = new TreeMap<>();

        txsWaitingForSignatures.put(pegoutCreationRskTxHash, btcTx);
        when(provider.getPegoutsWaitingForSignatures()).thenReturn(txsWaitingForSignatures);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(2L);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(spiedBridgeConstants)
            .withProvider(provider)
            .withExecutionBlock(executionBlock)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .build();

        assertThrows(IllegalStateException.class, () -> bridgeSupport.updateCollections(tx));
    }

    @Test
    void when_registerBtcTransaction_sender_not_recognized_before_rskip170_no_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            tx1.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(BRIDGE_ADDR));
        assertEquals(0, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_sender_not_recognized_after_rskip170_lock() throws Exception {
        // Assert
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        RskAddress rskAddress = new RskAddress(key.getAddress());
        RskAddress rskDestinationAddress = new RskAddress(new byte[20]);
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.empty());

        PeginInstructionsProvider peginInstructionsProvider = getPeginInstructionsProviderForVersion1(rskDestinationAddress, Optional.empty());

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        // Act
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        // Assert
        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskDestinationAddress));
        assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance( BRIDGE_ADDR));
        assertEquals(1, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(amountToLock, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).get(0).getValue());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesLegacyType_beforeFork_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        // Whitelist the address
        LockWhitelist whitelist = whitelistStorageProvider.getLockWhitelist(activations, btcRegTestParams);
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(5)));

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddress, rskAddress);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withWhitelistSupport(whitelistSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        // Assert
        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        assertThat(whitelist.isWhitelisted(btcAddress), is(false));
        assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(BRIDGE_ADDR));
        assertEquals(1, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(amountToLock, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).get(0).getValue());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesLegacyType_afterFork_notWhitelisted_no_lock_and_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        // Don't whitelist the addresses
        LockWhitelist whitelist = whitelistStorageProvider.getLockWhitelist(activations, btcRegTestParams);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddress, rskAddress);

        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        assertThat(whitelist.isWhitelisted(btcAddress), is(false));
        assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(BRIDGE_ADDR));
        assertEquals(0, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        List<BtcTransaction> pegoutBtcTxs = provider.getPegoutsWaitingForConfirmations().getEntries()
            .stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .sorted(Comparator.comparing(BtcTransaction::getOutputSum))
            .toList();

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction pegoutBtcTx = pegoutBtcTxs.get(0);
        assertEquals(1, pegoutBtcTx.getOutputs().size());
        assertThat(amountToLock.subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        assertEquals(btcAddress, pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        assertEquals(1, pegoutBtcTx.getInputs().size());
        assertEquals(tx1.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesLegacyType_afterFork_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        //First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        //Whitelist the address
        LockWhitelist whitelist = whitelistStorageProvider.getLockWhitelist(genesisActivations, btcRegTestParams);
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(5)));

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddress, rskAddress);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withWhitelistSupport(whitelistSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(5, 0));

        assertThat(whitelist.isWhitelisted(btcAddress), is(false));
        assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(BRIDGE_ADDR));
        assertEquals(1, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(Coin.COIN.multiply(5), federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).get(0).getValue());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesSegCompatibilityType_beforeFork_no_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(BRIDGE_ADDR));
        assertEquals(0, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesSegCompatibilityType_afterFork_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        // Whitelist the address
        LockWhitelist whitelist = whitelistStorageProvider.getLockWhitelist(genesisActivations, btcRegTestParams);
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, amountToLock));

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress);


        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withWhitelistSupport(whitelistSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            tx1.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(Coin.valueOf(5, 0));

        assertThat(whitelist.isWhitelisted(btcAddress), is(false));
        assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        assertEquals(LIMIT_MONETARY_BASE.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(BRIDGE_ADDR));
        assertEquals(1, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(amountToLock, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).get(0).getValue());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesSegCompatibilityType_afterFork_notWhitelisted_no_lock_and_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(
            TxSenderAddressType.P2SHP2WPKH,
            btcAddress,
            rskAddress
        );

        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            tx1.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(BRIDGE_ADDR));
        assertEquals(0, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        List<BtcTransaction> pegoutBtcTxs = provider.getPegoutsWaitingForConfirmations().getEntries()
            .stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .sorted(Comparator.comparing(BtcTransaction::getOutputSum))
            .toList();

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction pegoutBtcTx = pegoutBtcTxs.get(0);
        assertEquals(1, pegoutBtcTx.getOutputs().size());
        assertThat(amountToLock.subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        assertEquals(btcAddress, pegoutBtcTx.getOutput(0).getAddressFromP2PKHScript(btcRegTestParams));
        assertEquals(1, pegoutBtcTx.getInputs().size());
        assertEquals(tx1.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesMultisigType_beforeFork_no_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(5), federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHMULTISIG, btcAddress, rskAddress);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(BRIDGE_ADDR));
        assertEquals(0, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesMultisigType_afterFork_no_lock_and_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHMULTISIG, btcAddress, null);

        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(BRIDGE_ADDR));
        assertEquals(0, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        List<BtcTransaction> pegoutBtcTxs = provider.getPegoutsWaitingForConfirmations().getEntries()
            .stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .toList();

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction pegoutBtcTx = pegoutBtcTxs.get(0);
        assertEquals(1, pegoutBtcTx.getOutputs().size());
        assertThat(amountToLock.subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        assertEquals(btcAddress, pegoutBtcTx.getOutput(0).getScriptPubKey().getToAddress(btcRegTestParams));
        assertEquals(1, pegoutBtcTx.getInputs().size());
        assertEquals(tx1.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesMultisigWithWitnessType_beforeFork_no_lock_and_no_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WSH, btcAddress, rskAddress);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;

        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmt.bitcoinSerialize());

        assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskAddress));
        assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(BRIDGE_ADDR));
        assertEquals(0, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void when_registerBtcTransaction_usesMultisigWithWitnessType_afterFork_no_lock_and_refund() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);

        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        Block executionBlock = mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        BtcECKey srcKey1 = new BtcECKey();
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        Coin amountToLock = Coin.COIN.multiply(5);

        // First transaction goes only to the first federation
        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey1));

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(
            TxSenderAddressType.P2SHP2WSH,
            btcAddress,
            null
        );

        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 30;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 35, height);

        bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            tx1.bitcoinSerialize(),
            height,
            pmt.bitcoinSerialize()
        );

        assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(BRIDGE_ADDR));
        assertEquals(0, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        List<BtcTransaction> pegoutBtcTxs = provider.getPegoutsWaitingForConfirmations().getEntries()
            .stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .toList();

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction pegoutBtcTx = pegoutBtcTxs.get(0);
        assertEquals(1, pegoutBtcTx.getOutputs().size());
        assertThat(amountToLock.subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        assertEquals(btcAddress, pegoutBtcTx.getOutput(0).getScriptPubKey().getToAddress(btcRegTestParams));
        assertEquals(1, pegoutBtcTx.getInputs().size());
        assertEquals(tx1.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void registerBtcTransaction_rejects_tx_with_witness_before_rskip_143_activation() throws BlockStoreException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);
        tx1.addOutput(Coin.COIN, Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            Sha256Hash.ZERO_HASH,
            1,
            1,
            1,
            new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes2, 1);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(block);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withRepository(repository)
            .withBtcBlockStoreFactory(mockFactory)
            .build();

        assertThrows(VerificationException.EmptyInputsOrOutputs.class, () -> bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize()));

        // When we send a segwit tx when the fork is not enabled, the tx is rejected because it does not have the
        // expected input format, therefore this method is never reached
        verify(btcBlockStore, never()).getStoredBlockAtMainChainHeight(height);
    }

    @Test
    void registerBtcTransaction_accepts_lock_tx_with_witness_after_rskip_143_activation() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);
        repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        Coin amountToLock = Coin.COIN.multiply(10);

        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes2, 1);
        List<Sha256Hash> hashlist2 = new ArrayList<>();
        Sha256Hash witnessMerkleRoot = pmtWithWitness.getTxnHashAndMerkleRoot(hashlist2);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeConstantsRegtest.getBtcParams(),
            activations
        );
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        // Whitelist the address
        LockWhitelist whitelist = whitelistStorageProvider.getLockWhitelist(genesisActivations, btcRegTestParams);
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(10)));

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress))
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withWhitelistSupport(whitelistSupport)
            .build();

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        assertEquals(1, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(amountToLock, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).get(0).getValue());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash(false)).isPresent());
    }

    @Test
    void registerBtcTransaction_rejects_tx_with_witness_and_unregistered_coinbase_after_rskip_143_activation() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        Coin amountToLock = Coin.COIN.multiply(10);

        tx1.addOutput(amountToLock, Address.fromBase58((new BridgeRegTestConstants()).getBtcParams(), "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes2, 1);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations));

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress),
            new PeginInstructionsProvider(),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(tx1.getHash(true), height);
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(Sha256Hash.class), anyLong());
    }

    @Test
    void registerBtcTransaction_rejects_tx_with_witness_and_unqual_witness_root_after_rskip_143_activation() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        Coin amountToLock = Coin.COIN.multiply(10);

        tx1.addOutput(amountToLock, Address.fromBase58(btcRegTestParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmtWithWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes2, 1);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            bridgeConstantsRegtest.getBtcParams(),
            activations)
        );

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            getBtcLockSenderProvider(TxSenderAddressType.P2SHP2WPKH, btcAddress, rskAddress),
            new PeginInstructionsProvider(),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithWitness.bitcoinSerialize());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(tx1.getHash(true), height);
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(Sha256Hash.class), anyLong());
    }

    @Test
    void registerBtcTransaction_rejects_tx_without_witness_unequal_roots_after_rskip_143() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        Coin amountToLock = Coin.COIN.multiply(10);

        tx1.addOutput(amountToLock, Address.fromBase58(btcRegTestParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            bridgeConstantsRegtest.getBtcParams(),
            activations)
        );

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddress, rskAddress))
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .build();

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(tx1.getHash(), height);
        verify(provider, never()).setHeightBtcTxhashAlreadyProcessed(any(Sha256Hash.class), anyLong());
    }

    @Test
    void registerBtcTransaction_accepts_lock_tx_without_witness_after_rskip_143_activation() throws BlockStoreException, IOException, BridgeIllegalArgumentException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);
        repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());

        Coin amountToLock = Coin.COIN.multiply(10);

        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);

        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        // Whitelist the address
        LockWhitelist whitelist = whitelistStorageProvider.getLockWhitelist(genesisActivations, btcRegTestParams);
        whitelist.put(btcAddress, new OneOffWhiteListEntry(btcAddress, Coin.COIN.multiply(10)));

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(getBtcLockSenderProvider(TxSenderAddressType.P2PKH, btcAddress, rskAddress))
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withWhitelistSupport(whitelistSupport)
            .build();

        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskAddress));
        assertEquals(1, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(amountToLock, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).get(0).getValue());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash(true)).isPresent());
    }

    @Test
    void registerBtcTransaction_accepts_lock_tx_version1_after_rskip_170_activation()
        throws BlockStoreException, IOException, PeginInstructionsException, BridgeIllegalArgumentException {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);
        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddressFromBtcLockSender = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskDerivedAddress = new RskAddress(key.getAddress());
        RskAddress rskDestinationAddress = new RskAddress(new byte[20]);

        Coin amountToLock = Coin.COIN.multiply(10);

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(
            TxSenderAddressType.P2PKH,
            btcAddressFromBtcLockSender,
            rskDerivedAddress
        );
        PeginInstructionsProvider peginInstructionsProvider = getPeginInstructionsProviderForVersion1(
            rskDestinationAddress,
            Optional.empty()
        );

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .build();

        // Act
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());

        // Assert
        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskDerivedAddress));
        assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskDestinationAddress));
        assertEquals(1, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(amountToLock, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).get(0).getValue());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash(true)).isPresent());
    }

    @Test
    void registerBtcTransaction_ignores_pegin_instructions_before_rskip_170_activation()
        throws BlockStoreException, IOException, PeginInstructionsException, BridgeIllegalArgumentException {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(false);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);
        repository = createRepository();

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddressFromBtcLockSender = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskDerivedAddress = new RskAddress(key.getAddress());
        RskAddress rskDestinationAddress = new RskAddress(new byte[20]);

        Coin amountToLock = Coin.COIN.multiply(10);

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeConstantsRegtest.getBtcParams(),
            activations
        );
        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        // Whitelist the address
        LockWhitelist whitelist = whitelistStorageProvider.getLockWhitelist(genesisActivations, btcRegTestParams);
        whitelist.put(btcAddressFromBtcLockSender, new OneOffWhiteListEntry(btcAddressFromBtcLockSender, amountToLock));

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(
            TxSenderAddressType.P2PKH,
            btcAddressFromBtcLockSender,
            rskDerivedAddress
        );
        PeginInstructionsProvider peginInstructionsProvider = getPeginInstructionsProviderForVersion1(
            rskDestinationAddress,
            Optional.empty()
        );

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withWhitelistSupport(whitelistSupport)
            .build();

        // Act
        bridgeSupport.registerBtcTransaction(mock(Transaction.class), tx1.bitcoinSerialize(), height, pmtWithoutWitness.bitcoinSerialize());

        // Assert
        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(amountToLock);

        assertEquals(co.rsk.core.Coin.ZERO, repository.getBalance(rskDestinationAddress));
        assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(rskDerivedAddress));
        assertEquals(1, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(amountToLock, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).get(0).getValue());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(0, provider.getPegoutsWaitingForConfirmations().getEntries().size());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash(true)).isPresent());
    }

    @Test
    void when_registerBtcTransaction_invalidPeginProtocolVersion_afterFork_no_lock_and_refund()
        throws BlockStoreException, IOException, PeginInstructionsException, BridgeIllegalArgumentException {
        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(true);

        Federation federation1 = PegTestUtils.createSimpleActiveFederation(bridgeConstantsRegtest);
        repository = createRepository();
        repository.addBalance(BRIDGE_ADDR, LIMIT_MONETARY_BASE);

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddressFromBtcLockSender = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        RskAddress rskDestinationAddress = new RskAddress(new byte[20]);

        Coin amountToLock = Coin.COIN.multiply(10);

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(amountToLock, federation1.getAddress());
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmtWithoutWitness = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmtWithoutWitness.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        co.rsk.bitcoinj.core.BtcBlock headBlock = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(2),
            Sha256Hash.of(new byte[]{1}),
            1,
            1,
            1,
            new ArrayList<>()
        );

        StoredBlock chainHead = new StoredBlock(headBlock, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeConstantsRegtest.getBtcParams(),
            activations
        );

        federationStorageProvider = createFederationStorageProvider(repository);
        federationStorageProvider.setNewFederation(federation1);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(
            TxSenderAddressType.P2PKH,
            btcAddressFromBtcLockSender,
            rskAddress
        );

        PeginInstructions peginInstructions = mock(PeginInstructions.class);
        when(peginInstructions.getProtocolVersion()).thenReturn(99);
        when(peginInstructions.getRskDestinationAddress()).thenReturn(rskDestinationAddress);
        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenReturn(Optional.of(peginInstructions));

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProvider)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        // Act
        bridgeSupport.registerBtcTransaction(
            mock(Transaction.class),
            tx1.bitcoinSerialize(),
            height,
            pmtWithoutWitness.bitcoinSerialize()
        );

        // Assert
        assertEquals(LIMIT_MONETARY_BASE, repository.getBalance(BRIDGE_ADDR));
        assertEquals(0, federationStorageProvider.getNewFederationBtcUTXOs(btcRegTestParams, activations).size());
        assertEquals(0, provider.getReleaseRequestQueue().getEntries().size());
        assertEquals(1, provider.getPegoutsWaitingForConfirmations().getEntries().size());

        List<BtcTransaction> pegoutBtcTxs = provider.getPegoutsWaitingForConfirmations().getEntries()
            .stream()
            .map(PegoutsWaitingForConfirmations.Entry::getBtcTransaction)
            .toList();

        // First release tx should correspond to the 5 BTC lock tx
        BtcTransaction pegoutBtcTx = pegoutBtcTxs.get(0);
        assertEquals(1, pegoutBtcTx.getOutputs().size());
        assertThat(amountToLock.subtract(pegoutBtcTx.getOutput(0).getValue()), is(lessThanOrEqualTo(Coin.MILLICOIN)));
        assertEquals(btcAddressFromBtcLockSender, pegoutBtcTx.getOutput(0).getScriptPubKey().getToAddress(btcRegTestParams));
        assertEquals(1, pegoutBtcTx.getInputs().size());
        assertEquals(tx1.getHash(), pegoutBtcTx.getInput(0).getOutpoint().getHash());
        assertEquals(0, pegoutBtcTx.getInput(0).getOutpoint().getIndex());
        assertTrue(provider.getPegoutsWaitingForSignatures().isEmpty());
        assertTrue(provider.getHeightIfBtcTxhashIsAlreadyProcessed(tx1.getHash()).isPresent());
    }

    @Test
    void isBlockMerkleRootValid_equal_merkle_roots() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            mock(BridgeStorageProvider.class),
            mock(Repository.class),
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        Sha256Hash merkleRoot = BitcoinTestUtils.createHash(1);
        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(merkleRoot);
        assertTrue(bridgeSupport.isBlockMerkleRootValid(merkleRoot, btcBlock));
    }

    @Test
    void isBlockMerkleRootValid_unequal_merkle_roots_before_rskip_143() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            mock(BridgeStorageProvider.class),
            mock(Repository.class),
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        Sha256Hash merkleRoot = BitcoinTestUtils.createHash(1);
        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        assertFalse(bridgeSupport.isBlockMerkleRootValid(merkleRoot, btcBlock));
    }

    @Test
    void isBlockMerkleRootValid_coinbase_information_null_after_rskip_143() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        when(provider.getCoinbaseInformation(Sha256Hash.ZERO_HASH)).thenReturn(null);

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            mock(Repository.class),
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlock.getHash()).thenReturn(Sha256Hash.ZERO_HASH);

        assertFalse(bridgeSupport.isBlockMerkleRootValid(BitcoinTestUtils.createHash(1), btcBlock));
    }

    @Test
    void isBlockMerkleRootValid_coinbase_information_not_null_and_unequal_mroots_after_rskip_143() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(BitcoinTestUtils.createHash(1));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getCoinbaseInformation(Sha256Hash.ZERO_HASH)).thenReturn(coinbaseInformation);

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            mock(Repository.class),
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlock.getHash()).thenReturn(Sha256Hash.ZERO_HASH);

        assertFalse(bridgeSupport.isBlockMerkleRootValid(BitcoinTestUtils.createHash(2), btcBlock));
    }

    @Test
    void isBlockMerkleRootValid_coinbase_information_not_null_and_equal_mroots_after_rskip_143() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        Sha256Hash merkleRoot = BitcoinTestUtils.createHash(1);
        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(merkleRoot);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getCoinbaseInformation(Sha256Hash.ZERO_HASH)).thenReturn(coinbaseInformation);

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            mock(Repository.class),
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlock.getHash()).thenReturn(Sha256Hash.ZERO_HASH);

        assertTrue(bridgeSupport.isBlockMerkleRootValid(merkleRoot, btcBlock));
    }

    @Test
    void getBtcTransactionConfirmations_rejects_tx_with_witness_before_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(10), Address.fromBase58(btcRegTestParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.get(registerHeader.getHash())).thenReturn(block);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .build();

        MerkleBranch merkleBranch = mock(MerkleBranch.class);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(
            tx1.getHash(true),
            registerHeader.getHash(),
            merkleBranch
        );
        assertEquals(-5, confirmations);
    }

    @Test
    void getBtcTransactionConfirmations_accepts_tx_with_witness_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(10), Address.fromBase58(btcRegTestParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmt2 = new PartialMerkleTree(btcRegTestParams, bits, hashes2, 1);
        List<Sha256Hash> hashlist2 = new ArrayList<>();
        Sha256Hash witnessMerkleRoot = pmt2.getTxnHashAndMerkleRoot(hashlist2);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.get(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            bridgeConstantsRegtest.getBtcParams(),
            activations
        ));

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash(true))).thenReturn(witnessMerkleRoot);

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(witnessMerkleRoot);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(true), registerHeader.getHash(), merkleBranch);
        assertEquals(chainHead.getHeight() - block.getHeight() + 1, confirmations);
    }

    @Test
    void getBtcTransactionConfirmations_unregistered_coinbase_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(10), Address.fromBase58(btcRegTestParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmt2 = new PartialMerkleTree(btcRegTestParams, bits, hashes2, 1);
        List<Sha256Hash> hashlist2 = new ArrayList<>();
        Sha256Hash witnessMerkleRoot = pmt2.getTxnHashAndMerkleRoot(hashlist2);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.get(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);
        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            bridgeConstantsRegtest.getBtcParams(),
            activations)
        );

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash(true))).thenReturn(witnessMerkleRoot);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(true), registerHeader.getHash(), merkleBranch);
        assertEquals(-5, confirmations);
    }

    @Test
    void getBtcTransactionConfirmations_registered_coinbase_unequal_witnessroot_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);
        tx1.addOutput(Coin.COIN.multiply(10), Address.fromBase58(btcRegTestParams, "mvbnrCX3bg1cDRUu8pkecrvP6vQkSLDSou"));
        tx1.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));
        TransactionWitness txWit = new TransactionWitness(1);
        txWit.setPush(0, new byte[]{});
        tx1.setWitness(0, txWit);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        List<Sha256Hash> hashes2 = new ArrayList<>();
        hashes2.add(tx1.getHash(true));
        PartialMerkleTree pmt2 = new PartialMerkleTree(btcRegTestParams, bits, hashes2, 1);
        List<Sha256Hash> hashlist2 = new ArrayList<>();
        Sha256Hash witnessMerkleRoot = pmt2.getTxnHashAndMerkleRoot(hashlist2);

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.get(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(),
            activations));

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash(true))).thenReturn(witnessMerkleRoot);

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(registerHeader.getHash(), coinbaseInformation);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(true), registerHeader.getHash(), merkleBranch);
        assertEquals(-5, confirmations);
    }

    @Test
    void getBtcTransactionConfirmations_tx_without_witness_unequal_roots_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            Sha256Hash.ZERO_HASH,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.get(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            bridgeConstantsRegtest.getBtcParams(),
            activations)
        );

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash())).thenReturn(BitcoinTestUtils.createHash(5));

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        doReturn(coinbaseInformation).when(provider).getCoinbaseInformation(registerHeader.getHash());

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(), registerHeader.getHash(), merkleBranch);
        assertEquals(-5, confirmations);
    }

    @Test
    void getBtcTransactionConfirmations_accepts_tx_without_witness_after_rskip_143() throws Exception {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams);

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash blockMerkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            blockMerkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 50;
        StoredBlock block = new StoredBlock(registerHeader, new BigInteger("0"), height);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStore.get(registerHeader.getHash())).thenReturn(block);

        StoredBlock chainHead = new StoredBlock(registerHeader, new BigInteger("0"), 132);
        when(btcBlockStore.getChainHead()).thenReturn(chainHead);

        when(btcBlockStore.getStoredBlockAtMainChainHeight(block.getHeight())).thenReturn(block);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
            repository,
            bridgeConstantsRegtest.getBtcParams(),
            activations)
        );

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        MerkleBranch merkleBranch = mock(MerkleBranch.class);
        when(merkleBranch.reduceFrom(tx1.getHash())).thenReturn(blockMerkleRoot);

        BtcBlock btcBlock = mock(BtcBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(blockMerkleRoot);

        int confirmations = bridgeSupport.getBtcTransactionConfirmations(tx1.getHash(), registerHeader.getHash(), merkleBranch);
        assertEquals(chainHead.getHeight() - block.getHeight() + 1, confirmations);
    }

    @Test
    void when_RegisterBtcCoinbaseTransaction_wrong_witnessReservedValue_noSent() throws BlockStoreException, AddressFormatException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
            "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
            "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
            "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction txWithoutWitness = new BtcTransaction(btcRegTestParams, rawTx);

        byte[] witnessReservedValue = new byte[10];

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, mock(BtcBlock.class), 5, height);
        when(btcBlockStore.getFromCache(mock(Sha256Hash.class))).thenReturn(new StoredBlock(mock(BtcBlock.class), BigInteger.ZERO, 0));

        assertThrows(BridgeIllegalArgumentException.class, () -> bridgeSupport.registerBtcCoinbaseTransaction(
            txWithoutWitness.bitcoinSerialize(),
            mock(Sha256Hash.class),
            pmt.bitcoinSerialize(),
            mock(Sha256Hash.class),
            witnessReservedValue
        ));

        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    void when_RegisterBtcCoinbaseTransaction_MerkleTreeWrongFormat_noSent() throws BlockStoreException, AddressFormatException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
            "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
            "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
            "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcRegTestParams, rawTx);

        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, mock(BtcBlock.class), 5, height);
        when(btcBlockStore.getFromCache(mock(Sha256Hash.class))).thenReturn(new StoredBlock(mock(BtcBlock.class), BigInteger.ZERO, 0));

        assertThrows(BridgeIllegalArgumentException.class, () -> bridgeSupport.registerBtcCoinbaseTransaction(
            txWithoutWitness.bitcoinSerialize(),
            mock(Sha256Hash.class),
            new byte[]{6, 6, 6},
            mock(Sha256Hash.class),
            witnessReservedValue
        ));

        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    void when_RegisterBtcCoinbaseTransaction_HashNotInPmt_noSent() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
            "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
            "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
            "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcRegTestParams, rawTx);

        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));
        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(secondHashTx);
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);
        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(new StoredBlock(registerHeader, BigInteger.ZERO, 0));

        bridgeSupport.registerBtcCoinbaseTransaction(txWithoutWitness.bitcoinSerialize(), mock(Sha256Hash.class), pmt.bitcoinSerialize(), mock(Sha256Hash.class), witnessReservedValue);
        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    void when_RegisterBtcCoinbaseTransaction_notVerify_noSent() throws BlockStoreException, AddressFormatException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();

        BtcTransaction coinbaseTx = new BtcTransaction(btcRegTestParams);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(coinbaseTx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);
        Sha256Hash hash = registerHeader.getHash();
        when(btcBlockStore.get(hash)).thenReturn(new StoredBlock(registerHeader, BigInteger.ZERO, 0));

        byte[] coinbaseTxSerialized = coinbaseTx.bitcoinSerialize();
        byte[] pmtSerialized = pmt.bitcoinSerialize();
        byte[] bytes = Sha256Hash.ZERO_HASH.getBytes();
        assertThrows(VerificationException.class, () -> bridgeSupport.registerBtcCoinbaseTransaction(
            coinbaseTxSerialized,
            hash,
            pmtSerialized,
            mock(Sha256Hash.class),
            bytes
        ));

        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    void when_RegisterBtcCoinbaseTransaction_not_equal_merkle_root_noSent() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
            "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
            "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
            "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcRegTestParams, rawTx);
        txWithoutWitness.setWitness(0, null);

        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        BridgeSupport bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(txWithoutWitness.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            Sha256Hash.ZERO_HASH,
            1,
            1,
            1,
            new ArrayList<>()
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);

        BtcBlock btcBlock = mock(BtcBlock.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        when(btcBlock.getMerkleRoot()).thenReturn(Sha256Hash.ZERO_HASH);
        when(storedBlock.getHeader()).thenReturn(btcBlock);
        when(btcBlockStore.get(registerHeader.getHash())).thenReturn(storedBlock);

        bridgeSupport.registerBtcCoinbaseTransaction(
            txWithoutWitness.bitcoinSerialize(),
            registerHeader.getHash(),
            pmt.bitcoinSerialize(),
            mock(Sha256Hash.class),
            witnessReservedValue
        );
        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    void when_RegisterBtcCoinbaseTransaction_null_stored_block_noSent() throws BlockStoreException, AddressFormatException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
            "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
            "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
            "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcRegTestParams, rawTx);

        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));

        txWithoutWitness.setWitness(0, null);
        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(secondHashTx);
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 2);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);

        when(btcBlockStore.getFromCache(registerHeader.getHash())).thenReturn(null);

        assertThrows(BridgeIllegalArgumentException.class, () -> bridgeSupport.registerBtcCoinbaseTransaction(
            txWithoutWitness.bitcoinSerialize(),
            mock(Sha256Hash.class),
            pmt.bitcoinSerialize(),
            mock(Sha256Hash.class),
            witnessReservedValue
        ));

        verify(mock(BridgeStorageProvider.class), never()).setCoinbaseInformation(any(Sha256Hash.class), any(CoinbaseInformation.class));
    }

    @Test
    void registerBtcCoinbaseTransaction() throws BlockStoreException, AddressFormatException, VMException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();

        byte[] rawTx = Hex.decode("020000000001010000000000000000000000000000000000000000000000000000000000000000fff" +
            "fffff0502cc000101ffffffff029c070395000000002321036d6b5bc8c0e902f296b5bdf3dfd4b6f095d8d0987818a557e1766e" +
            "a25c664524ac0000000000000000266a24aa21a9edfeb3b9170ae765cc6586edd67229eaa8bc19f9674d64cb10ee8a205f4ccf0" +
            "bc60120000000000000000000000000000000000000000000000000000000000000000000000000");

        BtcTransaction tx1 = new BtcTransaction(btcRegTestParams, rawTx);
        BtcTransaction txWithoutWitness = new BtcTransaction(btcRegTestParams, rawTx);

        Sha256Hash secondHashTx = Sha256Hash.wrap(Hex.decode("e3d0840a0825fb7d880e5cb8306745352920a8c7e8a30fac882b275e26c6bb65"));
        Sha256Hash mRoot = MerkleTreeUtils.combineLeftRight(tx1.getHash(), secondHashTx);

        txWithoutWitness.setWitness(0, null);
        byte[] witnessReservedValue = tx1.getWitness(0).getPush(0);
        Sha256Hash witnessRoot = MerkleTreeUtils.combineLeftRight(Sha256Hash.ZERO_HASH, secondHashTx);
        byte[] witnessRootBytes = witnessRoot.getReversedBytes();
        byte[] wc = tx1.getOutputs().stream().filter(t -> t.getValue().getValue() == 0).toList().get(0).getScriptPubKey().getChunks().get(1).data;
        wc = Arrays.copyOfRange(wc, 4, 36);
        Sha256Hash witCom = Sha256Hash.wrap(wc);

        assertEquals(Sha256Hash.twiceOf(witnessRootBytes, witnessReservedValue), witCom);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstantsRegtest, provider, activations)).thenReturn(btcBlockStore);

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mockFactory,
            activations,
            signatureCache
        );

        byte[] bits = new byte[1];
        bits[0] = 0x3f;

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx1.getHash());
        hashes.add(secondHashTx);
        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 2);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        //Merkle root is from the original block
        assertEquals(merkleRoot, mRoot);

        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            btcRegTestParams,
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        //Leaving no confirmation blocks
        int height = 5;
        mockChainOfStoredBlocks(btcBlockStore, registerHeader, 5, height);
        when(btcBlockStore.get(registerHeader.getHash())).thenReturn(new StoredBlock(registerHeader, BigInteger.ZERO, 0));
        bridgeSupport.registerBtcCoinbaseTransaction(
            txWithoutWitness.bitcoinSerialize(),
            registerHeader.getHash(),
            pmt.bitcoinSerialize(),
            witnessRoot,
            witnessReservedValue
        );

        ArgumentCaptor<CoinbaseInformation> argumentCaptor = ArgumentCaptor.forClass(CoinbaseInformation.class);
        verify(provider).setCoinbaseInformation(eq(registerHeader.getHash()), argumentCaptor.capture());
        assertEquals(witnessRoot, argumentCaptor.getValue().getWitnessMerkleRoot());
    }

    @Test
    void hasBtcCoinbaseTransaction_before_rskip_143_activation() throws AddressFormatException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(Sha256Hash.ZERO_HASH, coinbaseInformation);
        assertFalse(bridgeSupport.hasBtcBlockCoinbaseTransactionInformation(Sha256Hash.ZERO_HASH));
    }

    @Test
    void hasBtcCoinbaseTransaction_after_rskip_143_activation() throws AddressFormatException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), activations);

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        CoinbaseInformation coinbaseInformation = new CoinbaseInformation(Sha256Hash.ZERO_HASH);
        provider.setCoinbaseInformation(Sha256Hash.ZERO_HASH, coinbaseInformation);
        assertTrue(bridgeSupport.hasBtcBlockCoinbaseTransactionInformation(Sha256Hash.ZERO_HASH));
    }

    @Test
    void hasBtcCoinbaseTransaction_fails_with_null_coinbase_information_after_rskip_143_activation() throws AddressFormatException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        repository = createRepository();
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        bridgeSupport = getBridgeSupport(
            bridgeConstantsRegtest,
            provider,
            repository,
            mock(BtcLockSenderProvider.class),
            mock(PeginInstructionsProvider.class),
            mock(Block.class),
            mock(BtcBlockStoreWithCache.Factory.class),
            activations,
            signatureCache
        );

        assertFalse(bridgeSupport.hasBtcBlockCoinbaseTransactionInformation(Sha256Hash.ZERO_HASH));
    }

    @Test
    void isAlreadyBtcTxHashProcessedHeight_true() throws IOException {
        repository = createRepository();
        BtcTransaction btcTransaction = new BtcTransaction(btcRegTestParams);
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), genesisActivations);

        provider.setHeightBtcTxhashAlreadyProcessed(btcTransaction.getHash(), 1L);
        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .build();

        assertTrue(bridgeSupport.isAlreadyBtcTxHashProcessed(btcTransaction.getHash()));
    }

    @Test
    void isAlreadyBtcTxHashProcessedHeight_false() throws IOException {
        BtcTransaction btcTransaction = new BtcTransaction(btcRegTestParams);
        bridgeSupport = bridgeSupportBuilder.withBridgeConstants(bridgeConstantsRegtest).build();

        assertFalse(bridgeSupport.isAlreadyBtcTxHashProcessed(btcTransaction.getHash()));
    }

    @Test
    void validationsForRegisterBtcTransaction_negative_height() throws BlockStoreException, BridgeIllegalArgumentException {
        BtcTransaction peginTx = new BtcTransaction(btcRegTestParams);
        repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(repository, bridgeConstantsRegtest.getBtcParams(), genesisActivations);
        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .build();

        byte[] data = Hex.decode("ab");

        assertFalse(bridgeSupport.validationsForRegisterBtcTransaction(peginTx.getHash(), -1, data, data));
    }

    @Test
    void validationsForRegisterBtcTransaction_insufficient_confirmations() throws BlockStoreException, BridgeIllegalArgumentException {
        BtcTransaction peginTx = new BtcTransaction(btcRegTestParams);
        btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeConstantsRegtest.getBtcParams());
        repository = createRepository();
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeConstantsRegtest.getBtcParams(),
            genesisActivations
        );
        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .build();

        byte[] data = Hex.decode("ab");

        assertFalse(bridgeSupport.validationsForRegisterBtcTransaction(peginTx.getHash(), 100, data, data));
    }

    @Test
    void validationsForRegisterBtcTransaction_invalid_pmt() throws BlockStoreException {
        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);

        String pmtSerializedEncoded = "030000000279e7c0da739df8a00f12c0bff55e5438f530aa5859ff9874258cd7bad3fe709746aff89" +
            "7e6a851faa80120d6ae99db30883699ac0428fc7192d6c3fec0ca64010d";
        byte[] pmtSerialized = Hex.decode(pmtSerializedEncoded);

        int btcTxHeight = 2;

        doReturn(btcRegTestParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withBtcBlockStoreFactory(mockFactory)
            .build();

        assertThrows(BridgeIllegalArgumentException.class, () -> bridgeSupport.validationsForRegisterBtcTransaction(
            btcTx.getHash(),
            btcTxHeight,
            pmtSerialized,
            btcTx.bitcoinSerialize()
        ));
    }

    @Test
    void validationsForRegisterBtcTransaction_hash_not_in_pmt() throws BlockStoreException, AddressFormatException, BridgeIllegalArgumentException {
        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(BitcoinTestUtils.createHash(0));

        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);

        int btcTxHeight = 2;

        doReturn(btcRegTestParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withBtcBlockStoreFactory(mockFactory)
            .build();

        assertFalse(bridgeSupport.validationsForRegisterBtcTransaction(btcTx.getHash(), 0, pmt.bitcoinSerialize(), btcTx.bitcoinSerialize()));
    }

    @Test
    void validationsForRegisterBtcTransaction_exception_in_getTxnHashAndMerkleRoot()
        throws BlockStoreException, AddressFormatException {
        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);

        PartialMerkleTree pmt = mock(PartialMerkleTree.class);
        when(pmt.getTxnHashAndMerkleRoot(anyList())).thenReturn(Sha256Hash.ZERO_HASH).thenThrow(VerificationException.class);

        int btcTxHeight = 2;

        doReturn(btcRegTestParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withBtcBlockStoreFactory(mockFactory)
            .build();

        assertThrows(BridgeIllegalArgumentException.class,
            () -> bridgeSupport.validationsForRegisterBtcTransaction(btcTx.getHash(), 0, pmt.bitcoinSerialize(), btcTx.bitcoinSerialize()));
    }


    @Test
    void validationsForRegisterBtcTransaction_tx_without_inputs_before_rskip_143() throws BlockStoreException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);

        int btcTxHeight = 2;

        doReturn(btcRegTestParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .build();

        Sha256Hash hash = btcTx.getHash();
        byte[] pmtSerialized = pmt.bitcoinSerialize();
        byte[] btcTxSerialized = btcTx.bitcoinSerialize();
        assertThrows(VerificationException.class, () -> bridgeSupport.validationsForRegisterBtcTransaction(
            hash,
            btcTxHeight,
            pmtSerialized,
            btcTxSerialized
        ));
    }

    @Test
    void validationsForRegisterBtcTransaction_tx_without_inputs_after_rskip_143() throws BlockStoreException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        BridgeConstants bridgeConstants = mock(BridgeConstants.class);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcRegTestParams, bits, hashes, 1);

        int btcTxHeight = 2;

        doReturn(btcRegTestParams).when(bridgeConstants).getBtcParams();
        doReturn(0).when(bridgeConstants).getBtc2RskMinimumAcceptableConfirmations();
        StoredBlock storedBlock = mock(StoredBlock.class);
        doReturn(btcTxHeight - 1).when(storedBlock).getHeight();
        BtcBlock btcBlock = mock(BtcBlock.class);
        doReturn(Sha256Hash.of(Hex.decode("aa"))).when(btcBlock).getHash();
        doReturn(btcBlock).when(storedBlock).getHeader();
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        doReturn(storedBlock).when(btcBlockStore).getChainHead();
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .build();

        Sha256Hash hash = btcTx.getHash();
        byte[] pmtSerialized = pmt.bitcoinSerialize();
        byte[] decode = Hex.decode("00000000000100");
        assertThrows(VerificationException.class, () -> bridgeSupport.validationsForRegisterBtcTransaction(
            hash,
            0,
            pmtSerialized,
            decode
        ));
    }

    @Test
    void validationsForRegisterBtcTransaction_invalid_block_merkle_root() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        BtcTransaction peginTx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        BtcECKey srcKey = new BtcECKey();
        peginTx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create tx and PMT. Also create a btc block, that has not relation with tx and PMT.
        // The tx will be rejected because merkle block doesn't match.
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(peginTx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstantsRegtest.getBtcParams(), bits, hashes, 1);
        co.rsk.bitcoinj.core.BtcBlock btcBlock =
            new co.rsk.bitcoinj.core.BtcBlock(bridgeConstantsRegtest.getBtcParams(), 1, BitcoinTestUtils.createHash(1), Sha256Hash.ZERO_HASH,
                1, 1, 1, new ArrayList<>());

        int height = 1;

        mockChainOfStoredBlocks(btcBlockStore, btcBlock, height + bridgeConstantsRegtest.getBtc2RskMinimumAcceptableConfirmations(), height);

        bridgeSupport = new BridgeSupport(
            bridgeConstantsRegtest,
            mockBridgeStorageProvider,
            mock(BridgeEventLogger.class),
            new BtcLockSenderProvider(),
            new PeginInstructionsProvider(),
            mock(Repository.class),
            mock(Block.class),
            mock(Context.class),
            feePerKbSupport,
            whitelistSupport,
            mock(FederationSupport.class),
            lockingCapSupport,
            unionBridgeSupport,
            btcBlockStoreFactory,
            mock(ActivationConfig.ForBlock.class),
            signatureCache
        );

        assertFalse(bridgeSupport.validationsForRegisterBtcTransaction(peginTx.getHash(), height, pmt.bitcoinSerialize(), peginTx.bitcoinSerialize()));
    }

    @Test
    void validationsForRegisterBtcTransaction_successful() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        BridgeStorageProvider mockBridgeStorageProvider = mock(BridgeStorageProvider.class);
        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);

        when(mockBridgeStorageProvider.getHeightIfBtcTxhashIsAlreadyProcessed(any(Sha256Hash.class))).thenReturn(Optional.empty());

        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(genesisFederation);

        btcBlockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        when(btcBlockStoreFactory.newInstance(any(Repository.class), any(), any(), any())).thenReturn(btcBlockStore);

        // Create transaction
        Coin lockValue = Coin.COIN;
        BtcTransaction peginTx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());
        peginTx.addOutput(lockValue, federationStorageProviderMock.getNewFederation(any(), any()).getAddress());
        BtcECKey srcKey = new BtcECKey();
        peginTx.addInput(BitcoinTestUtils.createHash(1), 0, ScriptBuilder.createInputScript(null, srcKey));

        // Create header and PMT. The block includes a valid merkleRoot calculated from the PMT.
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(peginTx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstantsRegtest.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock btcBlock = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstantsRegtest.getBtcParams(),
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        int height = 1;

        mockChainOfStoredBlocks(
            btcBlockStore,
            btcBlock,
            height + bridgeConstantsRegtest.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );
        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(mockBridgeStorageProvider)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withFeePerKbSupport(feePerKbSupport)
            .withWhitelistSupport(whitelistSupport)
            .withLockingCapSupport(lockingCapSupport)
            .withBtcBlockStoreFactory(btcBlockStoreFactory)
            .withSignatureCache(signatureCache)
            .build();

        assertTrue(bridgeSupport.validationsForRegisterBtcTransaction(
            peginTx.getHash(),
            height,
            pmt.bitcoinSerialize(),
            peginTx.bitcoinSerialize()
        ));
    }

    @Test
    void registerPegIn_version0_tx_no_lockable_by_invalid_sender() throws IOException, RegisterBtcTransactionException {
        assertRefundInRegisterPegInVersionLegacy(
            true,
            false,
            TxSenderAddressType.P2SHMULTISIG,
            ConsensusRule.RSKIP143
        );
    }

    @Test
    void registerPegIn_version0_tx_no_lockable_by_not_whitelisted_address() throws IOException, RegisterBtcTransactionException {
        assertRefundInRegisterPegInVersionLegacy(
            false,
            false,
            TxSenderAddressType.P2PKH,
            null
        );
    }

    @Test
    void registerPegIn_version0_tx_no_lockable_by_surpassing_locking_cap() throws IOException, RegisterBtcTransactionException {
        assertRefundInRegisterPegInVersionLegacy(
            true,
            true,
            TxSenderAddressType.P2PKH,
            ConsensusRule.RSKIP134
        );
    }

    @Test
    void registerPegIn_version1_tx_no_lockable_by_surpassing_locking_cap() throws IOException,
        RegisterBtcTransactionException, PeginInstructionsException {

        assertRefundInRegisterPegInVersion1(
            TxSenderAddressType.P2PKH,
            Optional.empty(),
            Arrays.asList(ConsensusRule.RSKIP134, ConsensusRule.RSKIP170)
        );
    }

    @Test
    void registerPegIn_version1_tx_no_lockable_by_surpassing_locking_cap_unknown_sender_with_refund_address()
        throws IOException, RegisterBtcTransactionException, PeginInstructionsException {

        BtcECKey key = new BtcECKey();
        Address btcRefundAddress = key.toAddress(btcRegTestParams);

        assertRefundInRegisterPegInVersion1(
            TxSenderAddressType.UNKNOWN,
            Optional.of(btcRefundAddress),
            Arrays.asList(ConsensusRule.RSKIP134, ConsensusRule.RSKIP170)
        );
    }

    @Test
    void registerPegIn_version1_tx_no_lockable_by_surpassing_locking_cap_unknown_sender_without_refund_address()
        throws IOException, RegisterBtcTransactionException, PeginInstructionsException {

        assertRefundInRegisterPegInVersion1(
            TxSenderAddressType.UNKNOWN,
            Optional.empty(),
            Arrays.asList(ConsensusRule.RSKIP134, ConsensusRule.RSKIP170)
        );
    }

    @Test
    void registerPegIn_noPeginInstructions() {
        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withFederationSupport(federationSupport)
            .build();

        BtcTransaction btcTx = mock(BtcTransaction.class);
        when(btcTx.getValueSentToMe(any())).thenReturn(Coin.valueOf(1));

        assertThrows(RegisterBtcTransactionException.class, () -> bridgeSupport.registerPegIn(
            btcTx,
            PegTestUtils.createHash3(1),
            0
        ));
    }

    @Test
    void registerPegIn_errorParsingPeginInstructions_beforeRskip170_dontRefundSender() throws IOException {

        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP170)).thenReturn(false);

        repository = createRepository();
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);
        Address federationAddress = genesisFederation.getAddress();

        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        btcTx.addOutput(Coin.COIN.multiply(10), federationAddress);
        btcTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(btcTx)).thenReturn(Optional.empty());

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .build();

        // Act
        assertThrows(RegisterBtcTransactionException.class, () -> bridgeSupport.registerPegIn(
            btcTx,
            PegTestUtils.createHash3(1),
            0
        ));
        assertEquals(0, pegoutsWaitingForConfirmations.getEntries().size());
    }

    @Test
    void registerPegIn_errorParsingPeginInstructions_afterRskip170_refundSender() throws IOException, PeginInstructionsException {
        // Arrange
        ActivationConfig.ForBlock irisActivations = ActivationConfigsForTest.iris300().forBlock(0L);
        repository = createRepository();

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Address btcSenderAddress = srcKey1.toAddress(btcRegTestParams);
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);
        Address federationAddress = genesisFederation.getAddress();

        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        btcTx.addOutput(Coin.COIN.multiply(10), federationAddress);
        btcTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(
            TxSenderAddressType.P2PKH,
            btcSenderAddress,
            rskAddress
        );

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(btcTx)).thenThrow(PeginInstructionsException.class);

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withActivations(irisActivations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withActivations(irisActivations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        // Act
        assertThrows(RegisterBtcTransactionException.class, () -> bridgeSupport.registerPegIn(btcTx, PegTestUtils.createHash3(1), 0));

        // Assert
        assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());

        // Check rejection tx input was created from btc tx and sent to the btc refund address indicated by the user
        boolean successfulRejection = false;
        for (PegoutsWaitingForConfirmations.Entry e : pegoutsWaitingForConfirmations.getEntries()) {
            BtcTransaction refundTx = e.getBtcTransaction();
            if (refundTx.getInput(0).getOutpoint().getHash() == btcTx.getHash() &&
                refundTx.getOutput(0).getScriptPubKey().getToAddress(btcRegTestParams).equals(btcSenderAddress)) {
                successfulRejection = true;
                break;
            }
        }

        assertTrue(successfulRejection);
    }

    @Test
    void receiveHeader_time_not_present_in_storage() throws IOException, BlockStoreException {
        repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
            bridgeConstantsRegtest.getBtcParams(),
                allActivations
            )
        );

        bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            genesisActivations
        );

        StoredBlock storedBlock2 = mock(StoredBlock.class);
        when(storedBlock.build(btcBlock2)).thenReturn(storedBlock2);

        bridgeSupport.receiveHeader(btcBlock2);

        verify(btcBlockStore).put(storedBlock2);
        verify(provider).getReceiveHeadersLastTimestamp();
        verify(provider).setReceiveHeadersLastTimestamp(anyLong());
    }

    @Test
    void receiveHeader_time_exceed_X() throws IOException, BlockStoreException {
        repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        Block executionBlockMock = mock(Block.class);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
            bridgeConstantsRegtest.getBtcParams(),
                allActivations
            )
        );

        bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            executionBlockMock,
            allActivations
        );

        long timeStampOld = executionBlockMock.getTimestamp() - (bridgeConstantsRegtest.getMinSecondsBetweenCallsToReceiveHeader() * 2L);
        doReturn(Optional.of(timeStampOld)).when(provider).getReceiveHeadersLastTimestamp();

        StoredBlock storedBlock2 = mock(StoredBlock.class);
        when(storedBlock.build(btcBlock2)).thenReturn(storedBlock2);

        bridgeSupport.receiveHeader(btcBlock2);

        verify(btcBlockStore).put(storedBlock2);
        verify(provider).setReceiveHeadersLastTimestamp(anyLong());
    }

    @Test
    void receiveHeader_time_less_than_X() throws IOException, BlockStoreException {
        repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        Block executionBlockMock = mock(Block.class);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
            bridgeConstantsRegtest.getBtcParams(),
                allActivations
            )
        );

        bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            executionBlockMock,
            allActivations
        );

        long timeStampOld = executionBlockMock.getTimestamp() - (bridgeConstantsRegtest.getMinSecondsBetweenCallsToReceiveHeader() / 2L);
        doReturn(Optional.of(timeStampOld)).when(provider).getReceiveHeadersLastTimestamp();

        int result = bridgeSupport.receiveHeader(btcBlock2);

        StoredBlock storedBlock2 = storedBlock.build(btcBlock2);

        verify(btcBlockStore, never()).put(storedBlock2);
        verify(provider, never()).setReceiveHeadersLastTimestamp(anyLong());
        assertEquals(-1, result);
    }

    @Test
    void receiveHeader_unexpected_exception() throws IOException, BlockStoreException {
        repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.of(new byte[]{}));

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
            bridgeConstantsRegtest.getBtcParams(),
                allActivations
            )
        );

        bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            allActivations
        );

        int result = bridgeSupport.receiveHeader(btcBlock2);

        verify(btcBlockStore, never()).put(storedBlock);
        verify(provider).getReceiveHeadersLastTimestamp();
        verify(provider, never()).setReceiveHeadersLastTimestamp(anyLong());
        assertEquals(-99, result);
    }

    @Test
    void receiveHeader_previous_block_not_in_storage() throws IOException, BlockStoreException {
        repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
            bridgeConstantsRegtest.getBtcParams(),
                allActivations
            )
        );

        bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            allActivations
        );

        when(btcBlockStore.get(any())).thenReturn(null);
        int result = bridgeSupport.receiveHeader(btcBlock2);

        StoredBlock storedBlock2 = storedBlock.build(btcBlock2);

        // Calls put when is adding the block header. (Saves his storedBlock)
        verify(btcBlockStore, never()).put(storedBlock2);
        verify(provider, never()).setReceiveHeadersLastTimestamp(anyLong());
        assertEquals(-3, result);
    }

    @Test
    void receiveHeader_block_too_old() throws IOException, BlockStoreException {
        repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        when(btcBlock2.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
            bridgeConstantsRegtest.getBtcParams(),
                allActivations
            )
        );

        bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock2,
            btcBlockStore,
            provider,
            storedBlock,
            allActivations
        );

        when(storedBlock.getHeight()).thenReturn(10, 5000, 10);

        int result = bridgeSupport.receiveHeader(btcBlock2);

        StoredBlock storedBlock2 = storedBlock.build(btcBlock2);

        verify(btcBlockStore, never()).put(storedBlock2);
        verify(provider, never()).setReceiveHeadersLastTimestamp(anyLong());
        assertEquals(-2, result);
    }

    @Test
    void receiveHeader_block_exist_in_storage() throws IOException, BlockStoreException {
        repository = mock(Repository.class);
        StoredBlock storedBlock = mock(StoredBlock.class);
        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);

        BtcBlock btcBlock = mock(BtcBlock.class);
        Sha256Hash btcBlockHash = BitcoinTestUtils.createHash(1);
        when(btcBlock.getHash()).thenReturn(btcBlockHash);
        when(btcBlockStore.get(btcBlockHash)).thenReturn(mock(StoredBlock.class));

        BridgeStorageProvider provider = spy(new BridgeStorageProvider(
                repository,
            bridgeConstantsRegtest.getBtcParams(),
                allActivations
            )
        );

        bridgeSupport = getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock,
            btcBlockStore,
            provider,
            storedBlock,
            allActivations
        );

        int result = bridgeSupport.receiveHeader(btcBlock);

        // Calls put when is adding the block header. (Saves his storedBlock)
        verify(btcBlockStore, never()).put(any(StoredBlock.class));
        verify(provider, never()).setReceiveHeadersLastTimestamp(anyLong());
        assertEquals(-4, result);
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("test the methods involved are keeping testnet consensus")
    class BuildBlockThatKeepsTestnetConsensusTests {
        BridgeConstants bridgeTestnetConstants = BridgeTestNetConstants.getInstance();
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory;
        BtcBlockStoreWithCache btcBlockStoreWithCache;

        BridgeSupport bridgeSupport;
        FederationSupport federationSupport = mock(FederationSupport.class);
        Block rskExecutionBlock = mock(Block.class);
        Sha256Hash btcBlockHash = Sha256Hash.wrap("00000000e8e7b540df01a7067e020fd7e2026bf86289def2283a35120c1af379");

        @BeforeEach
        void setUp() {
            long rskBlockNumber = 5_148_285;
            when(rskExecutionBlock.getNumber()).thenReturn(rskBlockNumber);

            repository = createRepository();
            bridgeStorageProvider = new BridgeStorageProvider(
                repository,
                bridgeTestnetConstants.getBtcParams(),
                allActivations
            );

            btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeTestnetConstants.getBtcParams(), 100, 100);
            btcBlockStoreWithCache = btcBlockStoreFactory.newInstance(repository, bridgeTestnetConstants, bridgeStorageProvider, allActivations);

            when(federationSupport.getActiveFederation()).thenReturn(activeFederation);

            bridgeSupport = BridgeSupportBuilder.builder()
                .withBridgeConstants(bridgeTestnetConstants)
                .withProvider(bridgeStorageProvider)
                .withRepository(repository)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withExecutionBlock(rskExecutionBlock)
                .withActivations(allActivations)
                .withFederationSupport(federationSupport)
                .build();
        }

        @Test
        void getBtcTransactionConfirmations() throws BlockStoreException, IOException {
            // act
            int result = bridgeSupport.getBtcTransactionConfirmations(mock(Sha256Hash.class), btcBlockHash, mock(MerkleBranch.class)); // we dont reach the use of btcTxHash and merkleBranch in this situation

            // assert
            // checking the block is not in the storage
            assertNull(btcBlockStoreWithCache.get(btcBlockHash));
            // checking the method returns the expected code from the error thrown
            // when block from cache and block from storage dont match
            assertEquals(BTC_TRANSACTION_CONFIRMATION_INCONSISTENT_BLOCK_ERROR_CODE, result);
        }

        @Test
        void getBtcTransactionConfirmationsGetCost() throws BlockStoreException {
            // arrange
            Object[] args = new Object[4];
            args[1] = btcBlockHash.getBytes();
            args[3] = new Object[]{};

            // recreating the chainHead as real btc block 2_817_200 so blockDepth is not 0
            byte[] chainHeadRawHeader = HexUtils.stringHexToByteArray("0000642d6b8df2c8ae4a20e20a10ae3b34e485701b8ae7e80ede0828040000000000000063a17c3e5063da2f8f952e496beee729932d35f62c0765baaf12f32931f01c9b49354f66ecd410190f25f402");
            BtcBlock chainHeadHeader = new BtcBlock(bridgeTestnetConstants.getBtcParams(), chainHeadRawHeader);
            int chainHeadBlockNumber = 2_817_200;
            BigInteger chainHeadChainWork = BigInteger.ONE;
            StoredBlock chainHead = new StoredBlock(chainHeadHeader, chainHeadChainWork, chainHeadBlockNumber);
            btcBlockStoreWithCache.setChainHead(chainHead);

            // act
            Long result = bridgeSupport.getBtcTransactionConfirmationsGetCost(args);

            // assert
            final long BASIC_COST = 27_000;
            final long STEP_COST = 315;
            final long DOUBLE_HASH_COST = 144;
            int blockDepth = chainHeadBlockNumber - 2_817_125;
            int branchHashesSize = 0; // since branchHashesSize = args[3].length
            Long expectedResult = BASIC_COST + blockDepth * STEP_COST + branchHashesSize * DOUBLE_HASH_COST;

            // checking the block is not in the storage
            assertNull(btcBlockStoreWithCache.get(btcBlockHash));
            // check the result is different from BASIC_COST (result = BASIC_COST would break consensus)
            assertTrue(result > BASIC_COST);
            assertEquals(expectedResult, result);
        }
    }


    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("test chain work before and after rskip 434")
    class ChainWorkTests {
        ActivationConfig.ForBlock activationsPreRSKIP434 = ActivationConfigsForTest.arrowhead600().forBlock(0);
        ActivationConfig.ForBlock activationsPostRSKIP434 = ActivationConfigsForTest.arrowhead631().forBlock(0);
        Repository repository;
        BtcBlockStoreWithCache.Factory btcBlockStoreFactory;
        BtcBlockStoreWithCache btcBlockStoreWithCachePreRSKIP434;
        BtcBlockStoreWithCache btcBlockStoreWithCachePostRSKIP434;

        BridgeSupportBuilder bridgeSupportBuilder = BridgeSupportBuilder.builder();
        BridgeSupport bridgeSupportPreRSKIP434;
        BridgeSupport bridgeSupportPostRSKIP434;

        BtcBlock block849134;
        BtcBlock block849135;
        BtcBlock block849136;
        BtcBlock block849137;
        BtcBlock blockWithTooMuchWork;
        BtcBlock block849139;

        @BeforeEach
        void setUp() {
            BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
            repository = createRepository();
            btcBlockStoreFactory = new RepositoryBtcBlockStoreWithCache.Factory(bridgeMainnetConstants.getBtcParams(), 100, 100);

            // recreate context pre rskip 434 for mainnet
            BridgeStorageProvider bridgeStorageProviderPreRSKIP434 = new BridgeStorageProvider(
                repository,
                bridgeMainnetConstants.getBtcParams(),
                activationsPreRSKIP434
            );
            bridgeSupportPreRSKIP434 = bridgeSupportBuilder
                .withBridgeConstants(bridgeMainnetConstants)
                .withProvider(bridgeStorageProviderPreRSKIP434)
                .withRepository(repository)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withActivations(activationsPreRSKIP434)
                .build();
            btcBlockStoreWithCachePreRSKIP434 =
                btcBlockStoreFactory.newInstance(repository, bridgeMainnetConstants, bridgeStorageProviderPreRSKIP434, activationsPreRSKIP434);

            // recreate context post rskip 434 for mainnet
            BridgeStorageProvider bridgeStorageProviderPostRSKIP434 = new BridgeStorageProvider(
                repository,
                bridgeMainnetConstants.getBtcParams(),
                activationsPostRSKIP434
            );
            bridgeSupportPostRSKIP434 = bridgeSupportBuilder
                .withBridgeConstants(bridgeMainnetConstants)
                .withProvider(bridgeStorageProviderPostRSKIP434)
                .withRepository(repository)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withActivations(activationsPostRSKIP434)
                .build();
            btcBlockStoreWithCachePostRSKIP434 =
                btcBlockStoreFactory.newInstance(repository, bridgeMainnetConstants, bridgeStorageProviderPostRSKIP434, activationsPostRSKIP434);

            String block849134Header = "0080b92c24f123130ae29e899f0cab72653722e54cdf3b30445202000000000000000000c72ead65a3b78ab637d1876c00414a77e47bcc5b52667ac1e573633563bea5a695aa7766255d031728d182a8";
            block849134 = new BtcBlock(bridgeMainnetConstants.getBtcParams(), HexUtils.stringHexToByteArray(block849134Header));

            String block849135Header = "00004020bf67910b5d3996ee594848b482ee84d0e28c97a9a2d601000000000000000000865e218552bb92df36c962f5163e84a6c2542584fb36be2fa8b2a4246c73a701f1ae7766255d031791836b22";
            block849135 = new BtcBlock(bridgeMainnetConstants.getBtcParams(), HexUtils.stringHexToByteArray(block849135Header));

            String block849136Header = "0000003a796f8b7a9d6ba6e13064e7c64e94570f877170262f1f0200000000000000000036b2ab17565a24a9be4626ca801cb31f91232034ba848295475f931a58dd5446e5b07766255d03173b01a491";
            block849136 = new BtcBlock(bridgeMainnetConstants.getBtcParams(), HexUtils.stringHexToByteArray(block849136Header));

            String block849137Header = "00e00820925b77c9ff4d0036aa29f3238cde12e9af9d55c34ed30200000000000000000032a9fa3e12ef87a2327b55db6a16a1227bb381db8b269d90aa3a6e38cf39665f91b47766255d0317c1b1575f";
            block849137 = new BtcBlock(bridgeMainnetConstants.getBtcParams(), HexUtils.stringHexToByteArray(block849137Header));

            String blockWithTooMuchWorkHeader = "006001207ca158816ffc9d45b9ecd6a49ffbf3038f3646cf13fc01000000000000000000e0182ce7cc10db785b5fb2fb4314053f5b12cd6116168797cb461aa339fc725078b87766255d0317ba5261e2";
            blockWithTooMuchWork = new BtcBlock(bridgeMainnetConstants.getBtcParams(), HexUtils.stringHexToByteArray(blockWithTooMuchWorkHeader));

            String block849139Header = "00a0b625ffa2f7cbf95219fc74c3db38f84ae265784bc1417c71020000000000000000008e5b319a229376089f4a7b77c90ed90ac19a0532fc4c62426f5a5931ee7e3e8dd2c67766255d03171e91a015";
            block849139 = new BtcBlock(bridgeMainnetConstants.getBtcParams(), HexUtils.stringHexToByteArray(block849139Header));
        }

        @Test
        void receiveHeader_beforeRSKIP434_returnsUnexpectedExceptionResponseCodeAndDoesNotSaveTheBlock() throws BlockStoreException, IOException {
            // Create block with too much work parent with cumulative difficulty
            BigInteger block849137ChainWork = new BigInteger("00000000000000000000000000000000000000007fffdc6f043e4a69ea179a7a", 16);
            StoredBlock block849137ToStore = new StoredBlock(block849137, block849137ChainWork, 849137);

            // save parent in storage
            btcBlockStoreWithCachePreRSKIP434.put(block849137ToStore);
            btcBlockStoreWithCachePreRSKIP434.setChainHead(block849137ToStore);
            repository.save();
            // assert that parent was correctly saved
            assertEquals(btcBlockStoreWithCachePreRSKIP434.getChainHead().getHeader().getHash(), block849137ToStore.getHeader().getHash());

            // assert receive header returns an exception and does not save the block
            final Integer RECEIVE_HEADER_UNEXPECTED_EXCEPTION = -99;
            assertThat(bridgeSupportPreRSKIP434.receiveHeader(blockWithTooMuchWork), is(RECEIVE_HEADER_UNEXPECTED_EXCEPTION));
            assertNull(btcBlockStoreWithCachePreRSKIP434.get(blockWithTooMuchWork.getHash()));
        }

        @Test
        void receiveHeaders_beforeRSKIP434_savesBlocksUntilReachesBlockWithTooMuchChainWork() throws BlockStoreException, IOException {
            // Create block 849134 with cumulative difficulty
            BigInteger block849134ChainWork = new BigInteger("00000000000000000000000000000000000000007ffef81fa11393037c9df17b", 16);
            StoredBlock block849134ToStore = new StoredBlock(block849134, block849134ChainWork, 849134);

            // save block 849134 in storage
            btcBlockStoreWithCachePreRSKIP434.put(block849134ToStore);
            btcBlockStoreWithCachePreRSKIP434.setChainHead(block849134ToStore);
            repository.save();
            // assert that block 849134 was correctly saved
            assertEquals(btcBlockStoreWithCachePreRSKIP434.getChainHead().getHeader().getHash(), block849134.getHash());

            BtcBlock[] headersToSend = new BtcBlock[]{block849135, block849136, block849137, blockWithTooMuchWork, block849139};
            // assert blocks until 849137 are correctly saved
            // and blocks from 849138 are not saved
            bridgeSupportPreRSKIP434.receiveHeaders(headersToSend);
            assertNotNull(btcBlockStoreWithCachePreRSKIP434.get(block849135.getHash()));
            assertNotNull(btcBlockStoreWithCachePreRSKIP434.get(block849136.getHash()));
            assertNotNull(btcBlockStoreWithCachePreRSKIP434.get(block849137.getHash()));
            assertNull(btcBlockStoreWithCachePreRSKIP434.get(blockWithTooMuchWork.getHash()));
            assertNull(btcBlockStoreWithCachePreRSKIP434.get(block849139.getHash()));
        }

        @Test
        void receiveHeader_afterRSKIP434_returnsSuccessfulAndSavesTheBlock() throws BlockStoreException, IOException {
            // Create block with too much work parent with cumulative difficulty
            BigInteger block849137ChainWork = new BigInteger("00000000000000000000000000000000000000007fffdc6f043e4a69ea179a7a", 16);
            StoredBlock block849137ToStore = new StoredBlock(block849137, block849137ChainWork, 849137);

            // save parent in storage
            btcBlockStoreWithCachePostRSKIP434.put(block849137ToStore);
            btcBlockStoreWithCachePostRSKIP434.setChainHead(block849137ToStore);
            repository.save();
            // assert that previous block was correctly saved
            assertEquals(btcBlockStoreWithCachePostRSKIP434.getChainHead().getHeader().getHash(), block849137ToStore.getHeader().getHash());

            // assert receive header returns successful response and saves the block
            final Integer RECEIVE_HEADER_SUCCESSFUL = 0;
            assertThat(bridgeSupportPostRSKIP434.receiveHeader(blockWithTooMuchWork), is(RECEIVE_HEADER_SUCCESSFUL));
            assertNotNull(btcBlockStoreWithCachePostRSKIP434.get(blockWithTooMuchWork.getHash()));
        }

        @Test
        void receiveHeaders_afterRSKIP434_savesAllBlocks() throws BlockStoreException, IOException {
            // Create block 849134 with cumulative difficulty
            BigInteger block849134ChainWork = new BigInteger("00000000000000000000000000000000000000007ffef81fa11393037c9df17b", 16);
            StoredBlock block849134ToStore = new StoredBlock(block849134, block849134ChainWork, 849134);

            // save block 849134 in storage
            btcBlockStoreWithCachePostRSKIP434.put(block849134ToStore);
            btcBlockStoreWithCachePostRSKIP434.setChainHead(block849134ToStore);
            repository.save();
            // assert that block 849134 was correctly saved
            assertEquals(btcBlockStoreWithCachePostRSKIP434.getChainHead().getHeader().getHash(), block849134.getHash());

            BtcBlock[] headersToSend = new BtcBlock[]{block849135, block849136, block849137, blockWithTooMuchWork, block849139};
            // assert all blocks are correctly saved
            bridgeSupportPostRSKIP434.receiveHeaders(headersToSend);
            assertNotNull(btcBlockStoreWithCachePostRSKIP434.get(block849135.getHash()));
            assertNotNull(btcBlockStoreWithCachePostRSKIP434.get(block849136.getHash()));
            assertNotNull(btcBlockStoreWithCachePostRSKIP434.get(block849137.getHash()));
            assertNotNull(btcBlockStoreWithCachePostRSKIP434.get(blockWithTooMuchWork.getHash()));
            assertNotNull(btcBlockStoreWithCachePostRSKIP434.get(block849139.getHash()));
        }

        @ParameterizedTest
        @MethodSource("notMainnetAndActivationsArgs")
        void receiveHeader_networkNotMainnet_returnsSuccessfulAndSavesTheBlock(ActivationConfig.ForBlock activations, BridgeConstants bridgeConstants) throws BlockStoreException, IOException {
            bridgeStorageProvider = new BridgeStorageProvider(
                repository,
                bridgeConstants.getBtcParams(),
                activations
            );
            bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeConstants)
                .withProvider(bridgeStorageProvider)
                .withRepository(repository)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withActivations(activations)
                .build();
            BtcBlockStoreWithCache btcBlockStoreWithCache = btcBlockStoreFactory.newInstance(repository, bridgeConstants, bridgeStorageProvider, activations);

            // Create block with too much work parent with cumulative difficulty
            BigInteger block849137ChainWork = new BigInteger("00000000000000000000000000000000000000007fffdc6f043e4a69ea179a7a", 16);
            StoredBlock block849137ToStore = new StoredBlock(block849137, block849137ChainWork, 849137);

            // save parent in storage
            btcBlockStoreWithCache.put(block849137ToStore);
            btcBlockStoreWithCache.setChainHead(block849137ToStore);
            repository.save();
            // assert that previous block was correctly saved
            assertEquals(btcBlockStoreWithCache.getChainHead().getHeader().getHash(), block849137ToStore.getHeader().getHash());

            // assert receive header returns successful response and saves the block
            final Integer RECEIVE_HEADER_SUCCESSFUL = 0;
            assertThat(bridgeSupport.receiveHeader(blockWithTooMuchWork), is(RECEIVE_HEADER_SUCCESSFUL));
            assertNotNull(btcBlockStoreWithCache.get(blockWithTooMuchWork.getHash()));
        }

        @ParameterizedTest
        @MethodSource("notMainnetAndActivationsArgs")
        void receiveHeaders_networkNotMainnet_savesAllBlocks(ActivationConfig.ForBlock activations, BridgeConstants bridgeConstants) throws BlockStoreException, IOException {
            bridgeStorageProvider = new BridgeStorageProvider(
                repository,
                bridgeConstants.getBtcParams(),
                activations
            );
            bridgeSupport = bridgeSupportBuilder
                .withBridgeConstants(bridgeConstants)
                .withProvider(bridgeStorageProvider)
                .withRepository(repository)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withActivations(activations)
                .build();
            BtcBlockStoreWithCache btcBlockStoreWithCache = btcBlockStoreFactory.newInstance(repository, bridgeConstants, bridgeStorageProvider, activations);

            // Create block 849134 with cumulative difficulty
            BigInteger block849134ChainWork = new BigInteger("00000000000000000000000000000000000000007ffef81fa11393037c9df17b", 16);
            StoredBlock block849134ToStore = new StoredBlock(block849134, block849134ChainWork, 849134);

            // save block 849134 in storage
            btcBlockStoreWithCache.put(block849134ToStore);
            btcBlockStoreWithCache.setChainHead(block849134ToStore);
            repository.save();
            // assert that block 849134 was correctly saved
            assertEquals(btcBlockStoreWithCache.getChainHead().getHeader().getHash(), block849134.getHash());

            BtcBlock[] headersToSend = new BtcBlock[]{block849135, block849136, block849137, blockWithTooMuchWork, block849139};
            // assert all blocks are correctly saved
            bridgeSupport.receiveHeaders(headersToSend);
            assertNotNull(btcBlockStoreWithCache.get(block849135.getHash()));
            assertNotNull(btcBlockStoreWithCache.get(block849136.getHash()));
            assertNotNull(btcBlockStoreWithCache.get(block849137.getHash()));
            assertNotNull(btcBlockStoreWithCache.get(blockWithTooMuchWork.getHash()));
            assertNotNull(btcBlockStoreWithCache.get(block849139.getHash()));
        }

        private Stream<Arguments> notMainnetAndActivationsArgs() {
            BridgeConstants testnet = BridgeTestNetConstants.getInstance();
            BridgeConstants regtest = new BridgeRegTestConstants();

            return Stream.of(
                Arguments.of(activationsPreRSKIP434, testnet),
                Arguments.of(activationsPostRSKIP434, testnet),
                Arguments.of(activationsPreRSKIP434, regtest),
                Arguments.of(activationsPostRSKIP434, regtest)
            );
        }
    }

    private void assertRefundInRegisterPegInVersionLegacy(
        boolean isWhitelisted,
        boolean mockLockingCap,
        TxSenderAddressType lockSenderAddressType,
        @Nullable ConsensusRule consensusRule) throws IOException, RegisterBtcTransactionException {

        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        if (consensusRule != null) {
            when(activations.isActive(consensusRule)).thenReturn(true);
        }

        repository = createRepository();

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        Address btcAddress = srcKey1.toAddress(btcRegTestParams);
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);
        Address federationAddress = genesisFederation.getAddress();

        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        btcTx.addOutput(Coin.COIN.multiply(10), federationAddress);
        btcTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        LockWhitelist lockWhitelist = mock(LockWhitelist.class);
        when(lockWhitelist.isWhitelistedFor(eq(btcAddress), any(Coin.class), any(int.class))).thenReturn(isWhitelisted);
        WhitelistStorageProvider whitelistProvider = mock(WhitelistStorageProvider.class);
        when(whitelistProvider.getLockWhitelist(activations, btcRegTestParams)).thenReturn(lockWhitelist);

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);

        if (mockLockingCap) {
            LockingCapSupport lockingCapSupportMock = mock(LockingCapSupport.class);
            when(lockingCapSupportMock.getLockingCap()).thenReturn(Optional.of(Coin.COIN.multiply(1)));
        }

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(lockSenderAddressType, btcAddress, rskAddress);

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withLockingCapSupport(lockingCapSupport)
            .build();

        // Act
        bridgeSupport.registerPegIn(btcTx, PegTestUtils.createHash3(1), 0);

        // Assert
        assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());

        // Check rejection tx input was created from btc tx
        boolean successfulRejection = false;
        for (PegoutsWaitingForConfirmations.Entry e : pegoutsWaitingForConfirmations.getEntries()) {
            if (e.getBtcTransaction().getInput(0).getOutpoint().getHash() == btcTx.getHash()) {
                successfulRejection = true;
                break;
            }
        }

        assertTrue(successfulRejection);

        // Check tx was not marked as processed
        assertFalse(provider.getHeightIfBtcTxhashIsAlreadyProcessed(btcTx.getHash()).isPresent());
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("test release transaction info processing")
    class ReleaseTransactionInfo {
        private final Coin outpointValue1 = Coin.valueOf(400_000);
        private final Coin outpointValue2 = Coin.valueOf(90_000);
        private final Coin outpointValue3 = Coin.valueOf(50_000);

        // requesting a release with an amount that needs all the outpoints,
        // but that also has some change to the fed
        private final Coin requestedValue1 = Coin.valueOf(100_000);
        private final Coin requestedValue2 = Coin.valueOf(400_000);
        private final List<Coin> outpointValues = List.of(outpointValue1, outpointValue2, outpointValue3);
        private final Coin totalAmountRequested = requestedValue1.add(requestedValue2);

        private List<LogInfo> logs;
        private Block currentBlock;

        @BeforeEach
        void setUp() {
            repository = createRepository();
            federationStorageProvider.setNewFederation(activeFederation);
            logs = new ArrayList<>();

            Coin feePerKb = Coin.valueOf(10_000L);
            when(feePerKbSupport.getFeePerKb()).thenReturn(feePerKb);

            currentBlock = createRskBlock(0L);
        }

        private void setUpWithActivations(ActivationConfig.ForBlock activations) {
            bridgeStorageProvider = new BridgeStorageProvider(repository, btcMainnetParams, activations);
            btcBlockStoreFactory =
                new RepositoryBtcBlockStoreWithCache.Factory(btcMainnetParams, 100, 100);

            BridgeEventLogger bridgeEventLogger = new BridgeEventLoggerImpl(
                bridgeMainNetConstants,
                activations,
                logs
            );

            // recreate bridge support with real btcLockSenderProvider instead of a mock to be able to parse pegin
            BtcLockSenderProvider btcLockSenderProvider = new BtcLockSenderProvider();
            bridgeSupport = bridgeSupportBuilder
                .withActivations(activations)
                .withBridgeConstants(bridgeMainNetConstants)
                .withRepository(repository)
                .withProvider(bridgeStorageProvider)
                .withEventLogger(bridgeEventLogger)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withExecutionBlock(currentBlock)
                .withBtcLockSenderProvider(btcLockSenderProvider)
                .build();
        }

        private void setUpReleaseRequests() {
            // save utxos in active federation wallet
            Script outputScript = ScriptBuilder.createOutputScript(activeFederation.getAddress());
            UTXOBuilder utxoBuilder = UTXOBuilder.builder()
                .withOutputScript(outputScript)
                .withHeight(10);
            List<UTXO> activeFederationUTXOs = List.of(
                utxoBuilder.withTransactionHash(createHash(1)).withValue(outpointValue1).build(),
                utxoBuilder.withTransactionHash(createHash(2)).withValue(outpointValue2).build(),
                utxoBuilder.withTransactionHash(createHash(3)).withValue(outpointValue3).build()
            );
            bridgeStorageAccessor.saveToRepository(NEW_FEDERATION_BTC_UTXOS_KEY.getKey(), activeFederationUTXOs, BridgeSerializationUtils::serializeUTXOList);

            // save releases in queue
            Address destinationAddress1 = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "firstAddress");
            Keccak256 rskTxHash1 = RskTestUtils.createHash(1);
            ReleaseRequestQueue.Entry releaseEntry1 = new ReleaseRequestQueue.Entry(destinationAddress1, requestedValue1, rskTxHash1);

            Address destinationAddress2 = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "secondAddress");
            Keccak256 rskTxHash2 = RskTestUtils.createHash(2);
            ReleaseRequestQueue.Entry releaseEntry2 = new ReleaseRequestQueue.Entry(destinationAddress2, requestedValue2, rskTxHash2);

            ReleaseRequestQueue releaseRequestQueue = new ReleaseRequestQueue(List.of(releaseEntry1, releaseEntry2));
            repository.addStorageBytes(contractAddress, RELEASE_REQUEST_QUEUE_WITH_TXHASH.getKey(), BridgeSerializationUtils.serializeReleaseRequestQueueWithTxHash(releaseRequestQueue));
        }

        @Test
        void updateCollections_whenReleasesInQueue_beforeRSKIP305_justEmitsPegoutTransactionCreatedEvent() throws IOException {
            // Arrange
            setUpReleaseRequests();

            ActivationConfig.ForBlock lovellActivations = ActivationConfigsForTest.lovell700().forBlock(0L);
            setUpWithActivations(lovellActivations);

            // Act
            bridgeSupport.updateCollections(tx);
            bridgeSupport.save();

            // Assert
            BtcTransaction releaseTransaction = getReleaseFromPegoutsWFC(bridgeStorageProvider);
            Sha256Hash releaseTransactionHash = releaseTransaction.getHash();
            Keccak256 releaseCreationTxHash = tx.getHash();

            PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = bridgeStorageProvider.getPegoutsWaitingForConfirmations();
            assertPegoutWasAddedToPegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations, releaseTransactionHash, releaseCreationTxHash, currentBlock.getNumber());
            assertPegoutTxSigHashWasSaved(bridgeStorageProvider, releaseTransaction);
            assertLogReleaseRequested(logs, releaseCreationTxHash, releaseTransactionHash, totalAmountRequested);
            assertLogPegoutTransactionCreated(logs, releaseTransaction, outpointValues);

            assertReleaseOutpointsValuesWereNotSavedInStorage(releaseTransaction);
        }

        @Test
        void updateCollections_whenReleasesInQueueAndLegacyFed_afterRSKIP305_shouldSetRedeemDataInScriptSigAndProcessReleaseTransactionInfo() throws IOException {
            // Arrange
            setUpReleaseRequests();
            setUpWithActivations(allActivations);

            // Act
            bridgeSupport.updateCollections(tx);
            bridgeSupport.save();

            // Assert
            BtcTransaction releaseTransaction = getReleaseFromPegoutsWFC(bridgeStorageProvider);

            // check the active fed redeem script data is in input script sig
            assertScriptSigHasExpectedInputRedeemData(releaseTransaction.getInput(0), activeFederation.getRedeemScript());

            assertReleaseWasSettled(
                repository,
                bridgeStorageProvider,
                logs,
                currentBlock.getNumber(),
                tx.getHash(),
                releaseTransaction,
                outpointValues,
                totalAmountRequested
            );
        }

        @Test
        void updateCollections_whenReleasesInQueueAndSegwitCompatibleFed_shouldSetRedeemDataInWitnessAndProcessReleaseTransactionInfo() throws IOException {
            // Arrange
            activeFederation = P2shP2wshErpFederationBuilder.builder().build();
            federationStorageProvider.setNewFederation(activeFederation);

            setUpReleaseRequests();
            setUpWithActivations(allActivations);

            // Act
            bridgeSupport.updateCollections(tx);
            bridgeSupport.save();

            // Assert
            BtcTransaction releaseTransaction = getReleaseFromPegoutsWFC(bridgeStorageProvider);

            // check the active fed redeem script data
            Script redeemScript = activeFederation.getRedeemScript();
            int inputIndex = 0;
            assertWitnessAndScriptSigHaveExpectedInputRedeemData(
                releaseTransaction.getWitness(inputIndex),
                releaseTransaction.getInput(inputIndex),
                redeemScript
            );

            assertReleaseWasSettled(
                repository,
                bridgeStorageProvider,
                logs,
                currentBlock.getNumber(),
                tx.getHash(),
                releaseTransaction,
                outpointValues,
                totalAmountRequested
            );
        }

        @Test
        void pegoutsFlow_fromAReleaseRequest_toThePegoutChangeBeingCorrectlyRegistered_whenSegwitFed() throws Exception {
            // arrange
            // we need to recreate the federators keys to have the priv keys for signing
            List<BtcECKey> membersBtcPublicKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{
                "member01", "member02", "member03", "member04", "member05", "member06", "member07", "member08", "member09", "member10",
                "member11", "member12", "member13", "member14", "member15", "member16", "member17", "member18", "member19", "member20"
            }, true);
            activeFederation = P2shP2wshErpFederationBuilder.builder()
                .withMembersBtcPublicKeys(membersBtcPublicKeys)
                .build();
            federationStorageProvider.setNewFederation(activeFederation);
            setUpReleaseRequests();

            currentBlock = createRskBlock(pegoutTxIndexActivationHeight);
            setUpWithActivations(allActivations);

            // call update collections so release requests are moved to pegouts wfc structure
            bridgeSupport.updateCollections(tx);
            bridgeSupport.save();

            // get release from pegouts wfc
            BtcTransaction releaseTransaction = getReleaseFromPegoutsWFC(bridgeStorageProvider);
            // assert release transaction was created as expected
            assertReleaseWasSettled(
                repository,
                bridgeStorageProvider,
                logs,
                currentBlock.getNumber(),
                tx.getHash(),
                releaseTransaction,
                outpointValues,
                totalAmountRequested
            );
            assertWitnessAndScriptSigHaveExpectedInputRedeemData(
                releaseTransaction.getWitness(0),
                releaseTransaction.getInput(0),
                activeFederation.getRedeemScript()
            );

            // advance blockchain so pegouts have enough confirmations
            var blockNumber = currentBlock.getNumber() + bridgeMainNetConstants.getRsk2BtcMinimumAcceptableConfirmations();
            currentBlock = createRskBlock(blockNumber);
            updateBridgeSupport();

            // call update collections so pegouts are moved from wfc to wfs
            bridgeSupport.updateCollections(tx);
            bridgeSupport.save();

            // get pegout from pegouts wfs
            SortedMap<Keccak256, BtcTransaction> pegoutsWFS = bridgeStorageProvider.getPegoutsWaitingForSignatures();
            assertEquals(1, pegoutsWFS.size());
            Keccak256 rskTxHash = pegoutsWFS.firstKey();
            BtcTransaction pegoutWFS = pegoutsWFS.get(rskTxHash);

            // advance blockchain to start signing pegout
            var newBlockNumber = currentBlock.getNumber() + 1;
            currentBlock = createRskBlock(newBlockNumber);
            updateBridgeSupport();

            List<BtcECKey> signers = membersBtcPublicKeys.subList(0, activeFederation.getNumberOfSignaturesRequired());
            // sign with federators
            List<Sha256Hash> sigHashes = generateTransactionInputsSigHashes(pegoutWFS);
            for (BtcECKey federatorSignerKey : signers) {
                List<byte[]> signatures = generateSignerEncodedSignatures(federatorSignerKey, sigHashes);
                bridgeSupport.addSignature(federatorSignerKey, signatures, rskTxHash);
            }

            // assert federators signed and release was made
            for (BtcECKey federatorSignerKey : signers) {
                assertFederatorSigning(rskTxHash.getBytes(), pegoutWFS, sigHashes, activeFederation, federatorSignerKey, logs);
            }
            assertLogReleaseBtc(logs, pegoutWFS, rskTxHash);

            int activeFedUtxosSizeBeforeRegisteringChange = federationSupport.getActiveFederationBtcUTXOs().size();
            // register release change utxo
            int releaseTxBlockNumber = (int) currentBlock.getNumber();
            setUpForTransactionRegistration(pegoutWFS, releaseTxBlockNumber, allActivations);

            bridgeSupport.registerBtcTransaction(
                tx,
                pegoutWFS.bitcoinSerialize(),
                releaseTxBlockNumber,
                pmtWithTransactions.bitcoinSerialize()
            );

            // assert utxo was registered
            assertEquals(activeFedUtxosSizeBeforeRegisteringChange + 1, federationSupport.getActiveFederationBtcUTXOs().size());

            assertTransactionWasProcessed(bridgeStorageProvider, pegoutWFS.getHash(), releaseTxBlockNumber);
        }

        private void updateBridgeSupport() {
            bridgeSupport = bridgeSupportBuilder
                .withActivations(allActivations)
                .withBridgeConstants(bridgeMainNetConstants)
                .withRepository(repository)
                .withProvider(bridgeStorageProvider)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withExecutionBlock(currentBlock)
                .build();
        }

        @Test
        void registerBtcTransaction_forRefundableLegacyPeginFromMultisig_beforeRSKIP305_justEmitsPegoutTransactionCreatedEvent() throws Exception {
            // arrange
            ActivationConfig.ForBlock lovellActivations = ActivationConfigsForTest.lovell700().forBlock(0L);
            setUpWithActivations(lovellActivations);
            Coin amountToSend = Coin.COIN;
            BtcTransaction pegin = arrangeLegacyPeginFromMultiSigToActiveFed(amountToSend, lovellActivations);

            // act
            bridgeSupport.registerBtcTransaction(
                tx,
                pegin.bitcoinSerialize(),
                pegoutTxIndexActivationHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeSupport.save();

            // assert
            BtcTransaction releaseTransaction = getReleaseFromPegoutsWFC(bridgeStorageProvider);
            Sha256Hash releaseTransactionHash = releaseTransaction.getHash();
            Keccak256 releaseCreationTxHash = tx.getHash();

            PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = bridgeStorageProvider.getPegoutsWaitingForConfirmations();
            assertPegoutWasAddedToPegoutsWaitingForConfirmations(pegoutsWaitingForConfirmations, releaseTransactionHash, releaseCreationTxHash, currentBlock.getNumber());
            assertLogReleaseRequested(logs, releaseCreationTxHash, releaseTransactionHash, amountToSend);
            assertLogPegoutTransactionCreated(logs, releaseTransaction, List.of(amountToSend));
            assertReleaseOutpointsValuesWereNotSavedInStorage(releaseTransaction);
        }

        private void assertReleaseOutpointsValuesWereNotSavedInStorage(BtcTransaction releaseTransaction) {
            byte[] actualReleaseOutpointsValues = repository.getStorageBytes(
                contractAddress,
                getStorageKeyForReleaseOutpointsValues(releaseTransaction.getHash())
            );
            assertNull(actualReleaseOutpointsValues);
        }

        @Test
        void registerBtcTransaction_forRefundableLegacyPeginFromMultisig_afterRSKIP305_processReleaseTransactionsInfo() throws Exception {
            // arrange
            setUpWithActivations(allActivations);
            Coin amountToSend = Coin.COIN;
            BtcTransaction pegin = arrangeLegacyPeginFromMultiSigToActiveFed(amountToSend, allActivations);

            // act
            bridgeSupport.registerBtcTransaction(
                tx,
                pegin.bitcoinSerialize(),
                pegoutTxIndexActivationHeight,
                pmtWithTransactions.bitcoinSerialize()
            );
            bridgeSupport.save();

            // assert
            BtcTransaction releaseTransaction = getReleaseFromPegoutsWFC(bridgeStorageProvider);
            assertReleaseRejectionWasSettled(
                repository,
                bridgeStorageProvider,
                logs,
                currentBlock.getNumber(),
                tx.getHash(),
                releaseTransaction,
                List.of(amountToSend),
                amountToSend
            );
        }

        private BtcTransaction arrangeLegacyPeginFromMultiSigToActiveFed(Coin amountToSend, ActivationConfig.ForBlock activations) throws Exception {
            BtcTransaction pegin = createLegacyPeginFromMultiSigToActiveFed(amountToSend);
            setUpForTransactionRegistration(pegin, pegoutTxIndexActivationHeight, activations);

            return pegin;
        }

        private BtcTransaction createLegacyPeginFromMultiSigToActiveFed(Coin amountToSend) {
            BtcTransaction pegin = new BtcTransaction(btcMainnetParams);
            List<BtcECKey> pubKeys = Arrays.asList(
                getBtcEcKeyFromSeed("legacy_pegin_p2sh_multisig_key_1"),
                getBtcEcKeyFromSeed("legacy_pegin_p2sh_multisig_key_2")
            );
            Script redeemScript = ScriptBuilder.createRedeemScript(2, pubKeys);
            Script scriptSig = BitcoinUtils.createBaseInputScriptThatSpendsFromRedeemScript(redeemScript);
            pegin.addInput(BitcoinTestUtils.createHash(1), 0, scriptSig);
            pegin.addOutput(amountToSend, activeFederation.getAddress());

            return pegin;
        }
    }

    private void setUpForTransactionRegistration(BtcTransaction btcTx, int btcTxToRegisterBlockNumber, ActivationConfig.ForBlock activations) throws Exception {
        pmtWithTransactions = createValidPmtForTransactions(List.of(btcTx), btcMainnetParams);
        var chainHeight = btcTxToRegisterBlockNumber + bridgeMainNetConstants.getBtc2RskMinimumAcceptableConfirmations();

        BtcBlockStoreWithCache btcBlockStore = btcBlockStoreFactory.newInstance(repository, bridgeMainNetConstants, bridgeStorageProvider, activations);
        recreateChainFromPmt(btcBlockStore, chainHeight, pmtWithTransactions, btcTxToRegisterBlockNumber, btcMainnetParams);
        bridgeStorageProvider.save();
    }

    @Test
    void migrating_many_utxos_works_before_rskip294_divide_in_2() throws IOException {
        test_migrating_many_utxos(false, 380, 2);
    }

    @Test
    void migrating_many_utxos_works_before_rskip294_divide_in_4() throws IOException {
        test_migrating_many_utxos(false, 600, 4);
    }

    @Test
    void migrating_many_utxos_works_after_rskip294_even_utxos_distribution() throws IOException {
        int utxosToCreate = 400;
        int expectedTransactions = (int) Math.ceil((double) utxosToCreate / bridgeConstantsRegtest.getMaxInputsPerPegoutTransaction());
        test_migrating_many_utxos(true, utxosToCreate, expectedTransactions);
    }

    @Test
    void migrating_many_utxos_works_after_rskip294_uneven_utxos_distribution() throws IOException {
        int utxosToCreate = 410;
        int expectedTransactions = (int) Math.ceil((double) utxosToCreate / bridgeConstantsRegtest.getMaxInputsPerPegoutTransaction());
        test_migrating_many_utxos(true, utxosToCreate, expectedTransactions);
    }

    private void test_migrating_many_utxos(boolean isRskip294Active, int utxosToCreate, int expectedTransactions) throws IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP294)).thenReturn(isRskip294Active);

        List<FederationMember> oldFedMembers = new ArrayList<>();
        int oldFedMembersAmount = 13;
        for (int i = 0; i < oldFedMembersAmount; i++) {
            oldFedMembers.add(FederationMember.getFederationMemberFromKey(new BtcECKey()));
        }

        FederationArgs oldFedArgs = new FederationArgs(oldFedMembers,
            Instant.now(),
            0,
            btcRegTestParams
        );
        Federation oldFed = FederationFactory.buildStandardMultiSigFederation(oldFedArgs);

        List<FederationMember> newFedMembers = Arrays.asList(
            FederationMember.getFederationMemberFromKey(new BtcECKey()),
            FederationMember.getFederationMemberFromKey(new BtcECKey()),
            FederationMember.getFederationMemberFromKey(new BtcECKey())
        );
        FederationArgs newFedArgs = new FederationArgs(newFedMembers, Instant.now(), 1, btcRegTestParams);
        Federation newFed = FederationFactory.buildStandardMultiSigFederation(newFedArgs);

        Block block = mock(Block.class);
        // Set block right after the migration should start
        long blockNumber = newFed.getCreationBlockNumber() +
            bridgeConstantsRegtest.getFederationConstants().getFederationActivationAge(activations) +
            bridgeConstantsRegtest.getFederationConstants().getFundsMigrationAgeSinceActivationBegin() +
            1;
        when(block.getNumber()).thenReturn(blockNumber);

        List<UTXO> utxosToMigrate = new ArrayList<>();
        for (int i = 0; i < utxosToCreate; i++) {
            UTXO utxo = UTXOBuilder.builder()
                .withTransactionHash(createHash(i))
                .withHeight(10)
                .withOutputScript(oldFed.getP2SHScript())
                .build();
            utxosToMigrate.add(utxo);
        }

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(Collections.emptySet());

        bridgeStorageProvider = mock(BridgeStorageProvider.class);
        when(bridgeStorageProvider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);
        when(bridgeStorageProvider.getReleaseRequestQueue()).thenReturn(new ReleaseRequestQueue(new ArrayList<>()));

        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);
        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(newFed);
        when(federationStorageProviderMock.getOldFederation(any(), any())).thenReturn(oldFed);
        when(federationStorageProviderMock.getOldFederationBtcUTXOs()).thenReturn(utxosToMigrate);

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withRskExecutionBlock(block)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withActivations(activations)
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(bridgeStorageProvider)
            .withExecutionBlock(block)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        // Ensure a new transaction is created after each call to updateCollections
        // until the expected number is reached
        for (int i = 0; i < expectedTransactions; i++) {
            bridgeSupport.updateCollections(mock(Transaction.class));
            assertEquals(i + 1, pegoutsWaitingForConfirmations.getEntries().size());
        }
        assertTrue(utxosToMigrate.isEmpty()); // Migrated UTXOs are removed from the list

        // Assert inputs size of each transaction
        List<Integer> expectedInputSizes = new ArrayList<>();
        int remainingUtxos = utxosToCreate;
        while (remainingUtxos > 0) {
            int expectedSize;
            if (isRskip294Active) {
                int maxInputsPerTransaction = bridgeConstantsRegtest.getMaxInputsPerPegoutTransaction();
                expectedSize = Math.min(remainingUtxos, maxInputsPerTransaction);
            } else {
                expectedSize = remainingUtxos;
                while (expectedSize > 200) { // Approximately 200 inputs fit in a release transaction with this federation size
                    expectedSize = (int) Math.ceil((double) expectedSize / 2);
                }
            }
            expectedInputSizes.add(expectedSize);
            remainingUtxos -= expectedSize;
        }

        pegoutsWaitingForConfirmations.getEntries().forEach(e -> {
            Integer inputsSize = e.getBtcTransaction().getInputs().size();
            expectedInputSizes.remove(inputsSize);
        });

        assertTrue(expectedInputSizes.isEmpty()); // All expected sizes should have been found and removed
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Tag("test migration transaction between different types of feds")
    class MigrationTransaction {
        Coin value1 = Coin.valueOf(1_000_000);
        Coin value2 = Coin.valueOf(500_000);
        Coin value3 = Coin.valueOf(300_000);
        List<Coin> utxos = Arrays.asList(value1, value2, value3);
        Coin totalAmountRequested = value1.add(value2).add(value3);
        List<UTXO> retiringFederationUTXOs;
        List<LogInfo> logs;
        long blockNumber;

        private void setUp(Federation retiringFederation, Federation activeFederation) {
            federationStorageProvider.setOldFederation(retiringFederation);
            Address proposedFederationAddress = retiringFederation.getAddress();
            Script outputScript = ScriptBuilder.createOutputScript(proposedFederationAddress);
            UTXOBuilder utxoBuilder = UTXOBuilder.builder().withOutputScript(outputScript).withHeight(10);
            retiringFederationUTXOs = Arrays.asList(
                utxoBuilder.withTransactionHash(createHash(1)).withValue(utxos.get(0)).build(),
                utxoBuilder.withTransactionHash(createHash(2)).withValue(utxos.get(1)).build(),
                utxoBuilder.withTransactionHash(createHash(3)).withValue(utxos.get(2)).build()
            );
            bridgeStorageAccessor.saveToRepository(OLD_FEDERATION_BTC_UTXOS_KEY.getKey(), retiringFederationUTXOs, BridgeSerializationUtils::serializeUTXOList);

            federationStorageProvider.setNewFederation(activeFederation);

            blockNumber = federationConstantsMainnet.getFederationActivationAge(allActivations) + federationConstantsMainnet.getFundsMigrationAgeSinceActivationBegin() + activeFederation.getCreationBlockNumber() + 1;
            Block currentBlock = createRskBlock(blockNumber);
            federationSupport = FederationSupportBuilder.builder()
                .withFederationConstants(federationConstantsMainnet)
                .withFederationStorageProvider(federationStorageProvider)
                .withRskExecutionBlock(currentBlock)
                .withActivations(allActivations)
                .build();

            logs = new ArrayList<>();
            BridgeEventLogger bridgeEventLogger = new BridgeEventLoggerImpl(
                bridgeMainNetConstants,
                allActivations,
                logs
            );

            bridgeSupport = bridgeSupportBuilder
                .withActivations(allActivations)
                .withBridgeConstants(bridgeMainNetConstants)
                .withRepository(repository)
                .withProvider(bridgeStorageProvider)
                .withBtcBlockStoreFactory(btcBlockStoreFactory)
                .withFederationSupport(federationSupport)
                .withFeePerKbSupport(feePerKbSupport)
                .withEventLogger(bridgeEventLogger)
                .withExecutionBlock(currentBlock)
                .build();
        }

        @ParameterizedTest
        @MethodSource("federationArgs")
        void migration_fromLegacyRetiring_toLegacyActiveFed(Federation retiringFederation, Federation activeFederation) throws IOException {
            // arrange
            setUp(retiringFederation, activeFederation);

            bridgeSupport.updateCollections(tx);
            bridgeSupport.save();

            BtcTransaction migrationTransaction = getReleaseFromPegoutsWFC(bridgeStorageProvider);
            assertReleaseWasSettled(
                repository,
                bridgeStorageProvider,
                logs,
                blockNumber,
                tx.getHash(),
                migrationTransaction,
                utxos,
                totalAmountRequested
            );
        }

        private static Stream<Arguments> federationArgs() {
            Federation legacyRetiringFederation = P2shErpFederationBuilder.builder().build();
            List<BtcECKey> legacyActiveMembersBtcKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
                new String[]{"active01", "active02", "active03", "active04", "active05", "active06", "active07", "active08", "active09"},
                true
            );
            Federation legacyActiveFederation = P2shErpFederationBuilder.builder()
                .withMembersBtcPublicKeys(legacyActiveMembersBtcKeys)
                .build();

            Federation segwitActiveFederation = P2shP2wshErpFederationBuilder.builder()
                .build();

            List<BtcECKey> segwitRetiringMembersBtcKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{
                    "retiring01", "retiring02", "retiring03", "retiring04", "retiring05", "retiring06", "retiring07", "retiring08", "retiring09", "retiring10",
                    "retiring11", "retiring12", "retiring13", "retiring14", "retiring15", "retiring16", "retiring17", "retiring18", "retiring19", "retiring20"
                },
                true
            );
            Federation segwitRetiringFederation = P2shP2wshErpFederationBuilder.builder()
                .withMembersBtcPublicKeys(segwitRetiringMembersBtcKeys)
                .build();

            return Stream.of(
                Arguments.of(legacyRetiringFederation, legacyActiveFederation),
                Arguments.of(legacyRetiringFederation, segwitActiveFederation),
                Arguments.of(segwitRetiringFederation, segwitActiveFederation)
            );
        }
    }

    @Test
    void getNextPegoutCreationBlockNumber_before_RSKIP271_activation() {
        ActivationConfig.ForBlock irisActivations = ActivationConfigsForTest.iris300().forBlock(0L);

        BridgeSupport bridgeSupportIris = bridgeSupportBuilder
            .withActivations(irisActivations)
            .build();

        assertEquals(0L, bridgeSupportIris.getNextPegoutCreationBlockNumber());
    }

    @Test
    void getNextPegoutCreationBlockNumber_after_RSKIP271_activation() {
        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0L);
        final long nextPegoutCreationBlockNumber = 10L;

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNextPegoutHeight()).thenReturn(Optional.of(nextPegoutCreationBlockNumber));

        BridgeSupport bridgeSupportHop = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(hopActivations)
            .build();

        assertEquals(nextPegoutCreationBlockNumber, bridgeSupportHop.getNextPegoutCreationBlockNumber());
    }

    @Test
    void getQueuedPegoutsCount_before_RSKIP271_activation() throws IOException {
        ActivationConfig.ForBlock irisActivations = ActivationConfigsForTest.iris300().forBlock(0L);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        BridgeSupport bridgeSupportIris = bridgeSupportBuilder
            .withActivations(irisActivations)
            .withProvider(provider)
            .build();

        verify(provider, never()).getReleaseRequestQueueSize();
        assertEquals(0, bridgeSupportIris.getQueuedPegoutsCount());
    }

    @Test
    void getQueuedPegoutsCount_after_RSKIP271_activation() throws IOException {
        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0L);

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getReleaseRequestQueueSize()).thenReturn(2);

        BridgeSupport bridgeSupportHop = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(hopActivations)
            .build();

        assertEquals(2, bridgeSupportHop.getQueuedPegoutsCount());
    }

    private static Stream<Arguments> getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP271(BridgeConstants bridgeConstants) {
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            PegTestUtils.createRandomBtcECKeys(7)
        );

        FederationConstants federationConstants = bridgeConstants.getFederationConstants();
        Federation standardFederation = FederationTestUtils.getGenesisFederation(federationConstants);

        ActivationConfig.ForBlock irisActivations = ActivationConfigsForTest.iris300().forBlock(0L);

        FederationArgs p2shFedArgs = new FederationArgs(members, Instant.now(), 1L, bridgeConstants.getBtcParams());
        Federation p2shFed =
            FederationFactory.buildP2shErpFederation(p2shFedArgs, federationConstants.getErpFedPubKeysList(), federationConstants.getErpFedActivationDelay());

        return Stream.of(
            // active fed is standard and pegoutRequestsCount is equal to zero
            Arguments.of(
                irisActivations,
                standardFederation,
                0,
                Coin.valueOf(0L)
            ),
            // active fed is standard and pegoutRequestsCount is equal to one
            Arguments.of(
                irisActivations,
                standardFederation,
                1,
                Coin.valueOf(0L)
            ),
            // active fed is standard and there are many pegout requests
            Arguments.of(
                irisActivations,
                standardFederation,
                150,
                Coin.valueOf(0L)
            ),
            // active fed is p2sh and there are zero pegout requests
            Arguments.of(
                irisActivations,
                p2shFed,
                0,
                Coin.valueOf(0L)
            ),
            // active fed is p2sh and there is one pegout request
            Arguments.of(
                irisActivations,
                p2shFed,
                1,
                Coin.valueOf(0L)
            ),
            // active fed is p2sh and there are many pegout requests
            Arguments.of(
                irisActivations,
                p2shFed,
                150,
                Coin.valueOf(0L)
            )
        );
    }

    private static Stream<Arguments> getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP385(BridgeConstants bridgeConstants) {
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            PegTestUtils.createRandomBtcECKeys(7)
        );

        FederationConstants federationConstants = bridgeConstants.getFederationConstants();
        Federation standardFederation = FederationTestUtils.getGenesisFederation(federationConstants);

        ActivationConfig.ForBlock hopActivations = ActivationConfigsForTest.hop400().forBlock(0L);

        FederationArgs p2shFedArgs = new FederationArgs(members,
            Instant.now(),
            1L,
            bridgeConstants.getBtcParams()
        );
        Federation p2shFed = FederationFactory.buildP2shErpFederation(
            p2shFedArgs,
            federationConstants.getErpFedPubKeysList(),
            federationConstants.getErpFedActivationDelay()
        );

        return Stream.of(
            // active fed is standard and pegoutRequestsCount is equal to zero
            Arguments.of(
                hopActivations,
                standardFederation,
                0,
                Coin.valueOf(0L)
            ),
            // active fed is standard and pegoutRequestsCount is equal to one
            Arguments.of(
                hopActivations,
                standardFederation,
                1,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 237000L: 68600L)
            ),
            // active fed is standard and there are many pegout requests
            Arguments.of(
                hopActivations,
                standardFederation,
                150,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 713800L: 545400L)
            ),
            // active fed is p2sh and there are zero pegout requests
            Arguments.of(
                hopActivations,
                p2shFed,
                0,
                Coin.valueOf(0L)
            ),
            // active fed is p2sh and there is one pegout request
            Arguments.of(
                hopActivations,
                p2shFed,
                1,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 154600L: 154400L)
            ),
            // active fed is p2sh and there are many pegout requests
            Arguments.of(
                hopActivations,
                p2shFed,
                150,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 631400L: 631200L)
            )
        );
    }

    private static Stream<Arguments> getEstimatedFeesForNextPegOutEventArgsProvider_post_RSKIP385(BridgeConstants bridgeConstants) {
        ActivationConfig.ForBlock fingerrootActivations = ActivationConfigsForTest.fingerroot500().forBlock(0L);

        FederationConstants federationConstants = bridgeConstants.getFederationConstants();
        Federation standardFederation = FederationTestUtils.getGenesisFederation(federationConstants);
        List<FederationMember> members = FederationMember.getFederationMembersFromKeys(
            PegTestUtils.createRandomBtcECKeys(7)
        );

        FederationArgs p2shFedArgs = new FederationArgs(members,
            Instant.now(),
            1L,
            bridgeConstants.getBtcParams()
        );
        ErpFederation p2shFed =
            FederationFactory.buildP2shErpFederation(p2shFedArgs, federationConstants.getErpFedPubKeysList(), federationConstants.getErpFedActivationDelay());

        return Stream.of(
            // active fed is standard and pegoutRequestsCount is equal to zero
            Arguments.of(
                fingerrootActivations,
                standardFederation,
                0,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 233800L: 65400L)
            ),
            // active fed is standard and pegoutRequestsCount is equal to one
            Arguments.of(
                fingerrootActivations,
                standardFederation,
                1,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 237000L: 68600L)
            ),
            // active fed is standard and there are many pegout requests
            Arguments.of(
                fingerrootActivations,
                standardFederation,
                150,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 713800L: 545400L)
            ),
            // active fed is p2sh and there are zero pegout requests
            Arguments.of(
                fingerrootActivations,
                p2shFed,
                0,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 151400L: 151200L)
            ),
            // active fed is p2sh and there is one pegout request
            Arguments.of(
                fingerrootActivations,
                p2shFed,
                1,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 154600L: 154400L)
            ),
            // active fed is p2sh and there are many pegout requests
            Arguments.of(
                fingerrootActivations,
                p2shFed,
                150,
                Coin.valueOf(bridgeConstants instanceof BridgeMainNetConstants? 631400L: 631200L)
            )
        );
    }

    private static Stream<Arguments> getEstimatedFeesForNextPegOutEventArgsProvider_forSegwit() {
        ActivationConfig.ForBlock activations = ActivationConfigsForTest.all().forBlock(0L);
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        NetworkParameters networkParameters = bridgeConstants.getBtcParams();

        FederationConstants federationConstants = bridgeConstants.getFederationConstants();
        List<BtcECKey> erpFedPubKeys = federationConstants.getErpFedPubKeysList();
        long creationBlockNumber = 1L;
        Instant creationTime = Instant.now();
        long activationDelay = federationConstants.getErpFedActivationDelay();

        ErpFederation p2shFed = P2shErpFederationBuilder.builder()
            .withCreationTime(creationTime)
            .withCreationBlockNumber(creationBlockNumber)
            .withNetworkParameters(networkParameters)
            .withErpPublicKeys(erpFedPubKeys)
            .withErpActivationDelay(activationDelay)
            .build();

        ErpFederation p2shP2wshFed = P2shP2wshErpFederationBuilder.builder()
            .withCreationTime(Instant.now())
            .withCreationBlockNumber(1L)
            .withNetworkParameters(bridgeConstants.getBtcParams())
            .withErpPublicKeys(erpFedPubKeys)
            .withErpActivationDelay(federationConstants.getErpFedActivationDelay())
            .build();

        Address pegoutDestinationAddress1 = BitcoinTestUtils.createP2PKHAddress(networkParameters, "address1");
        Address pegoutDestinationAddress2 = BitcoinTestUtils.createP2PKHAddress(networkParameters, "address2");
        Address pegoutDestinationAddress3 = BitcoinTestUtils.createP2PKHAddress(networkParameters, "address3");
        Address pegoutDestinationAddress4 = BitcoinTestUtils.createP2PKHAddress(networkParameters, "address4");
        Coin minPegoutValue = bridgeConstants.getMinimumPegoutTxValue();

        ReleaseRequestQueue.Entry pegoutRequest1 = new ReleaseRequestQueue.Entry(pegoutDestinationAddress1, minPegoutValue);
        ReleaseRequestQueue.Entry pegoutRequest2 = new ReleaseRequestQueue.Entry(pegoutDestinationAddress2, minPegoutValue.add(Coin.valueOf(1000)));
        ReleaseRequestQueue.Entry pegoutRequest3 = new ReleaseRequestQueue.Entry(pegoutDestinationAddress3, minPegoutValue.add(Coin.valueOf(2000)));
        ReleaseRequestQueue.Entry bigPegoutRequest = new ReleaseRequestQueue.Entry(pegoutDestinationAddress4, Coin.COIN.multiply(10));

        Script p2shOutputScript = ScriptBuilder.createOutputScript(p2shFed.getAddress());
        UTXOBuilder p2shUtxoBuilder = UTXOBuilder.builder()
            .withOutputScript(p2shOutputScript)
            .withHeight(10);
        UTXO p2shFedUtxo1 = p2shUtxoBuilder.withValue(Coin.valueOf(8, 0)).build();
        UTXO p2shFedBigUtxoUtxo = p2shUtxoBuilder.withValue(Coin.valueOf(13, 0)).build();

        Script p2shP2wshOutputScript = ScriptBuilder.createOutputScript(p2shP2wshFed.getAddress());
        UTXOBuilder p2shP2wshUtxoBuilder = UTXOBuilder.builder()
            .withOutputScript(p2shP2wshOutputScript)
            .withHeight(10);
        UTXO p2shP2wshFedUtxo1 = p2shP2wshUtxoBuilder.withValue(Coin.valueOf(8, 0)).build();
        UTXO p2shP2wshFedBigUtxo = p2shP2wshUtxoBuilder.withValue(Coin.valueOf(13, 0)).build();

        return Stream.of(
            // active fed is p2sh and there are 0 pegout requests
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(9490L),
                List.of(),
                List.of(p2shFedUtxo1)
            ),
            // active fed is p2sh and there are 1 pegout requests
            // when there are no utxos available, the old logic should be executed and return the expected fee
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(17940L),
                List.of(pegoutRequest1),
                List.of()
            ),
            // active fed is p2sh and there is 1 pegout request
            // when there are utxos available, the new logic is used.
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(9830L),
                List.of(pegoutRequest1),
                List.of(p2shFedUtxo1)
            ),
            // active fed is p2sh and there are 2 pegout requests
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(10170L),
                List.of(pegoutRequest1, pegoutRequest2),
                List.of(p2shFedUtxo1)
            ),
            // active fed is p2sh and there are 3 pegout requests
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(10510L),
                List.of(pegoutRequest1, pegoutRequest2, pegoutRequest3),
                List.of(p2shFedUtxo1)
            ),

            // 2 inputs
            // active fed is p2sh and there is 2 pegout requests
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(18900L),
                List.of(pegoutRequest1, bigPegoutRequest),
                List.of(p2shFedUtxo1, p2shFedBigUtxoUtxo)
            ),
            // active fed is p2sh and there are 2 pegout requests
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(19240L),
                List.of(pegoutRequest1, pegoutRequest2, bigPegoutRequest),
                List.of(p2shFedUtxo1, p2shFedBigUtxoUtxo)
            ),
            // active fed is p2sh and there are 3 pegout requests
            Arguments.of(
                activations,
                p2shFed,
                Coin.valueOf(19580L),
                List.of(pegoutRequest1, pegoutRequest2, pegoutRequest3, bigPegoutRequest),
                List.of(p2shFedUtxo1, p2shFedBigUtxoUtxo)
            ),
            // active fed is p2sh p2wsh and there are 0 pegout requests
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(5670L), // Savings: 59.75%
                List.of(),
                List.of(p2shP2wshFedUtxo1)
            ),
            // active fed is p2sh p2wsh and there is 1 pegout requests
            // when there are no utxos available, it will call `calculatePegoutTxSize` and return the expected value for a segwit federation
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(9580L),
                List.of(pegoutRequest1),
                List.of()
            ),
            // active fed is p2sh p2wsh and there is 1 pegout requests
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(6010L), // Savings: 61.12%
                List.of(pegoutRequest1),
                List.of(p2shP2wshFedUtxo1)
            ),
            // active fed is p2sh p2wsh and there are 2 pegout requests
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(6350L), // Savings: 62.42%
                List.of(pegoutRequest1, pegoutRequest2),
                List.of(p2shP2wshFedUtxo1)
            ),
            // active fed is p2sh p2wsh and there are 3 pegout requests
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(6690L), // Savings: 63.66%
                List.of(pegoutRequest1, pegoutRequest2, pegoutRequest3),
                List.of(p2shP2wshFedUtxo1)
            ),

            // 2 inputs
            // active fed is segwit and there is 1 pegout requests
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(11260L), // Savings: 59.58%
                List.of(pegoutRequest1, bigPegoutRequest),
                List.of(p2shP2wshFedUtxo1, p2shP2wshFedBigUtxo)
            ),
            // active fed is p2sh p2wsh and there are 2 pegout requests
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(11600L), // Savings: 60.29%
                List.of(pegoutRequest1, pegoutRequest2, bigPegoutRequest),
                List.of(p2shP2wshFedUtxo1, p2shP2wshFedBigUtxo)
            ),
            // active fed is p2sh p2wsh and there are 3 pegout requests
            Arguments.of(
                activations,
                p2shP2wshFed,
                Coin.valueOf(11940L), // Savings: 60.97%
                List.of(pegoutRequest1, pegoutRequest2, pegoutRequest3, bigPegoutRequest),
                List.of(p2shP2wshFedUtxo1, p2shP2wshFedBigUtxo)
            )
        );
    }

    private static Stream<Arguments> getEstimatedFeesForNextPegOutEventArgsProvider() {
        BridgeRegTestConstants bridgeConstantsRegtest = new BridgeRegTestConstants();

        Stream<Arguments> preRskip271RegTest = getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP271(bridgeConstantsRegtest);
        Stream<Arguments> preRskip385RegTest = getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP385(bridgeConstantsRegtest);
        Stream<Arguments> postRskip385Regtest = getEstimatedFeesForNextPegOutEventArgsProvider_post_RSKIP385(bridgeConstantsRegtest);

        BridgeMainNetConstants bridgeMainNetConstants = BridgeMainNetConstants.getInstance();

        Stream<Arguments> preRskip271Mainnet = getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP271(bridgeMainNetConstants);
        Stream<Arguments> preRskip385Mainnet = getEstimatedFeesForNextPegOutEventArgsProvider_pre_RSKIP385(bridgeMainNetConstants);
        Stream<Arguments> postRskip385Mainnet = getEstimatedFeesForNextPegOutEventArgsProvider_post_RSKIP385(bridgeMainNetConstants);

        return Stream.of(
            preRskip271RegTest,
            preRskip385RegTest,
            postRskip385Regtest,
            preRskip271Mainnet,
            preRskip385Mainnet,
            postRskip385Mainnet
        ).flatMap(Function.identity());
    }

    @ParameterizedTest
    @MethodSource("getEstimatedFeesForNextPegOutEventArgsProvider")
    void getEstimatedFeesForNextPegOutEvent(
        ActivationConfig.ForBlock activations,
        Federation federation,
        int pegoutRequestsCount,
        Coin expectedEstimatedFee
    ) throws IOException {
        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);

        when(provider.getReleaseRequestQueueSize()).thenReturn(pegoutRequestsCount);
        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(federation);

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .build();

        // Act
        Coin estimatedFeesForNextPegOutEvent = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

        // Assert
        assertEquals(expectedEstimatedFee, estimatedFeesForNextPegOutEvent);
    }

    @ParameterizedTest
    @MethodSource("getEstimatedFeesForNextPegOutEventArgsProvider_forSegwit")
    void getEstimatedFeesForNextPegOutEvent_forSegwit(
        ActivationConfig.ForBlock activations,
        Federation federation,
        Coin expectedEstimatedFee,
        List<ReleaseRequestQueue.Entry> releaseRequestQueueEntries,
        List<UTXO> utxos
    ) throws IOException {
        // Arrange
        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        FederationStorageProvider federationStorageProviderMock = mock(FederationStorageProvider.class);

        ReleaseRequestQueue releaseRequestQueue = new ReleaseRequestQueue(releaseRequestQueueEntries);

        when(provider.getReleaseRequestQueue()).thenReturn(releaseRequestQueue);
        when(federationStorageProviderMock.getNewFederation(any(), any())).thenReturn(federation);

        when(federationStorageProviderMock.getNewFederationBtcUTXOs(any(), any())).thenReturn(utxos);

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.valueOf(10_000));

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withFederationStorageProvider(federationStorageProviderMock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withBridgeConstants(bridgeConstantsRegtest)
            .build();

        // Act
        Coin estimatedFeesForNextPegOutEvent = bridgeSupport.getEstimatedFeesForNextPegOutEvent();

        // Assert
        assertEquals(expectedEstimatedFee, estimatedFeesForNextPegOutEvent);
    }

    private void assertRefundInRegisterPegInVersion1(
        TxSenderAddressType lockSenderAddressType,
        Optional<Address> btcRefundAddress,
        List<ConsensusRule> consensusRules)
        throws IOException, RegisterBtcTransactionException, PeginInstructionsException {

        Federation genesisFederation = FederationTestUtils.getGenesisFederation(federationConstantsRegtest);
        Address federationAddress = genesisFederation.getAddress();

        // Arrange
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        for (ConsensusRule consensusRule : consensusRules) {
            when(activations.isActive(consensusRule)).thenReturn(true);
        }

        repository = createRepository();

        BtcECKey srcKey1 = new BtcECKey();
        ECKey key = ECKey.fromPublicOnly(srcKey1.getPubKey());
        RskAddress rskAddress = new RskAddress(key.getAddress());
        Address btcSenderAddress = null;
        if (lockSenderAddressType != TxSenderAddressType.UNKNOWN) {
            btcSenderAddress = srcKey1.toAddress(btcRegTestParams);
        }

        BtcTransaction btcTx = new BtcTransaction(btcRegTestParams);
        btcTx.addOutput(Coin.COIN.multiply(10), federationAddress);
        btcTx.addInput(BitcoinTestUtils.createHash(1), 0, new Script(new byte[]{}));

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);

        PegoutsWaitingForConfirmations pegoutsWaitingForConfirmations = new PegoutsWaitingForConfirmations(new HashSet<>());
        when(provider.getPegoutsWaitingForConfirmations()).thenReturn(pegoutsWaitingForConfirmations);
        LockingCapSupport lockingCapSupportMock = mock(LockingCapSupport.class);
        when(lockingCapSupportMock.getLockingCap()).thenReturn(Optional.of(Coin.COIN.multiply(1)));

        BtcLockSenderProvider btcLockSenderProvider = getBtcLockSenderProvider(lockSenderAddressType, btcSenderAddress, rskAddress);

        if (!btcRefundAddress.isPresent()  && btcSenderAddress != null) {
            btcRefundAddress = Optional.of(btcSenderAddress);
        }
        PeginInstructionsProvider peginInstructionsProvider = getPeginInstructionsProviderForVersion1(
            rskAddress,
            btcRefundAddress
        );

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        federationSupport = federationSupportBuilder
            .withFederationConstants(federationConstantsRegtest)
            .withActivations(activations)
            .build();

        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(btcLockSenderProvider)
            .withPeginInstructionsProvider(peginInstructionsProvider)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withLockingCapSupport(lockingCapSupportMock)
            .build();

        // Act
        bridgeSupport.registerPegIn(btcTx, PegTestUtils.createHash3(1), 0);

        // Assert
        if (lockSenderAddressType == TxSenderAddressType.UNKNOWN && btcRefundAddress.isEmpty()) {
            // Unknown sender and no refund address. Can't refund
            assertEquals(0, pegoutsWaitingForConfirmations.getEntries().size());
        } else {
            assertEquals(1, pegoutsWaitingForConfirmations.getEntries().size());

            // Check rejection tx input was created from btc tx and sent to the btc refund address indicated by the user
            boolean successfulRejection = false;
            for (PegoutsWaitingForConfirmations.Entry e : pegoutsWaitingForConfirmations.getEntries()) {
                BtcTransaction refundTx = e.getBtcTransaction();
                if (refundTx.getInput(0).getOutpoint().getHash() == btcTx.getHash() &&
                    refundTx.getOutput(0).getScriptPubKey().getToAddress(btcRegTestParams).equals(btcRefundAddress.get())) {
                    successfulRejection = true;
                    break;
                }
            }

            assertTrue(successfulRejection);
        }
    }

    private void assertLockingCap(
        boolean shouldLock,
        boolean isLockingCapEnabled,
        Coin lockingCap,
        Coin amountSentToNewFed,
        Coin amountSentToOldFed,
        Coin amountInNewFed,
        Coin amountInOldFed) throws BlockStoreException, IOException, BridgeIllegalArgumentException {

        // Configure if locking cap should be evaluated
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP134)).thenReturn(isLockingCapEnabled);

        BridgeConstants bridgeConstants = mock(BridgeConstants.class);
        when(bridgeConstants.getMinimumPeginTxValue(activations)).thenReturn(Coin.SATOSHI);
        when(bridgeConstants.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_MAINNET));
        when(bridgeConstants.getBtc2RskMinimumAcceptableConfirmations()).thenReturn(1);
        when(bridgeConstants.getMaxRbtc()).thenReturn(BridgeMainNetConstants.getInstance().getMaxRbtc());

        FederationConstants federationConstants = mock(FederationConstants.class);
        when(federationConstants.getBtcParams()).thenReturn(NetworkParameters.fromID(NetworkParameters.ID_MAINNET));
        when(federationConstants.getOldFederationAddress()).thenReturn(BridgeMainNetConstants.getInstance().getFederationConstants().getOldFederationAddress());
        when(bridgeConstants.getFederationConstants()).thenReturn(federationConstants);

        // Configure locking cap
        LockingCapConstants lockingCapConstants = mock(LockingCapConstants.class);
        when(lockingCapConstants.getInitialValue()).thenReturn(lockingCap);

        repository = createRepository();

        LockingCapStorageProvider lockingCapStorageProvider = new LockingCapStorageProviderImpl(new BridgeStorageAccessorImpl(repository));
        lockingCapSupport = new LockingCapSupportImpl(lockingCapStorageProvider, activations, lockingCapConstants, signatureCache);

        Federation oldFederation = PegTestUtils.createSimpleActiveFederation(bridgeConstants);

        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            bridgeConstants.getBtcParams(),
            activations
        );
        federationStorageProvider = createFederationStorageProvider(repository);
        // We need a random new fed
        federationStorageProvider.setNewFederation(PegTestUtils.createFederation(
            bridgeConstants,
            Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fb01")),
                BtcECKey.fromPrivate(Hex.decode("fb02")),
                BtcECKey.fromPrivate(Hex.decode("fb03"))
            )
        ));
        // Use genesis fed as old
        federationStorageProvider.setOldFederation(oldFederation);

        Coin currentFunds = Coin.ZERO;

        // Configure existing utxos in both federations
        if (amountInOldFed != null) {
            Script outputScript = new Script(EMPTY_BYTE_ARRAY);
            Sha256Hash transactionHash = Sha256Hash.wrap(TestUtils.generateBytes("amountInOldFed", 32));
            UTXO utxo = UTXOBuilder.builder()
                .withTransactionHash(transactionHash)
                .withValue(amountInOldFed)
                .withHeight(1)
                .withOutputScript(outputScript)
                .build();
            federationStorageProvider.getOldFederationBtcUTXOs().add(utxo);
            currentFunds = currentFunds.add(amountInOldFed);
        }
        if (amountInNewFed != null) {
            Script outputScript = new Script(EMPTY_BYTE_ARRAY);
            Sha256Hash transactionHash = Sha256Hash.wrap(TestUtils.generateBytes("amountInNewFed", 32));
            UTXO utxo = UTXOBuilder.builder()
                .withTransactionHash(transactionHash)
                .withValue(amountInNewFed)
                .withHeight(1)
                .withOutputScript(outputScript)
                .build();
            federationStorageProvider.getNewFederationBtcUTXOs(bridgeConstants.getBtcParams(), activations).add(utxo);
            currentFunds = currentFunds.add(amountInNewFed);
        }
        // Fund bridge in RBTC, substracting the funds that we are indicating were locked in the federations (which means a user locked)
        co.rsk.core.Coin bridgeBalance = LIMIT_MONETARY_BASE.subtract(co.rsk.core.Coin.fromBitcoin(currentFunds));
        repository.addBalance(BRIDGE_ADDR, bridgeBalance);

        // The locking cap shouldn't be surpassed by the initial configuration
        assertFalse(isLockingCapEnabled && currentFunds.isGreaterThan(lockingCap));

        Coin lockValue = Coin.ZERO;
        int newUtxos = 0;

        // Create transaction setting the outputs to the configured values
        BtcTransaction peginTx = new BtcTransaction(bridgeConstants.getBtcParams());
        if (amountSentToOldFed != null) {
            peginTx.addOutput(amountSentToOldFed, federationStorageProvider.getOldFederation(bridgeConstants.getFederationConstants(), activations).getAddress());
            lockValue = lockValue.add(amountSentToOldFed);
            newUtxos++;
        }
        if (amountSentToNewFed != null) {
            peginTx.addOutput(amountSentToNewFed, federationStorageProvider.getNewFederation(bridgeConstants.getFederationConstants(), activations).getAddress());
            lockValue = lockValue.add(amountSentToNewFed);
            newUtxos++;
        }
        BtcECKey srcKey = new BtcECKey();
        peginTx.addInput(
            BitcoinTestUtils.createHash(1),
            0,
            ScriptBuilder.createInputScript(null, srcKey)
        );

        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(peginTx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(bridgeConstants.getBtcParams(), bits, hashes, 1);
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(new ArrayList<>());
        co.rsk.bitcoinj.core.BtcBlock registerHeader = new co.rsk.bitcoinj.core.BtcBlock(
            bridgeConstants.getBtcParams(),
            1,
            BitcoinTestUtils.createHash(1),
            merkleRoot,
            1,
            1,
            1,
            new ArrayList<>()
        );

        BtcBlockStoreWithCache btcBlockStore = mock(BtcBlockStoreWithCache.class);
        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(repository, bridgeConstants, provider, activations)).thenReturn(btcBlockStore);

        Block executionBlock = Mockito.mock(Block.class);
        when(executionBlock.getNumber()).thenReturn(10L);

        // Get the tx sender public key
        byte[] data = peginTx.getInput(0).getScriptSig().getChunks().get(1).data;
        BtcECKey senderBtcKey = BtcECKey.fromPublicOnly(data);

        // Whitelist the address
        LockWhitelist whitelist = whitelistStorageProvider.getLockWhitelist(activations, btcRegTestParams);
        Address address = senderBtcKey.toAddress(bridgeConstants.getBtcParams());
        whitelist.put(address, new OneOffWhiteListEntry(address, lockValue));
        // The address is whitelisted
        assertThat(whitelist.isWhitelisted(address), is(true));

        feePerKbSupport = mock(FeePerKbSupport.class);
        when(feePerKbSupport.getFeePerKb()).thenReturn(Coin.MILLICOIN);

        federationSupport = federationSupportBuilder
            .withFederationConstants(bridgeConstants.getFederationConstants())
            .withFederationStorageProvider(federationStorageProvider)
            .withRskExecutionBlock(executionBlock)
            .withActivations(activations)
            .build();

        bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
            .withProvider(provider)
            .withRepository(repository)
            .withBtcLockSenderProvider(new BtcLockSenderProvider())
            .withPeginInstructionsProvider(new PeginInstructionsProvider())
            .withExecutionBlock(executionBlock)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activations)
            .withSignatureCache(signatureCache)
            .withFederationSupport(federationSupport)
            .withFeePerKbSupport(feePerKbSupport)
            .withWhitelistSupport(whitelistSupport)
            .withLockingCapSupport(lockingCapSupport)
            .build();

        // Simulate blockchain
        int height = 1;
        mockChainOfStoredBlocks(
            btcBlockStore,
            registerHeader,
            height + bridgeConstants.getBtc2RskMinimumAcceptableConfirmations(),
            height
        );

        Transaction rskTx = mock(Transaction.class);
        Keccak256 hash = new Keccak256(HashUtil.keccak256(new byte[]{}));
        when(rskTx.getHash()).thenReturn(hash);

        // Try to register tx
        bridgeSupport.registerBtcTransaction(rskTx, peginTx.bitcoinSerialize(), height, pmt.bitcoinSerialize());
        bridgeSupport.save();

        whitelist.consume(address);
        // If the address is no longer whitelisted, it means it was consumed, whether the lock was rejected by lockingCap or not
        assertThat(whitelist.isWhitelisted(address), is(false));

        // The Btc transaction should have been processed
        assertTrue(bridgeSupport.isBtcTxHashAlreadyProcessed(peginTx.getHash()));

        co.rsk.core.Coin totalAmountExpectedToHaveBeenLocked = co.rsk.core.Coin.fromBitcoin(shouldLock ? lockValue : Coin.ZERO);
        RskAddress srcKeyRskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPrivate(srcKey.getPrivKey()).getAddress());

        // Verify amount was locked
        assertEquals(totalAmountExpectedToHaveBeenLocked, repository.getBalance(srcKeyRskAddress));
        assertEquals(bridgeBalance.subtract(totalAmountExpectedToHaveBeenLocked), repository.getBalance(BRIDGE_ADDR));

        if (!shouldLock) {
            // Release tx should have been created directly to the signatures stack
            BtcTransaction pegoutBtcTx = provider.getPegoutsWaitingForConfirmations().getEntries().iterator().next().getBtcTransaction();
            assertNotNull(pegoutBtcTx);
            // returns the funds to the sender
            assertEquals(1, pegoutBtcTx.getOutputs().size());
            assertEquals(address, pegoutBtcTx.getOutputs().get(0).getAddressFromP2PKHScript(bridgeConstants.getBtcParams()));
            assertEquals(lockValue, pegoutBtcTx.getOutputs().get(0).getValue().add(pegoutBtcTx.getFee()));
            // Uses the same UTXO(s)
            assertEquals(newUtxos, pegoutBtcTx.getInputs().size());
            for (int i = 0; i < newUtxos; i++) {
                TransactionInput input = pegoutBtcTx.getInput(i);
                assertEquals(peginTx.getHash(), input.getOutpoint().getHash());
                assertEquals(i, input.getOutpoint().getIndex());
            }
        }
    }

    private BridgeSupport getBridgeSupport(BridgeConstants constants, BridgeStorageProvider provider, Repository track,
        BtcLockSenderProvider btcLockSenderProvider, PeginInstructionsProvider peginInstructionsProvider,
        Block executionBlock, BtcBlockStoreWithCache.Factory blockStoreFactory,
        ActivationConfig.ForBlock activations, SignatureCache signatureCache) {

        if (btcLockSenderProvider == null) {
            btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        }
        if (peginInstructionsProvider == null) {
            peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        }
        if (blockStoreFactory == null) {
            blockStoreFactory = mock(BtcBlockStoreWithCache.Factory.class);
        }
        feePerKbSupport = mock(FeePerKbSupport.class);
        federationSupport = mock(FederationSupport.class);

        return new BridgeSupport(
            constants,
            provider,
            mock(BridgeEventLogger.class),
            btcLockSenderProvider,
            peginInstructionsProvider,
            track,
            executionBlock,
            new Context(constants.getBtcParams()),
            feePerKbSupport,
            whitelistSupport,
            federationSupport,
            lockingCapSupport,
            unionBridgeSupport,
            blockStoreFactory,
            activations,
            signatureCache
        );
    }

    private BridgeSupport getBridgeSupportConfiguredToTestReceiveHeader(
        BtcBlock btcBlock,
        BtcBlockStoreWithCache btcBlockStore,
        BridgeStorageProvider provider,
        StoredBlock storedBlock,
        ActivationConfig.ForBlock activation
    ) throws BlockStoreException {
        return getBridgeSupportConfiguredToTestReceiveHeader(
            btcBlock,
            btcBlockStore,
            provider,
            storedBlock,
            mock(Block.class),
            activation
        );
    }

    private BridgeSupport getBridgeSupportConfiguredToTestReceiveHeader(
        BtcBlock btcBlock,
        BtcBlockStoreWithCache btcBlockStore,
        BridgeStorageProvider provider,
        StoredBlock storedBlock,
        Block rskBlock,
        ActivationConfig.ForBlock activation
    ) throws BlockStoreException {

        doReturn(10).when(storedBlock).getHeight();

        BtcBlock btcBlock2 = mock(BtcBlock.class);
        doReturn(BitcoinTestUtils.createHash(1)).when(btcBlock2).getHash();
        doReturn(btcBlock2).when(storedBlock).getHeader();

        doReturn(storedBlock).when(btcBlockStore).getChainHead();

        BtcBlockStoreWithCache.Factory mockFactory = mock(BtcBlockStoreWithCache.Factory.class);
        when(mockFactory.newInstance(any(), any(), any(), any())).thenReturn(btcBlockStore);

        when(btcBlock.getPrevBlockHash()).thenReturn(Sha256Hash.ZERO_HASH);
        when(btcBlockStore.get(Sha256Hash.ZERO_HASH)).thenReturn(storedBlock);

        when(rskBlock.getTimestamp()).thenReturn(1611169584L);

        return bridgeSupportBuilder
            .withBridgeConstants(bridgeConstantsRegtest)
            .withProvider(provider)
            .withExecutionBlock(rskBlock)
            .withBtcBlockStoreFactory(mockFactory)
            .withActivations(activation)
            .build();
    }

    private BtcLockSenderProvider getBtcLockSenderProvider(BtcLockSender.TxSenderAddressType txSenderAddressType, Address btcAddress, RskAddress rskAddress) {
        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        when(btcLockSender.getTxSenderAddressType()).thenReturn(txSenderAddressType);
        when(btcLockSender.getBTCAddress()).thenReturn(btcAddress);
        when(btcLockSender.getRskAddress()).thenReturn(rskAddress);

        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        return btcLockSenderProvider;
    }

    private PeginInstructionsProvider getPeginInstructionsProviderForVersion1(RskAddress rskDestinationAddress, Optional<Address> btcRefundAddress)
        throws PeginInstructionsException {
        PeginInstructionsVersion1 peginInstructions = mock(PeginInstructionsVersion1.class);
        when(peginInstructions.getProtocolVersion()).thenReturn(1);
        when(peginInstructions.getRskDestinationAddress()).thenReturn(rskDestinationAddress);
        when(peginInstructions.getBtcRefundAddress()).thenReturn(btcRefundAddress);

        PeginInstructionsProvider peginInstructionsProvider = mock(PeginInstructionsProvider.class);
        when(peginInstructionsProvider.buildPeginInstructions(any())).thenReturn(Optional.of(peginInstructions));

        return peginInstructionsProvider;
    }

    private FederationStorageProvider createFederationStorageProvider(Repository repository) {
        bridgeStorageAccessor = new BridgeStorageAccessorImpl(repository);
        return new FederationStorageProviderImpl(bridgeStorageAccessor);
    }
}
