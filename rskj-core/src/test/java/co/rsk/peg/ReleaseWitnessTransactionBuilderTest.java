package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.P2shP2wshErpFederationRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

public class ReleaseWitnessTransactionBuilderTest {
    private Wallet wallet;
    private Address changeAddress;
    private ReleaseTransactionBuilder builder;
    private ActivationConfig.ForBlock activations;
    private Context btcContext;
    private NetworkParameters networkParameters;
    private BridgeConstants bridgeConstants;
    private Federation federation;

    @BeforeEach
    void setup() {
        wallet = mock(Wallet.class);
        changeAddress = mockAddress(1000);
        activations = mock(ActivationConfig.ForBlock.class);
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
        btcContext = new Context(networkParameters);
        federation = bridgeConstants.getGenesisFederation();
        builder = new ReleaseTransactionBuilder(
            networkParameters,
            wallet,
            changeAddress,
            Coin.MILLICOIN.multiply(2),
            activations
        );
    }

    private Address mockAddress(int pk) {
        return BtcECKey.fromPrivate(BigInteger.valueOf(pk)).toAddress(NetworkParameters.fromID(NetworkParameters.ID_REGTEST));
    }

    @Test
    void build_pegout_tx_from_p2shp2wsh_erp_federation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        BridgeConstants bridgeConstants = BridgeTestNetConstants.getInstance();

        List<BtcECKey> standardKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{
                "fed1", "fed2", "fed3"
                //, "fed4", "fed5", "fed6", "fed7", "fed8", "fed9"
            },
            true
        );

        List<BtcECKey> emergencyKeys = bridgeConstants.getErpFedPubKeysList();
        long activationDelay = bridgeConstants.getErpFedActivationDelay();

        Script standardRedeem = new ScriptBuilder().createRedeemScript(standardKeys.size()/2+1, standardKeys);
        Script emergencyRedeem = new ScriptBuilder().createRedeemScript(emergencyKeys.size()/2+1, emergencyKeys);
        Script redeemScript = P2shP2wshErpFederationRedeemScriptParser.createP2shP2wshErpRedeemScript(standardRedeem, emergencyRedeem, activationDelay);

        Script p2shP2wshOutputScript = ScriptBuilder.createP2SHP2WSHOutputScript(redeemScript);
        Address segwitAddress = Address.fromP2SHScript(
            networkParameters,
            p2shP2wshOutputScript
        );
        System.out.println(segwitAddress);

        Federation p2shErpFederation = new P2shErpFederation(
            FederationMember.getFederationMembersFromKeys(standardKeys),
            Instant.now(),
            0,
            bridgeConstants.getBtcParams(),
            emergencyKeys,
            activationDelay,
            activations
        );

        List<UTXO> utxos = Arrays.asList(
            new UTXO(
                Sha256Hash.of(new byte[]{1}),
                0,
                Coin.COIN,
                0,
                false,
                p2shErpFederation.getP2SHScript()
            ),
            new UTXO(
                Sha256Hash.of(new byte[]{1}),
                0,
                Coin.COIN,
                0,
                false,
                p2shErpFederation.getP2SHScript()
            )
        );

        Wallet thisWallet = BridgeUtils.getFederationSpendWallet(
            new Context(bridgeConstants.getBtcParams()),
            p2shErpFederation,
            utxos,
            false,
            mock(BridgeStorageProvider.class)
        );

        ReleaseWitnessTransactionBuilder releaseWitnessTransactionBuilder = new ReleaseWitnessTransactionBuilder(
            bridgeConstants.getBtcParams(),
            thisWallet,
            p2shErpFederation.getAddress(),
            redeemScript,
            standardKeys,
            Coin.SATOSHI.multiply(1000),
            activations
        );

        Address pegoutRecipient = Address.fromBase58(networkParameters,"msgc5Gtz2L9MVhXPDrFRCYPa16QgoZ2EjP");
        Coin pegoutAmount = Coin.COIN.add(Coin.SATOSHI);

        ReleaseWitnessTransactionBuilder.BuildResult result = releaseWitnessTransactionBuilder.buildAmountTo(
            pegoutRecipient,
            pegoutAmount
        );

        System.out.println(Hex.toHexString(result.getBtcTx().bitcoinSerialize()));

        Assertions.assertTrue(result.getBtcTx().hasWitness());
        Assertions.assertEquals(ReleaseWitnessTransactionBuilder.Response.SUCCESS, result.getResponseCode());
    }

}
