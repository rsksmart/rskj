package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.script.P2shErpFederationRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.config.BridgeTestNetConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import jdk.nashorn.internal.ir.annotations.Ignore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class P2shP2wshErpFederationTest {

    @Ignore
    @Test
    void spendFromP2shP2wshAddress() throws Exception {
        NetworkParameters networkParameters = BridgeTestNetConstants.getInstance().getBtcParams();

        String wifPrivKey1 = "cMgRyc5tpWLWKfs2gSv6zRXu2ZaQSqwVtU6oNbQneSgCB13JCQNA";
        String wifPrivKey2 = "cW19KKSRKHATNb4oPPvyaSqLCs82nqFyZqQbVY6zaD82YtERYL51";
        String wifPrivKey3 = "cUq9ruifqrtMPMwwu6SrKPr6A7egwhvPZxwE7uMmWQdn11LsHpWi";

        BtcECKey key1 = DumpedPrivateKey.fromBase58(networkParameters, wifPrivKey1).getKey();
        BtcECKey key2 = DumpedPrivateKey.fromBase58(networkParameters, wifPrivKey2).getKey();
        BtcECKey key3 = DumpedPrivateKey.fromBase58(networkParameters, wifPrivKey3).getKey();

        List<BtcECKey> standardKeys = new ArrayList<>();
        standardKeys.add(key1);
        standardKeys.add(key2);
        standardKeys.add(key3);

        Script redeemScript = new ScriptBuilder().createRedeemScript(standardKeys.size()/2+1, standardKeys);

        Coin prevValue = Coin.valueOf(10_000);
        Coin value = Coin.valueOf(10_000);
        Coin fee = Coin.valueOf(1_000);

        assertDoesNotThrow(() -> FederationErpP2shP2wshTestUtils.spendFromP2shP2wshAddress(
            networkParameters,
            redeemScript,
            standardKeys,
            Sha256Hash.wrap("e692d0daeda4b41fc38304df2d8b6ad537e11c687c29d6334d4f3026ab859621"),
            0,
            Address.fromBase58(networkParameters,"msgc5Gtz2L9MVhXPDrFRCYPa16QgoZ2EjP"),
            value,
            false
        ));
    }

    @Test
    void spendFromP2shP2wshErpStandardFed() throws Exception {
        NetworkParameters networkParameters = BridgeTestNetConstants.getInstance().getBtcParams();
        long activationDelay = 30;

        List<BtcECKey> standardKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{
                "fed1", "fed2", "fed3", "fed4", "fed5", "fed6", "fed7", "fed8", "fed9", "fed10",
                "fed11", "fed12", "fed13", "fed14", "fed15", "fed16", "fed17", "fed18", "fed19", "fed20"
            },
            true
        );

        List<BtcECKey> emergencyKeys = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{
                "erp1", "erp2", "erp3", "erp4","erp5", "erp6", "erp7", "erp8","erp9", "erp10",
                "erp11", "erp12", "erp13", "erp14","erp15", "erp16", "erp17", "erp18","erp19", "erp20"
            },
            true
        );

        Script standardRedeem = new ScriptBuilder().createRedeemScript(standardKeys.size()/2+1, standardKeys);
        Script emergencyRedeem = new ScriptBuilder().createRedeemScript(emergencyKeys.size()/2+1, emergencyKeys);
        Script redeemScript = P2shErpFederationRedeemScriptParser.createP2shP2wshErpRedeemScript(standardRedeem, emergencyRedeem, activationDelay);

        Script p2shP2wshOutputScript = ScriptBuilder.createP2SHP2WSHOutputScript(redeemScript);
        Address segwitAddress = Address.fromP2SHScript(
            networkParameters,
            p2shP2wshOutputScript
        );
        System.out.println(segwitAddress);

        Coin value = Coin.valueOf(10_000);

        // Spend from standard multisig
        assertDoesNotThrow(() -> FederationErpP2shP2wshTestUtils.spendFromP2shP2wshErpFed(
            networkParameters,
            redeemScript,
            standardKeys,
            Sha256Hash.wrap("b863291d286ba627d527dc8ec10f1c9ad4438f618fae032d8200fcb9b2577adc"),
            0,
            Address.fromBase58(networkParameters,"msgc5Gtz2L9MVhXPDrFRCYPa16QgoZ2EjP"), // testnet
            value,
            false
        ));

        // Spend from emergency multisig
        assertDoesNotThrow(() -> FederationErpP2shP2wshTestUtils.spendFromP2shP2wshErpFed(
            networkParameters,
            redeemScript,
            standardKeys,
            Sha256Hash.wrap("b863291d286ba627d527dc8ec10f1c9ad4438f618fae032d8200fcb9b2577adc"),
            0,
            Address.fromBase58(networkParameters,"msgc5Gtz2L9MVhXPDrFRCYPa16QgoZ2EjP"), // testnet
            value,
            true
        ));
    }
}
