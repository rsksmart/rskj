package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.*;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.peg.federation.*;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static co.rsk.peg.PegUtilsLegacy.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PegUtilsLegacyTest {

    private ActivationConfig.ForBlock activations;
    private BridgeConstants bridgeConstantsRegtest;
    private BridgeConstants bridgeConstantsMainnet;
    private NetworkParameters networkParameters;

    @BeforeEach
    void setupConfig() {
        activations = mock(ActivationConfig.ForBlock.class);
        bridgeConstantsRegtest = BridgeRegTestConstants.getInstance();
        bridgeConstantsMainnet = BridgeMainNetConstants.getInstance();
        networkParameters = bridgeConstantsRegtest.getBtcParams();
    }

    @Test
    void testIsValidPegInTx() {
        // Peg-in is for the genesis federation ATM
        Context btcContext = new Context(networkParameters);

        Federation federation = bridgeConstantsRegtest.getGenesisFederation();
        Wallet wallet = new BridgeBtcWallet(btcContext, Collections.singletonList(federation));
        Address federationAddress = federation.getAddress();
        wallet.addWatchedAddress(federationAddress, federation.getCreationTime().toEpochMilli());
        when(activations.isActive(any(ConsensusRule.class))).thenReturn(false);

        // Tx sending less than the minimum allowed, not a peg-in tx
        Coin minimumLockValue = bridgeConstantsRegtest.getMinimumPeginTxValue(activations);
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(minimumLockValue.subtract(Coin.CENT), federationAddress);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(isValidPegInTx(tx, federation, wallet, bridgeConstantsRegtest, activations));

        // Tx sending 1 btc to the federation, but also spending from the federation address,
        // the typical peg-out tx, not a peg-in tx.
        BtcTransaction tx2 = new BtcTransaction(networkParameters);
        tx2.addOutput(Coin.COIN, federationAddress);
        TransactionInput txIn = new TransactionInput(
            networkParameters,
            tx2,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx2.addInput(txIn);
        signWithNecessaryKeys(bridgeConstantsRegtest.getGenesisFederation(), BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS, txIn, tx2);
        assertFalse(isValidPegInTx(tx2, federation, wallet, bridgeConstantsRegtest, activations));

        // Tx sending 1 btc to the federation, is a peg-in tx
        BtcTransaction tx3 = new BtcTransaction(networkParameters);
        tx3.addOutput(Coin.COIN, federationAddress);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(isValidPegInTx(tx3, federation, wallet, bridgeConstantsRegtest, activations));

        // Tx sending 50 btc to the federation, is a peg-in tx
        BtcTransaction tx4 = new BtcTransaction(networkParameters);
        tx4.addOutput(Coin.FIFTY_COINS, federationAddress);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(isValidPegInTx(tx4, federation, wallet, bridgeConstantsRegtest, activations));
    }

    @Test
    void testIsValidPegInTx_less_than_minimum_not_pegin_after_iris() {
        // Tx sending less than the minimum allowed, not a peg-in tx
        Context btcContext = new Context(networkParameters);
        Federation federation = this.getGenesisFederationForTest(bridgeConstantsRegtest, btcContext);

        when(activations.isActive(ConsensusRule.RSKIP219)).thenReturn(true);
        Coin minimumPegInValueAfterIris = bridgeConstantsRegtest.getMinimumPeginTxValue(activations);

        // Tx sending less than the minimum allowed, not a peg-in tx
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(minimumPegInValueAfterIris.subtract(Coin.CENT), federation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(federation));
        assertFalse(isValidPegInTx(tx, federation, federationWallet, bridgeConstantsRegtest, activations));
    }

    @Test
    void testIsValidPegInTx_spending_from_federation_is_pegout_after_iris() {
        // Tx sending 1 btc to the federation, but also spending from the federation address,
        // the typical peg-out tx, not a peg-in tx.
        Context btcContext = new Context(networkParameters);
        Federation federation = this.getGenesisFederationForTest(bridgeConstantsRegtest, btcContext);

        when(activations.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, federation.getAddress());
        TransactionInput txIn = new TransactionInput(
            networkParameters,
            tx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx.addInput(txIn);
        signWithNecessaryKeys(bridgeConstantsRegtest.getGenesisFederation(), BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS, txIn, tx);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(federation));
        assertFalse(isValidPegInTx(tx, federation, federationWallet, bridgeConstantsRegtest, activations));
    }

    @Test
    void testIsValidPegInTx_sending_50_btc_after_iris() {
        // Tx sending 50 btc to the federation, is a peg-in tx
        Context btcContext = new Context(networkParameters);
        Federation federation = this.getGenesisFederationForTest(bridgeConstantsRegtest, btcContext);

        when(activations.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.FIFTY_COINS, federation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(federation));
        assertTrue(isValidPegInTx(tx, federation, federationWallet, bridgeConstantsRegtest, activations));
    }

    @Test
    void testIsValidPegInTx_value_between_old_and_new_before_iris() {
        // Tx sending btc between old and new value, it is not a peg-in before iris
        Context btcContext = new Context(networkParameters);
        Federation federation = this.getGenesisFederationForTest(bridgeConstantsRegtest, btcContext);

        when(activations.isActive(ConsensusRule.RSKIP219)).thenReturn(false);

        BtcTransaction tx = new BtcTransaction(networkParameters);

        // Get a value in between pre and post iris minimum
        Coin minimumPegInValueBeforeIris = bridgeConstantsRegtest.getMinimumPeginTxValue(ActivationConfigsForTest.papyrus200().forBlock(0));
        Coin minimumPegInValueAfterIris = bridgeConstantsRegtest.getMinimumPeginTxValue(ActivationConfigsForTest.iris300().forBlock(0));
        Coin valueLock = minimumPegInValueAfterIris.plus((minimumPegInValueBeforeIris.subtract(minimumPegInValueAfterIris)).div(2));
        assertTrue(valueLock.isLessThan(minimumPegInValueBeforeIris));
        assertTrue(valueLock.isGreaterThan(minimumPegInValueAfterIris));

        tx.addOutput(valueLock, federation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(federation));
        assertFalse(isValidPegInTx(tx, federation, federationWallet, bridgeConstantsRegtest, activations));
    }

    @Test
    void testIsValidPegInTx_value_between_old_and_new_after_iris() {
        // Tx sending btc between old and new value, it is a peg-in after iris
        Context btcContext = new Context(networkParameters);
        Federation federation = this.getGenesisFederationForTest(bridgeConstantsRegtest, btcContext);

        when(activations.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        BtcTransaction tx = new BtcTransaction(networkParameters);

        // Get a value in between pre and post iris minimum
        Coin minimumPegInValueBeforeIris = bridgeConstantsRegtest.getMinimumPeginTxValue(ActivationConfigsForTest.papyrus200().forBlock(0));
        Coin minimumPegInValueAfterIris = bridgeConstantsRegtest.getMinimumPeginTxValue(ActivationConfigsForTest.iris300().forBlock(0));
        Coin valueLock = minimumPegInValueAfterIris.plus((minimumPegInValueBeforeIris.subtract(minimumPegInValueAfterIris)).div(2));
        assertTrue(valueLock.isGreaterThan(minimumPegInValueAfterIris));
        assertTrue(valueLock.isLessThan(minimumPegInValueBeforeIris));

        tx.addOutput(valueLock, federation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(federation));
        assertTrue(isValidPegInTx(tx, federation, federationWallet, bridgeConstantsRegtest, activations));
    }

    @Test
    void testIsValidPegInTxForTwoFederations() {
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(any(ConsensusRule.class))).thenReturn(false);

        List<BtcECKey> federation1Keys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        FederationArgs args1 = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
            Instant.ofEpochMilli(1000L),
            0L
        );
        Federation federation1 = FederationFactory.buildStandardMultiSigFederation(
            args1,
            networkParameters
        );

        List<BtcECKey> federation2Keys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fb01")),
            BtcECKey.fromPrivate(Hex.decode("fb02")),
            BtcECKey.fromPrivate(Hex.decode("fb03"))
        );
        federation2Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        FederationArgs args2 = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(federation2Keys),
            Instant.ofEpochMilli(2000L),
            0L
        );
        Federation federation2 = FederationFactory.buildStandardMultiSigFederation(
            args2,
            networkParameters
        );

        Address address1 = federation1.getAddress();
        Address address2 = federation2.getAddress();

        List<Federation> federations = Arrays.asList(federation1, federation2);
        Wallet federationsWallet = new BridgeBtcWallet(btcContext, federations);

        Coin minimumPeginTxValue = bridgeConstantsRegtest.getMinimumPeginTxValue(activations);

        // Tx sending less than 1 btc to the first federation, not a peg-in tx
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.CENT, address1);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(isValidPegInTx(
            tx,
            federations,
            null,
            federationsWallet,
            minimumPeginTxValue,
            activations
        ));

        // Tx sending less than 1 btc to the second federation, not a peg-in tx
        tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.CENT, address2);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(isValidPegInTx(
            tx,
            federations,
            null,
            federationsWallet,
            minimumPeginTxValue,
            activations
        ));

        // Tx sending less than 1 btc to both federations, not a peg-in tx
        tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.CENT, address1);
        tx.addOutput(Coin.CENT, address2);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(isValidPegInTx(
            tx,
            federations,
            null,
            federationsWallet,
            minimumPeginTxValue,
            activations
        ));

        // Tx sending 1 btc to the first federation, but also spending from the first federation address, the typical peg-out tx, not a peg-in tx.
        BtcTransaction tx2 = new BtcTransaction(networkParameters);
        tx2.addOutput(Coin.COIN, address1);
        TransactionInput txIn = new TransactionInput(
            networkParameters,
            tx2,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx2.addInput(txIn);
        signWithNecessaryKeys(federation1, federation1Keys, txIn, tx2);
        assertFalse(isValidPegInTx(
            tx2,
            federations,
            null,
            federationsWallet,
            minimumPeginTxValue,
            activations
        ));

        // Tx sending 1 btc to the second federation, but also spending from the second federation address,
        // the typical peg-out tx, not a peg-in tx.
        tx2 = new BtcTransaction(networkParameters);
        tx2.addOutput(Coin.COIN, address2);
        txIn = new TransactionInput(
            networkParameters,
            tx2,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx2.addInput(txIn);
        signWithNecessaryKeys(federation2, federation2Keys, txIn, tx2);
        assertFalse(isValidPegInTx(
            tx2,
            federations,
            null,
            federationsWallet,
            minimumPeginTxValue,
            activations
        ));

        // Tx sending 1 btc to both federations, but also spending from the first federation address,
        // the typical peg-out tx, not a peg-in tx.
        tx2 = new BtcTransaction(networkParameters);
        tx2.addOutput(Coin.COIN, address1);
        tx2.addOutput(Coin.COIN, address2);
        txIn = new TransactionInput(
            networkParameters,
            tx2,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx2.addInput(txIn);
        signWithNecessaryKeys(federation1, federation1Keys, txIn, tx2);
        assertFalse(isValidPegInTx(
            tx2,
            federations,
            null,
            federationsWallet,
            minimumPeginTxValue,
            activations
        ));

        // Tx sending 1 btc to both federations, but also spending from the second federation address,
        // the typical peg-out tx, not a peg-in tx.
        tx2 = new BtcTransaction(networkParameters);
        tx2.addOutput(Coin.COIN, address1);
        tx2.addOutput(Coin.COIN, address2);
        txIn = new TransactionInput(
            networkParameters,
            tx2,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx2.addInput(txIn);
        signWithNecessaryKeys(federation2, federation2Keys, txIn, tx2);
        assertFalse(isValidPegInTx(
            tx2,
            federations,
            null,
            federationsWallet,
            minimumPeginTxValue,
            activations
        ));

        // Tx sending 1 btc from federation1 to federation2, the typical migration tx, not a peg-in tx.
        tx2 = new BtcTransaction(networkParameters);
        tx2.addOutput(Coin.COIN, address2);
        txIn = new TransactionInput(
            networkParameters,
            tx2,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx2.addInput(txIn);
        signWithNecessaryKeys(federation1, federation1Keys, txIn, tx2);
        assertFalse(isValidPegInTx(
            tx2,
            federations,
            null,
            federationsWallet,
            minimumPeginTxValue,
            activations
        ));

        // Tx sending 1 btc from federation1 to federation2, the typical migration tx from the retired federation,
        // not a peg-in tx.
        tx2 = new BtcTransaction(networkParameters);
        tx2.addOutput(Coin.COIN, address2);
        txIn = new TransactionInput(
            networkParameters,
            tx2,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx2.addInput(txIn);
        signWithNecessaryKeys(federation1, federation1Keys, txIn, tx2);
        assertFalse(isValidPegInTx(
            tx2,
            Collections.singletonList(federation2),
            federation1.getP2SHScript(),
            federationsWallet,
            minimumPeginTxValue,
            activations
        ));

        // Tx sending 1 btc to the first federation, is a peg-in tx
        BtcTransaction tx3 = new BtcTransaction(networkParameters);
        tx3.addOutput(Coin.COIN, address1);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(isValidPegInTx(
            tx3,
            federations,
            null,
            federationsWallet,
            minimumPeginTxValue,
            activations
        ));

        // Tx sending 1 btc to the second federation, is a peg-in tx
        tx3 = new BtcTransaction(networkParameters);
        tx3.addOutput(Coin.COIN, address2);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(isValidPegInTx(
            tx3,
            federations,
            null,
            federationsWallet,
            minimumPeginTxValue,
            activations
        ));

        // Tx sending 1 btc to the both federations, is a peg-in tx
        tx3 = new BtcTransaction(networkParameters);
        tx3.addOutput(Coin.COIN, address1);
        tx3.addOutput(Coin.COIN, address2);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(isValidPegInTx(
            tx3,
            federations,
            null,
            federationsWallet,
            minimumPeginTxValue,
            activations
        ));

        // Tx sending 50 btc to the first federation, is a peg-in tx
        BtcTransaction tx4 = new BtcTransaction(networkParameters);
        tx4.addOutput(Coin.FIFTY_COINS, address1);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(isValidPegInTx(
            tx4,
            federations,
            null,
            federationsWallet,
            minimumPeginTxValue,
            activations
        ));

        // Tx sending 50 btc to the second federation, is a peg-in tx
        tx4 = new BtcTransaction(networkParameters);
        tx4.addOutput(Coin.FIFTY_COINS, address2);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));

        assertTrue(isValidPegInTx(
            tx4,
            federations,
            null,
            federationsWallet,
            minimumPeginTxValue,
            activations
        ));

        // Tx sending 50 btc to the both federations, is a peg-in tx
        tx4 = new BtcTransaction(networkParameters);
        tx4.addOutput(Coin.FIFTY_COINS, address1);
        tx4.addOutput(Coin.FIFTY_COINS, address2);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(isValidPegInTx(
            tx4,
            federations,
            null,
            federationsWallet,
            minimumPeginTxValue,
            activations
        ));
    }

    @Test
    void testIsValidPegInTx_hasChangeUtxoFromFlyoverFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        Script flyoverRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            activeFederation.getRedeemScript(),
            Sha256Hash.of(PegTestUtils.createHash(1).getBytes())
        );

        // Create a tx from the fast bridge fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, flyoverRedeemScript);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        Assertions.assertTrue(isValidPegInTx(tx, activeFederation, federationWallet,
            bridgeConstantsRegtest, activations));
    }

    @Test
    void testIsValidPegInTx_hasChangeUtxoFromFlyoverFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        Script flyoverRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            activeFederation.getRedeemScript(),
            Sha256Hash.of(PegTestUtils.createHash(1).getBytes())
        );

        // Create a tx from the fast bridge fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, flyoverRedeemScript);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));
        Assertions.assertFalse(isValidPegInTx(tx, activeFederation, federationWallet,
            bridgeConstantsRegtest, activations));
    }

    @Test
    void testIsValidPegInTx_hasChangeUtxoFromFlyoverErpFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        List<BtcECKey> erpFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        erpFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        FederationArgs args = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(erpFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L
        );
        Federation erpFederation = FederationFactory.buildStandardMultiSigFederation(
            args,
            networkParameters
        );

        Script erpRedeemScript = ErpFederationRedeemScriptParser.createErpRedeemScript(
            activeFederation.getRedeemScript(),
            erpFederation.getRedeemScript(),
            500L
        );
        Script flyoverErpRedeemScript = FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
            erpRedeemScript,
            Sha256Hash.of(PegTestUtils.createHash(1).getBytes())
        );

        // Create a tx from the fast bridge erp fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, flyoverErpRedeemScript);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        Assertions.assertTrue(isValidPegInTx(
            tx,
            activeFederation,
            federationWallet,
            bridgeConstantsRegtest,
            activations
        ));
    }

    @Test
    void testIsValidPegInTx_hasChangeUtxoFromFlyoverErpFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        List<BtcECKey> erpFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        erpFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        FederationArgs args = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(erpFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L
        );
        Federation erpFederation = FederationFactory.buildStandardMultiSigFederation(
            args,
            networkParameters
        );

        Script erpRedeemScript = ErpFederationRedeemScriptParser.createErpRedeemScript(
            activeFederation.getRedeemScript(),
            erpFederation.getRedeemScript(),
            500L
        );
        Script flyoverErpRedeemScript = FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
            erpRedeemScript,
            Sha256Hash.of(PegTestUtils.createHash(1).getBytes())
        );

        // Create a tx from the fast bridge erp fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, flyoverErpRedeemScript);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        Assertions.assertFalse(isValidPegInTx(
            tx,
            activeFederation,
            federationWallet,
            bridgeConstantsRegtest,
            activations
        ));
    }

    @Test
    void testIsValidPegInTx_hasChangeUtxoFromErpFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        List<BtcECKey> erpFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        erpFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        FederationArgs args = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(erpFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L
        );
        Federation erpFederation = FederationFactory.buildStandardMultiSigFederation(
            args,
            networkParameters
        );

        Script erpRedeemScript = ErpFederationRedeemScriptParser.createErpRedeemScript(
            activeFederation.getRedeemScript(),
            erpFederation.getRedeemScript(),
            500L
        );

        // Create a tx from the erp fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, erpRedeemScript);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        Assertions.assertTrue(isValidPegInTx(tx, activeFederation, federationWallet,
            bridgeConstantsRegtest, activations));
    }

    @Test
    void testIsValidPegInTx_hasChangeUtxoFromErpFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        List<BtcECKey> erpFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        erpFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        FederationArgs args = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(erpFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L
        );
        Federation erpFederation = FederationFactory.buildStandardMultiSigFederation(
            args,
            networkParameters
        );

        Script erpRedeemScript = ErpFederationRedeemScriptParser.createErpRedeemScript(
            activeFederation.getRedeemScript(),
            erpFederation.getRedeemScript(),
            500L
        );

        // Create a tx from the erp fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, erpRedeemScript);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        Assertions.assertFalse(isValidPegInTx(tx, activeFederation, federationWallet,
            bridgeConstantsRegtest, activations));
    }

    @Test
    void testIsValidPegInTx_hasChangeUtxoFromFlyoverRetiredFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        List<BtcECKey> retiredFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        retiredFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        FederationArgs args = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L
        );
        Federation retiredFederation = FederationFactory.buildStandardMultiSigFederation(
            args,
            networkParameters
        );

        // Create a tx from the retired fast bridge fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        TransactionInput txInput = new TransactionInput(
            networkParameters,
            tx,
            new byte[0],
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx.addInput(txInput);

        Script flyoverRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            retiredFederation.getRedeemScript(),
            PegTestUtils.createHash(2)
        );
        signWithNecessaryKeys(retiredFederation, flyoverRedeemScript, retiredFederationKeys, txInput, tx);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertTrue(isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            federationWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            activations
        ));
    }

    @Test
    void testIsValidPegInTx_hasChangeUtxoFromFlyoverRetiredFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        List<BtcECKey> retiredFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        retiredFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        FederationArgs args = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L
        );
        Federation retiredFederation = FederationFactory.buildStandardMultiSigFederation(
            args,
            networkParameters
        );

        // Create a tx from the retired fast bridge fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        TransactionInput txInput = new TransactionInput(
            networkParameters,
            tx,
            new byte[0],
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx.addInput(txInput);

        Script flyoverRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            retiredFederation.getRedeemScript(),
            PegTestUtils.createHash(2)
        );
        signWithNecessaryKeys(retiredFederation, flyoverRedeemScript, retiredFederationKeys, txInput, tx);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertFalse(isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            federationWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            activations
        ));
    }

    @Test
    void testIsValidPegInTx_hasChangeUtxoFromFlyoverErpRetiredFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        List<BtcECKey> retiredFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        retiredFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        FederationArgs args = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L
        );
        Federation retiredFederation = FederationFactory.buildStandardMultiSigFederation(
            args,
            networkParameters
        );

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation erpFederation = FederationFactory.buildNonStandardErpFederation(
            args,
            networkParameters,
            erpFederationPublicKeys,
            500L,
            activations
        );

        // Create a tx from the retired fast bridge fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        TransactionInput txInput = new TransactionInput(
            networkParameters,
            tx,
            new byte[0],
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx.addInput(txInput);

        Script flyoverErpRedeemScript = FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
            erpFederation.getRedeemScript(),
            PegTestUtils.createHash(2)
        );
        signWithNecessaryKeys(erpFederation, flyoverErpRedeemScript, retiredFederationKeys, txInput, tx);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertTrue(isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            federationWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            activations
        ));
    }

    @Test
    void testIsValidPegInTx_hasChangeUtxoFromFlyoverErpRetiredFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        List<BtcECKey> retiredFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        retiredFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        FederationArgs args = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L
        );
        Federation retiredFederation = FederationFactory.buildStandardMultiSigFederation(
            args,
            networkParameters
        );

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation erpFederation = FederationFactory.buildNonStandardErpFederation(
            args,
            networkParameters,
            erpFederationPublicKeys,
            500L,
            activations
        );

        // Create a tx from the retired fast bridge fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        TransactionInput txInput = new TransactionInput(
            networkParameters,
            tx,
            new byte[0],
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx.addInput(txInput);

        Script flyoverErpRedeemScript = FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
            erpFederation.getRedeemScript(),
            PegTestUtils.createHash(2)
        );
        signWithNecessaryKeys(erpFederation, flyoverErpRedeemScript, retiredFederationKeys, txInput, tx);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertFalse(isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            federationWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            activations
        ));
    }

    @Test
    void testIsValidPegInTx_hasChangeUtxoFromErpRetiredFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();

        List<BtcECKey> retiredFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        retiredFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        FederationArgs args = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L
        );
        Federation retiredFederation = FederationFactory.buildStandardMultiSigFederation(
            args,
            networkParameters
        );

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation erpFederation = FederationFactory.buildNonStandardErpFederation(
            args,
            networkParameters,
            erpFederationPublicKeys,
            500L,
            activations
        );

        // Create a tx from the retired erp fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        TransactionInput txInput = new TransactionInput(
            networkParameters,
            tx,
            new byte[0],
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx.addInput(txInput);
        signWithErpFederation(erpFederation, retiredFederationKeys, txInput, tx);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertTrue(isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            federationWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            activations
        ));
    }

    @Test
    void testIsValidPegInTx_hasChangeUtxoFromErpRetiredFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        List<BtcECKey> retiredFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        retiredFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        FederationArgs retiredFedArgs = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L);
        Federation retiredFederation = FederationFactory.buildStandardMultiSigFederation(
            retiredFedArgs,
            networkParameters
        );

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation erpFederation = FederationFactory.buildNonStandardErpFederation(
            retiredFedArgs,
            networkParameters,
            erpFederationPublicKeys,
            500L,
            activations
        );

        // Create a tx from the retired erp fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        TransactionInput txInput = new TransactionInput(
            networkParameters,
            tx,
            new byte[0],
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx.addInput(txInput);
        signWithErpFederation(erpFederation, retiredFederationKeys, txInput, tx);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertFalse(isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            federationWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            activations
        ));
    }

    @Test
    void testIsValidPegInTx_has_multiple_utxos_below_minimum_but_total_amount_is_ok_before_RSKIP293() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(false);

        Coin minimumPeginValue = bridgeConstantsRegtest.getMinimumPeginTxValue(activations);
        // Create a tx with multiple utxos below the minimum but the sum of each utxos is equal to the minimum
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(minimumPeginValue.div(4), activeFederation.getAddress());
        tx.addOutput(minimumPeginValue.div(4), activeFederation.getAddress());
        tx.addOutput(minimumPeginValue.div(4), activeFederation.getAddress());
        tx.addOutput(minimumPeginValue.div(4), activeFederation.getAddress());

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertTrue(isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            null,
            federationWallet,
            minimumPeginValue,
            activations
        ));
    }

    @Test
    void testIsValidPegInTx_has_utxos_below_minimum_and_total_amount_as_well_before_RSKIP293() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(false);

        Coin minimumPeginValue = bridgeConstantsRegtest.getMinimumPeginTxValue(activations);
        // Create a tx with multiple utxos below the minimum, and the sum of each utxos as well is below the minimum
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(minimumPeginValue.div(4), activeFederation.getAddress());
        tx.addOutput(minimumPeginValue.div(4), activeFederation.getAddress());
        tx.addOutput(minimumPeginValue.div(4), activeFederation.getAddress());
        tx.addOutput(minimumPeginValue.div(5), activeFederation.getAddress());

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertFalse(isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            null,
            federationWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            activations
        ));
    }

    @Test
    void testIsValidPegInTx_has_utxos_below_minimum_after_RSKIP293() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        Coin minimumPeginValue = bridgeConstantsRegtest.getMinimumPeginTxValue(activations);
        Coin belowMinimumPeginValue = minimumPeginValue.divide(2);

        Coin aboveMinimumPeginValue = minimumPeginValue.add(Coin.COIN);
        // Create a tx with multiple utxos below, one equal to, and one above, the minimum.
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(belowMinimumPeginValue, activeFederation.getAddress());
        tx.addOutput(belowMinimumPeginValue, activeFederation.getAddress());
        tx.addOutput(belowMinimumPeginValue, activeFederation.getAddress());
        tx.addOutput(minimumPeginValue, activeFederation.getAddress());
        tx.addOutput(aboveMinimumPeginValue, activeFederation.getAddress());

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertFalse(isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            null,
            federationWallet,
            minimumPeginValue,
            activations
        ));
    }

    @Test
    void testIsValidPegInTx_utxo_equal_to_minimum_after_RSKIP293() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);

        Coin minimumPeginValue = bridgeConstantsRegtest.getMinimumPeginTxValue(activations);

        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(minimumPeginValue, activeFederation.getAddress());

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertTrue(isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            null,
            federationWallet,
            minimumPeginValue,
            activations
        ));
    }

    @Test
    void testIsValidPegInTx_p2shErpScript_sends_funds_to_federation_address_before_RSKIP353() {
        Address activeFederationAddress = bridgeConstantsRegtest.getGenesisFederation().getAddress();
        testIsValidPegInTx_fromP2shErpScriptSender(
            false,
            false,
            activeFederationAddress,
            true
        );
    }

    // It shouldn't identify transactions sent to random addresses as peg-in, but it is the current behaviour
    @Test
    void testIsValidPegInTx_p2shErpScript_sends_funds_to_random_address_before_RSKIP353() {
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        testIsValidPegInTx_fromP2shErpScriptSender(
            false,
            false,
            randomAddress,
            true
        );
    }

    @Test
    void testIsValidPegInTx_p2shErpScript_sends_funds_to_federation_address_after_RSKIP353() {
        Address activeFederationAddress = bridgeConstantsRegtest.getGenesisFederation().getAddress();
        testIsValidPegInTx_fromP2shErpScriptSender(
            true,
            false,
            activeFederationAddress,
            false
        );
    }

    @Test
    void testIsValidPegInTx_p2shErpScript_sends_funds_to_random_address_after_RSKIP353() {
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        testIsValidPegInTx_fromP2shErpScriptSender(
            true,
            false,
            randomAddress,
            false
        );
    }

    @Test
    void testIsValidPegInTx_flyoverP2shErpScript_sends_funds_to_federation_address_before_RSKIP353() {
        Address activeFederationAddress = bridgeConstantsRegtest.getGenesisFederation().getAddress();
        testIsValidPegInTx_fromP2shErpScriptSender(
            false,
            true,
            activeFederationAddress,
            true
        );
    }

    // It shouldn't identify transactions sent to random addresses as peg-in, but it is the current behaviour
    @Test
    void testIsValidPegInTx_flyoverP2shErpScript_sends_funds_to_random_address_before_RSKIP353() {
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        testIsValidPegInTx_fromP2shErpScriptSender(
            false,
            true,
            randomAddress,
            true
        );
    }

    @Test
    void testIsValidPegInTx_flyoverpP2shErpScript_sends_funds_to_federation_address_after_RSKIP353() {
        Address activeFederationAddress = bridgeConstantsRegtest.getGenesisFederation().getAddress();
        testIsValidPegInTx_fromP2shErpScriptSender(
            true,
            true,
            activeFederationAddress,
            false
        );
    }

    @Test
    void testIsValidPegInTx_flyoverP2shErpScript_sends_funds_to_random_address_after_RSKIP353() {
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        testIsValidPegInTx_fromP2shErpScriptSender(
            true,
            true,
            randomAddress,
            false
        );
    }

    private void testIsValidPegInTx_fromP2shErpScriptSender(
        boolean isRskip353Active,
        boolean flyoverFederation,
        Address destinationAddress,
        boolean expectedResult) {

        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP293)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP353)).thenReturn(isRskip353Active);

        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();

        List<BtcECKey> emergencyKeys = PegTestUtils.createRandomBtcECKeys(3);
        long activationDelay = 256L;

        FederationArgs args = new FederationArgs(activeFederation.getMembers(),
            activeFederation.getCreationTime(),
            activeFederation.getCreationBlockNumber());
        Federation p2shErpFederation = FederationFactory.buildP2shErpFederation(
            args,
            networkParameters,
            emergencyKeys,
            activationDelay
        );

        Script flyoverP2shErpRedeemScript = FastBridgeP2shErpRedeemScriptParser.createFastBridgeP2shErpRedeemScript(
            p2shErpFederation.getRedeemScript(),
            PegTestUtils.createHash(2)
        );

        // Create a tx from the p2sh erp fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, destinationAddress);
        tx.addInput(
            Sha256Hash.ZERO_HASH,
            0,
            flyoverFederation ? flyoverP2shErpRedeemScript : p2shErpFederation.getRedeemScript()
        );

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertEquals(expectedResult, isValidPegInTx(
            tx,
            activeFederation,
            federationWallet,
            bridgeConstantsRegtest,
            activations)
        );
    }

    @Test
    void testTxIsProcessableInLegacyVersion() {
        // Before hard fork
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        assertTrue(BridgeUtils.txIsProcessableInLegacyVersion(BtcLockSender.TxSenderAddressType.P2PKH, activations));
        assertFalse(BridgeUtils.txIsProcessableInLegacyVersion(BtcLockSender.TxSenderAddressType.P2SHP2WPKH, activations));
        assertFalse(BridgeUtils.txIsProcessableInLegacyVersion(BtcLockSender.TxSenderAddressType.P2SHMULTISIG, activations));
        assertFalse(BridgeUtils.txIsProcessableInLegacyVersion(BtcLockSender.TxSenderAddressType.P2SHP2WSH, activations));
        assertFalse(BridgeUtils.txIsProcessableInLegacyVersion(BtcLockSender.TxSenderAddressType.UNKNOWN, activations));

        // After hard fork
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        assertTrue(BridgeUtils.txIsProcessableInLegacyVersion(BtcLockSender.TxSenderAddressType.P2PKH, activations));
        assertTrue(BridgeUtils.txIsProcessableInLegacyVersion(BtcLockSender.TxSenderAddressType.P2SHP2WPKH, activations));
        assertTrue(BridgeUtils.txIsProcessableInLegacyVersion(BtcLockSender.TxSenderAddressType.P2SHMULTISIG, activations));
        assertTrue(BridgeUtils.txIsProcessableInLegacyVersion(BtcLockSender.TxSenderAddressType.P2SHP2WSH, activations));
        assertFalse(BridgeUtils.txIsProcessableInLegacyVersion(BtcLockSender.TxSenderAddressType.UNKNOWN, activations));
    }

    @Test
    void testIsMigrationTx_sending_funds_from_retired_p2sh_fed_to_active_p2sh_fed() {
        NetworkParameters networkParameters = bridgeConstantsMainnet.getBtcParams();
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        List<BtcECKey> retiredFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fc01")),
            BtcECKey.fromPrivate(Hex.decode("fc02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        FederationArgs retiredFedArgs = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L);
        ErpFederation retiredFederation = FederationFactory.buildP2shErpFederation(
            retiredFedArgs,
            bridgeConstantsMainnet.getBtcParams(),
            bridgeConstantsMainnet.getErpFedPubKeysList(),
            bridgeConstantsMainnet.getErpFedActivationDelay()
        );

        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        FederationArgs activeFedArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L);
        Federation activeFederation = FederationFactory.buildP2shErpFederation(
            activeFedArgs,
            bridgeConstantsMainnet.getBtcParams(),
            bridgeConstantsMainnet.getErpFedPubKeysList(),
            bridgeConstantsMainnet.getErpFedActivationDelay()
        );

        Address activeFederationAddress = activeFederation.getAddress();

        BtcTransaction migrationTx = new BtcTransaction(networkParameters);
        migrationTx.addOutput(Coin.COIN, activeFederationAddress);
        TransactionInput migrationTxInput = new TransactionInput(
            networkParameters,
            migrationTx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        migrationTx.addInput(migrationTxInput);
        signWithNecessaryKeys(retiredFederation, retiredFederationKeys, migrationTxInput, migrationTx);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertFalse(isMigrationTx(
            migrationTx,
            activeFederation,
            null,
            retiredFederation.getP2SHScript(),
            federationWallet,
            bridgeConstantsMainnet.getMinimumPeginTxValue(activations),
            activations
        ));

        assertTrue(isMigrationTx(
            migrationTx,
            activeFederation,
            null,
            retiredFederation.getDefaultP2SHScript(),
            federationWallet,
            bridgeConstantsMainnet.getMinimumPeginTxValue(activations),
            activations
        ));
    }

    @Test
    void testIsMigrationTx_sending_funds_from_retiring_p2sh_fed_to_active_p2sh_fed() {
        NetworkParameters networkParameters = bridgeConstantsMainnet.getBtcParams();
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        List<BtcECKey> retiringFed = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fc01")),
            BtcECKey.fromPrivate(Hex.decode("fc02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        FederationArgs retiringFedArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(retiringFed),
            Instant.ofEpochMilli(1000L),
            1L);
        Federation retiringFederation = FederationFactory.buildP2shErpFederation(
            retiringFedArgs,
            bridgeConstantsMainnet.getBtcParams(),
            bridgeConstantsMainnet.getErpFedPubKeysList(),
            bridgeConstantsMainnet.getErpFedActivationDelay()
        );

        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        FederationArgs activeFedArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L);
        Federation activeFederation = FederationFactory.buildP2shErpFederation(
            activeFedArgs,
            bridgeConstantsMainnet.getBtcParams(),
            bridgeConstantsMainnet.getErpFedPubKeysList(),
            bridgeConstantsMainnet.getErpFedActivationDelay()
        );

        Address activeFederationAddress = activeFederation.getAddress();

        BtcTransaction migrationTx = new BtcTransaction(networkParameters);
        migrationTx.addOutput(Coin.COIN, activeFederationAddress);
        TransactionInput migrationTxInput = new TransactionInput(
            networkParameters,
            migrationTx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        migrationTx.addInput(migrationTxInput);
        signWithNecessaryKeys(retiringFederation, retiringFed, migrationTxInput, migrationTx);

        Wallet federationsWallet = new BridgeBtcWallet(btcContext, Arrays.asList(activeFederation, retiringFederation));

        assertTrue(isMigrationTx(
            migrationTx,
            activeFederation,
            retiringFederation,
            null,
            federationsWallet,
            bridgeConstantsMainnet.getMinimumPeginTxValue(activations),
            activations
        ));
    }

    @Test
    void testIsMigrationTx_sending_funds_from_retired_standard_fed_to_active_p2sh_fed() {
        NetworkParameters networkParameters = bridgeConstantsMainnet.getBtcParams();
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        List<BtcECKey> retiredFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fc01")),
            BtcECKey.fromPrivate(Hex.decode("fc02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        FederationArgs retiredFedArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L);
        Federation retiredFederation = FederationFactory.buildStandardMultiSigFederation(
            retiredFedArgs,
            bridgeConstantsMainnet.getBtcParams()
        );

        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        FederationArgs activeFedArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L);
        Federation activeFederation = FederationFactory.buildP2shErpFederation(
            activeFedArgs,
            bridgeConstantsMainnet.getBtcParams(),
            bridgeConstantsMainnet.getErpFedPubKeysList(),
            bridgeConstantsMainnet.getErpFedActivationDelay()
        );

        Address activeFederationAddress = activeFederation.getAddress();

        BtcTransaction migrationTx = new BtcTransaction(networkParameters);
        migrationTx.addOutput(Coin.COIN, activeFederationAddress);
        TransactionInput migrationTxInput = new TransactionInput(
            networkParameters,
            migrationTx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        migrationTx.addInput(migrationTxInput);
        signWithNecessaryKeys(retiredFederation, retiredFederationKeys, migrationTxInput, migrationTx);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertTrue(isMigrationTx(
            migrationTx,
            activeFederation,
            null,
            retiredFederation.getP2SHScript(),
            federationWallet,
            bridgeConstantsMainnet.getMinimumPeginTxValue(activations),
            activations
        ));

        assertTrue(isMigrationTx(
            migrationTx,
            activeFederation,
            null,
            retiredFederation.getP2SHScript(),
            federationWallet,
            bridgeConstantsMainnet.getMinimumPeginTxValue(activations),
            activations
        ));
    }

    @Test
    void testIsMigrationTx_sending_funds_from_retiring_standard_fed_to_active_p2sh_fed() {
        NetworkParameters networkParameters = bridgeConstantsMainnet.getBtcParams();
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        List<BtcECKey> retiringFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fc01")),
            BtcECKey.fromPrivate(Hex.decode("fc02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        FederationArgs retiringFedArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(retiringFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L);
        Federation retiringFederation = FederationFactory.buildStandardMultiSigFederation(
            retiringFedArgs,
            bridgeConstantsMainnet.getBtcParams()
        );

        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        FederationArgs activeFedArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L);
        Federation activeFederation = FederationFactory.buildP2shErpFederation(
            activeFedArgs,
            bridgeConstantsMainnet.getBtcParams(),
            bridgeConstantsMainnet.getErpFedPubKeysList(),
            bridgeConstantsMainnet.getErpFedActivationDelay()
        );

        Address activeFederationAddress = activeFederation.getAddress();

        BtcTransaction migrationTx = new BtcTransaction(networkParameters);
        migrationTx.addOutput(Coin.COIN, activeFederationAddress);
        TransactionInput migrationTxInput = new TransactionInput(
            networkParameters,
            migrationTx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        migrationTx.addInput(migrationTxInput);
        signWithNecessaryKeys(retiringFederation, retiringFederationKeys, migrationTxInput, migrationTx);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertTrue(isMigrationTx(
            migrationTx,
            activeFederation,
            retiringFederation,
            null,
            federationWallet,
            bridgeConstantsMainnet.getMinimumPeginTxValue(activations),
            activations
        ));
    }

    @Test
    void testIsMigrationTx_sending_funds_from_retired_standard_fed_to_active_standard_fed() {
        NetworkParameters networkParameters = bridgeConstantsMainnet.getBtcParams();
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        List<BtcECKey> retiredFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fc01")),
            BtcECKey.fromPrivate(Hex.decode("fc02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        FederationArgs retiredFedArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L);
        Federation retiredFederation = FederationFactory.buildStandardMultiSigFederation(
            retiredFedArgs,
            bridgeConstantsMainnet.getBtcParams()
        );

        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        FederationArgs activeFedArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L);
        Federation activeFederation = FederationFactory.buildStandardMultiSigFederation(
            activeFedArgs,
            bridgeConstantsMainnet.getBtcParams()
        );

        Address activeFederationAddress = activeFederation.getAddress();

        BtcTransaction migrationTx = new BtcTransaction(networkParameters);
        migrationTx.addOutput(Coin.COIN, activeFederationAddress);
        TransactionInput migrationTxInput = new TransactionInput(
            networkParameters,
            migrationTx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        migrationTx.addInput(migrationTxInput);
        signWithNecessaryKeys(retiredFederation, retiredFederationKeys, migrationTxInput, migrationTx);

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertTrue(isMigrationTx(
            migrationTx,
            activeFederation,
            null,
            retiredFederation.getP2SHScript(),
            federationWallet,
            bridgeConstantsMainnet.getMinimumPeginTxValue(activations),
            activations
        ));

        assertTrue(isMigrationTx(
            migrationTx,
            activeFederation,
            null,
            retiredFederation.getP2SHScript(),
            federationWallet,
            bridgeConstantsMainnet.getMinimumPeginTxValue(activations),
            activations
        ));
    }

    @Test
    void testIsMigrationTx_sending_funds_from_retiring_standard_fed_to_active_standard_fed() {
        NetworkParameters networkParameters = bridgeConstantsMainnet.getBtcParams();
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        List<BtcECKey> retiringFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fc01")),
            BtcECKey.fromPrivate(Hex.decode("fc02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        FederationArgs retiringFedArgs = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiringFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L);
        Federation retiringFederation = FederationFactory.buildStandardMultiSigFederation(
            retiringFedArgs,
            bridgeConstantsMainnet.getBtcParams()
        );

        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        FederationArgs activeFedArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L);
        Federation activeFederation = FederationFactory.buildStandardMultiSigFederation(
            activeFedArgs,
            bridgeConstantsMainnet.getBtcParams()
        );

        Address activeFederationAddress = activeFederation.getAddress();

        BtcTransaction migrationTx = new BtcTransaction(networkParameters);
        migrationTx.addOutput(Coin.COIN, activeFederationAddress);
        TransactionInput migrationTxInput = new TransactionInput(
            networkParameters,
            migrationTx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        migrationTx.addInput(migrationTxInput);
        signWithNecessaryKeys(retiringFederation, retiringFederationKeys, migrationTxInput, migrationTx);

        Wallet federationsWallet = new BridgeBtcWallet(btcContext, Arrays.asList(activeFederation, retiringFederation));

        assertTrue(isMigrationTx(
            migrationTx,
            activeFederation,
            retiringFederation,
            null,
            federationsWallet,
            bridgeConstantsMainnet.getMinimumPeginTxValue(activations),
            activations
        ));
    }

    @Test
    void testIsMigrationTx() {
        Context btcContext = new Context(networkParameters);

        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        FederationArgs activeFedArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
            Instant.ofEpochMilli(2000L),
            2L);
        Federation activeFederation = FederationFactory.buildStandardMultiSigFederation(
            activeFedArgs,
            networkParameters
        );

        List<BtcECKey> retiringFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fb01")),
            BtcECKey.fromPrivate(Hex.decode("fb02")),
            BtcECKey.fromPrivate(Hex.decode("fb03"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());

        FederationArgs retiringFedArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(retiringFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L);
        Federation retiringFederation = FederationFactory.buildStandardMultiSigFederation(
            retiringFedArgs,
            networkParameters
        );

        List<BtcECKey> retiredFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fc01")),
            BtcECKey.fromPrivate(Hex.decode("fc02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        FederationArgs retiredFedArgs = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L);
        Federation retiredFederation = FederationFactory.buildStandardMultiSigFederation(
            retiredFedArgs,
            networkParameters
        );

        Address activeFederationAddress = activeFederation.getAddress();

        BtcTransaction migrationTx = new BtcTransaction(networkParameters);
        migrationTx.addOutput(Coin.COIN, activeFederationAddress);
        TransactionInput migrationTxInput = new TransactionInput(
            networkParameters,
            migrationTx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        migrationTx.addInput(migrationTxInput);
        signWithNecessaryKeys(retiringFederation, retiringFederationKeys, migrationTxInput, migrationTx);

        Wallet federationsWallet = new BridgeBtcWallet(btcContext, Arrays.asList(activeFederation, retiringFederation));

        assertTrue(isMigrationTx(
            migrationTx,
            activeFederation,
            retiringFederation,
            null,
            federationsWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            activations
        ));

        BtcTransaction toActiveFederationTx = new BtcTransaction(networkParameters);
        toActiveFederationTx.addOutput(Coin.COIN, activeFederationAddress);
        toActiveFederationTx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(isMigrationTx(
            toActiveFederationTx,
            activeFederation,
            retiringFederation,
            null,
            federationsWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            activations
        ));

        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstantsRegtest.getBtcParams());
        BtcTransaction fromRetiringFederationTx = new BtcTransaction(networkParameters);
        fromRetiringFederationTx.addOutput(Coin.COIN, randomAddress);
        TransactionInput fromRetiringFederationTxInput = new TransactionInput(
            networkParameters,
            fromRetiringFederationTx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        fromRetiringFederationTx.addInput(fromRetiringFederationTxInput);
        signWithNecessaryKeys(retiringFederation, retiringFederationKeys, fromRetiringFederationTxInput, fromRetiringFederationTx);
        assertFalse(isMigrationTx(
            fromRetiringFederationTx,
            activeFederation,
            retiringFederation,
            null,
            federationsWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            activations
        ));

        Wallet federationWallet = new BridgeBtcWallet(btcContext, Collections.singletonList(activeFederation));

        assertFalse(isMigrationTx(
            migrationTx,
            activeFederation,
            null,
            null,
            federationWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            activations
        ));

        BtcTransaction retiredMigrationTx = new BtcTransaction(networkParameters);
        retiredMigrationTx.addOutput(Coin.COIN, activeFederationAddress);
        TransactionInput retiredMigrationTxInput = new TransactionInput(
            networkParameters,
            retiredMigrationTx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        retiredMigrationTx.addInput(retiredMigrationTxInput);
        signWithNecessaryKeys(retiredFederation, retiredFederationKeys, retiredMigrationTxInput, retiredMigrationTx);
        Script p2SHScript = retiredFederation.getP2SHScript();
        assertTrue(isMigrationTx(
            retiredMigrationTx,
            activeFederation,
            null,
            p2SHScript,
            federationWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            activations
        ));

        assertTrue(isMigrationTx(
            retiredMigrationTx,
            activeFederation,
            retiringFederation,
            p2SHScript,
            federationsWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            activations
        ));
        assertFalse(isMigrationTx(
            toActiveFederationTx,
            activeFederation,
            null,
            p2SHScript,
            federationWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            activations
        ));
        assertFalse(isMigrationTx(
            toActiveFederationTx,
            activeFederation,
            retiringFederation,
            p2SHScript,
            federationsWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            activations
        ));
    }

    @Test
    void testIsPegOutTx() {
        Federation federation = bridgeConstantsRegtest.getGenesisFederation();
        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        FederationArgs args = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
            Instant.ofEpochMilli(2000L),
            2L);
        Federation federation2 = FederationFactory.buildStandardMultiSigFederation(
            args,
            bridgeConstantsRegtest.getBtcParams()
        );
        List<BtcECKey> federationPrivateKeys = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS;
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstantsRegtest.getBtcParams());

        BtcTransaction pegOutTx1 = new BtcTransaction(networkParameters);
        pegOutTx1.addOutput(Coin.COIN, randomAddress);
        TransactionInput pegOutInput1 = new TransactionInput(
            networkParameters,
            pegOutTx1,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        pegOutTx1.addInput(pegOutInput1);
        signWithNecessaryKeys(federation, federationPrivateKeys, pegOutInput1, pegOutTx1);

        assertTrue(isPegOutTx(pegOutTx1, Collections.singletonList(federation), activations));
        assertTrue(isPegOutTx(pegOutTx1, Arrays.asList(federation, federation2), activations));
        assertFalse(isPegOutTx(pegOutTx1, Collections.singletonList(federation2), activations));

        assertTrue(isPegOutTx(pegOutTx1, activations, federation.getP2SHScript()));
        assertTrue(isPegOutTx(pegOutTx1, activations, federation.getP2SHScript(), federation2.getP2SHScript()));
        assertFalse(isPegOutTx(pegOutTx1, activations, federation2.getP2SHScript()));
    }

    @Test
    void testIsPegOutTx_fromFlyoverFederation() {
        List<BtcECKey> flyoverFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        flyoverFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        FederationArgs args = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(flyoverFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L);
        Federation flyoverFederation = FederationFactory.buildStandardMultiSigFederation(
            args,
            networkParameters
        );

        Federation standardFederation = bridgeConstantsRegtest.getGenesisFederation();

        // Create a tx from the fast bridge fed to a random address
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        BtcTransaction pegOutTx1 = new BtcTransaction(networkParameters);
        pegOutTx1.addOutput(Coin.COIN, randomAddress);
        TransactionInput pegOutInput1 = new TransactionInput(
            networkParameters,
            pegOutTx1,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        pegOutTx1.addInput(pegOutInput1);

        Script flyoverRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            flyoverFederation.getRedeemScript(),
            PegTestUtils.createHash(2)
        );
        signWithNecessaryKeys(flyoverFederation, flyoverRedeemScript, flyoverFederationKeys, pegOutInput1, pegOutTx1);

        // Before RSKIP 201 activation
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        assertFalse(isPegOutTx(pegOutTx1, Collections.singletonList(flyoverFederation), activations));
        assertFalse(isPegOutTx(pegOutTx1, Arrays.asList(flyoverFederation, standardFederation), activations));
        assertFalse(isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertFalse(isPegOutTx(pegOutTx1, activations, flyoverFederation.getP2SHScript()));
        assertFalse(isPegOutTx(pegOutTx1, activations, flyoverFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));

        // After RSKIP 201 activation
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        assertTrue(isPegOutTx(pegOutTx1, Collections.singletonList(flyoverFederation), activations));
        assertTrue(isPegOutTx(pegOutTx1, Arrays.asList(flyoverFederation, standardFederation), activations));
        assertFalse(isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertTrue(isPegOutTx(pegOutTx1, activations, flyoverFederation.getP2SHScript()));
        assertTrue(isPegOutTx(pegOutTx1, activations, flyoverFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));
    }

    @Test
    void testIsPegOutTx_fromErpFederation() {
        List<BtcECKey> defaultFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        defaultFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        FederationArgs args = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(defaultFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L);
        Federation defaultFederation = FederationFactory.buildStandardMultiSigFederation(
            args,
            networkParameters
        );

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04")),
            BtcECKey.fromPrivate(Hex.decode("fa05"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        ErpFederation erpFederation = FederationFactory.buildNonStandardErpFederation(
            args,
            networkParameters,
            erpFederationPublicKeys,
            500L,
            activations
        );

        Federation standardFederation = bridgeConstantsRegtest.getGenesisFederation();

        // Create a tx from the erp fed to a random address
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        BtcTransaction pegOutTx1 = new BtcTransaction(networkParameters);
        pegOutTx1.addOutput(Coin.COIN, randomAddress);
        TransactionInput pegOutInput1 = new TransactionInput(
            networkParameters,
            pegOutTx1,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        pegOutTx1.addInput(pegOutInput1);
        signWithErpFederation(erpFederation, defaultFederationKeys, pegOutInput1, pegOutTx1);

        // Before RSKIP 201 activation
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        assertFalse(isPegOutTx(pegOutTx1, Collections.singletonList(defaultFederation), activations));
        assertFalse(isPegOutTx(pegOutTx1, Arrays.asList(defaultFederation, standardFederation), activations));
        assertFalse(isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertFalse(isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript()));
        assertFalse(isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));

        assertFalse(isPegOutTx(pegOutTx1, Collections.singletonList(erpFederation), activations));
        assertFalse(isPegOutTx(pegOutTx1, activations, erpFederation.getDefaultP2SHScript()));

        // After RSKIP 201 activation
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        assertTrue(isPegOutTx(pegOutTx1, Collections.singletonList(defaultFederation), activations));
        assertTrue(isPegOutTx(pegOutTx1, Arrays.asList(defaultFederation, standardFederation), activations));
        assertFalse(isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertTrue(isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript()));
        assertTrue(isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));

        assertTrue(isPegOutTx(pegOutTx1, Collections.singletonList(erpFederation), activations));
        assertTrue(isPegOutTx(pegOutTx1, activations, erpFederation.getDefaultP2SHScript()));
    }

    @Test
    void testIsPegOutTx_fromFlyoverErpFederation() {
        List<BtcECKey> defaultFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        defaultFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        FederationArgs args = new FederationArgs(FederationTestUtils.getFederationMembersWithBtcKeys(defaultFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L);
        Federation defaultFederation = FederationFactory.buildStandardMultiSigFederation(
            args,
            networkParameters
        );

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04")),
            BtcECKey.fromPrivate(Hex.decode("fa05"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation erpFederation = FederationFactory.buildNonStandardErpFederation(
            args,
            networkParameters,
            erpFederationPublicKeys,
            500L,
            activations
        );

        Federation standardFederation = bridgeConstantsRegtest.getGenesisFederation();

        // Create a tx from the fast bridge erp fed to a random address
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        BtcTransaction pegOutTx1 = new BtcTransaction(networkParameters);
        pegOutTx1.addOutput(Coin.COIN, randomAddress);
        TransactionInput pegOutInput1 = new TransactionInput(
            networkParameters,
            pegOutTx1,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        pegOutTx1.addInput(pegOutInput1);

        Script flyoverErpRedeemScript = FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
            erpFederation.getRedeemScript(),
            PegTestUtils.createHash(2)
        );
        signWithNecessaryKeys(erpFederation, flyoverErpRedeemScript, defaultFederationKeys, pegOutInput1, pegOutTx1);

        // Before RSKIP 201 activation
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        assertFalse(isPegOutTx(pegOutTx1, Collections.singletonList(defaultFederation), activations));
        assertFalse(isPegOutTx(pegOutTx1, Arrays.asList(defaultFederation, standardFederation), activations));
        assertFalse(isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertFalse(isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript()));
        assertFalse(isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));

        // After RSKIP 201 activation
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        assertTrue(isPegOutTx(pegOutTx1, Collections.singletonList(defaultFederation), activations));
        assertTrue(isPegOutTx(pegOutTx1, Arrays.asList(defaultFederation, standardFederation), activations));
        assertFalse(isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertTrue(isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript()));
        assertTrue(isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));
    }

    @Test
    void testIsPegOutTx_noRedeemScript() {
        Federation federation = bridgeConstantsRegtest.getGenesisFederation();
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);

        BtcTransaction pegOutTx1 = new BtcTransaction(networkParameters);
        pegOutTx1.addOutput(Coin.COIN, randomAddress);
        TransactionInput pegOutInput1 = new TransactionInput(
            networkParameters,
            pegOutTx1,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        pegOutTx1.addInput(pegOutInput1);

        assertFalse(isPegOutTx(pegOutTx1, Collections.singletonList(federation), activations));
    }

    @Test
    void testIsPegOutTx_invalidRedeemScript() {
        Federation federation = bridgeConstantsRegtest.getGenesisFederation();
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Script invalidRedeemScript = ScriptBuilder.createRedeemScript(2, Arrays.asList(new BtcECKey(), new BtcECKey()));

        BtcTransaction pegOutTx1 = new BtcTransaction(networkParameters);
        pegOutTx1.addOutput(Coin.COIN, randomAddress);
        TransactionInput pegOutInput1 = new TransactionInput(
            networkParameters,
            pegOutTx1,
            invalidRedeemScript.getProgram(),
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        pegOutTx1.addInput(pegOutInput1);

        assertFalse(isPegOutTx(pegOutTx1, Collections.singletonList(federation), activations));
    }

    @Test
    void testChangeBetweenFederations() {
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);
        Context btcContext = new Context(networkParameters);

        List<BtcECKey> federation1Keys = Stream.of("fa01", "fa02")
            .map(Hex::decode)
            .map(BtcECKey::fromPrivate)
            .sorted(BtcECKey.PUBKEY_COMPARATOR)
            .collect(Collectors.toList());
        FederationArgs args1 = new FederationArgs(
            FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
            Instant.ofEpochMilli(1000L),
            0L
        );
        Federation federation1 = FederationFactory.buildStandardMultiSigFederation(
            args1,
            networkParameters
        );

        List<BtcECKey> federation2Keys = Stream.of("fb01", "fb02", "fb03")
            .map(Hex::decode)
            .map(BtcECKey::fromPrivate)
            .sorted(BtcECKey.PUBKEY_COMPARATOR)
            .collect(Collectors.toList());
        List<FederationMember> federation2Members = FederationTestUtils.getFederationMembersWithBtcKeys(federation2Keys);
        FederationArgs args2 = new FederationArgs(federation2Members,
            Instant.ofEpochMilli(2000L),
            0L);
        Federation federation2 = FederationFactory.buildStandardMultiSigFederation(
            args2,
            networkParameters
        );

        Address federation2Address = federation2.getAddress();

        List<Federation> federations = Arrays.asList(federation1, federation2);

        BtcTransaction pegOutWithChange = new BtcTransaction(networkParameters);
        pegOutWithChange.addOutput(Coin.COIN, randomAddress);
        pegOutWithChange.addOutput(Coin.COIN, federation2Address);
        TransactionInput pegOutFromFederation2 = new TransactionInput(
            networkParameters,
            pegOutWithChange,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        pegOutWithChange.addInput(pegOutFromFederation2);
        signWithNecessaryKeys(federation2, federation2Keys, pegOutFromFederation2, pegOutWithChange);

        BridgeBtcWallet federationsWallet = new BridgeBtcWallet(btcContext, federations);

        assertFalse(isValidPegInTx(
            pegOutWithChange,
            federations,
            null,
            federationsWallet,
            bridgeConstantsRegtest.getMinimumPeginTxValue(activations),
            mock(ActivationConfig.ForBlock.class)
        ));
        assertTrue(isPegOutTx(pegOutWithChange, federations, activations));
    }

    @Test
    void testIsAnyUTXOAmountBelowMinimum_has_utxos_below_minimum() {
        Coin minimumPegInTxValue = bridgeConstantsRegtest.getMinimumPeginTxValue(activations);
        Coin valueBelowMinimum = minimumPegInTxValue.minus(Coin.SATOSHI);
        Coin valueAboveMinimum = minimumPegInTxValue.plus(Coin.SATOSHI);

        BtcTransaction btcTx = new BtcTransaction(bridgeConstantsRegtest.getBtcParams());

        Address btcAddressReceivingFundsBelowMin = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstantsRegtest.getBtcParams());
        Address btcAddressReceivingFundsEqualToMin = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstantsRegtest.getBtcParams());
        Address btcAddressReceivingFundsAboveMin = PegTestUtils.createRandomP2PKHBtcAddress(bridgeConstantsRegtest.getBtcParams());

        btcTx.addOutput(valueBelowMinimum, btcAddressReceivingFundsBelowMin);
        btcTx.addOutput(minimumPegInTxValue, btcAddressReceivingFundsEqualToMin);
        btcTx.addOutput(minimumPegInTxValue, btcAddressReceivingFundsEqualToMin);
        btcTx.addOutput(valueAboveMinimum, btcAddressReceivingFundsAboveMin);
        btcTx.addOutput(valueAboveMinimum, btcAddressReceivingFundsAboveMin);


        WatchedBtcWallet wallet = new WatchedBtcWallet(new Context(bridgeConstantsRegtest.getBtcParams()));
        long now = Utils.currentTimeMillis() / 1000L;
        wallet.addWatchedAddresses(Arrays.asList(
            btcAddressReceivingFundsBelowMin,
            btcAddressReceivingFundsAboveMin
        ), now);

        assertTrue(
            isAnyUTXOAmountBelowMinimum(
                minimumPegInTxValue,
                btcTx,
                wallet
            )
        );

        wallet = new WatchedBtcWallet(new Context(bridgeConstantsRegtest.getBtcParams()));
        wallet.addWatchedAddresses(Arrays.asList(
                btcAddressReceivingFundsBelowMin,
                btcAddressReceivingFundsEqualToMin
            ),
            now
        );

        assertTrue(
            isAnyUTXOAmountBelowMinimum(
                minimumPegInTxValue,
                btcTx,
                wallet
            )
        );

        wallet = new WatchedBtcWallet(new Context(bridgeConstantsRegtest.getBtcParams()));
        wallet.addWatchedAddresses(Arrays.asList(
                btcAddressReceivingFundsBelowMin
            ),
            now
        );

        assertTrue(
            isAnyUTXOAmountBelowMinimum(
                minimumPegInTxValue,
                btcTx,
                wallet
            )
        );

        wallet = new WatchedBtcWallet(new Context(bridgeConstantsRegtest.getBtcParams()));
        wallet.addWatchedAddresses(Arrays.asList(
                btcAddressReceivingFundsEqualToMin
            ),
            now
        );

        assertFalse(
            isAnyUTXOAmountBelowMinimum(
                minimumPegInTxValue,
                btcTx,
                wallet
            )
        );

        wallet = new WatchedBtcWallet(new Context(bridgeConstantsRegtest.getBtcParams()));
        wallet.addWatchedAddresses(Arrays.asList(
                btcAddressReceivingFundsAboveMin
            ),
            now
        );

        assertFalse(
            isAnyUTXOAmountBelowMinimum(
                minimumPegInTxValue,
                btcTx,
                wallet
            )
        );

        wallet = new WatchedBtcWallet(new Context(bridgeConstantsRegtest.getBtcParams()));
        wallet.addWatchedAddresses(Arrays.asList(
                btcAddressReceivingFundsEqualToMin,
                btcAddressReceivingFundsAboveMin
            ),
            now
        );

        assertFalse(
            isAnyUTXOAmountBelowMinimum(
                minimumPegInTxValue,
                btcTx,
                wallet
            )
        );
    }

    @Test
    void scriptCorrectlySpends_fromGenesisFederation_ok() {
        Federation genesisFederation = bridgeConstantsRegtest.getGenesisFederation();
        Address destinationAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);

        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, destinationAddress);
        TransactionInput txIn = new TransactionInput(
            networkParameters,
            tx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx.addInput(txIn);
        signWithNecessaryKeys(genesisFederation, BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS, txIn, tx);

        assertTrue(scriptCorrectlySpendsTx(tx, 0, genesisFederation.getP2SHScript()));
    }

    @Test
    void scriptCorrectlySpends_invalidScript() {
        Federation genesisFederation = bridgeConstantsRegtest.getGenesisFederation();
        Address destinationAddress = PegTestUtils.createRandomP2PKHBtcAddress(networkParameters);

        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, destinationAddress);
        TransactionInput txIn = new TransactionInput(
            networkParameters,
            tx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx.addInput(txIn);
        signWithNecessaryKeys(genesisFederation, BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS, txIn, tx);

        // Add script op codes to the tx input script sig to make it invalid
        ScriptBuilder scriptBuilder = new ScriptBuilder(tx.getInput(0).getScriptSig());
        Script invalidScript = scriptBuilder
            .op(ScriptOpCodes.OP_IF)
            .op(ScriptOpCodes.OP_ENDIF)
            .build();
        tx.getInput(0).setScriptSig(invalidScript);

        assertFalse(scriptCorrectlySpendsTx(tx, 0, genesisFederation.getP2SHScript()));
    }

    private void signWithErpFederation(Federation erpFederation, List<BtcECKey> privateKeys, TransactionInput txIn, BtcTransaction tx) {
        signWithNecessaryKeys(erpFederation, privateKeys, txIn, tx);
        // Add OP_0 prefix to make it a valid erp federation script
        Script erpInputScript = new ScriptBuilder()
            .number(ScriptOpCodes.OP_0)
            .addChunks(txIn.getScriptSig().getChunks())
            .build();

        txIn.setScriptSig(erpInputScript);
    }

    private void signWithNecessaryKeys(
        Federation federation,
        List<BtcECKey> privateKeys,
        TransactionInput txIn,
        BtcTransaction tx) {

        signWithNecessaryKeys(
            federation,
            federation.getRedeemScript(),
            privateKeys,
            txIn,
            tx
        );
    }

    private void signWithNecessaryKeys(
        Federation federation,
        Script federationRedeemScript,
        List<BtcECKey> privateKeys,
        TransactionInput txIn,
        BtcTransaction tx) {

        signWithNKeys(
            federation,
            federationRedeemScript,
            privateKeys,
            txIn,
            tx,
            federation.getNumberOfSignaturesRequired()
        );
    }

    private void signWithNKeys(
        Federation federation,
        Script federationRedeemScript,
        List<BtcECKey> privateKeys,
        TransactionInput txIn,
        BtcTransaction tx,
        int numberOfSignatures) {

        Script scriptPubKey = federation.getP2SHScript();
        RedeemData redeemData = RedeemData.of(federation.getBtcPublicKeys(), federationRedeemScript);
        Script inputScript = scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);

        txIn.setScriptSig(inputScript);

        Sha256Hash sighash = tx.hashForSignature(
            0,
            federationRedeemScript,
            BtcTransaction.SigHash.ALL,
            false
        );

        for (int i = 0; i < numberOfSignatures; i++) {
            inputScript = signWithOneKey(federation, privateKeys, inputScript, sighash, i);
        }
        txIn.setScriptSig(inputScript);
    }

    private Script signWithOneKey(
        Federation federation,
        List<BtcECKey> privateKeys,
        Script inputScript,
        Sha256Hash sighash,
        int federatorIndex) {

        BtcECKey federatorPrivKey = privateKeys.get(federatorIndex);
        BtcECKey federatorPublicKey = federation.getBtcPublicKeys().get(federatorIndex);

        BtcECKey.ECDSASignature sig = federatorPrivKey.sign(sighash);
        TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);

        int sigIndex = inputScript.getSigInsertionIndex(sighash, federatorPublicKey);
        inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSig.encodeToBitcoin(), sigIndex, 1, 1);

        return inputScript;
    }

    private Federation getGenesisFederationForTest(BridgeConstants bridgeConstants, Context btcContext){
        Federation federation = bridgeConstants.getGenesisFederation();
        Wallet wallet = new BridgeBtcWallet(btcContext, Collections.singletonList(federation));
        Address federationAddress = federation.getAddress();
        wallet.addWatchedAddress(federationAddress, federation.getCreationTime().toEpochMilli());

        return federation;
    }
}
