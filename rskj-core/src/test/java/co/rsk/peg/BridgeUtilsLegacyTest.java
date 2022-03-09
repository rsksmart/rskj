package co.rsk.peg;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.script.FastBridgeRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
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
import java.util.Collections;
import java.util.List;

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
        Coin valueToTransfer,
        Boolean includeOutput
    ) {
        Address receiver = constants.getGenesisFederation().getAddress();
        BtcTransaction btcTx = new BtcTransaction(constants.getBtcParams());
        if (includeOutput){
            btcTx.addOutput(valueToTransfer, receiver);
        }
        Assert.assertEquals(
            valueToTransfer,
            BridgeUtilsLegacy.getAmountSentToAddress(
                activations,
                constants.getBtcParams(),
                btcTx,
                receiver
            )
        );
    }

    @Test
    public void getAmountSentToAddress_coin() {
        Coin valueToTransfer = Coin.COIN;
        testGetAmountSentToAddress(activations, bridgeConstantsRegtest, valueToTransfer, true);
        testGetAmountSentToAddress(activations, bridgeConstantsMainnet, valueToTransfer, true);
    }

    @Test
    public void getAmountSentToAddress_no_output() {
        Coin valueToTransfer = Coin.ZERO;
        testGetAmountSentToAddress(activations, bridgeConstantsRegtest, valueToTransfer, false);
        testGetAmountSentToAddress(activations, bridgeConstantsMainnet, valueToTransfer, false);
    }

    @Test
    public void getAmountSentToAddress_output_value_is_0() {
        Coin valueToTransfer = Coin.ZERO;
        testGetAmountSentToAddress(activations, bridgeConstantsRegtest, valueToTransfer, true);
        testGetAmountSentToAddress(activations, bridgeConstantsMainnet, valueToTransfer, true);
    }

    @Test(expected = DeprecatedMethodCallException.class)
    public void getAmountSentToAddress_for_regtest_after_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        Coin valueToTransfer = Coin.COIN;
        testGetAmountSentToAddress(activations, bridgeConstantsRegtest, valueToTransfer, true);
    }

    @Test(expected = DeprecatedMethodCallException.class)
    public void getAmountSentToAddress_for_mainnet_after_RSKIP293() {
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        Coin valueToTransfer = Coin.COIN;
        testGetAmountSentToAddress(activations, bridgeConstantsMainnet, valueToTransfer, true);
    }

    private void getUTXOsForAddress_no_utxos_for_address_by_network(BridgeConstants bridgeConstants, Address address) {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        Assert.assertEquals(
            Collections.emptyList(),
            BridgeUtilsLegacy.getUTXOsForAddress(
                activations,
                bridgeConstants.getBtcParams(),
                tx,
                address
            )
        );
    }

    @Test
    public void getUTXOsForAddress_no_utxos_for_address() {
        Address btcAddress = Address.fromBase58(
                bridgeConstantsMainnet.getBtcParams(),
                "17VZNX1SN5NtKa8UQFxwQbFeFc3iqRYhem"
        );
        getUTXOsForAddress_no_utxos_for_address_by_network(bridgeConstantsRegtest, btcAddress);

        btcAddress = Address.fromBase58(
                bridgeConstantsRegtest.getBtcParams(),
                "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );
        getUTXOsForAddress_no_utxos_for_address_by_network(bridgeConstantsRegtest, btcAddress);
    }

    private void getUTXOsForAddress_OK_by_network(BridgeConstants bridgeConstants) {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP176)).thenReturn(true);

        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
                bridgeConstants.getGenesisFederation().getRedeemScript(),
                Sha256Hash.of(new byte[1])
        );

        Script fastBridgeP2SH = ScriptBuilder.createP2SHOutputScript(fastBridgeRedeemScript);

        Address fastBridgeFedAddress =
                Address.fromP2SHScript(bridgeConstants.getBtcParams(), fastBridgeP2SH);

        BtcTransaction tx = new BtcTransaction(bridgeConstants.getBtcParams());
        tx.addOutput(Coin.COIN, fastBridgeFedAddress);
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(1),
                0, ScriptBuilder.createInputScript(null, srcKey));

        List<UTXO> utxoList = new ArrayList<>();
        UTXO utxo = new UTXO(tx.getHash(), 0, Coin.COIN, 0, false, fastBridgeP2SH);
        utxoList.add(utxo);

        Assert.assertEquals(
            utxoList,
            BridgeUtilsLegacy.getUTXOsForAddress(activations, bridgeConstants.getBtcParams(), tx, fastBridgeFedAddress)
        );
    }
    @Test
    public void getUTXOsForAddress_OK() {
        getUTXOsForAddress_OK_by_network(bridgeConstantsRegtest);
        getUTXOsForAddress_OK_by_network(bridgeConstantsMainnet);
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
