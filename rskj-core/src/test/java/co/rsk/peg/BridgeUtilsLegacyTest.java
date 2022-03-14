package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import java.time.Instant;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Optional;

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
        ActivationConfig.ForBlock activations,
        BridgeConstants constants,
        Coin expectedValue,
        Coin valueToTransfer,
        Boolean includeOutput
    ) {
        Address receiver = constants.getGenesisFederation().getAddress();
        BtcTransaction btcTx = new BtcTransaction(constants.getBtcParams());
        if (includeOutput) {
            btcTx.addOutput(valueToTransfer, receiver);
        }
        Assert.assertEquals(
            expectedValue,
            BridgeUtilsLegacy.getAmountSentToAddress(
                activations,
                constants.getBtcParams(),
                btcTx,
                receiver
            )
        );
    }

    @Test
    public void getAmountSentToAddress_one_coin() {
        Coin valueToTransfer = Coin.COIN;
        testGetAmountSentToAddress(activations, bridgeConstantsRegtest, valueToTransfer, valueToTransfer, true);
        testGetAmountSentToAddress(activations, bridgeConstantsMainnet, valueToTransfer, valueToTransfer, true);
    }

    @Test
    public void getAmountSentToAddress_no_outputs() {
        Coin valueToTransfer = Coin.COIN;
        testGetAmountSentToAddress(activations, bridgeConstantsRegtest, Coin.ZERO, valueToTransfer, false);
        testGetAmountSentToAddress(activations, bridgeConstantsMainnet, Coin.ZERO, valueToTransfer, false);
    }

    @Test
    public void getAmountSentToAddress_zero_amount() {
        Coin valueToTransfer = Coin.ZERO;
        testGetAmountSentToAddress(activations, bridgeConstantsRegtest, valueToTransfer, valueToTransfer, true);
        testGetAmountSentToAddress(activations, bridgeConstantsMainnet, valueToTransfer, valueToTransfer, true);
    }

    @Test(expected = DeprecatedMethodCallException.class)
    public void getAmountSentToAddress_after_RSKIP293_testnet() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        Coin valueToTransfer = Coin.COIN;
        testGetAmountSentToAddress(activations, bridgeConstantsRegtest, Coin.COIN, valueToTransfer, true);
    }

    @Test(expected = DeprecatedMethodCallException.class)
    public void getAmountSentToAddress_after_RSKIP293_mainnet() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        Coin valueToTransfer = Coin.COIN;
        testGetAmountSentToAddress(activations, bridgeConstantsMainnet, valueToTransfer, valueToTransfer, true);
    }

    private void getUTXOsSentToAddress_has_utxos_to_given_address(
        Boolean isRSKIP293Active
        , BridgeConstants bridgeConstants
        , Address address) {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(isRSKIP293Active);

        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(Coin.COIN, address);
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1),
            0, ScriptBuilder.createInputScript(null, srcKey));

        List<UTXO> utxoListExpected = new ArrayList<>();
        UTXO utxo = new UTXO(
            tx.getHash(), 0, Coin.COIN, 0, false, ScriptBuilder.createOutputScript(address)
        );
        utxoListExpected.add(utxo);

        List<UTXO> utxosFound = BridgeUtilsLegacy.getUTXOsSentToAddress(activations, bridgeConstants.getBtcParams(), tx, address);

        Assert.assertEquals(
            utxoListExpected.size(), utxosFound.size()
        );

        Assert.assertTrue(
            utxoListExpected.get(0).getValue().equals(utxosFound.get(0).getValue())
        );
    }

    @Test
    public void getUTXOsSentToAddress_has_utxos_to_given_address() {
        getUTXOsSentToAddress_has_utxos_to_given_address(
            false,
            bridgeConstantsRegtest,
            PegTestUtils.createRandomBtcAddress(bridgeConstantsRegtest.getBtcParams())
        );
        getUTXOsSentToAddress_has_utxos_to_given_address(
            false,
            bridgeConstantsMainnet,
            PegTestUtils.createRandomBtcAddress(bridgeConstantsMainnet.getBtcParams())
        );
    }

    private void getUTXOsSentToAddress_no_utxos_to_given_address(
        Boolean isRSKIP293Active,
        BridgeConstants bridgeConstants,
        Address address,
        Boolean includeOutputToAddress
    ) {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(isRSKIP293Active);

        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        if (includeOutputToAddress) {
            tx.addOutput(Coin.COIN, PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams()));
        }
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1),
            0, ScriptBuilder.createInputScript(null, srcKey));

        List<UTXO> utxoListExpected = new ArrayList<>();
        UTXO utxo = new UTXO(
            tx.getHash(),
            0,
            Coin.COIN,
            0,
            false,
            ScriptBuilder.createOutputScript(address)
        );
        utxoListExpected.add(utxo);

        List<UTXO> utxosFound = BridgeUtilsLegacy.getUTXOsSentToAddress(
            activations,
            bridgeConstants.getBtcParams(),
            tx,
            address
        );

        Assert.assertTrue(
            utxosFound.isEmpty()
        );
    }

    @Test
    public void getUTXOsSentToAddress_no_utxos_to_given_address() {
        getUTXOsSentToAddress_no_utxos_to_given_address(
            false,
            bridgeConstantsRegtest,
            PegTestUtils.createRandomBtcAddress(bridgeConstantsRegtest.getBtcParams()),
            true
        );
        getUTXOsSentToAddress_no_utxos_to_given_address(
            false,
            bridgeConstantsRegtest,
            PegTestUtils.createRandomBtcAddress(bridgeConstantsRegtest.getBtcParams()),
            false
        );

        getUTXOsSentToAddress_no_utxos_to_given_address(
            false,
            bridgeConstantsMainnet,
            PegTestUtils.createRandomBtcAddress(bridgeConstantsMainnet.getBtcParams()),
            true
        );
        getUTXOsSentToAddress_no_utxos_to_given_address(
            false,
            bridgeConstantsMainnet,
            PegTestUtils.createRandomBtcAddress(bridgeConstantsMainnet.getBtcParams()),
            false
        );
    }

    private void getUTXOsSentToAddress_has_multiple_utxos(
        Boolean isRSKIP293Active,
        BridgeConstants bridgeConstants,
        Address address,
        boolean includeOutputToAddress
    ) {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(isRSKIP293Active);

        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());

        // Create n random outputs to simulate a btc transaction with outputs to multiple addresses
        byte utxosQty = 5;
        for (int i = 0; i < utxosQty; i++) {
            tx.addOutput(Coin.COIN, PegTestUtils.createRandomBtcAddress(bridgeConstants.getBtcParams()));
        }

        if (includeOutputToAddress) {
            // Add multiple to the given address
            for (int i = 0; i < utxosQty; i++) {
                tx.addOutput(Coin.COIN, address);
            }
        }

        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1),
            0, ScriptBuilder.createInputScript(null, srcKey));

        List<UTXO> utxoListExpected = new ArrayList<>();
        if (includeOutputToAddress){
            for (int i = 0; i < utxosQty; i++) {
                UTXO utxo = new UTXO(
                    tx.getHash(),
                    0,
                    Coin.COIN,
                    0,
                    false,
                    ScriptBuilder.createOutputScript(address)
                );
                utxoListExpected.add(utxo);
            }
        }

        List<UTXO> utxosFound = BridgeUtilsLegacy.getUTXOsSentToAddress(
            activations,
            bridgeConstants.getBtcParams(),
            tx,
            address
        );

        if (includeOutputToAddress){
            Assert.assertEquals(
                utxoListExpected.size(), utxosFound.size()
            );

            Optional<Coin> expectedValue = utxoListExpected.stream().map(UTXO::getValue).reduce(
                Coin::add
             );

            Optional<Coin> result = utxosFound.stream().map(UTXO::getValue).reduce(
                Coin::add
            );

            Assert.assertTrue(
                expectedValue.isPresent()
            );

            Assert.assertEquals(
                utxoListExpected.size(), utxosFound.size()
            );

            Assert.assertTrue(
                expectedValue.equals(result)
            );

        } else {
            Assert.assertTrue(
                utxosFound.isEmpty()
            );
        }
    }

    @Test
    public void getUTXOsSentToAddress_has_multiple_utxos() {
        getUTXOsSentToAddress_has_multiple_utxos(
            false,
            bridgeConstantsRegtest,
            PegTestUtils.createRandomBtcAddress(bridgeConstantsRegtest.getBtcParams()),
            true
        );
        getUTXOsSentToAddress_has_multiple_utxos(
            false,
            bridgeConstantsRegtest,
            PegTestUtils.createRandomBtcAddress(bridgeConstantsRegtest.getBtcParams()),
            false
        );

        getUTXOsSentToAddress_has_multiple_utxos(
            false,
            bridgeConstantsMainnet,
            PegTestUtils.createRandomBtcAddress(bridgeConstantsMainnet.getBtcParams()),
            true
        );
        getUTXOsSentToAddress_has_multiple_utxos(
            false,
            bridgeConstantsMainnet,
            PegTestUtils.createRandomBtcAddress(bridgeConstantsMainnet.getBtcParams()),
            false
        );
    }

    @Test(expected = DeprecatedMethodCallException.class)
    public void getUTXOsSentToAddress_after_RSKIP293_testnet() {
        getUTXOsSentToAddress_has_multiple_utxos(
            true,
            bridgeConstantsRegtest,
            PegTestUtils.createRandomBtcAddress(bridgeConstantsRegtest.getBtcParams()),
            true
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
}
