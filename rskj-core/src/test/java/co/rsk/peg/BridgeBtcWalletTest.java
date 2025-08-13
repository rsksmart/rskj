package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.P2shErpFederationBuilder;
import co.rsk.peg.federation.P2shP2wshErpFederationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BridgeBtcWalletTest {
    private final Coin coin = Coin.COIN;
    private final BtcECKey newKey = BitcoinTestUtils.getBtcEcKeyFromSeed("newKey");
    private final List<BtcECKey> multiSigKeys =
        BitcoinTestUtils.getBtcEcKeysFromSeeds(new String[]{"key1", "key2", "key3"}, true);
    private final Script multiSigRedeemScript = ScriptBuilder.createRedeemScript(2, multiSigKeys);

    private List<Federation> liveFeds;
    private BridgeBtcWallet wallet;
    private BtcTransaction tx;
    private byte[] outputToMultiSigP2shScript;
    private byte[] outputToP2PkhScript;

    @BeforeEach
    void setUp() {
        liveFeds = new ArrayList<>();
    }

    void setUpWalletAndNetworkParams(NetworkParameters params) {
        Address multiSigAddress = Address.fromP2SHScript(params, ScriptBuilder.createP2SHOutputScript(multiSigRedeemScript));
        String p2PkhAddressSeed = "seed";
        Address p2PkhAddress = BitcoinTestUtils.createP2PKHAddress(params, p2PkhAddressSeed);

        Context context = new Context(params);
        Context.propagate(context);
        wallet = new BridgeBtcWallet(context, liveFeds);

        tx = new BtcTransaction(params);
        // always adding one output to some multisig address and one to some p2pkh address
        tx.addOutput(coin, multiSigAddress);
        tx.addOutput(coin, p2PkhAddress);

        TransactionOutput outputToMultiSigAddress = tx.getOutput(0);
        outputToMultiSigP2shScript = BitcoinTestUtils.getOutputScriptPubKeyHash(outputToMultiSigAddress);

        TransactionOutput outputToP2PkhAddress = tx.getOutput(1);
        outputToP2PkhScript = BitcoinTestUtils.getOutputScriptPubKeyHash(outputToP2PkhAddress);
    }

    @ParameterizedTest
    @MethodSource("networkParamsArgs")
    void getDestinationFederation_oneLegacyLiveFed_returnsExpectedFederationFromOutputScript(NetworkParameters params) {
        // arrange
        Federation fed1 = P2shErpFederationBuilder.builder()
            .withNetworkParameters(params)
            .build();
        liveFeds.add(fed1);

        setUpWalletAndNetworkParams(params);
        tx.addOutput(coin, fed1.getAddress());

        // act
        TransactionOutput outputToFed1 = tx.getOutput(2);
        byte[] outputToFed1P2shScript = BitcoinTestUtils.getOutputScriptPubKeyHash(outputToFed1);

        // assert
        assertNoDestinationFederation(outputToMultiSigP2shScript);
        assertNoDestinationFederation(outputToP2PkhScript);
        assertDestinationFederation(fed1, outputToFed1P2shScript);
    }

    @ParameterizedTest
    @MethodSource("networkParamsArgs")
    void getDestinationFederation_twoLegacyLiveFeds_returnsExpectedFederationFromOutputScript(NetworkParameters params) {
        // arrange
        Federation fed1 = P2shErpFederationBuilder.builder()
            .withNetworkParameters(params)
            .build();

        List<BtcECKey> fed2Keys = fed1.getBtcPublicKeys();
        fed2Keys.remove(0);
        fed2Keys.add(newKey);
        Federation fed2 = P2shErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(fed2Keys)
            .withNetworkParameters(params)
            .build();
        liveFeds.add(fed1);
        liveFeds.add(fed2);

        setUpWalletAndNetworkParams(params);
        tx.addOutput(coin, fed1.getAddress());
        tx.addOutput(coin, fed2.getAddress());

        // act
        TransactionOutput outputToFed1 = tx.getOutput(2);
        byte[] outputToFed1P2shScript = BitcoinTestUtils.getOutputScriptPubKeyHash(outputToFed1);

        TransactionOutput outputToFed2 = tx.getOutput(3);
        byte[] outputToFed2P2shScript = BitcoinTestUtils.getOutputScriptPubKeyHash(outputToFed2);

        // assert
        assertNoDestinationFederation(outputToMultiSigP2shScript);
        assertNoDestinationFederation(outputToP2PkhScript);
        assertDestinationFederation(fed1, outputToFed1P2shScript);
        assertDestinationFederation(fed2, outputToFed2P2shScript);
    }

    @ParameterizedTest
    @MethodSource("networkParamsArgs")
    void getDestinationFederation_oneLegacyAndOneSegwitLiveFeds_returnsExpectedFederationFromOutputScript(NetworkParameters params) {
        // arrange
        Federation fed1 = P2shErpFederationBuilder.builder()
            .withNetworkParameters(params)
            .build();
        Federation fed2 = P2shP2wshErpFederationBuilder.builder()
            .withNetworkParameters(params)
            .build();
        liveFeds.add(fed1);
        liveFeds.add(fed2);

        setUpWalletAndNetworkParams(params);
        tx.addOutput(coin, fed1.getAddress());
        tx.addOutput(coin, fed2.getAddress());

        // act
        TransactionOutput outputToFed1 = tx.getOutput(2);
        byte[] outputToFed1P2shScript = BitcoinTestUtils.getOutputScriptPubKeyHash(outputToFed1);

        TransactionOutput outputToFed2 = tx.getOutput(3);
        byte[] outputToFed2P2shScript = BitcoinTestUtils.getOutputScriptPubKeyHash(outputToFed2);

        // assert
        assertNoDestinationFederation(outputToMultiSigP2shScript);
        assertNoDestinationFederation(outputToP2PkhScript);
        assertDestinationFederation(fed1, outputToFed1P2shScript);
        assertDestinationFederation(fed2, outputToFed2P2shScript);
    }


    @ParameterizedTest
    @MethodSource("networkParamsArgs")
    void getDestinationFederation_oneSegwitLiveFed_returnsExpectedFederationFromOutputScript(NetworkParameters params) {
        // arrange
        Federation fed1 = P2shP2wshErpFederationBuilder.builder()
            .withNetworkParameters(params)
            .build();
        liveFeds.add(fed1);

        setUpWalletAndNetworkParams(params);
        tx.addOutput(coin, fed1.getAddress());

        // act
        TransactionOutput outputToFed1 = tx.getOutput(2);
        byte[] outputToFed1P2shScript = BitcoinTestUtils.getOutputScriptPubKeyHash(outputToFed1);

        // assert
        assertNoDestinationFederation(outputToMultiSigP2shScript);
        assertNoDestinationFederation(outputToP2PkhScript);
        assertDestinationFederation(fed1, outputToFed1P2shScript);
    }

    @ParameterizedTest
    @MethodSource("networkParamsArgs")
    void getDestinationFederation_twoSegwitLiveFeds_returnsExpectedFederationFromOutputScript(NetworkParameters params) {
        // arrange
        Federation fed1 = P2shP2wshErpFederationBuilder.builder()
            .withNetworkParameters(params)
            .build();
        List<BtcECKey> fed2Keys = fed1.getBtcPublicKeys();
        fed2Keys.remove(0);
        fed2Keys.add(newKey);
        Federation fed2 = P2shP2wshErpFederationBuilder.builder()
            .withMembersBtcPublicKeys(fed2Keys)
            .withNetworkParameters(params)
            .build();
        liveFeds.add(fed1);
        liveFeds.add(fed2);

        setUpWalletAndNetworkParams(params);
        tx.addOutput(coin, fed1.getAddress());
        tx.addOutput(coin, fed2.getAddress());

        // act
        TransactionOutput outputToFed1 = tx.getOutput(2);
        byte[] outputToFed1P2shScript = BitcoinTestUtils.getOutputScriptPubKeyHash(outputToFed1);

        TransactionOutput outputToFed2 = tx.getOutput(3);
        byte[] outputToFed2P2shScript = BitcoinTestUtils.getOutputScriptPubKeyHash(outputToFed2);

        // assert
        assertNoDestinationFederation(outputToMultiSigP2shScript);
        assertNoDestinationFederation(outputToP2PkhScript);
        assertDestinationFederation(fed1, outputToFed1P2shScript);
        assertDestinationFederation(fed2, outputToFed2P2shScript);
    }

    private void assertDestinationFederation(Federation expectedDestinationFederation, byte[] outputScriptPubKey) {
        Optional<Federation> destinationFed = wallet.getDestinationFederation(outputScriptPubKey);
        assertTrue(destinationFed.isPresent());
        assertEquals(expectedDestinationFederation, destinationFed.get());
    }

    private void assertNoDestinationFederation(byte[] outputScriptPubKey) {
        Optional<Federation> destinationFed = wallet.getDestinationFederation(outputScriptPubKey);
        assertFalse(destinationFed.isPresent());
    }

    private static Stream<NetworkParameters> networkParamsArgs() {
        final NetworkParameters mainnetParams = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
        final NetworkParameters testnetParams = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

        return Stream.of(
            mainnetParams,
            testnetParams
        );
    }

}
