package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.PartialMerkleTree;
import co.rsk.bitcoinj.core.ScriptException;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.TransactionWitness;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.FastBridgeRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.store.BlockStoreException;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.BridgeTestNetConstants;
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
import org.ethereum.crypto.ECKey;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static co.rsk.peg.PegTestUtils.createBech32Output;
import static co.rsk.peg.PegTestUtils.createP2pkhOutput;
import static co.rsk.peg.PegTestUtils.createP2shOutput;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

public class BridgeSupportFlyoverTest extends BridgeSupportTestBase {
    @Before
    public void setUpOnEachTest() {
        activations = mock(ActivationConfig.ForBlock.class);
        bridgeConstants = BridgeRegTestConstants.getInstance();
        btcParams = bridgeConstants.getBtcParams();
        bridgeSupportBuilder = new BridgeSupportBuilder();
    }

    private BtcTransaction createBtcTransactionWithOutputToAddress(Coin amount, Address btcAddress) {
        return PegTestUtils.createBtcTransactionWithOutputToAddress(btcParams, amount, btcAddress);
    }

    private BigInteger testRegisterFastBridgeBtcTransaction(
        BridgeConstants constants,
        boolean isRskip293Active,
        BtcTransaction btcTx,
        List<Coin> valuesToSend,
        boolean includeActiveFederation,
        boolean includeRetiringFederation,
        boolean retiringFederationExists
    ) throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        BridgeConstants bridgeConstants = spy(constants);
        doReturn(BridgeRegTestConstants.getInstance().getFederationActivationAge()).when(bridgeConstants).getFederationActivationAge();

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP219)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(isRskip293Active);

        Context btcContext = mock(Context.class);
        doReturn(bridgeConstants.getBtcParams()).when(btcContext).getParams();

        Federation activeFederation = PegTestUtils.createFederation(bridgeConstants, "fa03", "fa04");
        Federation retiringFederation = PegTestUtils.createFederation(bridgeConstants, "fa01", "fa02");

        Repository repository = createRepository();

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.getNewFederation()).thenReturn(activeFederation);
        if (retiringFederationExists){
            when(provider.getOldFederation()).thenReturn(retiringFederation);
        }

        String userRefundBtcBase58Address;
        String lpBtcBase58Address;
        if (bridgeConstants.getBtcParamsString() == NetworkParameters.ID_MAINNET){
            userRefundBtcBase58Address = "1Q7vKWkB38irzQ3SoGCH9AATtkmjbZYdUb";
            lpBtcBase58Address = "12swp2wLiw7UaZJSEJgajYr5BNfudNDy2h";
        } else {
            userRefundBtcBase58Address = "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj";
            lpBtcBase58Address = "mipcBbFg9gMiCh81Kj8tqqdgoZub1ZJRfn";
        }

        Address userRefundBtcAddress = Address.fromBase58(
            bridgeConstants.getBtcParams(),
            userRefundBtcBase58Address
        );

        Address lpBtcAddress = Address.fromBase58(
            bridgeConstants.getBtcParams(),
            lpBtcBase58Address
        );

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

        BtcECKey srcKey = new BtcECKey();

        int height = 1;
        // Create header and PMT
        byte[] bits = new byte[1];
        bits[0] = 0x3f;

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
        Keccak256 fastBridgeDerivationHash = bridgeSupport.getFastBridgeDerivationHash(
            derivationArgumentsHash,
            userRefundBtcAddress,
            lpBtcAddress,
            lbcAddress
        );

        if (includeActiveFederation) {
            Address activeFederationAddress = PegTestUtils.getFastBridgeAddressFromRedeemScript(
                bridgeConstants,
                activeFederation.getRedeemScript(),
                Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
            );

            for (Coin value : valuesToSend) {
                btcTx.addOutput(value, activeFederationAddress);
            }
        }

        if (includeRetiringFederation) {
            Address retiringFederationAddress = PegTestUtils.getFastBridgeAddressFromRedeemScript(
                bridgeConstants,
                retiringFederation.getRedeemScript(),
                Sha256Hash.wrap(fastBridgeDerivationHash.getBytes())
            );
            for (Coin value : valuesToSend) {
                btcTx.addOutput(value, retiringFederationAddress);
            }
        }
        btcTx.addInput(
            Sha256Hash.wrap(fastBridgeDerivationHash.getBytes()),
            0, ScriptBuilder.createInputScript(null, srcKey));

        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(btcTx.getHash());
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

        if (result.signum() == 1) {
            co.rsk.core.Coin expectedBalance;
            if (
                activations.isActive(ConsensusRule.RSKIP293) &&
                    includeActiveFederation &&
                    retiringFederationExists &&
                    includeRetiringFederation
            ){
                expectedBalance = preCallLbcAddressBalance.add(co.rsk.core.Coin.fromBitcoin(
                    valuesToSend.stream().reduce(Coin::add).orElseThrow(IllegalStateException::new).multiply(2))
                );
            } else {
                expectedBalance =  preCallLbcAddressBalance.add(co.rsk.core.Coin.fromBitcoin(
                    valuesToSend.stream().reduce(Coin::add).orElseThrow(IllegalStateException::new)
                ));
            }
            co.rsk.core.Coin postCallLbcAddressBalance = repository.getBalance(lbcAddress);
            Assert.assertEquals(
                expectedBalance,
                postCallLbcAddressBalance
            );

            int calledExpected = activations.isActive(ConsensusRule.RSKIP293) &&
                                     retiringFederationExists &&
                                     includeRetiringFederation? 2:1;

            verify(provider, times(calledExpected)).markFastBridgeFederationDerivationHashAsUsed(
                btcTx.getHash(false),
                fastBridgeDerivationHash
            );

            verify(provider, times(1)).setFastBridgeFederationInformation(
                any()
            );

            if (activations.isActive(ConsensusRule.RSKIP293) && retiringFederationExists && includeRetiringFederation){
                verify(provider, times(1)).setFastBridgeRetiringFederationInformation(
                    any()
                );
            }
        }
        return result;
    }

    private BigInteger testRegisterFastBridgeBtcTransaction(
        BridgeConstants bridgeConstants,
        boolean isRskip293Active,
        BtcTransaction btcTx,
        Coin valueToSend,
        boolean includeActiveFederation,
        boolean includeRetiringFederation,
        boolean retiringFederationExists
    ) throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        return testRegisterFastBridgeBtcTransaction(
            bridgeConstants,
            isRskip293Active,
            btcTx,
            Arrays.asList(valueToSend),
            includeActiveFederation,
            includeRetiringFederation,
            retiringFederationExists
        );
    }

    private BigInteger testRegisterFastBridgeBtcTransaction_testnet(
        boolean isRskip293Active,
        Coin valueToSend,
        boolean includeActiveFederation,
        boolean includeRetiringFederation,
        boolean retiringFederationExists
    ) throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        return this.testRegisterFastBridgeBtcTransaction(
            bridgeConstants,
            isRskip293Active,
            new BtcTransaction(bridgeConstants.getBtcParams()),
            valueToSend,
            includeActiveFederation,
            includeRetiringFederation,
            retiringFederationExists
        );
    }

    private void testRegisterFastBridgeBtcTransaction(
        boolean isRSKIP293Active,
        BridgeConstants bridgeConstants,
        Coin expectedValue,
        Coin value,
        boolean includeActiveFederation,
        boolean includeRetiringFederation,
        boolean retiringFederationExists,
        TransactionOutput ... outputs
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            expectedValue,
            Arrays.asList(value),
            includeActiveFederation,
            includeRetiringFederation,
            retiringFederationExists,
            outputs
        );
    }

    private void testRegisterFastBridgeBtcTransaction(
        boolean isRSKIP293Active,
        BridgeConstants bridgeConstants,
        Coin expectedValue,
        List<Coin> values,
        boolean includeActiveFederation,
        boolean includeRetiringFederation,
        boolean retiringFederationExists,
        TransactionOutput ... outputs
    ) throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        for (TransactionOutput output: outputs) {
            tx.addOutput(output);
        }

        BigInteger result = testRegisterFastBridgeBtcTransaction(
            bridgeConstants,
            isRSKIP293Active,
            tx,
            values,
            includeActiveFederation,
            includeRetiringFederation,
            retiringFederationExists
        );
        assertEquals(
            co.rsk.core.Coin.fromBitcoin(expectedValue).asBigInteger(),
            result
        );
    }

    @Test(expected = ScriptException.class)
    public void registerFastBridgeBtcTransaction_with_output_to_bech32_before_RSKIP293_testnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = false;
        Coin valueToSend = Coin.COIN;
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            null,
            valueToSend,
            true,
            true,
            true,
            createBech32Output(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test(expected = ScriptException.class)
    public void registerFastBridgeBtcTransaction_with_output_to_bech32_before_RSKIP293_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = false;
        Coin valueToSend = Coin.COIN;
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            null,
            valueToSend,
            true,
            false,
            true,
            createBech32Output(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_output_to_bech32_after_RSKIP293_testnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = true;
        Coin valueToSend = Coin.COIN;
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.multiply(2),
            valueToSend,
            true,
            true,
            true,
            createBech32Output(bridgeConstants.getBtcParams(), valueToSend)
        );

        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.multiply(2),
            valueToSend,
            true,
            true,
            true,
            createBech32Output(bridgeConstants.getBtcParams(), valueToSend),
            createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend),
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_output_to_bech32_after_RSKIP293_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = true;
        Coin valueToSend = Coin.COIN;
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend,
            valueToSend,
            true,
            false,
            true,
            createBech32Output(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_output_to_P2PKH_before_RSKIP293_testnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = false;
        Coin valueToSend = Coin.COIN;
        BridgeConstants bridgeConstants = BridgeTestNetConstants.getInstance();
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend,
            valueToSend,
            true,
            true,
            true,
            createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_output_to_P2PKH_before_RSKIP293_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = false;
        Coin valueToSend = Coin.COIN;
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend,
            valueToSend,
            true,
            true,
            true,
            createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_output_to_P2PKH_after_RSKIP293_testnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = true;
        Coin valueToSend = Coin.COIN;
        BridgeConstants bridgeConstants = BridgeTestNetConstants.getInstance();
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.multiply(2),
            valueToSend,
            true,
            true,
            true,
            createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_output_to_P2PKH_after_RSKIP293_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = true;
        Coin valueToSend = Coin.COIN;
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.multiply(2),
            valueToSend,
            true,
            true,
            true,
            createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_output_to_P2SH_before_RSKIP293_testnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = false;
        BridgeConstants bridgeConstants = BridgeTestNetConstants.getInstance();
        Coin valueToSend = Coin.COIN;
        Coin expectedValue = Coin.COIN;

        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            expectedValue,
            valueToSend,
            true,
            true,
            true,
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_output_to_P2SH_after_RSKIP293_testnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = true;
        BridgeConstants bridgeConstants = BridgeTestNetConstants.getInstance();
        Coin valueToSend = Coin.COIN;
        Coin expectedValue = Coin.COIN.multiply(2);

        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            expectedValue,
            valueToSend,
            true,
            true,
            true,
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_output_to_P2SH_before_RSKIP293_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = false;
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        Coin valueToSend = Coin.COIN;
        Coin expectedValue = Coin.COIN;

        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            expectedValue,
            valueToSend,
            true,
            true,
            true,
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_output_to_P2SH_after_RSKIP293_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = true;
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        Coin valueToSend = Coin.COIN;
        Coin expectedValue = Coin.COIN.multiply(2);

        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            expectedValue,
            valueToSend,
            true,
            true,
            true,
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    private void registerFastBridgeBtcTransaction_with_multiple_output(
        boolean isRSKIP293Active,
        BridgeConstants bridgeConstants,
        Coin expectedValue,
        Coin valueToSend
    ) throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            expectedValue,
            valueToSend,
            true,
            true,
            true,
            createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend),
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );

        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            expectedValue,
            valueToSend,
            true,
            true,
            true,
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_multiple_output_before_RSKIP293_testnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = false;
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        Coin valueToSend = Coin.COIN;
        registerFastBridgeBtcTransaction_with_multiple_output(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend,
            valueToSend
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_multiple_output_after_RSKIP293_testnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = true;
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        Coin valueToSend = Coin.COIN;
        registerFastBridgeBtcTransaction_with_multiple_output(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.multiply(2),
            valueToSend
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_multiple_output_before_RSKIP293_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = false;
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        Coin valueToSend = Coin.COIN;
        registerFastBridgeBtcTransaction_with_multiple_output(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend,
            valueToSend
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_multiple_output_after_RSKIP293_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = true;
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        Coin valueToSend = Coin.COIN;
        registerFastBridgeBtcTransaction_with_multiple_output(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.multiply(2),
            valueToSend
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_outputs_to_active_and_retiring_federation_before_RSKIP_testnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = false;
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        Coin valueToSend = Coin.COIN;
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.add(Coin.CENT).add(Coin.SATOSHI),
            Arrays.asList(valueToSend, Coin.ZERO, Coin.CENT, Coin.SATOSHI),
            true,
            true,
            false,
            createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend),
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );

        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.add(Coin.CENT).add(Coin.SATOSHI),
            Arrays.asList(valueToSend, Coin.ZERO, Coin.CENT, Coin.SATOSHI),
            true,
            false,
            true,
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_outputs_to_active_and_retiring_federation_after_RSKIP_testnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = true;
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        Coin valueToSend = Coin.COIN;
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.multiply(6),
            Arrays.asList(valueToSend, valueToSend.multiply(2), valueToSend.multiply(3)),
            true,
            false,
            false,
            createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend),
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );

        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.multiply(12),
            Arrays.asList(valueToSend, valueToSend.multiply(2), valueToSend.multiply(3)),
            true,
            true,
            true,
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_outputs_to_active_and_retiring_federation_before_RSKIP_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = false;
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        Coin valueToSend = Coin.COIN;
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.multiply(6),
            Arrays.asList(valueToSend, valueToSend.multiply(2), valueToSend.multiply(3)),
            true,
            false,
            false,
            createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend),
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );

        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.multiply(6),
            Arrays.asList(valueToSend, valueToSend.multiply(2), valueToSend.multiply(3)),
            true,
            true,
            true,
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_with_outputs_to_active_and_retiring_federation_after_RSKIP_mainnet() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        boolean isRSKIP293Active = true;
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        Coin valueToSend = Coin.COIN;
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.multiply(6),
            Arrays.asList(valueToSend, valueToSend.multiply(2), valueToSend.multiply(3)),
            true,
            false,
            false,
            createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend),
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );

        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.multiply(12),
            Arrays.asList(valueToSend, valueToSend.multiply(2), valueToSend.multiply(3)),
            true,
            true,
            true,
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test(expected = ScriptException.class)
    public void registerFastBridgeBtcTransaction_with_output_to_all_type_address_before_RSKIP293_testnet() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        boolean isRSKIP293Active = false;
        Coin valueToSend = Coin.COIN;
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            null,
            Arrays.asList(valueToSend),
            true,
            true,
            true,
            createBech32Output(bridgeConstants.getBtcParams(), valueToSend),
            createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend),
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test()
    public void registerFastBridgeBtcTransaction_with_output_to_all_type_address_after_RSKIP293_testnet() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        boolean isRSKIP293Active = true;
        Coin valueToSend = Coin.COIN;
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.multiply(2),
            Arrays.asList(valueToSend),
            true,
            true,
            true,
            createBech32Output(bridgeConstants.getBtcParams(), valueToSend),
            createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend),
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test(expected = ScriptException.class)
    public void registerFastBridgeBtcTransaction_with_output_to_all_type_address_before_RSKIP293_mainnet() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        boolean isRSKIP293Active = false;
        Coin valueToSend = Coin.COIN;
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.add(Coin.CENT).add(Coin.SATOSHI),
            Arrays.asList(valueToSend, Coin.ZERO, Coin.CENT, Coin.SATOSHI),
            true,
            true,
            true,
            createBech32Output(bridgeConstants.getBtcParams(), valueToSend),
            createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend),
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test()
    public void registerFastBridgeBtcTransaction_with_output_to_all_type_address_after_RSKIP293_mainnet() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        boolean isRSKIP293Active = true;
        Coin valueToSend = Coin.COIN;
        BridgeConstants bridgeConstants = BridgeMainNetConstants.getInstance();
        testRegisterFastBridgeBtcTransaction(
            isRSKIP293Active,
            bridgeConstants,
            valueToSend.multiply(12),
            Arrays.asList(valueToSend, valueToSend.multiply(2), valueToSend.multiply(3)),
            true,
            true,
            true,
            createBech32Output(bridgeConstants.getBtcParams(), valueToSend),
            createP2pkhOutput(bridgeConstants.getBtcParams(), valueToSend),
            createP2shOutput(bridgeConstants.getBtcParams(), valueToSend)
        );
    }

    @Test
    public void registerFastBridgeBtcTransaction_amount_sent_is_below_minimum() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP219)).thenReturn(true);
        // Before RSKIP293 ACTIVATION
        Coin valueToSend = BridgeUtils.getMinimumPegInTxValue(activations, bridgeConstants).minus(Coin.CENT);

        BigInteger result = testRegisterFastBridgeBtcTransaction_testnet(
            false,
            valueToSend,
            true,
            false,
            true
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);

        result = testRegisterFastBridgeBtcTransaction_testnet(
            false,
            valueToSend,
            true,
            true,
            true
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);

        result = testRegisterFastBridgeBtcTransaction_testnet(
            false,
            valueToSend,
            true,
            true,
            false
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);

        // After RSKIP293 ACTIVATION
        valueToSend = BridgeUtils.getMinimumPegInTxValue(activations, bridgeConstants).minus(Coin.CENT);

        result = testRegisterFastBridgeBtcTransaction_testnet(
            true,
            valueToSend,
            true,
            false,
            true
        );
        Assert.assertEquals(
            FastBridgeTxResponseCodes.UNPROCESSABLE_TX_AMOUNT_SENT_BELOW_MINIMUM_ERROR.value()
            , result.longValue());

        result = testRegisterFastBridgeBtcTransaction_testnet(
            true,
            valueToSend,
            true,
            true,
            false
        );
        Assert.assertEquals(
            FastBridgeTxResponseCodes.UNPROCESSABLE_TX_AMOUNT_SENT_BELOW_MINIMUM_ERROR.value()
            , result.longValue());
    }

    @Test
    public void registerFastBridgeBtcTransaction_amount_sent_is_equal_to_minimum()
        throws BlockStoreException, IOException, BridgeIllegalArgumentException {

        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        Coin valueToSend = BridgeUtils.getMinimumPegInTxValue(activations, bridgeConstants);

        // Before RSKIP293 ACTIVATION
        BigInteger result = testRegisterFastBridgeBtcTransaction_testnet(
            false,
            valueToSend,
            true,
            false,
            true
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);

        result = testRegisterFastBridgeBtcTransaction_testnet(
            false,
            valueToSend,
            true,
            true,
            true
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);

        result = testRegisterFastBridgeBtcTransaction_testnet(
            false,
            valueToSend,
            true,
            true,
            false
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);

        // After RSKIP293 ACTIVATION
        result = testRegisterFastBridgeBtcTransaction_testnet(
            true,
            valueToSend,
            true,
            false,
            true
        );
        Assert.assertEquals(
            co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger()
            , result);

        result = testRegisterFastBridgeBtcTransaction_testnet(
            true,
            valueToSend,
            true,
            true,
            false
        );
        Assert.assertEquals(
            co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger()
            , result);

        result = testRegisterFastBridgeBtcTransaction_testnet(
            true,
            valueToSend,
            true,
            true,
            true
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend.multiply(2)).asBigInteger(), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_amount_sent_is_over_minimum()
        throws BlockStoreException, IOException, BridgeIllegalArgumentException {

        Coin valueToSend = Coin.COIN;

        BigInteger result = testRegisterFastBridgeBtcTransaction_testnet(
            false,
            valueToSend,
            true,
            false,
            true
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);

        result = testRegisterFastBridgeBtcTransaction_testnet(
            false,
            valueToSend,
            true,
            true,
            true
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);

        result = testRegisterFastBridgeBtcTransaction_testnet(
            false,
            valueToSend,
            true,
            true,
            false
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);

        // After RSKIP293 ACTIVATION
        result = testRegisterFastBridgeBtcTransaction_testnet(
            true,
            valueToSend,
            true,
            false,
            true
        );
        Assert.assertEquals(
            co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger()
            , result);

        result = testRegisterFastBridgeBtcTransaction_testnet(
            true,
            valueToSend,
            true,
            true,
            false
        );
        Assert.assertEquals(
            co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger()
            , result);

        result = testRegisterFastBridgeBtcTransaction_testnet(
            true,
            valueToSend,
            true,
            true,
            true
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend.multiply(2)).asBigInteger(), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_funds_sent_to_active_and_currently_retiring_fed_before_RSKIP293_activation()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException
    {
        Coin valueToSend = Coin.COIN;
        // send funds to both federations, the active federation and also the current retiring federation
        // but only the values send to the active federation should be processed before RSKIP293
        BigInteger result = testRegisterFastBridgeBtcTransaction_testnet(
            false,
            valueToSend,
            true,
            true,
            true
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_funds_sent_to_active_and_no_active_retiring_fed_before_RSKIP293_activation()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException
    {
        Coin valueToSend = Coin.COIN;
        // send funds to the active federation, and also to a retiring federation that is not active anymore
        BigInteger result = testRegisterFastBridgeBtcTransaction_testnet(
            false,
            valueToSend,
            true,
            true,
            false
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_funds_sent_to_current_retiring_fed_before_RSKIP293_activation()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException
    {
        Coin valueToSend = Coin.COIN;
        // send funds to current retiring federation
        BigInteger result = testRegisterFastBridgeBtcTransaction_testnet(
            false,
            valueToSend,
            false,
            true,
            true
        );
        Assert.assertEquals(FastBridgeTxResponseCodes.UNPROCESSABLE_TX_VALUE_ZERO_ERROR.value(), result.longValue());
    }

    @Test
    public void registerFastBridgeBtcTransaction_funds_sent_to_active_fed_before_RSKIP293_activation() throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        Coin valueToSend = Coin.COIN;
        // send funds to the active federation
        BigInteger result = testRegisterFastBridgeBtcTransaction_testnet(
            false,
            valueToSend,
            true,
            false,
            false
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_funds_sent_to_active_and_currently_retiring_fed_after_RSKIP293_activation() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        Coin valueToSend = Coin.COIN;
        // send funds to both federations, the active federation and current retiring federation
        BigInteger result = testRegisterFastBridgeBtcTransaction_testnet(
            true,
            valueToSend,
            true,
            true,
            true
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend.multiply(2)).asBigInteger(), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_no_retiring_federation_after_RSKIP293_activation() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        Coin valueToSend = Coin.COIN;
        // send funds to a retiring federation that is not active anymore
        BigInteger result = testRegisterFastBridgeBtcTransaction_testnet(
            true,
            valueToSend,
            false,
            true,
            false
        );
        Assert.assertEquals(FastBridgeTxResponseCodes.UNPROCESSABLE_TX_VALUE_ZERO_ERROR.value(), result.longValue());

        // send funds to the active federation, and also to a retiring federation that is not active anymore
        result = testRegisterFastBridgeBtcTransaction_testnet(
            true,
            valueToSend,
            true,
            true,
            false
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);
    }

    @Test
    public void registerFastBridgeBtcTransaction_funds_sent_to_retiring_federation_after_RSKIP293_activation() throws BlockStoreException, BridgeIllegalArgumentException, IOException {
        Coin valueToSend = Coin.COIN;
        // sent funds to current retiring federation
        BigInteger result = testRegisterFastBridgeBtcTransaction_testnet(
            true,
            valueToSend,
            false,
            true,
            true
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend).asBigInteger(), result);

        // sent funds to current retiring federation and the active federation
        result = testRegisterFastBridgeBtcTransaction_testnet(
            true,
            valueToSend,
            true,
            true,
            true
        );
        Assert.assertEquals(co.rsk.core.Coin.fromBitcoin(valueToSend.multiply(2)).asBigInteger(), result);

        // sends zero to the current retiring federation
        valueToSend = Coin.ZERO;
        result = testRegisterFastBridgeBtcTransaction_testnet(
            true,
            valueToSend,
            false,
            true,
            true
        );
        Assert.assertEquals(FastBridgeTxResponseCodes.UNPROCESSABLE_TX_VALUE_ZERO_ERROR.value(), result.longValue());
    }

    @Test
    public void registerFastBridgeBtcTransaction_is_not_contract()
        throws IOException, BlockStoreException, BridgeIllegalArgumentException {
        BridgeSupport bridgeSupport = bridgeSupportBuilder.withBridgeConstants(bridgeConstants).build();
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
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());


        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .withBridgeConstants(bridgeConstants)
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
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());


        BridgeSupport bridgeSupport = spy(bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .withBridgeConstants(bridgeConstants)
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
        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

        BridgeSupport bridgeSupport = spy(bridgeSupportBuilder
            .withProvider(provider)
            .withActivations(activations)
            .withBridgeConstants(bridgeConstants)
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
        when(btcContext.getParams()).thenReturn(bridgeConstants.getBtcParams());

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstants,
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

        doReturn(bridgeConstants.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
            any(Keccak256.class),
            any(Address.class),
            any(Address.class),
            any(RskAddress.class)
        );

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, new BtcECKey().toAddress(btcParams));
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
        when(btcContext.getParams()).thenReturn(bridgeConstants.getBtcParams());

        BridgeStorageProvider provider = mock(BridgeStorageProvider.class);
        when(provider.isFastBridgeFederationDerivationHashUsed(any(), any())).thenReturn(false).thenReturn(true);

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstants,
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

        doReturn(bridgeConstants.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
            any(Keccak256.class),
            any(Address.class),
            any(Address.class),
            any(RskAddress.class)
        );

        BtcTransaction tx = createBtcTransactionWithOutputToAddress(Coin.COIN, new BtcECKey().toAddress(btcParams));
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
        when(btcContext.getParams()).thenReturn(bridgeConstants.getBtcParams());

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstants,
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

        doReturn(bridgeConstants.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(Coin.COIN).when(bridgeSupport).getLockingCap();
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
            any(Keccak256.class),
            any(Address.class),
            any(Address.class),
            any(RskAddress.class)
        );

        Address btcAddress = Address.fromBase58(
            btcParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

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
        when(btcContext.getParams()).thenReturn(bridgeConstants.getBtcParams());

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstants,
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

        doReturn(bridgeConstants.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(Coin.COIN).when(bridgeSupport).getLockingCap();
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
            any(Keccak256.class),
            any(Address.class),
            any(Address.class),
            any(RskAddress.class)
        );

        Address btcAddress = Address.fromBase58(
            btcParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

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
            bridgeConstants,
            activations
        );

        BtcLockSender btcLockSender = mock(BtcLockSender.class);
        BtcLockSenderProvider btcLockSenderProvider = mock(BtcLockSenderProvider.class);
        when(btcLockSenderProvider.tryGetBtcLockSender(any())).thenReturn(Optional.of(btcLockSender));

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstants.getBtcParams());

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstants,
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

        doReturn(bridgeConstants.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
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
            btcParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

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
        when(btcContext.getParams()).thenReturn(bridgeConstants.getBtcParams());

        Repository repository = spy(createRepository());
        BridgeStorageProvider provider = new BridgeStorageProvider(
            repository,
            PrecompiledContracts.BRIDGE_ADDR,
            bridgeConstants,
            activations
        );

        FederationSupport federationSupportMock = mock(FederationSupport.class);
        doReturn(provider.getNewFederationBtcUTXOs()).when(federationSupportMock).getActiveFederationBtcUTXOs();

        BridgeSupport bridgeSupport = spy(new BridgeSupport(
            bridgeConstants,
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

        doReturn(bridgeConstants.getGenesisFederation()).when(bridgeSupport).getActiveFederation();
        doReturn(true).when(bridgeSupport).validationsForRegisterBtcTransaction(any(), anyInt(), any(), any());
        doReturn(PegTestUtils.createHash3(1)).when(bridgeSupport).getFastBridgeDerivationHash(
            any(Keccak256.class),
            any(Address.class),
            any(Address.class),
            any(RskAddress.class)
        );

        Address btcAddress = Address.fromBase58(
            btcParams,
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );

        ECKey key = ECKey.fromPublicOnly(new BtcECKey().getPubKey());
        RskAddress lbcAddress = new RskAddress(key.getAddress());

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

        Federation fed = bridgeConstants.getGenesisFederation();
        FederationSupport federationSupport = mock(FederationSupport.class);
        when(federationSupport.getActiveFederation()).thenReturn(fed);

        BridgeSupport bridgeSupport = new BridgeSupport(
            bridgeConstants,
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
            bridgeConstants.getGenesisFederation().getRedeemScript(),
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
            expectedFastBridgeFederationInformation.getFastBridgeFederationAddress(bridgeConstants.getBtcParams()),
            obtainedFastBridgeFedInfo.getFastBridgeFederationAddress(bridgeConstants.getBtcParams())
        );
    }

    @Test
    public void getFastBridgeWallet_ok() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Context btcContext = mock(Context.class);
        when(btcContext.getParams()).thenReturn(bridgeConstants.getBtcParams());

        BridgeSupport bridgeSupport = new BridgeSupport(
            bridgeConstants,
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

        Federation fed = bridgeConstants.getGenesisFederation();
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

        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(Coin.COIN,
            fastBridgeFederationInformation.getFastBridgeFederationAddress(
                bridgeConstants.getBtcParams()
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
            .withBridgeConstants(bridgeConstants)
            .withActivations(activations)
            .build();

        Address userRefundBtcAddress = Address.fromBase58(
            bridgeConstants.getBtcParams(),
            "mgy8yiUZYB7o9vvCu2Yi8GB3Vr32MQsyQJ"
        );
        byte[] userRefundBtcAddressBytes = BridgeUtils.serializeBtcAddressWithVersion(activations, userRefundBtcAddress);

        Address lpBtcAddress = Address.fromBase58(
            bridgeConstants.getBtcParams(),
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
            bridgeConstants,
            activations
        );
        BridgeSupport bridgeSupport = bridgeSupportBuilder
            .withBridgeConstants(bridgeConstants)
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
        bridgeSupport.saveFastBridgeActiveFederationDataInStorage(btcTxHash, derivationHash, fastBridgeFederationInformation,  utxos);

        bridgeSupport.save();

        Assert.assertEquals(1, provider.getNewFederationBtcUTXOs().size());
        assertEquals(utxo, provider.getNewFederationBtcUTXOs().get(0));
        Assert.assertTrue(provider.isFastBridgeFederationDerivationHashUsed(btcTxHash, derivationHash));
        Optional<FastBridgeFederationInformation> optionalFastBridgeFederationInformation = provider.getFastBridgeFederationInformation(fastBridgeScriptHash);
        Assert.assertTrue(optionalFastBridgeFederationInformation.isPresent());
        FastBridgeFederationInformation obtainedFastBridgeFederationInformation = optionalFastBridgeFederationInformation.get();
        Assert.assertEquals(fastBridgeFederationInformation.getDerivationHash(), obtainedFastBridgeFederationInformation.getDerivationHash() );
        Assert.assertArrayEquals(fastBridgeFederationInformation.getFederationRedeemScriptHash(), obtainedFastBridgeFederationInformation.getFederationRedeemScriptHash() );
    }

    protected Address getFastBridgeAddressFromRedeemScript(Script redeemScript, Sha256Hash derivationArgumentHash) {
        return PegTestUtils.getFastBridgeAddressFromRedeemScript(bridgeConstants, redeemScript, derivationArgumentHash);
    }

    protected Address getFastBridgeFederationAddress() {
        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            bridgeConstants.getGenesisFederation().getRedeemScript(),
            PegTestUtils.createHash(1)
        );

        Script fastBridgeP2SH = ScriptBuilder.createP2SHOutputScript(fastBridgeRedeemScript);
        return Address.fromP2SHScript(bridgeConstants.getBtcParams(), fastBridgeP2SH);
    }
}
