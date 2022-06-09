/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.peg;

import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.*;
import co.rsk.bitcoinj.wallet.CoinSelector;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeMainNetConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.RskAllowUnconfirmedCoinSelector;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.peg.utils.PegUtils;
import co.rsk.peg.utils.ScriptBuilderWrapper;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BridgeUtilsTest {

    private static final String TO_ADDRESS = "0000000000000000000000000000000000000006";
    private static final BigInteger AMOUNT = new BigInteger("1");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";
    private static final byte[] MISSING_SIGNATURE = new byte[0];

    private Constants constants;
    private ActivationConfig activationConfig;
    private ActivationConfig.ForBlock activations;
    private BridgeConstants bridgeConstantsRegtest;
    private BridgeConstants bridgeConstantsMainnet;
    private NetworkParameters networkParameters;
    private PegUtils pegUtils;

    @Before
    public void setupConfig() {
        pegUtils = PegUtils.getInstance();
        constants = Constants.regtest();
        activationConfig = spy(ActivationConfigsForTest.all());
        activations = mock(ActivationConfig.ForBlock.class);
        bridgeConstantsRegtest = BridgeRegTestConstants.getInstance();
        bridgeConstantsMainnet = BridgeMainNetConstants.getInstance();
        networkParameters = bridgeConstantsRegtest.getBtcParams();
    }

    @Test
    public void testIsValidPegInTx() {
        // Peg-in is for the genesis federation ATM
        Context btcContext = new Context(networkParameters);

        Federation federation = bridgeConstantsRegtest.getGenesisFederation();
        Wallet wallet = new BridgeBtcWallet(btcContext, Collections.singletonList(federation));
        Address federationAddress = federation.getAddress();
        wallet.addWatchedAddress(federationAddress, federation.getCreationTime().toEpochMilli());
        when(activations.isActive(any(ConsensusRule.class))).thenReturn(false);

        // Tx sending less than the minimum allowed, not a peg-in tx
        Coin minimumLockValue = bridgeConstantsRegtest.getLegacyMinimumPeginTxValueInSatoshis();
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(minimumLockValue.subtract(Coin.CENT), federationAddress);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(tx, federation, btcContext, bridgeConstantsRegtest, activations));

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
        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(tx2, federation, btcContext, bridgeConstantsRegtest, activations));

        // Tx sending 1 btc to the federation, is a peg-in tx
        BtcTransaction tx3 = new BtcTransaction(networkParameters);
        tx3.addOutput(Coin.COIN, federationAddress);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(tx3, federation, btcContext, bridgeConstantsRegtest, activations));

        // Tx sending 50 btc to the federation, is a peg-in tx
        BtcTransaction tx4 = new BtcTransaction(networkParameters);
        tx4.addOutput(Coin.FIFTY_COINS, federationAddress);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(tx4, federation, btcContext, bridgeConstantsRegtest, activations));
    }

    @Test
    public void testIsValidPegInTx_less_than_minimum_not_pegin_after_iris() {
        // Tx sending less than the minimum allowed, not a peg-in tx
        Context btcContext = new Context(networkParameters);
        Federation federation = this.getGenesisFederationForTest(bridgeConstantsRegtest, btcContext);

        when(activations.isActive(ConsensusRule.RSKIP219)).thenReturn(true);
        Coin minimumPegInValueAfterIris = bridgeConstantsRegtest.getMinimumPeginTxValueInSatoshis();

        // Tx sending less than the minimum allowed, not a peg-in tx
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(minimumPegInValueAfterIris.subtract(Coin.CENT), federation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));

        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(tx, federation, btcContext, bridgeConstantsRegtest, activations));
    }

    @Test
    public void testIsValidPegInTx_spending_from_federation_is_pegout_after_iris() {
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

        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(tx, federation, btcContext, bridgeConstantsRegtest, activations));
    }

    @Test
    public void testIsValidPegInTx_sending_50_btc_after_iris() {
        // Tx sending 50 btc to the federation, is a peg-in tx
        Context btcContext = new Context(networkParameters);
        Federation federation = this.getGenesisFederationForTest(bridgeConstantsRegtest, btcContext);

        when(activations.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.FIFTY_COINS, federation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));

        assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(tx, federation, btcContext, bridgeConstantsRegtest, activations));
    }

    @Test
    public void testIsValidPegInTx_value_between_old_and_new_before_iris() {
        // Tx sending btc between old and new value, it is not a peg-in before iris
        Context btcContext = new Context(networkParameters);
        Federation federation = this.getGenesisFederationForTest(bridgeConstantsRegtest, btcContext);

        when(activations.isActive(ConsensusRule.RSKIP219)).thenReturn(false);

        BtcTransaction tx = new BtcTransaction(networkParameters);

        // Get a value in between pre and post iris minimum
        Coin minimumPegInValueBeforeIris = bridgeConstantsRegtest.getLegacyMinimumPeginTxValueInSatoshis();
        Coin minimumPegInValueAfterIris = bridgeConstantsRegtest.getMinimumPeginTxValueInSatoshis();
        Coin valueLock = minimumPegInValueAfterIris.plus((minimumPegInValueBeforeIris.subtract(minimumPegInValueAfterIris)).div(2));
        assertTrue(valueLock.isLessThan(minimumPegInValueBeforeIris));
        assertTrue(valueLock.isGreaterThan(minimumPegInValueAfterIris));

        tx.addOutput(valueLock, federation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));

        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(tx, federation, btcContext, bridgeConstantsRegtest, activations));
    }

    @Test
    public void testIsValidPegInTx_value_between_old_and_new_after_iris() {
        // Tx sending btc between old and new value, it is a peg-in after iris
        Context btcContext = new Context(networkParameters);
        Federation federation = this.getGenesisFederationForTest(bridgeConstantsRegtest, btcContext);

        when(activations.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        BtcTransaction tx = new BtcTransaction(networkParameters);

        // Get a value in between pre and post iris minimum
        Coin minimumPegInValueBeforeIris = bridgeConstantsRegtest.getLegacyMinimumPeginTxValueInSatoshis();
        Coin minimumPegInValueAfterIris = bridgeConstantsRegtest.getMinimumPeginTxValueInSatoshis();
        Coin valueLock = minimumPegInValueAfterIris.plus((minimumPegInValueBeforeIris.subtract(minimumPegInValueAfterIris)).div(2));
        assertTrue(valueLock.isGreaterThan(minimumPegInValueAfterIris));
        assertTrue(valueLock.isLessThan(minimumPegInValueBeforeIris));

        tx.addOutput(valueLock, federation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));

        assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(tx, federation, btcContext, bridgeConstantsRegtest, activations));
    }

    @Test
    public void testIsValidPegInTxForTwoFederations() {
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(any(ConsensusRule.class))).thenReturn(false);

        List<BtcECKey> federation1Keys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        List<BtcECKey> federation2Keys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fb01")),
                BtcECKey.fromPrivate(Hex.decode("fb02")),
                BtcECKey.fromPrivate(Hex.decode("fb03"))
        );
        federation2Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation2 = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(federation2Keys),
            Instant.ofEpochMilli(2000L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        Address address1 = federation1.getAddress();
        Address address2 = federation2.getAddress();

        List<Federation> federations = Arrays.asList(federation1, federation2);

        // Tx sending less than 1 btc to the first federation, not a peg-in tx
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.CENT, address1);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(
            tx,
            federations,
            null,
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));

        // Tx sending less than 1 btc to the second federation, not a peg-in tx
        tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.CENT, address2);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(
            tx,
            federations,
            null,
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));

        // Tx sending less than 1 btc to both federations, not a peg-in tx
        tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.CENT, address1);
        tx.addOutput(Coin.CENT, address2);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(
            tx,
            federations,
            null,
            btcContext,
            bridgeConstantsRegtest,
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
        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(
            tx2,
            federations,
            null,
            btcContext,
            bridgeConstantsRegtest,
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
        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(
            tx2,
            federations,
            null,
            btcContext,
            bridgeConstantsRegtest,
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
        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(
            tx2,
            federations,
            null,
            btcContext,
            bridgeConstantsRegtest,
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
        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(
            tx2,
            federations,
            null,
            btcContext,
            bridgeConstantsRegtest,
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
        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(
            tx2,
            federations,
            null,
            btcContext,
            bridgeConstantsRegtest,
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
        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(
            tx2,
            Collections.singletonList(federation2),
            federation1.getP2SHScript(),
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));

        // Tx sending 1 btc to the first federation, is a peg-in tx
        BtcTransaction tx3 = new BtcTransaction(networkParameters);
        tx3.addOutput(Coin.COIN, address1);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(
            tx3,
            federations,
            null,
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));

        // Tx sending 1 btc to the second federation, is a peg-in tx
        tx3 = new BtcTransaction(networkParameters);
        tx3.addOutput(Coin.COIN, address2);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(
            tx3,
            federations,
            null,
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));

        // Tx sending 1 btc to the both federations, is a peg-in tx
        tx3 = new BtcTransaction(networkParameters);
        tx3.addOutput(Coin.COIN, address1);
        tx3.addOutput(Coin.COIN, address2);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(
            tx3,
            federations,
            null,
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));

        // Tx sending 50 btc to the first federation, is a peg-in tx
        BtcTransaction tx4 = new BtcTransaction(networkParameters);
        tx4.addOutput(Coin.FIFTY_COINS, address1);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(
            tx4,
            federations,
            null,
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));

        // Tx sending 50 btc to the second federation, is a peg-in tx
        tx4 = new BtcTransaction(networkParameters);
        tx4.addOutput(Coin.FIFTY_COINS, address2);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(
            tx4,
            federations,
            null,
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));

        // Tx sending 50 btc to the both federations, is a peg-in tx
        tx4 = new BtcTransaction(networkParameters);
        tx4.addOutput(Coin.FIFTY_COINS, address1);
        tx4.addOutput(Coin.FIFTY_COINS, address2);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(
            tx4,
            federations,
            null,
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            activeFederation.getRedeemScript(),
            Sha256Hash.of(PegTestUtils.createHash(1).getBytes())
        );

        // Create a tx from the fast bridge fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, fastBridgeRedeemScript);

        Assert.assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(tx, activeFederation, btcContext,
            bridgeConstantsRegtest, activations));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            activeFederation.getRedeemScript(),
            Sha256Hash.of(PegTestUtils.createHash(1).getBytes())
        );

        // Create a tx from the fast bridge fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, fastBridgeRedeemScript);

        Assert.assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(tx, activeFederation, btcContext,
            bridgeConstantsRegtest, activations));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeErpFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        List<BtcECKey> erpFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        erpFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation erpFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(erpFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        Script fastBridgeErpRedeemScript = FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
            activeFederation.getRedeemScript(),
            erpFederation.getRedeemScript(),
            500L,
            Sha256Hash.of(PegTestUtils.createHash(1).getBytes())
        );

        // Create a tx from the fast bridge erp fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, fastBridgeErpRedeemScript);

        Assert.assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(tx, activeFederation, btcContext,
            bridgeConstantsRegtest, activations));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeErpFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        List<BtcECKey> erpFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        erpFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation erpFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(erpFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        Script fastBridgeErpRedeemScript = FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
            activeFederation.getRedeemScript(),
            erpFederation.getRedeemScript(),
            500L,
            Sha256Hash.of(PegTestUtils.createHash(1).getBytes())
        );

        // Create a tx from the fast bridge erp fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, fastBridgeErpRedeemScript);

        Assert.assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(tx, activeFederation, btcContext,
            bridgeConstantsRegtest, activations));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromErpFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        List<BtcECKey> erpFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        erpFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation erpFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(erpFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
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

        Assert.assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(tx, activeFederation, btcContext,
            bridgeConstantsRegtest, activations));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromErpFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        List<BtcECKey> erpFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        erpFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation erpFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(erpFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
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

        Assert.assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(tx, activeFederation, btcContext,
            bridgeConstantsRegtest, activations));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeRetiredFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        List<BtcECKey> retiredFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        retiredFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation retiredFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
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

        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            retiredFederation.getRedeemScript(),
            PegTestUtils.createHash(2)
        );
        signWithNecessaryKeys(retiredFederation, fastBridgeRedeemScript, retiredFederationKeys, txInput, tx);

        assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeRetiredFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        List<BtcECKey> retiredFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        retiredFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation retiredFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
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

        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            retiredFederation.getRedeemScript(),
            PegTestUtils.createHash(2)
        );
        signWithNecessaryKeys(retiredFederation, fastBridgeRedeemScript, retiredFederationKeys, txInput, tx);

        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeErpRetiredFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        List<BtcECKey> retiredFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        retiredFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation retiredFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            erpFederationPublicKeys,
            500L,
            activations,
            pegUtils.getScriptBuilderWrapper()
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

        Script fastBridgeErpRedeemScript = FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
            erpFederation.getRedeemScript(),
            PegTestUtils.createHash(2)
        );
        signWithNecessaryKeys(erpFederation, fastBridgeErpRedeemScript, retiredFederationKeys, txInput, tx);

        assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeErpRetiredFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        List<BtcECKey> retiredFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        retiredFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation retiredFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            erpFederationPublicKeys,
            500L,
            activations,
            pegUtils.getScriptBuilderWrapper()
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

        Script fastBridgeErpRedeemScript = FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
            erpFederation.getRedeemScript(),
            PegTestUtils.createHash(2)
        );
        signWithNecessaryKeys(erpFederation, fastBridgeErpRedeemScript, retiredFederationKeys, txInput, tx);

        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromErpRetiredFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();

        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        List<BtcECKey> retiredFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        retiredFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation retiredFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            erpFederationPublicKeys,
            500L,
            activations,
            pegUtils.getScriptBuilderWrapper()
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

        assertTrue(pegUtils.getBridgeUtils().isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromErpRetiredFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstantsRegtest.getGenesisFederation();
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        List<BtcECKey> retiredFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        retiredFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation retiredFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            erpFederationPublicKeys,
            500L,
            activations,
            pegUtils.getScriptBuilderWrapper()
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

        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));
    }

    @Test
    public void testTxIsProcessableInLegacyVersion() {
        // Before hard fork
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        assertTrue(pegUtils.getBridgeUtils().txIsProcessableInLegacyVersion(TxSenderAddressType.P2PKH, activations));
        assertFalse(pegUtils.getBridgeUtils().txIsProcessableInLegacyVersion(TxSenderAddressType.P2SHP2WPKH, activations));
        assertFalse(pegUtils.getBridgeUtils().txIsProcessableInLegacyVersion(TxSenderAddressType.P2SHMULTISIG, activations));
        assertFalse(pegUtils.getBridgeUtils().txIsProcessableInLegacyVersion(TxSenderAddressType.P2SHP2WSH, activations));
        assertFalse(pegUtils.getBridgeUtils().txIsProcessableInLegacyVersion(TxSenderAddressType.UNKNOWN, activations));

        // After hard fork
        when(activations.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        assertTrue(pegUtils.getBridgeUtils().txIsProcessableInLegacyVersion(TxSenderAddressType.P2PKH, activations));
        assertTrue(pegUtils.getBridgeUtils().txIsProcessableInLegacyVersion(TxSenderAddressType.P2SHP2WPKH, activations));
        assertTrue(pegUtils.getBridgeUtils().txIsProcessableInLegacyVersion(TxSenderAddressType.P2SHMULTISIG, activations));
        assertTrue(pegUtils.getBridgeUtils().txIsProcessableInLegacyVersion(TxSenderAddressType.P2SHP2WSH, activations));
        assertFalse(pegUtils.getBridgeUtils().txIsProcessableInLegacyVersion(TxSenderAddressType.UNKNOWN, activations));
    }

    @Test
    public void testIsMigrationTx() {
        Context btcContext = new Context(networkParameters);

        List<BtcECKey> activeFederationKeys = Stream.of(
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        Federation activeFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
            Instant.ofEpochMilli(2000L),
            2L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        List<BtcECKey> retiringFederationKeys = Stream.of(
                BtcECKey.fromPrivate(Hex.decode("fb01")),
                BtcECKey.fromPrivate(Hex.decode("fb02")),
                BtcECKey.fromPrivate(Hex.decode("fb03"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        Federation retiringFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiringFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        List<BtcECKey> retiredFederationKeys = Stream.of(
                BtcECKey.fromPrivate(Hex.decode("fc01")),
                BtcECKey.fromPrivate(Hex.decode("fc02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        Federation retiredFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
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
        assertTrue(pegUtils.getBridgeUtils().isMigrationTx(
            migrationTx,
            activeFederation,
            retiringFederation,
            null,
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));

        BtcTransaction toActiveFederationTx = new BtcTransaction(networkParameters);
        toActiveFederationTx.addOutput(Coin.COIN, activeFederationAddress);
        toActiveFederationTx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(pegUtils.getBridgeUtils().isMigrationTx(
            toActiveFederationTx,
            activeFederation,
            retiringFederation,
            null,
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));

        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress();
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
        assertFalse(pegUtils.getBridgeUtils().isMigrationTx(
            fromRetiringFederationTx,
            activeFederation,
            retiringFederation,
            null,
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));

        assertFalse(pegUtils.getBridgeUtils().isMigrationTx(
            migrationTx,
            activeFederation,
            null,
            null,
            btcContext,
            bridgeConstantsRegtest,
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
        assertTrue(pegUtils.getBridgeUtils().isMigrationTx(
            retiredMigrationTx,
            activeFederation,
            null,
            p2SHScript,
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));

        assertTrue(pegUtils.getBridgeUtils().isMigrationTx(
            retiredMigrationTx,
            activeFederation,
            retiringFederation,
            p2SHScript,
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));
        assertFalse(pegUtils.getBridgeUtils().isMigrationTx(
            toActiveFederationTx,
            activeFederation,
            null,
            p2SHScript,
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));
        assertFalse(pegUtils.getBridgeUtils().isMigrationTx(
            toActiveFederationTx,
            activeFederation,
            retiringFederation,
            p2SHScript,
            btcContext,
            bridgeConstantsRegtest,
            activations
        ));
    }

    @Test
    public void testIsPegOutTx() {
        Federation federation = bridgeConstantsRegtest.getGenesisFederation();
        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        Federation federation2 = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
            Instant.ofEpochMilli(2000L),
            2L,
            bridgeConstantsRegtest.getBtcParams(),
            pegUtils.getScriptBuilderWrapper()
        );
        List<BtcECKey> federationPrivateKeys = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS;
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress();

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

        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(federation), activations));
        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Arrays.asList(federation, federation2), activations));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(federation2), activations));

        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, federation.getP2SHScript()));
        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, federation.getP2SHScript(), federation2.getP2SHScript()));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, federation2.getP2SHScript()));
    }

    @Test
    public void testIsPegOutTx_fromFastBridgeFederation() {
        List<BtcECKey> fastBridgeFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        fastBridgeFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation fastBridgeFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(fastBridgeFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        Federation standardFederation = bridgeConstantsRegtest.getGenesisFederation();

        // Create a tx from the fast bridge fed to a random address
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress();
        BtcTransaction pegOutTx1 = new BtcTransaction(networkParameters);
        pegOutTx1.addOutput(Coin.COIN, randomAddress);
        TransactionInput pegOutInput1 = new TransactionInput(
            networkParameters,
            pegOutTx1,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        pegOutTx1.addInput(pegOutInput1);

        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            fastBridgeFederation.getRedeemScript(),
            PegTestUtils.createHash(2)
        );
        signWithNecessaryKeys(fastBridgeFederation, fastBridgeRedeemScript, fastBridgeFederationKeys, pegOutInput1, pegOutTx1);

        // Before RSKIP 201 activation
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(fastBridgeFederation), activations));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Arrays.asList(fastBridgeFederation, standardFederation), activations));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, fastBridgeFederation.getP2SHScript()));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, fastBridgeFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));

        // After RSKIP 201 activation
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(fastBridgeFederation), activations));
        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Arrays.asList(fastBridgeFederation, standardFederation), activations));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, fastBridgeFederation.getP2SHScript()));
        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, fastBridgeFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));
    }

    @Test
    public void testIsPegOutTx_fromErpFederation() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        List<BtcECKey> defaultFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        defaultFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation defaultFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(defaultFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04")),
            BtcECKey.fromPrivate(Hex.decode("fa05"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(defaultFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            erpFederationPublicKeys,
            500L,
            activations,
            pegUtils.getScriptBuilderWrapper()
        );

        Federation standardFederation = bridgeConstantsRegtest.getGenesisFederation();

        // Create a tx from the erp fed to a random address
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress();
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

        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(defaultFederation), activations));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Arrays.asList(defaultFederation, standardFederation), activations));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript()));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));

        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(erpFederation), activations));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, erpFederation.getStandardP2SHScript()));

        // After RSKIP 201 activation
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(defaultFederation), activations));
        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Arrays.asList(defaultFederation, standardFederation), activations));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript()));
        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));

        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(erpFederation), activations));
        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, erpFederation.getStandardP2SHScript()));
    }

    @Test
    public void testIsPegOutTx_fromFastBridgeErpFederation() {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        List<BtcECKey> defaultFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        defaultFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation defaultFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(defaultFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04")),
            BtcECKey.fromPrivate(Hex.decode("fa05"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(defaultFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            erpFederationPublicKeys,
            500L,
            activations,
            pegUtils.getScriptBuilderWrapper()
        );

        Federation standardFederation = bridgeConstantsRegtest.getGenesisFederation();

        // Create a tx from the fast bridge erp fed to a random address
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress();
        BtcTransaction pegOutTx1 = new BtcTransaction(networkParameters);
        pegOutTx1.addOutput(Coin.COIN, randomAddress);
        TransactionInput pegOutInput1 = new TransactionInput(
            networkParameters,
            pegOutTx1,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        pegOutTx1.addInput(pegOutInput1);

        Script fastBridgeErpRedeemScript = FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
            erpFederation.getRedeemScript(),
            PegTestUtils.createHash(2)
        );
        signWithNecessaryKeys(erpFederation, fastBridgeErpRedeemScript, defaultFederationKeys, pegOutInput1, pegOutTx1);

        // Before RSKIP 201 activation
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(defaultFederation), activations));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Arrays.asList(defaultFederation, standardFederation), activations));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript()));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));

        // After RSKIP 201 activation
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(defaultFederation), activations));
        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Arrays.asList(defaultFederation, standardFederation), activations));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript()));
        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));
    }

    @Test
    public void testIsPegOutTx_noRedeemScript() {
        Federation federation = bridgeConstantsRegtest.getGenesisFederation();
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress();

        BtcTransaction pegOutTx1 = new BtcTransaction(networkParameters);
        pegOutTx1.addOutput(Coin.COIN, randomAddress);
        TransactionInput pegOutInput1 = new TransactionInput(
            networkParameters,
            pegOutTx1,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        pegOutTx1.addInput(pegOutInput1);

        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(federation), activations));
    }

    @Test
    public void testIsPegOutTx_invalidRedeemScript() {
        Federation federation = bridgeConstantsRegtest.getGenesisFederation();
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress();
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

        assertFalse(pegUtils.getBridgeUtils().isPegOutTx(pegOutTx1, Collections.singletonList(federation), activations));
    }

    @Test
    public void testChangeBetweenFederations() {
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress();
        Context btcContext = new Context(networkParameters);

        List<BtcECKey> federation1Keys = Stream.of("fa01", "fa02")
            .map(Hex::decode)
            .map(BtcECKey::fromPrivate)
            .sorted(BtcECKey.PUBKEY_COMPARATOR)
            .collect(Collectors.toList());
        Federation federation1 = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
            Instant.ofEpochMilli(1000L), 0L, networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        List<BtcECKey> federation2Keys = Stream.of("fb01", "fb02", "fb03")
            .map(Hex::decode)
            .map(BtcECKey::fromPrivate)
            .sorted(BtcECKey.PUBKEY_COMPARATOR)
            .collect(Collectors.toList());
        Federation federation2 = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(federation2Keys),
            Instant.ofEpochMilli(2000L), 0L, networkParameters,
            pegUtils.getScriptBuilderWrapper()
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
        assertFalse(pegUtils.getBridgeUtils().isValidPegInTx(
            pegOutWithChange,
            federations,
            null,
            btcContext,
            bridgeConstantsRegtest,
            mock(ActivationConfig.ForBlock.class)
        ));
        assertTrue(pegUtils.getBridgeUtils().isPegOutTx(pegOutWithChange, federations, activations));
    }

    @Test
    public void getAddressFromEthTransaction() {
        org.ethereum.core.Transaction tx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(GAS_LIMIT)
                .destination(Hex.decode(TO_ADDRESS))
                .data(Hex.decode(DATA))
                .chainId(constants.getChainId())
                .value(AMOUNT)
                .build();
        byte[] privKey = generatePrivKey();
        tx.sign(privKey);

        Address expectedAddress = BtcECKey.fromPrivate(privKey).toAddress(RegTestParams.get());
        Address result = pegUtils.getBridgeUtils().recoverBtcAddressFromEthTransaction(tx, RegTestParams.get());

        assertEquals(expectedAddress, result);
    }

    @Test(expected = Exception.class)
    public void getAddressFromEthNotSignTransaction() {
        org.ethereum.core.Transaction tx = Transaction
                .builder()
                .nonce(NONCE)
                .gasPrice(GAS_PRICE)
                .gasLimit(BigIntegers.asUnsignedByteArray(GAS_LIMIT))
                .destination(Hex.decode(TO_ADDRESS))
                .data(Hex.decode(DATA))
                .chainId(constants.getChainId())
                .value(AMOUNT)
                .build();
        pegUtils.getBridgeUtils().recoverBtcAddressFromEthTransaction(tx, RegTestParams.get());
    }

    @Test
    public void hasEnoughSignatures_two_signatures() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, sign2), 1);
        Assert.assertTrue(pegUtils.getBridgeUtils().hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void hasEnoughSignatures_one_signature() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, MISSING_SIGNATURE), 1);
        Assert.assertFalse(pegUtils.getBridgeUtils().hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void hasEnoughSignatures_no_signatures() {
        BtcTransaction btcTx = createPegOutTx(Collections.emptyList(), 1);
        Assert.assertFalse(pegUtils.getBridgeUtils().hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void hasEnoughSignatures_several_inputs_all_signed() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, sign2), 3);
        Assert.assertTrue(pegUtils.getBridgeUtils().hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void hasEnoughSignatures_several_inputs_all_signed_erp_fed() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        Federation erpFederation = createErpFederation();
        BtcTransaction btcTx = createPegOutTx(
            Arrays.asList(sign1, sign2),
            3,
            erpFederation,
            false
        );

        Assert.assertTrue(pegUtils.getBridgeUtils().hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void hasEnoughSignatures_several_inputs_all_signed_fast_bridge() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTxForFastBridge(
            Arrays.asList(sign1, sign2),
            3,
            null
        );

        Assert.assertTrue(pegUtils.getBridgeUtils().hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void hasEnoughSignatures_several_inputs_all_signed_erp_fast_bridge() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        Federation erpFederation = createErpFederation();
        BtcTransaction btcTx = createPegOutTxForFastBridge(
            Arrays.asList(sign1, sign2),
            3,
            erpFederation
        );

        Assert.assertTrue(pegUtils.getBridgeUtils().hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void hasEnoughSignatures_several_inputs_one_missing_signature() {
        // Create 1 signature
        byte[] sign1 = new byte[]{0x79};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, MISSING_SIGNATURE), 3);
        Assert.assertFalse(pegUtils.getBridgeUtils().hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_two_signatures() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, sign2), 1);
        Assert.assertEquals(0, pegUtils.getBridgeUtils().countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_one_signature() {
        // Add 1 signature
        byte[] sign1 = new byte[]{0x79};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, MISSING_SIGNATURE), 1);
        Assert.assertEquals(1, pegUtils.getBridgeUtils().countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_no_signatures() {
        // As no signature was added, missing signatures is 2
        BtcTransaction btcTx = createPegOutTx(Collections.emptyList(), 1);
        Assert.assertEquals(2, pegUtils.getBridgeUtils().countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_several_inputs_all_signed() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, sign2), 3);
        Assert.assertEquals(0, pegUtils.getBridgeUtils().countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_several_inputs_all_signed_erp_fed() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        Federation erpFederation = createErpFederation();
        BtcTransaction btcTx = createPegOutTx(
            Arrays.asList(sign1, sign2),
            3,
            erpFederation,
            false
        );

        Assert.assertEquals(0, pegUtils.getBridgeUtils().countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_several_inputs_all_signed_fast_bridge() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTxForFastBridge(
            Arrays.asList(sign1, sign2),
            3,
            null
        );

        Assert.assertEquals(0, pegUtils.getBridgeUtils().countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_several_inputs_all_signed_erp_fast_bridge() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        Federation erpFederation = createErpFederation();
        BtcTransaction btcTx = createPegOutTxForFastBridge(
            Arrays.asList(sign1, sign2),
            3,
            erpFederation
        );

        Assert.assertEquals(0, pegUtils.getBridgeUtils().countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_several_inputs_one_missing_signature() {
        // Create 1 signature
        byte[] sign1 = new byte[]{0x79};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, MISSING_SIGNATURE), 3);
        Assert.assertEquals(1, pegUtils.getBridgeUtils().countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void isFreeBridgeTxTrue() {
        activationConfig = ActivationConfigsForTest.bridgeUnitTest();
        isFreeBridgeTx(true, PrecompiledContracts.BRIDGE_ADDR, BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes());
    }

    @Test
    public void isFreeBridgeTxOtherContract() {
        activationConfig = ActivationConfigsForTest.bridgeUnitTest();
        isFreeBridgeTx(false, PrecompiledContracts.IDENTITY_ADDR, BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes());
    }

    @Test
    public void isFreeBridgeTxFreeTxDisabled() {
        activationConfig = ActivationConfigsForTest.only(ConsensusRule.ARE_BRIDGE_TXS_PAID);
        isFreeBridgeTx(false, PrecompiledContracts.BRIDGE_ADDR, BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS.get(0).getPrivKeyBytes());
    }

    @Test
    public void isFreeBridgeTxNonFederatorKey() {
        activationConfig = ActivationConfigsForTest.bridgeUnitTest();
        isFreeBridgeTx(false, PrecompiledContracts.BRIDGE_ADDR, new BtcECKey().getPrivKeyBytes());
    }

    @Test
    public void getFederationNoSpendWallet() {
        test_getNoSpendWallet(false);
    }

    @Test
    public void getFederationNoSpendWallet_fastBridgeCompatible() {
        test_getNoSpendWallet(true);
    }

    @Test
    public void getFederationSpendWallet() throws UTXOProviderException {
        test_getSpendWallet(false);
    }

    @Test
    public void getFederationSpendWallet_fastBridgeCompatible() throws UTXOProviderException {
        test_getSpendWallet(true);
    }

    @Test
    public void testIsContractTx() {
        Assert.assertFalse(
                pegUtils.getBridgeUtils().isContractTx(
                        Transaction.builder().build()
                )
        );
        Assert.assertTrue(
            pegUtils.getBridgeUtils().isContractTx(new org.ethereum.vm.program.InternalTransaction(
                Keccak256.ZERO_HASH.getBytes(),
                0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            ))
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsContractTxInvalidTx() {
        new ImmutableTransaction(null);
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void getCoinFromBigInteger_bigger_than_long_value() throws BridgeIllegalArgumentException {
        pegUtils.getBridgeUtils().getCoinFromBigInteger(new BigInteger("9223372036854775808"));
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void getCoinFromBigInteger_null_value() throws BridgeIllegalArgumentException {
        pegUtils.getBridgeUtils().getCoinFromBigInteger(null);
    }

    @Test
    public void getCoinFromBigInteger() throws BridgeIllegalArgumentException {
        Assert.assertEquals(Coin.COIN, pegUtils.getBridgeUtils().getCoinFromBigInteger(BigInteger.valueOf(Coin.COIN.getValue())));
    }

    @Test(expected = Exception.class)
    public void validateHeightAndConfirmations_invalid_height() throws Exception {
        Assert.assertFalse(pegUtils.getBridgeUtils().validateHeightAndConfirmations(-1, 0, 0, null));
    }

    @Test
    public void validateHeightAndConfirmation_insufficient_confirmations() throws Exception {
        Assert.assertFalse(pegUtils.getBridgeUtils().validateHeightAndConfirmations(2, 5, 10, Sha256Hash.of(Hex.decode("ab"))));
    }

    @Test
    public void validateHeightAndConfirmation_enough_confirmations() throws Exception {
        Assert.assertTrue(pegUtils.getBridgeUtils().validateHeightAndConfirmations(
            2,
            5,
            3,
            Sha256Hash.of(Hex.decode("ab")))
        );
    }

    @Test(expected = Exception.class)
    public void calculateMerkleRoot_invalid_pmt() {
        pegUtils.getBridgeUtils().calculateMerkleRoot(networkParameters, Hex.decode("ab"), null);
    }

    @Test
    public void calculateMerkleRoot_hashes_not_in_pmt() {
        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(PegTestUtils.createHash(2));

        BtcTransaction tx = new BtcTransaction(networkParameters);
        PartialMerkleTree pmt = new PartialMerkleTree(networkParameters, bits, hashes, 1);

        Assert.assertNull(pegUtils.getBridgeUtils().calculateMerkleRoot(networkParameters, pmt.bitcoinSerialize(), tx.getHash()));
    }

    @Test
    public void calculateMerkleRoot_hashes_in_pmt() {
        BtcTransaction tx = new BtcTransaction(networkParameters);
        byte[] bits = new byte[1];
        bits[0] = 0x5;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(Sha256Hash.ZERO_HASH);
        hashes.add(tx.getHash());
        PartialMerkleTree pmt = new PartialMerkleTree(networkParameters, bits, hashes, 2);
        Sha256Hash merkleRoot = pegUtils.getBridgeUtils().calculateMerkleRoot(networkParameters, pmt.bitcoinSerialize(), tx.getHash());
        Assert.assertNotNull(merkleRoot);
    }

    @Test(expected = VerificationException.class)
    public void validateInputsCount_active_rskip() {
        pegUtils.getBridgeUtils().validateInputsCount(Hex.decode("00000000000100"), true);
    }

    @Test(expected = VerificationException.class)
    public void validateInputsCount_inactive_rskip() {
        BtcTransaction tx = new BtcTransaction(networkParameters);
        pegUtils.getBridgeUtils().validateInputsCount(tx.bitcoinSerialize(), false);
    }

    @Test
    public void isInputSignedByThisFederator_isSigned() {
        // Arrange
        BtcECKey federator1Key = new BtcECKey();
        BtcECKey federator2Key = new BtcECKey();
        Federation federation = new Federation(
                FederationMember.getFederationMembersFromKeys(Arrays.asList(federator1Key, federator2Key)),
                Instant.now(),
                0,
                networkParameters,
                pegUtils.getScriptBuilderWrapper()
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction tx = new BtcTransaction(networkParameters);
        TransactionInput txInput = new TransactionInput(
                networkParameters,
                tx,
                new byte[]{},
                new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );

        // Create script to be signed by federation members
        Script inputScript = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
        txInput.setScriptSig(inputScript);

        tx.addInput(txInput);

        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);

        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        BtcECKey.ECDSASignature sig = federator1Key.sign(sighash);

        TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
        byte[] txSigEncoded = txSig.encodeToBitcoin();

        int sigIndex = inputScript.getSigInsertionIndex(sighash, federator1Key);
        inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSigEncoded, sigIndex, 1, 1);
        txInput.setScriptSig(inputScript);

        // Act
        boolean isSigned = pegUtils.getBridgeUtils().isInputSignedByThisFederator(federator1Key, sighash, txInput);

        // Assert
        Assert.assertTrue(isSigned);
    }

    @Test
    public void isInputSignedByThisFederator_isSignedByAnotherFederator() {
        // Arrange
        BtcECKey federator1Key = new BtcECKey();
        BtcECKey federator2Key = new BtcECKey();
        Federation federation = new Federation(
                FederationMember.getFederationMembersFromKeys(Arrays.asList(federator1Key, federator2Key)),
                Instant.now(),
                0,
                networkParameters,
                pegUtils.getScriptBuilderWrapper()
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction tx = new BtcTransaction(networkParameters);
        TransactionInput txInput = new TransactionInput(
                networkParameters,
                tx,
                new byte[]{},
                new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );

        // Create script to be signed by federation members
        Script inputScript = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
        txInput.setScriptSig(inputScript);

        tx.addInput(txInput);

        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);

        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        BtcECKey.ECDSASignature sig = federator1Key.sign(sighash);

        TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
        byte[] txSigEncoded = txSig.encodeToBitcoin();

        int sigIndex = inputScript.getSigInsertionIndex(sighash, federator1Key);
        inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSigEncoded, sigIndex, 1, 1);
        txInput.setScriptSig(inputScript);

        // Act
        boolean isSigned = pegUtils.getBridgeUtils().isInputSignedByThisFederator(federator2Key, sighash, txInput);

        // Assert
        Assert.assertFalse(isSigned);
    }

    @Test
    public void isInputSignedByThisFederator_notSigned() {
        // Arrange
        BtcECKey federator1Key = new BtcECKey();
        BtcECKey federator2Key = new BtcECKey();
        Federation federation = new Federation(
                FederationMember.getFederationMembersFromKeys(Arrays.asList(federator1Key, federator2Key)),
                Instant.now(),
                0,
                networkParameters,
                pegUtils.getScriptBuilderWrapper()
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction tx = new BtcTransaction(networkParameters);
        TransactionInput txInput = new TransactionInput(
                networkParameters,
                tx,
                new byte[]{},
                new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );

        // Create script to be signed by federation members
        Script inputScript = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
        txInput.setScriptSig(inputScript);

        tx.addInput(txInput);

        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);

        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);

        // Act
        boolean isSigned = pegUtils.getBridgeUtils().isInputSignedByThisFederator(federator1Key, sighash, txInput);

        // Assert
        Assert.assertFalse(isSigned);
    }

    @Test
    public void serializeBtcAddressWithVersion_p2pkh_testnet_before_rskip284() {
        Address address = Address.fromBase58(networkParameters, "mmWFbkYYKCT9jvCUzJD9XoVjSkfachVpMs");
        byte[] serializedVersion = Hex.decode("6f"); // Testnet pubkey hash
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(false, address, serializedVersion, serializedAddress);
    }

    @Test
    public void serializeBtcAddressWithVersion_p2sh_testnet_before_rskip284() {
        Address address = Address.fromBase58(networkParameters, "2MyEXHyt2fXqdFm3r4xXEkTdbwdZm7qFiDP");
        byte[] serializedVersion = Hex.decode("00c4"); // Testnet script hash, with leading zeroes
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(false, address, serializedVersion, serializedAddress);
    }

    @Test
    public void serializeBtcAddressWithVersion_p2pkh_mainnet_before_rskip284() {
        Address address = Address.fromBase58(bridgeConstantsMainnet.getBtcParams(), "16zJJhTZWB1txoisGjEmhtHQam4sikpTd2");
        byte[] serializedVersion = Hex.decode("00"); // Mainnet pubkey hash
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(false, address, serializedVersion, serializedAddress);
    }

    @Test
    public void serializeBtcAddressWithVersion_p2sh_mainnet_before_rskip284() {
        Address address = Address.fromBase58(bridgeConstantsMainnet.getBtcParams(), "37gKEEx145LH3yRJPpuN8WeLjHMbJJo8vn");
        byte[] serializedVersion = Hex.decode("05"); // Mainnet script hash
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(false, address, serializedVersion, serializedAddress);
    }

    @Test
    public void serializeBtcAddressWithVersion_p2pkh_testnet_after_rskip284() {
        Address address = Address.fromBase58(networkParameters, "mmWFbkYYKCT9jvCUzJD9XoVjSkfachVpMs");
        byte[] serializedVersion = Hex.decode("6f"); // Testnet pubkey hash
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(true, address, serializedVersion, serializedAddress);
    }

    @Test
    public void serializeBtcAddressWithVersion_p2sh_testnet_after_rskip284() {
        Address address = Address.fromBase58(networkParameters, "2MyEXHyt2fXqdFm3r4xXEkTdbwdZm7qFiDP");
        byte[] serializedVersion = Hex.decode("c4"); // Testnet script hash, no leading zeroes after HF activation
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(true, address, serializedVersion, serializedAddress);
    }

    @Test
    public void serializeBtcAddressWithVersion_p2pkh_mainnet_after_rskip284() {
        Address address = Address.fromBase58(bridgeConstantsMainnet.getBtcParams(), "16zJJhTZWB1txoisGjEmhtHQam4sikpTd2");
        byte[] serializedVersion = Hex.decode("00"); // Mainnet pubkey hash
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(true, address, serializedVersion, serializedAddress);
    }

    @Test
    public void serializeBtcAddressWithVersion_p2sh_mainnet_after_rskip284() {
        Address address = Address.fromBase58(bridgeConstantsMainnet.getBtcParams(), "37gKEEx145LH3yRJPpuN8WeLjHMbJJo8vn");
        byte[] serializedVersion = Hex.decode("05"); // Mainnet script hash
        byte[] serializedAddress = Hex.decode("41aec8ca3fcf17e62077e9f35961385360d6a570");

        test_serializeBtcAddressWithVersion(true, address, serializedVersion, serializedAddress);
    }

    @Test(expected = IllegalArgumentException.class)
    public void deserializeBtcAddressWithVersion_before_rskip284_p2sh_testnet() throws BridgeIllegalArgumentException {
        String addressVersionHex = "c4"; // Testnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        // Should use the legacy method and fail for using testnet script hash
        test_deserializeBtcAddressWithVersion(
            false,
            NetworkParameters.ID_TESTNET,
            addressBytes,
            0,
            new byte[]{},
            ""
        );
    }

    @Test
    public void deserializeBtcAddressWithVersion_before_rskip284_p2pkh_testnet() throws BridgeIllegalArgumentException {
        int addressVersion = 111;
        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));
        String addressBase58 = "mmWFbkYYKCT9jvCUzJD9XoVjSkfachVpMs";

        // Should use the legacy method and deserialize correctly if using testnet p2pkh
        test_deserializeBtcAddressWithVersion(
            false,
            NetworkParameters.ID_TESTNET,
            addressBytes,
            addressVersion,
            Hex.decode(addressHash160Hex),
            addressBase58
        );
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void deserializeBtcAddressWithVersion_null_bytes() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        pegUtils.getBridgeUtils().deserializeBtcAddressWithVersion(
            bridgeConstantsRegtest.getBtcParams(),
            activations,
            null
        );
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void deserializeBtcAddressWithVersion_empty_bytes() throws BridgeIllegalArgumentException {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(true);

        pegUtils.getBridgeUtils().deserializeBtcAddressWithVersion(
            bridgeConstantsRegtest.getBtcParams(),
            activations,
            new byte[]{}
        );
    }

    @Test
    public void deserializeBtcAddressWithVersion_p2pkh_testnet() throws BridgeIllegalArgumentException {
        int addressVersion = 111;
        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));
        String addressBase58 = "mmWFbkYYKCT9jvCUzJD9XoVjSkfachVpMs";

        test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_TESTNET,
            addressBytes,
            addressVersion,
            Hex.decode(addressHash160Hex),
            addressBase58
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void deserializeBtcAddressWithVersion_p2pkh_testnet_wrong_network() throws BridgeIllegalArgumentException {
        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_MAINNET,
            addressBytes,
            0,
            new byte[]{},
            ""
        );
    }

    @Test
    public void deserializeBtcAddressWithVersion_p2sh_testnet() throws BridgeIllegalArgumentException {
        int addressVersion = 196;
        String addressVersionHex = "c4"; // Testnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));
        String addressBase58 = "2MyEXHyt2fXqdFm3r4xXEkTdbwdZm7qFiDP";

        test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_TESTNET,
            addressBytes,
            addressVersion,
            Hex.decode(addressHash160Hex),
            addressBase58
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void deserializeBtcAddressWithVersion_p2sh_testnet_wrong_network() throws BridgeIllegalArgumentException {
        String addressVersionHex = "c4"; // Testnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_MAINNET,
            addressBytes,
            0,
            new byte[]{},
            ""
        );
    }

    @Test
    public void deserializeBtcAddressWithVersion_p2pkh_mainnet() throws BridgeIllegalArgumentException {
        int addressVersion = 0;
        String addressVersionHex = "00"; // Mainnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));
        String addressBase58 = "16zJJhTZWB1txoisGjEmhtHQam4sikpTd2";

        test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_MAINNET,
            addressBytes,
            addressVersion,
            Hex.decode(addressHash160Hex),
            addressBase58
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void deserializeBtcAddressWithVersion_p2pkh_mainnet_wrong_network() throws BridgeIllegalArgumentException {
        String addressVersionHex = "00"; // Mainnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_TESTNET,
            addressBytes,
            0,
            new byte[]{},
            ""
        );
    }

    @Test
    public void deserializeBtcAddressWithVersion_p2sh_mainnet() throws BridgeIllegalArgumentException {
        int addressVersion = 5;
        String addressVersionHex = "05"; // Mainnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));
        String addressBase58 = "37gKEEx145LH3yRJPpuN8WeLjHMbJJo8vn";

        test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_MAINNET,
            addressBytes,
            addressVersion,
            Hex.decode(addressHash160Hex),
            addressBase58
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void deserializeBtcAddressWithVersion_p2sh_mainnet_wrong_network() throws BridgeIllegalArgumentException {
        String addressVersionHex = "05"; // Mainnet script hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_TESTNET,
            addressBytes,
            0,
            new byte[]{},
            ""
        );
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void deserializeBtcAddressWithVersion_with_extra_bytes() throws BridgeIllegalArgumentException {
        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41aec8ca3fcf17e62077e9f35961385360d6a570";
        String extraData = "0000aaaaeeee1111ffff";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex).concat(extraData));

        // Should fail for having more than 21 bytes
        test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_TESTNET,
            addressBytes,
            0,
            new byte[]{},
            ""
        );
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void deserializeBtcAddressWithVersion_invalid_address_hash() throws BridgeIllegalArgumentException {
        String addressVersionHex = "6f"; // Testnet pubkey hash
        String addressHash160Hex = "41";
        byte[] addressBytes = Hex.decode(addressVersionHex.concat(addressHash160Hex));

        // Should fail for having less than 21 bytes
        test_deserializeBtcAddressWithVersion(
            true,
            NetworkParameters.ID_TESTNET,
            addressBytes,
            0,
            new byte[]{},
            ""
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCalculatePegoutTxSize_ZeroInput_ZeroOutput() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        Federation federation = new Federation(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        pegUtils.getBridgeUtils().calculatePegoutTxSize(activations, federation, 0, 0);
    }

    @Test
    public void testCalculatePegoutTxSize_2Inputs_2Outputs() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        Federation federation = new Federation(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            networkParameters,
                pegUtils.getScriptBuilderWrapper()
        );

        int inputSize = 2;
        int outputSize = 2;
        int pegoutTxSize = pegUtils.getBridgeUtils().calculatePegoutTxSize(activations, federation, inputSize, outputSize);

        // The difference between the calculated size and a real tx size should be smaller than 1% in any direction
        int origTxSize = 2076; // Data for 2 inputs, 2 outputs From https://www.blockchain.com/btc/tx/e92cab54ecf738a00083fd8990515247aa3404df4f76ec358d9fe87d95102ae4
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .01;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    public void testCalculatePegoutTxSize_9Inputs_2Outputs() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        Federation federation = new Federation(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        int inputSize = 9;
        int outputSize = 2;
        int pegoutTxSize = pegUtils.getBridgeUtils().calculatePegoutTxSize(activations, federation, inputSize, outputSize);

        // The difference between the calculated size and a real tx size should be smaller than 1% in any direction
        int origTxSize = 9069; // Data for 9 inputs, 2 outputs From https://www.blockchain.com/btc/tx/15adf52f7b4b7a7e563fca92aec7bbe8149b87fac6941285a181e6fcd799a1cd
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .01;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    public void testCalculatePegoutTxSize_10Inputs_20Outputs() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        Federation federation = new Federation(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        // Create a pegout tx with 10 inputs and 20 outputs
        int inputSize = 10;
        int outputSize = 20;
        BtcTransaction pegoutTx = createPegOutTx(inputSize, outputSize, federation, keys);

        int pegoutTxSize = pegUtils.getBridgeUtils().calculatePegoutTxSize(activations, federation, inputSize, outputSize);

        // The difference between the calculated size and a real tx size should be smaller than 2% in any direction
        int origTxSize = pegoutTx.bitcoinSerialize().length;
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .02;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    public void testCalculatePegoutTxSize_50Inputs_200Outputs() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        List<BtcECKey> keys = PegTestUtils.createRandomBtcECKeys(13);
        Federation federation = new Federation(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        // Create a pegout tx with 50 inputs and 200 outputs
        int inputSize = 50;
        int outputSize = 200;
        BtcTransaction pegoutTx = createPegOutTx(inputSize, outputSize, federation, keys);

        int pegoutTxSize = pegUtils.getBridgeUtils().calculatePegoutTxSize(activations, federation, inputSize, outputSize);

        // The difference between the calculated size and a real tx size should be smaller than 2% in any direction
        int origTxSize = pegoutTx.bitcoinSerialize().length;
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .02;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    public void testCalculatePegoutTxSize_50Inputs_200Outputs_erpFederation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        List<BtcECKey> defaultFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        defaultFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04")),
            BtcECKey.fromPrivate(Hex.decode("fa05"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(defaultFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            erpFederationPublicKeys,
            500L,
            activations,
            pegUtils.getScriptBuilderWrapper()
        );

        // Create a pegout tx with 50 inputs and 200 outputs
        int inputSize = 50;
        int outputSize = 200;
        BtcTransaction pegoutTx = createPegOutTx(inputSize, outputSize, erpFederation, defaultFederationKeys);

        int pegoutTxSize = pegUtils.getBridgeUtils().calculatePegoutTxSize(activations, erpFederation, inputSize, outputSize);

        // The difference between the calculated size and a real tx size should be smaller than 3% in any direction
        int origTxSize = pegoutTx.bitcoinSerialize().length;
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .03;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    public void testCalculatePegoutTxSize_100Inputs_50Outputs_erpFederation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        List<BtcECKey> defaultFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        );
        defaultFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        List<BtcECKey> erpFederationPublicKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa03")),
            BtcECKey.fromPrivate(Hex.decode("fa04")),
            BtcECKey.fromPrivate(Hex.decode("fa05"))
        );
        erpFederationPublicKeys.sort(BtcECKey.PUBKEY_COMPARATOR);

        Federation erpFederation = new ErpFederation(
            FederationTestUtils.getFederationMembersWithBtcKeys(defaultFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters,
            erpFederationPublicKeys,
            500L,
            activations,
            pegUtils.getScriptBuilderWrapper()
        );

        // Create a pegout tx with 100 inputs and 50 outputs
        int inputSize = 100;
        int outputSize = 50;
        BtcTransaction pegoutTx = createPegOutTx(inputSize, outputSize, erpFederation, defaultFederationKeys);

        int pegoutTxSize = pegUtils.getBridgeUtils().calculatePegoutTxSize(activations, erpFederation, inputSize, outputSize);

        // The difference between the calculated size and a real tx size should be smaller than 3% in any direction
        int origTxSize = pegoutTx.bitcoinSerialize().length;
        int difference = origTxSize - pegoutTxSize;
        double tolerance = origTxSize * .03;

        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    public void getRegularPegoutTxSize_has_proper_calculations() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP271)).thenReturn(true);

        BtcECKey key1 = new BtcECKey();
        BtcECKey key2 = new BtcECKey();
        BtcECKey key3 = new BtcECKey();
        List<BtcECKey> keys = Arrays.asList(key1, key2, key3);
        Federation fed = new Federation(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            networkParameters,
            pegUtils.getScriptBuilderWrapper()
        );

        // Create a pegout tx with two inputs and two outputs
        int inputs = 2;
        BtcTransaction pegoutTx = createPegOutTx(Collections.emptyList(), inputs, fed, false);

        for (int inputIndex = 0; inputIndex < inputs; inputIndex++) {
            Script inputScript = pegoutTx.getInput(inputIndex).getScriptSig();

            Sha256Hash sighash = pegoutTx.hashForSignature(inputIndex, fed.getRedeemScript(), BtcTransaction.SigHash.ALL, false);

            for (int keyIndex = 0; keyIndex < keys.size() - 1; keyIndex++) {
                BtcECKey key = keys.get(keyIndex);
                BtcECKey.ECDSASignature sig = key.sign(sighash);
                TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);
                byte[] txSigEncoded = txSig.encodeToBitcoin();

                int sigIndex = inputScript.getSigInsertionIndex(sighash, key);
                inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSigEncoded, sigIndex, 1, 1);
                pegoutTx.getInput(inputIndex).setScriptSig(inputScript);
            }
        }

        int pegoutTxSize = pegUtils.getBridgeUtils().getRegularPegoutTxSize(activations, fed);

        // The difference between the calculated size and a real tx size should be smaller than 10% in any direction
        int difference = pegoutTx.bitcoinSerialize().length - pegoutTxSize;
        double tolerance = pegoutTxSize * .1;
        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    public void scriptCorrectlySpends_fromGenesisFederation_ok() {
        Federation genesisFederation = bridgeConstantsRegtest.getGenesisFederation();
        Address destinationAddress = PegTestUtils.createRandomP2PKHBtcAddress();

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

        assertTrue(pegUtils.getBridgeUtils().scriptCorrectlySpendsTx(tx, 0, genesisFederation.getP2SHScript()));
    }

    @Test
    public void scriptCorrectlySpends_invalidScript() {
        Federation genesisFederation = bridgeConstantsRegtest.getGenesisFederation();
        Address destinationAddress = PegTestUtils.createRandomP2PKHBtcAddress();

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

        assertFalse(pegUtils.getBridgeUtils().scriptCorrectlySpendsTx(tx, 0, genesisFederation.getP2SHScript()));
    }

    private void test_getSpendWallet(boolean isFastBridgeCompatible) throws UTXOProviderException {
        Federation federation = new Federation(FederationTestUtils.getFederationMembersWithBtcKeys(Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
            BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")))),
            Instant.ofEpochMilli(5005L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper());
        Context mockedBtcContext = mock(Context.class);
        when(mockedBtcContext.getParams()).thenReturn(networkParameters);

        List<UTXO> mockedUtxos = new ArrayList<>();
        mockedUtxos.add(mock(UTXO.class));
        mockedUtxos.add(mock(UTXO.class));
        mockedUtxos.add(mock(UTXO.class));

        Wallet wallet = pegUtils.getBridgeUtils().getFederationSpendWallet(mockedBtcContext, federation, mockedUtxos, isFastBridgeCompatible, null);

        if (isFastBridgeCompatible) {
            Assert.assertEquals(FastBridgeCompatibleBtcWalletWithStorage.class, wallet.getClass());
        } else {
            Assert.assertEquals(BridgeBtcWallet.class, wallet.getClass());
        }

        assertIsWatching(federation.getAddress(), wallet, networkParameters);
        CoinSelector selector = wallet.getCoinSelector();
        Assert.assertEquals(RskAllowUnconfirmedCoinSelector.class, selector.getClass());
        UTXOProvider utxoProvider = wallet.getUTXOProvider();
        Assert.assertEquals(RskUTXOProvider.class, utxoProvider.getClass());
        Assert.assertEquals(mockedUtxos, utxoProvider.getOpenTransactionOutputs(Collections.emptyList()));
    }

    private void test_getNoSpendWallet(boolean isFastBridgeCompatible) {
        Federation federation = new Federation(FederationTestUtils.getFederationMembersWithBtcKeys(Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
            BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")))),
            Instant.ofEpochMilli(5005L),
            0L,
            networkParameters,
            pegUtils.getScriptBuilderWrapper());
        Context mockedBtcContext = mock(Context.class);
        when(mockedBtcContext.getParams()).thenReturn(networkParameters);

        Wallet wallet = pegUtils.getBridgeUtils().getFederationNoSpendWallet(mockedBtcContext, federation, isFastBridgeCompatible, null);

        if (isFastBridgeCompatible) {
            Assert.assertEquals(FastBridgeCompatibleBtcWalletWithStorage.class, wallet.getClass());
        } else {
            Assert.assertEquals(BridgeBtcWallet.class, wallet.getClass());
        }

        assertIsWatching(federation.getAddress(), wallet, networkParameters);
    }

    private void test_serializeBtcAddressWithVersion(boolean isRskip284Active, Address address, byte[] serializedVersion, byte[] serializedAddress) {
        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(isRskip284Active);

        byte[] addressWithVersionBytes = pegUtils.getBridgeUtils().serializeBtcAddressWithVersion(activations, address);
        int expectedLength = serializedVersion.length + serializedAddress.length;
        Assert.assertEquals(expectedLength, addressWithVersionBytes.length);

        byte[] versionBytes = new byte[serializedVersion.length];
        System.arraycopy(addressWithVersionBytes, 0, versionBytes, 0, serializedVersion.length);

        byte[] addressBytes = new byte[serializedAddress.length];
        System.arraycopy(addressWithVersionBytes, serializedVersion.length, addressBytes, 0, serializedAddress.length);

        Assert.assertArrayEquals(serializedVersion, versionBytes);
        Assert.assertArrayEquals(serializedAddress, addressBytes);
    }

    private void test_deserializeBtcAddressWithVersion(boolean isRskip284Active, String networkId, byte[] serializedAddress,
        int expectedVersion, byte[] expectedHash, String expectedAddress) throws BridgeIllegalArgumentException {

        when(activations.isActive(ConsensusRule.RSKIP284)).thenReturn(isRskip284Active);

        BridgeConstants bridgeConstants = networkId.equals(NetworkParameters.ID_MAINNET) ?
            BridgeMainNetConstants.getInstance() :
            BridgeRegTestConstants.getInstance();

        Address address = pegUtils.getBridgeUtils().deserializeBtcAddressWithVersion(
            bridgeConstants.getBtcParams(),
            activations,
            serializedAddress
        );

        Assert.assertEquals(expectedVersion, address.getVersion());
        Assert.assertArrayEquals(expectedHash, address.getHash160());
        Assert.assertEquals(expectedAddress, address.toBase58());
    }

    private void assertIsWatching(Address address, Wallet wallet, NetworkParameters parameters) {
        List<Script> watchedScripts = wallet.getWatchedScripts();
        Assert.assertEquals(1, watchedScripts.size());
        Script watchedScript = watchedScripts.get(0);
        Assert.assertTrue(watchedScript.isPayToScriptHash());
        Assert.assertEquals(address.toString(), watchedScript.getToAddress(parameters).toString());
    }

    private void isFreeBridgeTx(boolean expected, RskAddress destinationAddress, byte[] privKeyBytes) {
        BridgeSupportFactory bridgeSupportFactory = new BridgeSupportFactory(
                new RepositoryBtcBlockStoreWithCache.Factory(constants.getBridgeConstants().getBtcParams()),
                constants.getBridgeConstants(),
                activationConfig, pegUtils);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR, constants, activationConfig, bridgeSupportFactory);
        org.ethereum.core.Transaction rskTx = CallTransaction.createCallTransaction(
                0,
                1,
                1,
                destinationAddress,
                0,
                Bridge.UPDATE_COLLECTIONS, constants.getChainId());
        rskTx.sign(privKeyBytes);

        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        Repository repository = new MutableRepository(new MutableTrieCache(new MutableTrieImpl(trieStore, new Trie())));
        Block rskExecutionBlock = new BlockGenerator().createChildBlock(getGenesisInstance(trieStore));
        bridge.init(rskTx, rskExecutionBlock, repository.startTracking(), null, null, null);
        Assert.assertEquals(expected, pegUtils.getBridgeUtils().isFreeBridgeTx(rskTx, constants, activationConfig.forBlock(rskExecutionBlock.getNumber())));
    }

    private Genesis getGenesisInstance(TrieStore trieStore) {
        return new TestGenesisLoader(trieStore, "frontier.json", constants.getInitialNonce(), false, true, true).load();
    }

    private ErpFederation createErpFederation() {
        Federation genesisFederation = bridgeConstantsRegtest.getGenesisFederation();
        return new ErpFederation(
            genesisFederation.getMembers(),
            genesisFederation.getCreationTime(),
            genesisFederation.getCreationBlockNumber(),
            genesisFederation.getBtcParams(),
            bridgeConstantsRegtest.getErpFedPubKeysList(),
            bridgeConstantsRegtest.getErpFedActivationDelay(),
            activations,
            pegUtils.getScriptBuilderWrapper()
        );
    }

    private BtcTransaction createPegOutTx(
        List<byte[]> signatures,
        int inputsToAdd,
        Federation federation,
        boolean isFastBridge
    ) {
        // Setup
        Address address;
        byte[] program;

        if (federation == null) {
            federation = BridgeRegTestConstants.getInstance().getGenesisFederation();
        }

        if (isFastBridge) {
            // Create fast bridge redeem script
            Sha256Hash derivationArgumentsHash = Sha256Hash.of(new byte[]{1});
            Script fastBridgeRedeemScript;

            if (federation instanceof ErpFederation) {
                fastBridgeRedeemScript =
                    FastBridgeErpRedeemScriptParser.createFastBridgeErpRedeemScript(
                        federation.getRedeemScript(),
                        derivationArgumentsHash
                    );
            } else {
                fastBridgeRedeemScript =
                    FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
                        federation.getRedeemScript(),
                        derivationArgumentsHash
                    );
            }

            Script fastBridgeP2SH = ScriptBuilder
                .createP2SHOutputScript(fastBridgeRedeemScript);
            address = Address.fromP2SHHash(networkParameters, fastBridgeP2SH.getPubKeyHash());
            program = fastBridgeRedeemScript.getProgram();

        } else {
            address = federation.getAddress();
            program = federation.getRedeemScript().getProgram();
        }

        // Build prev btc tx
        BtcTransaction prevTx = new BtcTransaction(networkParameters);
        TransactionOutput prevOut = new TransactionOutput(networkParameters, prevTx, Coin.FIFTY_COINS, address);
        prevTx.addOutput(prevOut);

        // Build btc tx to be signed
        BtcTransaction btcTx = new BtcTransaction(networkParameters);

        // Add inputs
        for (int i = 0; i < inputsToAdd; i++) {
            btcTx.addInput(prevOut);
        }

        Script scriptSig;

        if (signatures.isEmpty()) {
            scriptSig = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
        } else {
            scriptSig = ScriptBuilder.createMultiSigInputScriptBytes(signatures, program);
        }

        // Sign inputs
        for (int i = 0; i < inputsToAdd; i++) {
            btcTx.getInput(i).setScriptSig(scriptSig);
        }

        TransactionOutput output = new TransactionOutput(
            networkParameters,
            btcTx,
            Coin.COIN,
            new BtcECKey().toAddress(networkParameters)
        );
        btcTx.addOutput(output);

        TransactionOutput changeOutput = new TransactionOutput(
            networkParameters,
            btcTx,
            Coin.COIN,
            federation.getAddress()
        );
        btcTx.addOutput(changeOutput);

        return btcTx;
    }

    private BtcTransaction createPegOutTx(int inputSize, int outputSize, Federation federation, List<BtcECKey> keys) {
        Address randomAddress = PegTestUtils.createRandomP2PKHBtcAddress();

        BtcTransaction btcTx = new BtcTransaction(networkParameters);

        TransactionInput transInput = new TransactionInput(
            networkParameters,
            btcTx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );

        // Add inputs
        for (int i = 0; i < inputSize; i++) {
            btcTx.addInput(transInput);
            // sign input
            signWithNecessaryKeys(federation, keys, transInput, btcTx);
        }

        // Add outputs
        for (int i = 0; i < outputSize; i++) {
            btcTx.addOutput(Coin.COIN, randomAddress);
        }

        return btcTx;
    }

    private BtcTransaction createPegOutTx(List<byte[]> signatures, int inputsToAdd) {
        return createPegOutTx(signatures, inputsToAdd, null, false);
    }

    private BtcTransaction createPegOutTxForFastBridge(List<byte[]> signatures, int inputsToAdd, Federation federation) {
        return createPegOutTx(signatures, inputsToAdd, federation, true);
    }

    private byte[] generatePrivKey() {
        SecureRandom random = new SecureRandom();
        byte[] privKey = new byte[32];
        random.nextBytes(privKey);
        return privKey;
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
