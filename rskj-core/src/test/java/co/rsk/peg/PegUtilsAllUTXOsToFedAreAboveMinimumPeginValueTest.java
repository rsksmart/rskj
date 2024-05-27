package co.rsk.peg;

import static co.rsk.peg.PegTestUtils.createFederation;
import static co.rsk.peg.PegTestUtils.createP2shErpFederation;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.peg.constants.BridgeConstants;
import co.rsk.peg.constants.BridgeMainNetConstants;
import co.rsk.peg.bitcoin.BitcoinTestUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import co.rsk.peg.federation.Federation;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PegUtilsAllUTXOsToFedAreAboveMinimumPeginValueTest {

    private static final BridgeConstants bridgeMainnetConstants = BridgeMainNetConstants.getInstance();
    private static final NetworkParameters btcMainnetParams = bridgeMainnetConstants.getBtcParams();
    private static final Context context = new Context(bridgeMainnetConstants.getBtcParams());
    private static final ActivationConfig.ForBlock activationsForFingerrot500 = ActivationConfigsForTest
        .fingerroot500()
        .forBlock(0);
    private static final ActivationConfig.ForBlock activationsForArrowhead600 = ActivationConfigsForTest
        .arrowhead600()
        .forBlock(0);

    private Federation retiringFederation;
    private Federation activeFederation;
    private Wallet activeFedWallet;
    private Wallet liveFedsWallet;
    private Address randomAddress;
    private Coin minimumPegInTxValue;

    @BeforeEach
    void init() {
        List<BtcECKey> retiringFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03"}, true
        );
        retiringFederation = createFederation(bridgeMainnetConstants, retiringFedSigners);

        List<BtcECKey> activeFedSigners = BitcoinTestUtils.getBtcEcKeysFromSeeds(
            new String[]{"fa01", "fa02", "fa03", "fa04", "fa05"}, true
        );
        activeFederation = createP2shErpFederation(bridgeMainnetConstants, activeFedSigners);

        activeFedWallet = new BridgeBtcWallet(context, Collections.singletonList(activeFederation));
        liveFedsWallet = new BridgeBtcWallet(context, Arrays.asList(activeFederation, retiringFederation));
        randomAddress = BitcoinTestUtils.createP2PKHAddress(btcMainnetParams, "add1");
        minimumPegInTxValue = bridgeMainnetConstants.getMinimumPeginTxValue(activationsForFingerrot500);
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_preRSKIP379AndNoOutputs_true() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForFingerrot500
        );

        assertTrue(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When RSKIP379 is not yet active and the utxo list to the fed is empty, it returns true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_postRSKIP379AndNoOutputs_false() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForArrowhead600
        );

        assertFalse(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When RSKIP379 is active and the utxo list to the fed is empty, it should return false."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_preRSKIP379WithManyOutputsBelowMinimumButNoneForFed_true() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress);
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress);
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress);

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForFingerrot500
        );

        assertTrue(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When RSKIP379 is not yet active and there are many utxos but none for the fed, it returns true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_postRSKIP379WithManyOutputsBelowMinimumButNoneForFed_false() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress);
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress);
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress);

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForArrowhead600
        );

        assertFalse(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When RSKIP379 is active and there are many utxos but none for the fed, it should return false."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_preRSKIP379WithManyOutputsEqualAndAboveMinimumButNoneForFed_true() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, randomAddress); // Minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), randomAddress); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), randomAddress); // Above minimum

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForFingerrot500
        );

        assertTrue(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When RSKIP379 is not yet active and there are many utxos equal and above minimum but none for the fed, it returns true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_postRSKIP379WithManyOutputsEqualAndAboveMinimumButNoneForFed_false() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, randomAddress); // Minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), randomAddress); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), randomAddress); // Above minimum

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForArrowhead600
        );

        assertFalse(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When RSKIP379 is active and there are many utxos equal and above minimum but none for the fed, it returns false."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_preRSKIP379WithManyOutputsAboveMinimumForFedAndManyOutputsBelowMinimumForRandomAddresses_true() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress); // Below Minimum
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress); // Below Minimum
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress); // Below Minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForFingerrot500
        );

        assertTrue(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When RSKIP379 is not yet active and there are many utxos for fed above minimum and many utxos to random addresses below minimum, it returns true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_postRSKIP379WithManyOutputsAboveMinimumForFedAndManyOutputsBelowMinimumForRandomAddresses_true() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress); // Below Minimum
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress); // Below Minimum
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), randomAddress); // Below Minimum
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // Minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForArrowhead600
        );

        assertTrue(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When RSKIP379 is active and there are many utxos for fed above minimum and many utxos to random addresses below minimum, it returns true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_preRSKIP379WithManyOutputsForFedButEachBelowMinimumAndSumAboveMinimum_false() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        Coin amountJustBelowMinimum = minimumPegInTxValue.minus(Coin.SATOSHI);

        btcTx.addOutput(amountJustBelowMinimum, activeFederation.getAddress());
        btcTx.addOutput(amountJustBelowMinimum, activeFederation.getAddress());
        btcTx.addOutput(amountJustBelowMinimum, activeFederation.getAddress());

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForFingerrot500
        );

        assertFalse(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When RSKIP379 is not yet active and there are many utxos to the fed but there is at least one of them below the minimum, it returns false."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_postRSKIP379WithManyOutputsForFedButEachBelowMinimumAndSumAboveMinimum_false() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        Coin amountJustBelowMinimum = minimumPegInTxValue.minus(Coin.SATOSHI);

        btcTx.addOutput(amountJustBelowMinimum, activeFederation.getAddress());
        btcTx.addOutput(amountJustBelowMinimum, activeFederation.getAddress());
        btcTx.addOutput(amountJustBelowMinimum, activeFederation.getAddress());

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForArrowhead600
        );

        assertFalse(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When RSKIP379 is active and there are many utxos to the fed but there is at least one of them below the minimum," +
                    " even though the sum of all those utxos is above minimum, it should return false."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_preRSKIP379WithManyOutputsForFedAboveMinimumAndOneBelowMinimum_false() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // With minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), activeFederation.getAddress()); // Below minimum

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForFingerrot500
        );

        assertFalse(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When RSKIP379 is not yet active and there are many utxos to the fed but there is at least one of them below the minimum, it returns false."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_postRSKIP379WithManyOutputsForFedAboveMinimumAndOneBelowMinimum_false() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // With minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), activeFederation.getAddress()); // Below minimum

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForArrowhead600
        );

        assertFalse(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When RSKIP379 is active and there are many utxos to the fed but there is at least one of them below the minimum, it should return true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_preRSKIP379WithManyOutputsForFedAllEqualOrAboveMinimum_true() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // With minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForFingerrot500
        );

        assertTrue(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When RSKIP379 is not yet active and there are many utxos to the fed above minimum, it returns true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_postRSKIP379WithManyOutputsForFedAllEqualOrAboveMinimum_true() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // With minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForArrowhead600
        );

        assertTrue(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When RSKIP379 is active and there are many utxos to the fed above minimum, it returns true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_preRSKIP379UtxosToRetiringFedAboveMinimumAndToActiveFedBelowMinimum_false() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), activeFederation.getAddress()); // Below minimum to active fed
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // Minimum to active fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), retiringFederation.getAddress()); // Above minimum to retiring fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), retiringFederation.getAddress()); // Above minimum to retiring fed

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            liveFedsWallet,
            minimumPegInTxValue,
            activationsForFingerrot500
        );

        assertFalse(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When PreRSKIP379 is not yet active and there are 2 feds, 1 retiring and 1 active, and there are utxos equal and above minimum for " +
                    "one of them and utxos below minimum for the other, it should return false."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_postRSKIP379UtxosToRetiringFedAboveMinimumAndToActiveFedBelowMinimum_false() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue.minus(Coin.SATOSHI), activeFederation.getAddress()); // Below minimum to active fed
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // Minimum to active fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), retiringFederation.getAddress()); // Above minimum to retiring fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), retiringFederation.getAddress()); // Above minimum to retiring fed

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            liveFedsWallet,
            minimumPegInTxValue,
            activationsForArrowhead600
        );

        assertFalse(allUTXOsToFedAreAboveMinimumPeginValue,
            "When PreRSKIP379 is active and there are 2 feds, 1 retiring and 1 active, and there are utxos equal and above minimum for " +
                        "one of them and utxos below minimum for the other, it should return false."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_preRSKIP379UtxosToRetiringAndActiveFedEqualToAndAboveMinimum_true() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // Minimum to active fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum to active fed
        btcTx.addOutput(minimumPegInTxValue, retiringFederation.getAddress()); // Minimum to retiring fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), retiringFederation.getAddress()); // Above minimum to retiring fed

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            liveFedsWallet,
            minimumPegInTxValue,
            activationsForFingerrot500
        );

        assertTrue(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When PreRSKIP379 is not yet active and there are 2 feds, 1 retiring and 1 active, and there are utxos equal and above minimum for " +
                        "both of them, it should return true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_postRSKIP379UtxosToRetiringAndActiveFedEqualToAndAboveMinimum_true() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // Minimum to active fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), activeFederation.getAddress()); // Above minimum to active fed
        btcTx.addOutput(minimumPegInTxValue, retiringFederation.getAddress()); // Minimum to retiring fed
        btcTx.addOutput(minimumPegInTxValue.add(Coin.SATOSHI), retiringFederation.getAddress()); // Above minimum to retiring fed

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            liveFedsWallet,
            minimumPegInTxValue,
            activationsForFingerrot500
        );

        assertTrue(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When PreRSKIP379 is active and there are 2 feds, 1 retiring and 1 active, and there are utxos equal and above minimum for " +
                        "both of them, it should return true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_preRSKIP379Exactly1UtxoToFedWithMinimumValue_true() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // Minimum to active fed

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForFingerrot500
        );

        assertTrue(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When PreRSKIP379 is not yet active and there is exactly 1 utxo with exactly the minimum value sent to the fed, it should return true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_postRSKIP379Exactly1UtxoToFedWithMinimumValue_true() {
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(minimumPegInTxValue, activeFederation.getAddress()); // Minimum to active fed

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            activeFedWallet,
            minimumPegInTxValue,
            activationsForFingerrot500
        );

        assertTrue(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When PreRSKIP379 is active and there is exactly 1 utxo with exactly the minimum value sent to the fed, it should return true."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_preRSKIP379UtxosBelowMinimumToBothRetiringAndActiveFed_false() {
        Coin belowMinimumValue = minimumPegInTxValue.minus(Coin.SATOSHI);
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(belowMinimumValue, activeFederation.getAddress());
        btcTx.addOutput(belowMinimumValue, activeFederation.getAddress());
        btcTx.addOutput(belowMinimumValue, retiringFederation.getAddress());
        btcTx.addOutput(belowMinimumValue, retiringFederation.getAddress());

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            liveFedsWallet,
            minimumPegInTxValue,
            activationsForFingerrot500
        );

        assertFalse(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When PreRSKIP379 is not yet active and there are 2 feds, 1 retiring and 1 active, and there are utxos below minimum for both, it should return false."
        );
    }

    @Test
    void test_allUTXOsToFedAreAboveMinimumPeginValue_postRSKIP379UtxosBelowMinimumToBothRetiringAndActiveFed_false() {
        Coin belowMinimumValue = minimumPegInTxValue.minus(Coin.SATOSHI);
        BtcTransaction btcTx = new BtcTransaction(bridgeMainnetConstants.getBtcParams());
        btcTx.addOutput(belowMinimumValue, activeFederation.getAddress());
        btcTx.addOutput(belowMinimumValue, activeFederation.getAddress());
        btcTx.addOutput(belowMinimumValue, retiringFederation.getAddress());
        btcTx.addOutput(belowMinimumValue, retiringFederation.getAddress());

        boolean allUTXOsToFedAreAboveMinimumPeginValue = PegUtils.allUTXOsToFedAreAboveMinimumPeginValue(
            btcTx,
            liveFedsWallet,
            minimumPegInTxValue,
            activationsForArrowhead600
        );

        assertFalse(
            allUTXOsToFedAreAboveMinimumPeginValue,
            "When PreRSKIP379 is active and there are 2 feds, 1 retiring and 1 active, and there are utxos below minimum for both, it should return false."
        );
    }
}
