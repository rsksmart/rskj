package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import java.time.Instant;
import java.util.List;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.function.Function;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BridgeUtilsLegacyTest {

    private ActivationConfig.ForBlock activations;
    private BridgeConstants bridgeConstantsRegtest;
    private BridgeConstants bridgeConstantsMainnet;

    @BeforeEach
    void setup() {
        activations = mock(ActivationConfig.ForBlock.class);
        bridgeConstantsRegtest = BridgeRegTestConstants.getInstance();
        bridgeConstantsMainnet = BridgeMainNetConstants.getInstance();
    }

    @Test
    void deserializeBtcAddressWithVersionLegacy_after_rskip284() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        NetworkParameters btcParams = bridgeConstantsRegtest.getBtcParams();
        Assertions.assertThrows(DeprecatedMethodCallException.class, () -> BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
                btcParams,
                activations,
                new byte[]{1}
        ));
    }

    @Test
    void deserializeBtcAddressWithVersionLegacy_null_bytes() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        Assertions.assertThrows(BridgeIllegalArgumentException.class, () -> BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
                bridgeConstantsRegtest.getBtcParams(),
                activations,
                null
        ));
    }

    @Test
    void deserializeBtcAddressWithVersionLegacy_empty_bytes() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        Assertions.assertThrows(BridgeIllegalArgumentException.class, () -> BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
                bridgeConstantsRegtest.getBtcParams(),
                activations,
                new byte[]{}
        ));
    }

    @Test
    void deserializeBtcAddressWithVersionLegacy_p2pkh_testnet() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        Address address = BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsRegtest.getBtcParams(),
            activations,
            addressBytes
        );

        Assertions.assertEquals(111, address.getVersion());
        Assertions.assertArrayEquals(Hex.decode(addressHash160Hex), address.getHash160());
        Assertions.assertEquals("mmWFbkYYKCT9jvCUzJD9XoVjSkfachVpMs", address.toBase58());
    }

    @Test
    void deserializeBtcAddressWithVersionLegacy_p2pkh_testnet_wrong_network() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        NetworkParameters btcParams = bridgeConstantsMainnet.getBtcParams();
        Assertions.assertThrows(IllegalArgumentException.class, () -> BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
                btcParams,
                activations,
                addressBytes
        ));
    }

    @Test
    void deserializeBtcAddressWithVersionLegacy_p2sh_testnet() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "c4"; // Testnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        NetworkParameters btcParams = bridgeConstantsRegtest.getBtcParams();
        // Should give an invalid version number given the way it's converting from bytes[] to int
        Assertions.assertThrows(IllegalArgumentException.class, () -> BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
                btcParams,
                activations,
                addressBytes
        ));
    }

    @Test
    void deserializeBtcAddressWithVersionLegacy_p2sh_testnet_wrong_network() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "c4"; // Testnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        NetworkParameters btcParams = bridgeConstantsMainnet.getBtcParams();
        // Should give an invalid version number given the way it's converting from bytes[] to int
        Assertions.assertThrows(IllegalArgumentException.class, () -> BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
                btcParams,
                activations,
                addressBytes
        ));
    }

    @Test
    void deserializeBtcAddressWithVersionLegacy_p2pkh_mainnet() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "00"; // Mainnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        Address address = BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsMainnet.getBtcParams(),
            activations,
            addressBytes
        );

        Assertions.assertEquals(0, address.getVersion());
        Assertions.assertArrayEquals(Hex.decode(addressHash160Hex), address.getHash160());
        Assertions.assertEquals("16zJJhTZWB1txoisGjEmhtHQam4sikpTd2", address.toBase58());
    }

    @Test
    void deserializeBtcAddressWithVersionLegacy_p2pkh_mainnet_wrong_network() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "00"; // Mainnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        NetworkParameters btcParams = bridgeConstantsRegtest.getBtcParams();
        Assertions.assertThrows(IllegalArgumentException.class, () -> BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
                btcParams,
                activations,
                addressBytes
        ));
    }

    @Test
    void deserializeBtcAddressWithVersionLegacy_p2sh_mainnet() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "05"; // Mainnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        Address address = BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsMainnet.getBtcParams(),
            activations,
            addressBytes
        );

        Assertions.assertEquals(5, address.getVersion());
        Assertions.assertArrayEquals(Hex.decode(addressHash160Hex), address.getHash160());
        Assertions.assertEquals("37gKEEx145LH3yRJPpuN8WeLjHMbJJo8vn", address.toBase58());
    }

    @Test
    void deserializeBtcAddressWithVersionLegacy_p2sh_mainnet_wrong_network() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "05"; // Mainnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        NetworkParameters btcParams = bridgeConstantsRegtest.getBtcParams();
        Assertions.assertThrows(IllegalArgumentException.class, () -> BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
                btcParams,
                activations,
                addressBytes
        ));

//        Assertions.assertEquals(5, address.getVersion());
//        Assertions.assertArrayEquals(Hex.decode(addressHash160Hex), address.getHash160());
//        Assertions.assertEquals("37gKEEx145LH3yRJPpuN8WeLjHMbJJo8vn", address.toBase58());
    }

    @Test
    void deserializeBtcAddressWithVersionLegacy_with_extra_bytes() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        String extraData = "0000aaaaeeee1111ffff";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex).concat(extraData));

        // The extra data should be ignored
        Address address = BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsRegtest.getBtcParams(),
            activations,
            addressBytes
        );

        Assertions.assertEquals(111, address.getVersion());
        Assertions.assertArrayEquals(Hex.decode(addressHash160Hex), address.getHash160());
        Assertions.assertEquals("mmWFbkYYKCT9jvCUzJD9XoVjSkfachVpMs", address.toBase58());
    }

    @Test
    void deserializeBtcAddressWithVersionLegacy_invalid_address_hash() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        NetworkParameters btcParams = bridgeConstantsRegtest.getBtcParams();
        // Should fail when trying to copy the address hash
        Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
                btcParams,
                activations,
                addressBytes
        ));
    }

    private void testGetAmountSentToAddress(
        BridgeConstants constants,
        BtcTransactionProvider btcTransactionProvider,
        Coin expectedValue
    ) {
        SimpleBtcTransaction simpleBtcTransaction = btcTransactionProvider.provide(constants);
        BtcTransaction btcTx = simpleBtcTransaction.getBtcTransaction();
        Address address = simpleBtcTransaction.getDestinationAddress();
        // Add output to a random btc address to test that only output sent to the given address
        // are being taken into account
        btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomP2PKHBtcAddress(constants.getBtcParams()));
        Assertions.assertEquals(
            expectedValue,
            BridgeUtilsLegacy.getAmountSentToAddress(
                activations,
                constants.getBtcParams(),
                btcTx,
                address
            )
        );
    }

    @Test
    void getAmountSentToAddress_ok() {
        Coin expectedResult = Coin.COIN.multiply(2);
        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
            Address btcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
            btcTx.addOutput(Coin.COIN, btcAddress);
            btcTx.addOutput(Coin.COIN, btcAddress);
            return new SimpleBtcTransaction(btcTx, btcAddress);
        };
        testGetAmountSentToAddress(bridgeConstantsRegtest, btcTransactionProvider, expectedResult);
        testGetAmountSentToAddress(bridgeConstantsMainnet, btcTransactionProvider, expectedResult);
    }

    @Test
    void getAmountSentToAddress_no_outputs() {
        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
            Address btcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
            return new SimpleBtcTransaction(btcTx, btcAddress);
        };
        testGetAmountSentToAddress(bridgeConstantsRegtest, btcTransactionProvider, Coin.ZERO);
        testGetAmountSentToAddress(bridgeConstantsMainnet, btcTransactionProvider, Coin.ZERO);
    }

    @Test
    void getAmountSentToAddress_zero_amount() {
        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
            Address btcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
            btcTx.addOutput(Coin.ZERO, btcAddress);
            return new SimpleBtcTransaction(btcTx, btcAddress);
        };
        testGetAmountSentToAddress(bridgeConstantsRegtest, btcTransactionProvider, Coin.ZERO);
        testGetAmountSentToAddress(bridgeConstantsMainnet, btcTransactionProvider, Coin.ZERO);
    }

    @Test
    void getAmountSentToAddress_after_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
            Address btcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
            btcTx.addOutput(Coin.ZERO, btcAddress);
            return new SimpleBtcTransaction(btcTx, btcAddress);
        };

        Assertions.assertThrows(DeprecatedMethodCallException.class, () -> testGetAmountSentToAddress(bridgeConstantsRegtest, btcTransactionProvider, null));
    }

    private void testGetUTXOsSentToAddress(
        BridgeConstants bridgeConstants,
        BtcTransactionProvider btcTransactionProvider,
        Function<BtcTransaction, List<UTXO>> expectedResult
    ) {
        SimpleBtcTransaction simpleBtcTransaction = btcTransactionProvider.provide(bridgeConstants);
        BtcTransaction btcTx = simpleBtcTransaction.getBtcTransaction();
        Address address = simpleBtcTransaction.getDestinationAddress();

        // Add output to a random btc address to test that only utxos sent to the given address are being returned
        btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()));
        List<UTXO> foundUTXOs = BridgeUtilsLegacy.getUTXOsSentToAddress(
            activations,
            bridgeConstants.getBtcParams(),
            btcTx,
            address
        );

        List<UTXO> expectedUTXOs = expectedResult.apply(btcTx);
        Assertions.assertArrayEquals(expectedUTXOs.toArray(), foundUTXOs.toArray());

        Coin amount = foundUTXOs.stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
        Coin expectedAmount = expectedUTXOs.stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
        Assertions.assertEquals(amount, expectedAmount);
    }

    @Test
    void getUTXOsSentToAddress_one_utxo_sent_to_given_address() {
        Function<BtcTransaction, List<UTXO>> expectedResult = btcTx -> {
            List<UTXO> expectedUTXOs = new ArrayList<>();
            expectedUTXOs.add(PegTestUtils.createUTXO(btcTx.getHash(), 0, Coin.COIN));
            return expectedUTXOs;
        };

        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
            Address btcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
            btcTx.addOutput(Coin.COIN, btcAddress);
            return new SimpleBtcTransaction(btcTx, btcAddress);
        };
        testGetUTXOsSentToAddress(
            bridgeConstantsRegtest,
            btcTransactionProvider,
            expectedResult
        );
        testGetUTXOsSentToAddress(
            bridgeConstantsMainnet,
            btcTransactionProvider,
            expectedResult
        );
    }

    @Test
    void getUTXOsSentToAddress_no_utxos_to_given_address() {
        Function<BtcTransaction, List<UTXO>> expectedResult = btcTx -> {
            List<UTXO> expectedUTXOs = new ArrayList<>();
            return expectedUTXOs;
        };
        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
            Address btcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
            return new SimpleBtcTransaction(btcTx, btcAddress);
        };
        testGetUTXOsSentToAddress(
            bridgeConstantsRegtest,
            btcTransactionProvider,
            expectedResult
        );
        testGetUTXOsSentToAddress(
            bridgeConstantsMainnet,
            btcTransactionProvider,
            expectedResult
        );
    }

    @Test
    void getUTXOsSentToAddress_multiple_utxos_sent_to_given_address() {
        Function<BtcTransaction, List<UTXO>> expectedResult = btcTx -> {
            List<UTXO> expectedUTXOs = new ArrayList<>();
            expectedUTXOs.add(PegTestUtils.createUTXO(btcTx.getHash(), 6, Coin.COIN));
            expectedUTXOs.add(PegTestUtils.createUTXO(btcTx.getHash(), 7, Coin.COIN));
            expectedUTXOs.add(PegTestUtils.createUTXO(btcTx.getHash(), 8, Coin.COIN));
            return expectedUTXOs;
        };

        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());

            btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()));
            btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()));
            btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()));
            btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()));
            btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()));
            btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams()));

            Address btcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
            btcTx.addOutput(Coin.COIN, btcAddress);
            btcTx.addOutput(Coin.COIN, btcAddress);
            btcTx.addOutput(Coin.COIN, btcAddress);

            return new SimpleBtcTransaction(btcTx, btcAddress);
        };
        testGetUTXOsSentToAddress(
            bridgeConstantsRegtest,
            btcTransactionProvider,
            expectedResult
        );
        testGetUTXOsSentToAddress(
            bridgeConstantsMainnet,
            btcTransactionProvider,
            expectedResult
        );
    }

    @Test
    void getUTXOsSentToAddress_after_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
            Address btcAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstants.getBtcParams());
            btcTx.addOutput(Coin.COIN, btcAddress);
            return new SimpleBtcTransaction(btcTx, btcAddress);
        };

        Assertions.assertThrows(DeprecatedMethodCallException.class, () -> testGetUTXOsSentToAddress(
                bridgeConstantsRegtest,
                btcTransactionProvider,
                null
        ));
    }

    @Test
    void calculatePegoutTxSize_before_rskip_271() {
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        Federation federation = FederationFactory.buildStandardMultiSigFederation(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            bridgeConstantsRegtest.getBtcParams()
        );

        int pegoutTxSize = BridgeUtilsLegacy.calculatePegoutTxSize(activations, federation, 2, 2);

        // The difference between the calculated size and a real tx size should be smaller than 2% in any direction
        int origTxSize = 2076; // Data for 2 inputs, 2 outputs From https://www.blockchain.com/btc/tx/e92cab54ecf738a00083fd8990515247aa3404df4f76ec358d9fe87d95102ae4
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .02;

        Assertions.assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    void calculatePegoutTxSize_after_rskip_271() {
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        Federation federation = FederationFactory.buildStandardMultiSigFederation(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            bridgeConstantsRegtest.getBtcParams()
        );

        Assertions.assertThrows(DeprecatedMethodCallException.class, () -> BridgeUtilsLegacy.calculatePegoutTxSize(activations, federation, 2, 2));
    }

    @Test
    void calculatePegoutTxSize_ZeroInput_ZeroOutput() {
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        Federation federation = FederationFactory.buildStandardMultiSigFederation(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            bridgeConstantsRegtest.getBtcParams()
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> BridgeUtilsLegacy.calculatePegoutTxSize(activations, federation, 0, 0));
    }

    private class SimpleBtcTransaction {
        private BtcTransaction btcTransaction;
        private Address destinationAddress;

        public SimpleBtcTransaction(BtcTransaction btcTransaction, Address destinationAddress) {
            this.btcTransaction = btcTransaction;
            this.destinationAddress = destinationAddress;
        }

        public BtcTransaction getBtcTransaction() {
            return btcTransaction;
        }

        public Address getDestinationAddress() {
            return destinationAddress;
        }
    }

    private interface BtcTransactionProvider {
        SimpleBtcTransaction provide(BridgeConstants bridgeConstants);
    }
}
