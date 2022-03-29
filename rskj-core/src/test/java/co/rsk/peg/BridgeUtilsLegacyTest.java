package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import org.apache.commons.lang3.tuple.Pair;
import java.time.Instant;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.function.Function;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BridgeUtilsLegacyTest {

    private ActivationConfig.ForBlock activations;
    private BridgeConstants bridgeConstantsRegtest;
    private BridgeConstants bridgeConstantsMainnet;

    @Before
    public void setup() {
        activations = mock(ActivationConfig.ForBlock.class);
        bridgeConstantsRegtest = BridgeRegTestConstants.getInstance();
        bridgeConstantsMainnet = BridgeMainNetConstants.getInstance();
    }

    @Test(expected = DeprecatedMethodCallException.class)
    public void deserializeBtcAddressWithVersionLegacy_after_rskip284() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsRegtest.getBtcParams(),
            activations,
            new byte[]{1}
        );
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void deserializeBtcAddressWithVersionLegacy_null_bytes() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsRegtest.getBtcParams(),
            activations,
            null
        );
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void deserializeBtcAddressWithVersionLegacy_empty_bytes() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsRegtest.getBtcParams(),
            activations,
            new byte[]{}
        );
    }

    @Test
    public void deserializeBtcAddressWithVersionLegacy_p2pkh_testnet() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        Address address = BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsRegtest.getBtcParams(),
            activations,
            addressBytes
        );

        Assert.assertEquals(111, address.getVersion());
        Assert.assertArrayEquals(Hex.decode(addressHash160Hex), address.getHash160());
        Assert.assertEquals("mmWFbkYYKCT9jvCUzJD9XoVjSkfachVpMs", address.toBase58());
    }

    @Test(expected = IllegalArgumentException.class)
    public void deserializeBtcAddressWithVersionLegacy_p2pkh_testnet_wrong_network() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsMainnet.getBtcParams(),
            activations,
            addressBytes
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void deserializeBtcAddressWithVersionLegacy_p2sh_testnet() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "c4"; // Testnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        // Should give an invalid version number given the way it's converting from bytes[] to int
        BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsRegtest.getBtcParams(),
            activations,
            addressBytes
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void deserializeBtcAddressWithVersionLegacy_p2sh_testnet_wrong_network() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "c4"; // Testnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        // Should give an invalid version number given the way it's converting from bytes[] to int
        BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsMainnet.getBtcParams(),
            activations,
            addressBytes
        );
    }

    @Test
    public void deserializeBtcAddressWithVersionLegacy_p2pkh_mainnet() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "00"; // Mainnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        Address address = BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsMainnet.getBtcParams(),
            activations,
            addressBytes
        );

        Assert.assertEquals(0, address.getVersion());
        Assert.assertArrayEquals(Hex.decode(addressHash160Hex), address.getHash160());
        Assert.assertEquals("16zJJhTZWB1txoisGjEmhtHQam4sikpTd2", address.toBase58());
    }

    @Test(expected = IllegalArgumentException.class)
    public void deserializeBtcAddressWithVersionLegacy_p2pkh_mainnet_wrong_network() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "00"; // Mainnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsRegtest.getBtcParams(),
            activations,
            addressBytes
        );
    }

    @Test
    public void deserializeBtcAddressWithVersionLegacy_p2sh_mainnet() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "05"; // Mainnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        Address address = BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsMainnet.getBtcParams(),
            activations,
            addressBytes
        );

        Assert.assertEquals(5, address.getVersion());
        Assert.assertArrayEquals(Hex.decode(addressHash160Hex), address.getHash160());
        Assert.assertEquals("37gKEEx145LH3yRJPpuN8WeLjHMbJJo8vn", address.toBase58());
    }

    @Test(expected = IllegalArgumentException.class)
    public void deserializeBtcAddressWithVersionLegacy_p2sh_mainnet_wrong_network() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "05"; // Mainnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        Address address = BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsRegtest.getBtcParams(),
            activations,
            addressBytes
        );

        Assert.assertEquals(5, address.getVersion());
        Assert.assertArrayEquals(Hex.decode(addressHash160Hex), address.getHash160());
        Assert.assertEquals("37gKEEx145LH3yRJPpuN8WeLjHMbJJo8vn", address.toBase58());
    }

    @Test
    public void deserializeBtcAddressWithVersionLegacy_with_extra_bytes() throws BridgeIllegalArgumentException {
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

        Assert.assertEquals(111, address.getVersion());
        Assert.assertArrayEquals(Hex.decode(addressHash160Hex), address.getHash160());
        Assert.assertEquals("mmWFbkYYKCT9jvCUzJD9XoVjSkfachVpMs", address.toBase58());
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void deserializeBtcAddressWithVersionLegacy_invalid_address_hash() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(false);

        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        // Should fail when trying to copy the address hash
        BridgeUtilsLegacy.deserializeBtcAddressWithVersionLegacy(
            bridgeConstantsRegtest.getBtcParams(),
            activations,
            addressBytes
        );
    }

    private void testGetAmountSentToAddress(
        BridgeConstants constants,
        BtcTransactionProvider btcTransactionProvider,
        Coin expectedValue
    ) {
        Pair<BtcTransaction, Address> pair = btcTransactionProvider.provide(constants);
        BtcTransaction btcTx = pair.getLeft();
        Address address = pair.getRight();
        // Add output to a random btc address to test that only output sent to the given address
        // are being taken into account
        btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomBtcAddress());
        Assert.assertEquals(
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
    public void getAmountSentToAddress_ok() {
        Coin expectedResult = Coin.COIN.multiply(2);
        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
            Address btcAddress = PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams());
            btcTx.addOutput(Coin.COIN, btcAddress);
            btcTx.addOutput(Coin.COIN, btcAddress);
            return Pair.of(btcTx, btcAddress);
        };
        testGetAmountSentToAddress(bridgeConstantsRegtest, btcTransactionProvider, expectedResult);
        testGetAmountSentToAddress(bridgeConstantsMainnet, btcTransactionProvider, expectedResult);
    }

    @Test
    public void getAmountSentToAddress_no_outputs() {
        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
            Address btcAddress = PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams());
            return Pair.of(btcTx, btcAddress);
        };
        testGetAmountSentToAddress(bridgeConstantsRegtest, btcTransactionProvider, Coin.ZERO);
        testGetAmountSentToAddress(bridgeConstantsMainnet, btcTransactionProvider, Coin.ZERO);
    }

    @Test
    public void getAmountSentToAddress_zero_amount() {
        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
            Address btcAddress = PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams());
            btcTx.addOutput(Coin.ZERO, btcAddress);
            return Pair.of(btcTx, btcAddress);
        };
        testGetAmountSentToAddress(bridgeConstantsRegtest, btcTransactionProvider, Coin.ZERO);
        testGetAmountSentToAddress(bridgeConstantsMainnet, btcTransactionProvider, Coin.ZERO);
    }

    @Test(expected = DeprecatedMethodCallException.class)
    public void getAmountSentToAddress_after_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
            Address btcAddress = PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams());
            btcTx.addOutput(Coin.ZERO, btcAddress);
            return Pair.of(btcTx, btcAddress);
        };
        testGetAmountSentToAddress(bridgeConstantsRegtest, btcTransactionProvider, null);
    }

    private void testGetUTXOsSentToAddress(
        BridgeConstants bridgeConstants,
        BtcTransactionProvider btcTransactionProvider,
        Function<BtcTransaction, List<UTXO>> expectedResult
    ) {
        Pair<BtcTransaction, Address> pair = btcTransactionProvider.provide(bridgeConstants);
        BtcTransaction btcTx = pair.getLeft();
        Address address = pair.getRight();

        // Add output to a random btc address to test that only utxos sent to the given address are being returned
        btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomBtcAddress());
        List<UTXO> foundUTXOs = BridgeUtilsLegacy.getUTXOsSentToAddress(
            activations,
            bridgeConstants.getBtcParams(),
            btcTx,
            address
        );

        List<UTXO> expectedUTXOs = expectedResult.apply(btcTx);
        Assert.assertArrayEquals(expectedUTXOs.toArray(), foundUTXOs.toArray());

        Coin amount = foundUTXOs.stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
        Coin expectedAmount = expectedUTXOs.stream().map(UTXO::getValue).reduce(Coin.ZERO, Coin::add);
        Assert.assertEquals(amount, expectedAmount);
    }

    @Test
    public void getUTXOsSentToAddress_one_utxo_sent_to_given_address() {
        Function<BtcTransaction, List<UTXO>> expectedResult = btcTx -> {
            List<UTXO> expectedUTXOs = new ArrayList<>();
            expectedUTXOs.add(PegTestUtils.createUTXO(btcTx.getHash(), 0, Coin.COIN));
            return expectedUTXOs;
        };

        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
            Address btcAddress = PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams());
            btcTx.addOutput(Coin.COIN, btcAddress);
            return Pair.of(btcTx, btcAddress);
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
    public void getUTXOsSentToAddress_no_utxos_to_given_address() {
        Function<BtcTransaction, List<UTXO>> expectedResult = btcTx -> {
            List<UTXO> expectedUTXOs = new ArrayList<>();
            return expectedUTXOs;
        };
        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
            Address btcAddress = PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams());
            return Pair.of(btcTx, btcAddress);
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
    public void getUTXOsSentToAddress_multiple_utxos_sent_to_given_address() {
        Function<BtcTransaction, List<UTXO>> expectedResult = btcTx -> {
            List<UTXO> expectedUTXOs = new ArrayList<>();
            expectedUTXOs.add(PegTestUtils.createUTXO(btcTx.getHash(), 6, Coin.COIN));
            expectedUTXOs.add(PegTestUtils.createUTXO(btcTx.getHash(), 7, Coin.COIN));
            expectedUTXOs.add(PegTestUtils.createUTXO(btcTx.getHash(), 8, Coin.COIN));
            return expectedUTXOs;
        };

        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());

            btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams()));
            btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams()));
            btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams()));
            btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams()));
            btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams()));
            btcTx.addOutput(Coin.COIN, PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams()));

            Address btcAddress = PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams());
            btcTx.addOutput(Coin.COIN, btcAddress);
            btcTx.addOutput(Coin.COIN, btcAddress);
            btcTx.addOutput(Coin.COIN, btcAddress);

            return Pair.of(btcTx, btcAddress);
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

    @Test(expected = DeprecatedMethodCallException.class)
    public void getUTXOsSentToAddress_after_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        BtcTransactionProvider btcTransactionProvider = bridgeConstants -> {
            BtcTransaction btcTx = new BtcTransaction(bridgeConstants.getBtcParams());
            Address btcAddress = PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams());
            btcTx.addOutput(Coin.COIN, btcAddress);
            return Pair.of(btcTx, btcAddress);
        };
        testGetUTXOsSentToAddress(
            bridgeConstantsRegtest,
            btcTransactionProvider,
            null
        );
    }

    @Test(expected = DeprecatedMethodCallException.class)
    public void getUTXOsSentToAddress_after_RSKIP293_mainnet() {
        getUTXOsSentToAddress_has_multiple_utxos(
            true,
            bridgeConstantsMainnet,
            PegTestUtils.createRandomBtcAddress(bridgeConstantsMainnet.getBtcParams()),
            true
        );
    }

    @Test
    public void calculatePegoutTxSize_before_rskip_271() {
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        Federation federation = new Federation(
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

        Assert.assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test(expected = DeprecatedMethodCallException.class)
    public void calculatePegoutTxSize_after_rskip_271() {
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        Federation federation = new Federation(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            bridgeConstantsRegtest.getBtcParams()
        );

        BridgeUtilsLegacy.calculatePegoutTxSize(activations, federation, 2, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void calculatePegoutTxSize_ZeroInput_ZeroOutput() {
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(false);

        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        Federation federation = new Federation(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            bridgeConstantsRegtest.getBtcParams()
        );

        BridgeUtilsLegacy.calculatePegoutTxSize(activations, federation, 0, 0);
    }

    private interface BtcTransactionProvider {
        Pair<BtcTransaction, Address> provide(BridgeConstants bridgeConstants);
    }
}
