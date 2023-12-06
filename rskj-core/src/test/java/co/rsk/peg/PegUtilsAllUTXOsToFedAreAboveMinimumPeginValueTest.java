package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static co.rsk.peg.PegTestUtils.createFederation;
import static co.rsk.peg.PegTestUtils.createP2shErpFederation;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PegUtilsAllUTXOsToFedAreAboveMinimumPeginValueTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final Context context = new Context(bridgeMainnetConstants.getBtcParams());
    private static final ActivationConfig.ForBlock activations = ActivationConfigsForTest.arrowhead600().forBlock(0);

    private List<BtcECKey> retiringFedSigners;
    private Federation retiringFederation;
    private List<BtcECKey> activeFedSigners;
    private Federation activeFederation;

    @BeforeEach
    void init() {

        retiringFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
                new String[]{"fa01", "fa02", "fa03"}, true
        );
        retiringFederation = createFederation(bridgeMainnetConstants, retiringFedSigners);

        activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
                new String[]{"fa01", "fa02", "fa03", "fa04", "fa05"}, true
        );
        activeFederation = createP2shErpFederation(bridgeMainnetConstants, activeFedSigners);
    }


    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PreRSKIP379AndNoOutputs_true() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForFingerrot = ActivationConfigsForTest.fingerroot500().forBlock(0);

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertTrue(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForFingerrot),
            "When RSKIP379 is not yet active and the utxo list to the fed is empty, it returns true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PostRSKIP379AndNoOutputs_false() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForTbd600 = ActivationConfigsForTest.arrowhead600().forBlock(0);

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertFalse(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForTbd600),
            "When RSKIP379 is active and the utxo list to the fed is empty, it should return false."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PreRSKIP379WithManyOutputsBelowMinimumButNoneForFed_true() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForFingerrot = ActivationConfigsForTest.fingerroot500().forBlock(0);

        Address randomAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "add1");

        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress);
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress);
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress);

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertTrue(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForFingerrot),
            "When RSKIP379 is not yet active and there are many utxos but none for the fed, it returns true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PostRSKIP379WithManyOutputsBelowMinimumButNoneForFed_false() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForTbd600 = ActivationConfigsForTest.arrowhead600().forBlock(0);

        Address randomAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "add1");

        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress);
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress);
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress);

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertFalse(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForTbd600),
        "When RSKIP379 is active and there are many utxos but none for the fed, it should return false."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PreRSKIP379WithManyOutputsEqualAndAboveMinimumButNoneForFed_true() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForFingerrot = ActivationConfigsForTest.fingerroot500().forBlock(0);

        Address randomAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "add1");

        btcTx.addOutput(minimumPegInTxValue, randomAddress); // Minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), randomAddress); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), randomAddress); // Above minimum

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertTrue(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForFingerrot),
        "When RSKIP379 is not yet active and there are many utxos equal and above minimum but none for the fed, it returns true."
        );

    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PostRSKIP379WithManyOutputsEqualAndAboveMinimumButNoneForFed_false() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForTbd600 = ActivationConfigsForTest.arrowhead600().forBlock(0);

        Address randomAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "add1");

        btcTx.addOutput(minimumPegInTxValue, randomAddress); // Minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), randomAddress); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), randomAddress); // Above minimum

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertFalse(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForTbd600),
            "When RSKIP379 is active and there are many utxos equal and above minimum but none for the fed, it returns false."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PreRSKIP379WithManyOutputsAboveMinimumForFedAndManyOutputsBelowMinimumForRandomAddresses_true() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForFingerrot = ActivationConfigsForTest.fingerroot500().forBlock(0);

        Address randomAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "add1");

        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress); // Below Minimum
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress); // Below Minimum
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress); // Below Minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertTrue(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForFingerrot),
            "When RSKIP379 is not yet active and there are many utxos for fed above minimum and many utxos to random addresses below minimum, it returns true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PostRSKIP379WithManyOutputsAboveMinimumForFedAndManyOutputsBelowMinimumForRandomAddresses_true() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForTbd600 = ActivationConfigsForTest.arrowhead600().forBlock(0);

        Address randomAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "add1");

        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress); // Below Minimum
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress); // Below Minimum
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress); // Below Minimum
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // Minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertTrue(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForTbd600),
        "When RSKIP379 is active and there are many utxos for fed above minimum and many utxos to random addresses below minimum, it returns true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PreRSKIP379WithManyOutputsForFedButEachBelowMinimumAndSumAboveMinimum_false() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForFingerrot = ActivationConfigsForTest.fingerroot500().forBlock(0);

        Coin amountJustBelowMinimum = minimumPegInTxValue.minus(Coin.SATOSHI);

        btcTx.addOutput(amountJustBelowMinimum, activeFederation.getAddress());
        btcTx.addOutput(amountJustBelowMinimum, activeFederation.getAddress());
        btcTx.addOutput(amountJustBelowMinimum, activeFederation.getAddress());

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertFalse(
                PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForFingerrot),
            "When RSKIP379 is not yet active and there are many utxos to the fed but there is at least one of them below the minimum, it returns false."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PostRSKIP379WithManyOutputsForFedButEachBelowMinimumAndSumAboveMinimum_false() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForTbd600 = ActivationConfigsForTest.arrowhead600().forBlock(0);

        Coin amountJustBelowMinimum = minimumPegInTxValue.minus(Coin.SATOSHI);

        btcTx.addOutput(amountJustBelowMinimum, activeFederation.getAddress());
        btcTx.addOutput(amountJustBelowMinimum, activeFederation.getAddress());
        btcTx.addOutput(amountJustBelowMinimum, activeFederation.getAddress());

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertFalse(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForTbd600),
        "When RSKIP379 is active and there are many utxos to the fed but there is at least one of them below the minimum," +
                    " even though the sum of all those utxos is above minimum, it should return false."
        );

    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PreRSKIP379WithManyOutputsForFedAboveMinimumAndOneBelowMinimum_false() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForFingerrot = ActivationConfigsForTest.fingerroot500().forBlock(0);

        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // With minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), activeFederation.getAddress()); // Below minimum

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertFalse(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForFingerrot),
            "When RSKIP379 is not yet active and there are many utxos to the fed but there is at least one of them below the minimum, it returns false."
        );

    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PostRSKIP379WithManyOutputsForFedAboveMinimumAndOneBelowMinimum_false() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForTbd600 = ActivationConfigsForTest.arrowhead600().forBlock(0);

        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // With minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), activeFederation.getAddress()); // Below minimum

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertFalse(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForTbd600),
        "When RSKIP379 is active and there are many utxos to the fed but there is at least one of them below the minimum, it should return true."
        );

    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PreRSKIP379WithManyOutputsForFedAllEqualOrAboveMinimum_true() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForFingerroot = ActivationConfigsForTest.fingerroot500().forBlock(0);

        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // With minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertTrue(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForFingerroot),
        "When RSKIP379 is not yet active and there are many utxos to the fed above minimum, it returns true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PostRSKIP379WithManyOutputsForFedAllEqualOrAboveMinimum_true() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForTbd600 = ActivationConfigsForTest.arrowhead600().forBlock(0);

        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // With minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertTrue(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForTbd600),
            "When RSKIP379 is active and there are many utxos to the fed above minimum, it returns true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PreRSKIP379UtxosToRetiringFedAboveMinimumAndToActiveFedBelowMinimum_false() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForFingerroot = ActivationConfigsForTest.fingerroot500().forBlock(0);

        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), activeFederation.getAddress()); // Below minimum to active fed
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // Minimum to active fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), retiringFederation.getAddress()); // Above minimum to retiring fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), retiringFederation.getAddress()); // Above minimum to retiring fed

        List<Federation> federations = new ArrayList<>();
        federations.add(activeFederation);
        federations.add(retiringFederation);

        Wallet fedWallet = new BridgeBtcWallet(context, federations);

        assertFalse(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForFingerroot)
            , "When PreRSKIP379 is not yet active and there are 2 feds, 1 retiring and 1 active, and there are utxos equal and above minimum for " +
                    "one of them and utxos below minimum for the other, it should return false."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PostRSKIP379UtxosToRetiringFedAboveMinimumAndToActiveFedBelowMinimum_false() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForTbd600 = ActivationConfigsForTest.arrowhead600().forBlock(0);

        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), activeFederation.getAddress()); // Below minimum to active fed
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // Minimum to active fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), retiringFederation.getAddress()); // Above minimum to retiring fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), retiringFederation.getAddress()); // Above minimum to retiring fed

        List<Federation> federations = new ArrayList<>();
        federations.add(activeFederation);
        federations.add(retiringFederation);

        Wallet fedWallet = new BridgeBtcWallet(context, federations);

        assertFalse(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForTbd600),
        "When PreRSKIP379 is active and there are 2 feds, 1 retiring and 1 active, and there are utxos equal and above minimum for " +
                        "one of them and utxos below minimum for the other, it should return false."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PreRSKIP379UtxosToRetiringAndActiveFedEqualToAndAboveMinimum_true() {
        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForFingerroot = ActivationConfigsForTest.fingerroot500().forBlock(0);

        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // Minimum to active fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum to active fed
        btcTx.addOutput(minimumPegInTxValue, retiringFederation.getAddress()); // Minimum to retiring fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), retiringFederation.getAddress()); // Above minimum to retiring fed

        List<Federation> federations = new ArrayList<>();
        federations.add(activeFederation);
        federations.add(retiringFederation);

        Wallet fedWallet = new BridgeBtcWallet(context, federations);

        assertTrue(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForFingerroot),
        "When PreRSKIP379 is not yet active and there are 2 feds, 1 retiring and 1 active, and there are utxos equal and above minimum for " +
                        "both of them, it should return true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PostRSKIP379UtxosToRetiringAndActiveFedEqualToAndAboveMinimum_true() {
        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForFingerroot = ActivationConfigsForTest.fingerroot500().forBlock(0);

        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // Minimum to active fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum to active fed
        btcTx.addOutput(minimumPegInTxValue, retiringFederation.getAddress()); // Minimum to retiring fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), retiringFederation.getAddress()); // Above minimum to retiring fed

        List<Federation> federations = new ArrayList<>();
        federations.add(activeFederation);
        federations.add(retiringFederation);

        Wallet fedWallet = new BridgeBtcWallet(context, federations);

        assertTrue(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForFingerroot),
        "When PreRSKIP379 is active and there are 2 feds, 1 retiring and 1 active, and there are utxos equal and above minimum for " +
                        "both of them, it should return true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PreRSKIP379Exactly1UtxoToFedWithMinimumValue_true() {
        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForFingerroot = ActivationConfigsForTest.fingerroot500().forBlock(0);

        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // Minimum to active fed

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertTrue(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForFingerroot),
        "When PreRSKIP379 is not yet active and there is exactly 1 utxo with exactly the minimum value sent to the fed, it should return true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PostRSKIP379Exactly1UtxoToFedWithMinimumValue_true() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForFingerroot = ActivationConfigsForTest.fingerroot500().forBlock(0);

        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // Minimum to active fed

        Wallet fedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));

        assertTrue(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForFingerroot),
            "When PreRSKIP379 is active and there is exactly 1 utxo with exactly the minimum value sent to the fed, it should return true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PreRSKIP379UtxosBelowMinimumToBothRetiringAndActiveFed_false() {

        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForFingerroot = ActivationConfigsForTest.fingerroot500().forBlock(0);

        Coin belowMinimumValue = minimumPegInTxValue.minus(Coin.SATOSHI);
        btcTx.addOutput(belowMinimumValue, activeFederation.getAddress());
        btcTx.addOutput(belowMinimumValue, activeFederation.getAddress());
        btcTx.addOutput(belowMinimumValue, retiringFederation.getAddress());
        btcTx.addOutput(belowMinimumValue, retiringFederation.getAddress());

        List<Federation> federations = new ArrayList<>();
        federations.add(activeFederation);
        federations.add(retiringFederation);

        Wallet fedWallet = new BridgeBtcWallet(context, federations);

        assertFalse(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForFingerroot),
            "When PreRSKIP379 is not yet active and there are 2 feds, 1 retiring and 1 active, and there are utxos below minimum for both, it should return false."
        );

    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_PostRSKIP379UtxosBelowMinimumToBothRetiringAndActiveFed_false() {
        Coin minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activations);

        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        ActivationConfig.ForBlock activationsForTbd600 = ActivationConfigsForTest.arrowhead600().forBlock(0);

        Coin belowMinimumValue = minimumPegInTxValue.minus(Coin.SATOSHI);
        btcTx.addOutput(belowMinimumValue, activeFederation.getAddress());
        btcTx.addOutput(belowMinimumValue, activeFederation.getAddress());
        btcTx.addOutput(belowMinimumValue, retiringFederation.getAddress());
        btcTx.addOutput(belowMinimumValue, retiringFederation.getAddress());

        List<Federation> federations = new ArrayList<>();
        federations.add(activeFederation);
        federations.add(retiringFederation);

        Wallet fedWallet = new BridgeBtcWallet(context, federations);

        assertFalse(
            PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(btcTx, fedWallet, minimumPegInTxValue, activationsForTbd600),
        "When PreRSKIP379 is active and there are 2 feds, 1 retiring and 1 active, and there are utxos below minimum for both, it should return false."
        );
    }
}
