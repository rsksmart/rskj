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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.Context;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.PartialMerkleTree;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.TransactionInput;
import co.rsk.bitcoinj.core.TransactionOutPoint;
import co.rsk.bitcoinj.core.TransactionOutput;
import co.rsk.bitcoinj.core.UTXO;
import co.rsk.bitcoinj.core.UTXOProvider;
import co.rsk.bitcoinj.core.UTXOProviderException;
import co.rsk.bitcoinj.core.VerificationException;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.ErpFederationRedeemScriptParser;
import co.rsk.bitcoinj.script.FastBridgeErpRedeemScriptParser;
import co.rsk.bitcoinj.script.FastBridgeRedeemScriptParser;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.bitcoinj.wallet.CoinSelector;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.RskAllowUnconfirmedCoinSelector;
import co.rsk.peg.btcLockSender.BtcLockSender.TxSenderAddressType;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ActivationConfigsForTest;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.Block;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Genesis;
import org.ethereum.core.ImmutableTransaction;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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
    private BridgeConstants bridgeConstants;
    private NetworkParameters networkParameters;

    @Before
    public void setupConfig() {
        constants = Constants.regtest();
        activationConfig = spy(ActivationConfigsForTest.all());
        bridgeConstants = BridgeRegTestConstants.getInstance();
        networkParameters = bridgeConstants.getBtcParams();
    }

    @Test
    public void testIsValidPegInTx() {
        // Peg-in is for the genesis federation ATM
        Context btcContext = new Context(networkParameters);

        Federation federation = bridgeConstants.getGenesisFederation();
        Wallet wallet = new BridgeBtcWallet(btcContext, Collections.singletonList(federation));
        Address federationAddress = federation.getAddress();
        wallet.addWatchedAddress(federationAddress, federation.getCreationTime().toEpochMilli());
        ActivationConfig.ForBlock actForBlock = mock(ActivationConfig.ForBlock.class);
        when(actForBlock.isActive(any(ConsensusRule.class))).thenReturn(false);

        // Tx sending less than the minimum allowed, not a peg-in tx
        Coin minimumLockValue = bridgeConstants.getLegacyMinimumPeginTxValueInSatoshis();
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(minimumLockValue.subtract(Coin.CENT), federationAddress);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(BridgeUtils.isValidPegInTx(tx, federation, btcContext, bridgeConstants, actForBlock));

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
        signWithNecessaryKeys(bridgeConstants.getGenesisFederation(), BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS, txIn, tx2);
        assertFalse(BridgeUtils.isValidPegInTx(tx2, federation, btcContext, bridgeConstants, actForBlock));

        // Tx sending 1 btc to the federation, is a peg-in tx
        BtcTransaction tx3 = new BtcTransaction(networkParameters);
        tx3.addOutput(Coin.COIN, federationAddress);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isValidPegInTx(tx3, federation, btcContext, bridgeConstants, actForBlock));

        // Tx sending 50 btc to the federation, is a peg-in tx
        BtcTransaction tx4 = new BtcTransaction(networkParameters);
        tx4.addOutput(Coin.FIFTY_COINS, federationAddress);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isValidPegInTx(tx4, federation, btcContext, bridgeConstants, actForBlock));
    }

    @Test
    public void testIsValidPegInTx_less_than_minimum_not_pegin_after_iris() {
        // Tx sending less than the minimum allowed, not a peg-in tx
        Context btcContext = new Context(networkParameters);
        Federation federation = this.getGenesisFederationForTest(bridgeConstants, btcContext);

        ActivationConfig.ForBlock actForBlock = mock(ActivationConfig.ForBlock.class);
        when(actForBlock.isActive(ConsensusRule.RSKIP219)).thenReturn(true);
        Coin minimumPegInValueAfterIris = bridgeConstants.getMinimumPeginTxValueInSatoshis();

        // Tx sending less than the minimum allowed, not a peg-in tx
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(minimumPegInValueAfterIris.subtract(Coin.CENT), federation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));

        assertFalse(BridgeUtils.isValidPegInTx(tx, federation, btcContext, bridgeConstants, actForBlock));
    }

    @Test
    public void testIsValidPegInTx_spending_from_federation_is_pegout_after_iris() {
        // Tx sending 1 btc to the federation, but also spending from the federation address,
        // the typical peg-out tx, not a peg-in tx.
        Context btcContext = new Context(networkParameters);
        Federation federation = this.getGenesisFederationForTest(bridgeConstants, btcContext);

        ActivationConfig.ForBlock actForBlock = mock(ActivationConfig.ForBlock.class);
        when(actForBlock.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, federation.getAddress());
        TransactionInput txIn = new TransactionInput(
            networkParameters,
            tx,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        tx.addInput(txIn);
        signWithNecessaryKeys(bridgeConstants.getGenesisFederation(), BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS, txIn, tx);

        assertFalse(BridgeUtils.isValidPegInTx(tx, federation, btcContext, bridgeConstants, actForBlock));
    }

    @Test
    public void testIsValidPegInTx_sending_50_btc_after_iris() {
        // Tx sending 50 btc to the federation, is a peg-in tx
        Context btcContext = new Context(networkParameters);
        Federation federation = this.getGenesisFederationForTest(bridgeConstants, btcContext);

        ActivationConfig.ForBlock actForBlock = mock(ActivationConfig.ForBlock.class);
        when(actForBlock.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.FIFTY_COINS, federation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));

        assertTrue(BridgeUtils.isValidPegInTx(tx, federation, btcContext, bridgeConstants, actForBlock));
    }

    @Test
    public void testIsValidPegInTx_value_between_old_and_new_before_iris() {
        // Tx sending btc between old and new value, it is not a peg-in before iris
        Context btcContext = new Context(networkParameters);
        Federation federation = this.getGenesisFederationForTest(bridgeConstants, btcContext);

        ActivationConfig.ForBlock actForBlock = mock(ActivationConfig.ForBlock.class);
        when(actForBlock.isActive(ConsensusRule.RSKIP219)).thenReturn(false);

        BtcTransaction tx = new BtcTransaction(networkParameters);

        // Get a value in between pre and post iris minimum
        Coin minimumPegInValueBeforeIris = bridgeConstants.getLegacyMinimumPeginTxValueInSatoshis();
        Coin minimumPegInValueAfterIris = bridgeConstants.getMinimumPeginTxValueInSatoshis();
        Coin valueLock = minimumPegInValueAfterIris.plus((minimumPegInValueBeforeIris.subtract(minimumPegInValueAfterIris)).div(2));
        assertTrue(valueLock.isLessThan(minimumPegInValueBeforeIris));
        assertTrue(valueLock.isGreaterThan(minimumPegInValueAfterIris));

        tx.addOutput(valueLock, federation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));

        assertFalse(BridgeUtils.isValidPegInTx(tx, federation, btcContext, bridgeConstants, actForBlock));
    }

    @Test
    public void testIsValidPegInTx_value_between_old_and_new_after_iris() {
        // Tx sending btc between old and new value, it is a peg-in after iris
        Context btcContext = new Context(networkParameters);
        Federation federation = this.getGenesisFederationForTest(bridgeConstants, btcContext);

        ActivationConfig.ForBlock actForBlock = mock(ActivationConfig.ForBlock.class);
        when(actForBlock.isActive(ConsensusRule.RSKIP219)).thenReturn(true);

        BtcTransaction tx = new BtcTransaction(networkParameters);

        // Get a value in between pre and post iris minimum
        Coin minimumPegInValueBeforeIris = bridgeConstants.getLegacyMinimumPeginTxValueInSatoshis();
        Coin minimumPegInValueAfterIris = bridgeConstants.getMinimumPeginTxValueInSatoshis();
        Coin valueLock = minimumPegInValueAfterIris.plus((minimumPegInValueBeforeIris.subtract(minimumPegInValueAfterIris)).div(2));
        assertTrue(valueLock.isGreaterThan(minimumPegInValueAfterIris));
        assertTrue(valueLock.isLessThan(minimumPegInValueBeforeIris));

        tx.addOutput(valueLock, federation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));

        assertTrue(BridgeUtils.isValidPegInTx(tx, federation, btcContext, bridgeConstants, actForBlock));
    }

    @Test
    public void testIsValidPegInTxForTwoFederations() {
        Context btcContext = new Context(networkParameters);
        ActivationConfig.ForBlock actForBlock = mock(ActivationConfig.ForBlock.class);
        when(actForBlock.isActive(any(ConsensusRule.class))).thenReturn(false);

        List<BtcECKey> federation1Keys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters
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
            networkParameters
        );

        Address address1 = federation1.getAddress();
        Address address2 = federation2.getAddress();

        List<Federation> federations = Arrays.asList(federation1, federation2);

        // Tx sending less than 1 btc to the first federation, not a peg-in tx
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.CENT, address1);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(BridgeUtils.isValidPegInTx(
            tx,
            federations,
            null,
            btcContext,
            bridgeConstants,
            actForBlock
        ));

        // Tx sending less than 1 btc to the second federation, not a peg-in tx
        tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.CENT, address2);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(BridgeUtils.isValidPegInTx(
            tx,
            federations,
            null,
            btcContext,
            bridgeConstants,
            actForBlock
        ));

        // Tx sending less than 1 btc to both federations, not a peg-in tx
        tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.CENT, address1);
        tx.addOutput(Coin.CENT, address2);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(BridgeUtils.isValidPegInTx(
            tx,
            federations,
            null,
            btcContext,
            bridgeConstants,
            actForBlock
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
        assertFalse(BridgeUtils.isValidPegInTx(
            tx2,
            federations,
            null,
            btcContext,
            bridgeConstants,
            actForBlock
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
        assertFalse(BridgeUtils.isValidPegInTx(
            tx2,
            federations,
            null,
            btcContext,
            bridgeConstants,
            actForBlock
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
        assertFalse(BridgeUtils.isValidPegInTx(
            tx2,
            federations,
            null,
            btcContext,
            bridgeConstants,
            actForBlock
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
        assertFalse(BridgeUtils.isValidPegInTx(
            tx2,
            federations,
            null,
            btcContext,
            bridgeConstants,
            actForBlock
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
        assertFalse(BridgeUtils.isValidPegInTx(
            tx2,
            federations,
            null,
            btcContext,
            bridgeConstants,
            actForBlock
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
        assertFalse(BridgeUtils.isValidPegInTx(
            tx2,
            Collections.singletonList(federation2),
            federation1.getP2SHScript(),
            btcContext,
            bridgeConstants,
            actForBlock
        ));

        // Tx sending 1 btc to the first federation, is a peg-in tx
        BtcTransaction tx3 = new BtcTransaction(networkParameters);
        tx3.addOutput(Coin.COIN, address1);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isValidPegInTx(
            tx3,
            federations,
            null,
            btcContext,
            bridgeConstants,
            actForBlock
        ));

        // Tx sending 1 btc to the second federation, is a peg-in tx
        tx3 = new BtcTransaction(networkParameters);
        tx3.addOutput(Coin.COIN, address2);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isValidPegInTx(
            tx3,
            federations,
            null,
            btcContext,
            bridgeConstants,
            actForBlock
        ));

        // Tx sending 1 btc to the both federations, is a peg-in tx
        tx3 = new BtcTransaction(networkParameters);
        tx3.addOutput(Coin.COIN, address1);
        tx3.addOutput(Coin.COIN, address2);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isValidPegInTx(
            tx3,
            federations,
            null,
            btcContext,
            bridgeConstants,
            actForBlock
        ));

        // Tx sending 50 btc to the first federation, is a peg-in tx
        BtcTransaction tx4 = new BtcTransaction(networkParameters);
        tx4.addOutput(Coin.FIFTY_COINS, address1);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isValidPegInTx(
            tx4,
            federations,
            null,
            btcContext,
            bridgeConstants,
            actForBlock
        ));

        // Tx sending 50 btc to the second federation, is a peg-in tx
        tx4 = new BtcTransaction(networkParameters);
        tx4.addOutput(Coin.FIFTY_COINS, address2);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isValidPegInTx(
            tx4,
            federations,
            null,
            btcContext,
            bridgeConstants,
            actForBlock
        ));

        // Tx sending 50 btc to the both federations, is a peg-in tx
        tx4 = new BtcTransaction(networkParameters);
        tx4.addOutput(Coin.FIFTY_COINS, address1);
        tx4.addOutput(Coin.FIFTY_COINS, address2);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isValidPegInTx(
            tx4,
            federations,
            null,
            btcContext,
            bridgeConstants,
            actForBlock
        ));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        Federation activeFederation = bridgeConstants.getGenesisFederation();
        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            activeFederation.getRedeemScript(),
            Sha256Hash.of(PegTestUtils.createHash(1).getBytes())
        );

        // Create a tx from the fast bridge fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, fastBridgeRedeemScript);

        Assert.assertTrue(BridgeUtils.isValidPegInTx(tx, activeFederation, btcContext, bridgeConstants, activations));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        Federation activeFederation = bridgeConstants.getGenesisFederation();
        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            activeFederation.getRedeemScript(),
            Sha256Hash.of(PegTestUtils.createHash(1).getBytes())
        );

        // Create a tx from the fast bridge fed to the active fed
        BtcTransaction tx = new BtcTransaction(networkParameters);
        tx.addOutput(Coin.COIN, activeFederation.getAddress());
        tx.addInput(Sha256Hash.ZERO_HASH, 0, fastBridgeRedeemScript);

        Assert.assertFalse(BridgeUtils.isValidPegInTx(tx, activeFederation, btcContext, bridgeConstants, activations));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeErpFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        Federation activeFederation = bridgeConstants.getGenesisFederation();
        List<BtcECKey> erpFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        erpFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation erpFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(erpFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters
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

        Assert.assertTrue(BridgeUtils.isValidPegInTx(tx, activeFederation, btcContext, bridgeConstants, activations));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeErpFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        Federation activeFederation = bridgeConstants.getGenesisFederation();
        List<BtcECKey> erpFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        erpFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation erpFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(erpFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters
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

        Assert.assertFalse(BridgeUtils.isValidPegInTx(tx, activeFederation, btcContext, bridgeConstants, activations));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromErpFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(false);

        Federation activeFederation = bridgeConstants.getGenesisFederation();
        List<BtcECKey> erpFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        erpFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation erpFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(erpFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
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

        Assert.assertTrue(BridgeUtils.isValidPegInTx(tx, activeFederation, btcContext, bridgeConstants, activations));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromErpFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        Federation activeFederation = bridgeConstants.getGenesisFederation();
        List<BtcECKey> erpFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        erpFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation erpFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(erpFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
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

        Assert.assertFalse(BridgeUtils.isValidPegInTx(tx, activeFederation, btcContext, bridgeConstants, activations));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeRetiredFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstants.getGenesisFederation();
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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

        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            retiredFederation.getRedeemScript(),
            PegTestUtils.createHash(2)
        );
        signWithNecessaryKeys(retiredFederation, fastBridgeRedeemScript, retiredFederationKeys, txInput, tx);

        assertTrue(BridgeUtils.isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            btcContext,
            bridgeConstants,
            activations
        ));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeRetiredFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstants.getGenesisFederation();
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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

        Script fastBridgeRedeemScript = FastBridgeRedeemScriptParser.createMultiSigFastBridgeRedeemScript(
            retiredFederation.getRedeemScript(),
            PegTestUtils.createHash(2)
        );
        signWithNecessaryKeys(retiredFederation, fastBridgeRedeemScript, retiredFederationKeys, txInput, tx);

        assertFalse(BridgeUtils.isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            btcContext,
            bridgeConstants,
            activations
        ));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeErpRetiredFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstants.getGenesisFederation();
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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
            networkParameters
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
            500L
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

        assertTrue(BridgeUtils.isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            btcContext,
            bridgeConstants,
            activations
        ));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromFastBridgeErpRetiredFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstants.getGenesisFederation();
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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
            networkParameters
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
            500L
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

        assertFalse(BridgeUtils.isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            btcContext,
            bridgeConstants,
            activations
        ));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromErpRetiredFederation_beforeRskip201_isPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstants.getGenesisFederation();
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        List<BtcECKey> retiredFederationKeys = Arrays.asList(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        );
        retiredFederationKeys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation retiredFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            0L,
            networkParameters
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
            500L
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

        assertTrue(BridgeUtils.isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            btcContext,
            bridgeConstants,
            activations
        ));
    }

    @Test
    public void testIsValidPegInTx_hasChangeUtxoFromErpRetiredFederation_afterRskip201_notPegin() {
        Context btcContext = new Context(networkParameters);
        Federation activeFederation = bridgeConstants.getGenesisFederation();
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
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
            networkParameters
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
            500L
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

        assertFalse(BridgeUtils.isValidPegInTx(
            tx,
            Collections.singletonList(activeFederation),
            retiredFederation.getP2SHScript(),
            btcContext,
            bridgeConstants,
            activations
        ));
    }

    @Test
    public void testTxIsProcessableInLegacyVersion() {
        // Before hard fork
        ActivationConfig.ForBlock actForBlock = mock(ActivationConfig.ForBlock.class);
        when(actForBlock.isActive(ConsensusRule.RSKIP143)).thenReturn(false);

        assertTrue(BridgeUtils.txIsProcessableInLegacyVersion(TxSenderAddressType.P2PKH, actForBlock));
        assertFalse(BridgeUtils.txIsProcessableInLegacyVersion(TxSenderAddressType.P2SHP2WPKH, actForBlock));
        assertFalse(BridgeUtils.txIsProcessableInLegacyVersion(TxSenderAddressType.P2SHMULTISIG, actForBlock));
        assertFalse(BridgeUtils.txIsProcessableInLegacyVersion(TxSenderAddressType.P2SHP2WSH, actForBlock));
        assertFalse(BridgeUtils.txIsProcessableInLegacyVersion(TxSenderAddressType.UNKNOWN, actForBlock));

        // After hard fork
        when(actForBlock.isActive(ConsensusRule.RSKIP143)).thenReturn(true);

        assertTrue(BridgeUtils.txIsProcessableInLegacyVersion(TxSenderAddressType.P2PKH, actForBlock));
        assertTrue(BridgeUtils.txIsProcessableInLegacyVersion(TxSenderAddressType.P2SHP2WPKH, actForBlock));
        assertTrue(BridgeUtils.txIsProcessableInLegacyVersion(TxSenderAddressType.P2SHMULTISIG, actForBlock));
        assertTrue(BridgeUtils.txIsProcessableInLegacyVersion(TxSenderAddressType.P2SHP2WSH, actForBlock));
        assertFalse(BridgeUtils.txIsProcessableInLegacyVersion(TxSenderAddressType.UNKNOWN, actForBlock));
    }

    @Test
    public void testIsMigrationTx() {
        Context btcContext = new Context(networkParameters);
        ActivationConfig.ForBlock activation = mock(ActivationConfig.ForBlock.class);

        List<BtcECKey> activeFederationKeys = Stream.of(
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        Federation activeFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
            Instant.ofEpochMilli(2000L),
            2L,
            networkParameters
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
            networkParameters
        );

        List<BtcECKey> retiredFederationKeys = Stream.of(
                BtcECKey.fromPrivate(Hex.decode("fc01")),
                BtcECKey.fromPrivate(Hex.decode("fc02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        Federation retiredFederation = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(retiredFederationKeys),
            Instant.ofEpochMilli(1000L),
            1L,
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
        assertTrue(BridgeUtils.isMigrationTx(
            migrationTx,
            activeFederation,
            retiringFederation,
            null,
            btcContext,
            bridgeConstants,
            activation
        ));

        BtcTransaction toActiveFederationTx = new BtcTransaction(networkParameters);
        toActiveFederationTx.addOutput(Coin.COIN, activeFederationAddress);
        toActiveFederationTx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(BridgeUtils.isMigrationTx(
            toActiveFederationTx,
            activeFederation,
            retiringFederation,
            null,
            btcContext,
            bridgeConstants,
            activation
        ));

        Address randomAddress = PegTestUtils.createRandomBtcAddress();
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
        assertFalse(BridgeUtils.isMigrationTx(
            fromRetiringFederationTx,
            activeFederation,
            retiringFederation,
            null,
            btcContext,
            bridgeConstants,
            activation
        ));

        assertFalse(BridgeUtils.isMigrationTx(
            migrationTx,
            activeFederation,
            null,
            null,
            btcContext,
            bridgeConstants,
            activation
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
        assertTrue(BridgeUtils.isMigrationTx(
            retiredMigrationTx,
            activeFederation,
            null,
            p2SHScript,
            btcContext,
            bridgeConstants,
            activation
        ));

        assertTrue(BridgeUtils.isMigrationTx(
            retiredMigrationTx,
            activeFederation,
            retiringFederation,
            p2SHScript,
            btcContext,
            bridgeConstants,
            activation
        ));
        assertFalse(BridgeUtils.isMigrationTx(
            toActiveFederationTx,
            activeFederation,
            null,
            p2SHScript,
            btcContext,
            bridgeConstants,
            activation
        ));
        assertFalse(BridgeUtils.isMigrationTx(
            toActiveFederationTx,
            activeFederation,
            retiringFederation,
            p2SHScript,
            btcContext,
            bridgeConstants,
            activation
        ));
    }

    @Test
    public void testIsPegOutTx() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

        Federation federation = bridgeConstants.getGenesisFederation();
        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02")),
            BtcECKey.fromPrivate(Hex.decode("fa03"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        Federation federation2 = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys),
            Instant.ofEpochMilli(2000L),
            2L,
            bridgeConstants.getBtcParams()
        );
        List<BtcECKey> federationPrivateKeys = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS;
        Address randomAddress = PegTestUtils.createRandomBtcAddress();

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

        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(federation), activations));
        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, Arrays.asList(federation, federation2), activations));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(federation2), activations));

        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, activations, federation.getP2SHScript()));
        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, activations, federation.getP2SHScript(), federation2.getP2SHScript()));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, activations, federation2.getP2SHScript()));
    }

    @Test
    public void testIsPegOutTx_fromFastBridgeFederation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

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
            networkParameters
        );

        Federation standardFederation = bridgeConstants.getGenesisFederation();

        // Create a tx from the fast bridge fed to a random address
        Address randomAddress = PegTestUtils.createRandomBtcAddress();
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

        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(fastBridgeFederation), activations));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Arrays.asList(fastBridgeFederation, standardFederation), activations));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, activations, fastBridgeFederation.getP2SHScript()));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, activations, fastBridgeFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));

        // After RSKIP 201 activation
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(fastBridgeFederation), activations));
        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, Arrays.asList(fastBridgeFederation, standardFederation), activations));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, activations, fastBridgeFederation.getP2SHScript()));
        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, activations, fastBridgeFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));
    }

    @Test
    public void testIsPegOutTx_fromErpFederation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

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
            networkParameters
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
            500L
        );

        Federation standardFederation = bridgeConstants.getGenesisFederation();

        // Create a tx from the erp fed to a random address
        Address randomAddress = PegTestUtils.createRandomBtcAddress();
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

        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(defaultFederation), activations));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Arrays.asList(defaultFederation, standardFederation), activations));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript()));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));

        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(erpFederation), activations));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, activations, erpFederation.getStandardP2SHScript()));

        // After RSKIP 201 activation
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(defaultFederation), activations));
        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, Arrays.asList(defaultFederation, standardFederation), activations));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript()));
        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));

        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(erpFederation), activations));
        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, activations, erpFederation.getStandardP2SHScript()));
    }

    @Test
    public void testIsPegOutTx_fromFastBridgeErpFederation() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);

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
            networkParameters
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
            500L
        );

        Federation standardFederation = bridgeConstants.getGenesisFederation();

        // Create a tx from the fast bridge erp fed to a random address
        Address randomAddress = PegTestUtils.createRandomBtcAddress();
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

        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(defaultFederation), activations));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Arrays.asList(defaultFederation, standardFederation), activations));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript()));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));

        // After RSKIP 201 activation
        when(activations.isActive(ConsensusRule.RSKIP201)).thenReturn(true);

        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(defaultFederation), activations));
        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, Arrays.asList(defaultFederation, standardFederation), activations));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(standardFederation), activations));

        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript()));
        assertTrue(BridgeUtils.isPegOutTx(pegOutTx1, activations, defaultFederation.getP2SHScript(), standardFederation.getP2SHScript()));
        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, activations, standardFederation.getP2SHScript()));
    }

    @Test
    public void testIsPegOutTx_noRedeemScript() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        Federation federation = bridgeConstants.getGenesisFederation();
        Address randomAddress = PegTestUtils.createRandomBtcAddress();

        BtcTransaction pegOutTx1 = new BtcTransaction(networkParameters);
        pegOutTx1.addOutput(Coin.COIN, randomAddress);
        TransactionInput pegOutInput1 = new TransactionInput(
            networkParameters,
            pegOutTx1,
            new byte[]{},
            new TransactionOutPoint(networkParameters, 0, Sha256Hash.ZERO_HASH)
        );
        pegOutTx1.addInput(pegOutInput1);

        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(federation), activations));
    }

    @Test
    public void testIsPegOutTx_invalidRedeemScript() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        Federation federation = bridgeConstants.getGenesisFederation();
        Address randomAddress = PegTestUtils.createRandomBtcAddress();
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

        assertFalse(BridgeUtils.isPegOutTx(pegOutTx1, Collections.singletonList(federation), activations));
    }

    @Test
    public void testChangeBetweenFederations() {
        ActivationConfig.ForBlock activations = mock(ActivationConfig.ForBlock.class);
        Address randomAddress = PegTestUtils.createRandomBtcAddress();
        Context btcContext = new Context(networkParameters);

        List<BtcECKey> federation1Keys = Stream.of("fa01", "fa02")
            .map(Hex::decode)
            .map(BtcECKey::fromPrivate)
            .sorted(BtcECKey.PUBKEY_COMPARATOR)
            .collect(Collectors.toList());
        Federation federation1 = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
            Instant.ofEpochMilli(1000L), 0L, networkParameters
        );

        List<BtcECKey> federation2Keys = Stream.of("fb01", "fb02", "fb03")
            .map(Hex::decode)
            .map(BtcECKey::fromPrivate)
            .sorted(BtcECKey.PUBKEY_COMPARATOR)
            .collect(Collectors.toList());
        Federation federation2 = new Federation(
            FederationTestUtils.getFederationMembersWithBtcKeys(federation2Keys),
            Instant.ofEpochMilli(2000L), 0L, networkParameters
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
        assertFalse(BridgeUtils.isValidPegInTx(
            pegOutWithChange,
            federations,
            null,
            btcContext,
            bridgeConstants,
            mock(ActivationConfig.ForBlock.class)
        ));
        assertTrue(BridgeUtils.isPegOutTx(pegOutWithChange, federations, activations));
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
        Address result = BridgeUtils.recoverBtcAddressFromEthTransaction(tx, RegTestParams.get());

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
        BridgeUtils.recoverBtcAddressFromEthTransaction(tx, RegTestParams.get());
    }

    @Test
    public void hasEnoughSignatures_two_signatures() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, sign2), 1);
        Assert.assertTrue(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void hasEnoughSignatures_one_signature() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, MISSING_SIGNATURE), 1);
        Assert.assertFalse(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void hasEnoughSignatures_no_signatures() {
        BtcTransaction btcTx = createPegOutTx(Collections.emptyList(), 1);
        Assert.assertFalse(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void hasEnoughSignatures_several_inputs_all_signed() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, sign2), 3);
        Assert.assertTrue(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
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

        Assert.assertTrue(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
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

        Assert.assertTrue(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
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

        Assert.assertTrue(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void hasEnoughSignatures_several_inputs_one_missing_signature() {
        // Create 1 signature
        byte[] sign1 = new byte[]{0x79};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, MISSING_SIGNATURE), 3);
        Assert.assertFalse(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_two_signatures() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, sign2), 1);
        Assert.assertEquals(0, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_one_signature() {
        // Add 1 signature
        byte[] sign1 = new byte[]{0x79};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, MISSING_SIGNATURE), 1);
        Assert.assertEquals(1, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_no_signatures() {
        // As no signature was added, missing signatures is 2
        BtcTransaction btcTx = createPegOutTx(Collections.emptyList(), 1);
        Assert.assertEquals(2, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_several_inputs_all_signed() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, sign2), 3);
        Assert.assertEquals(0, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
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

        Assert.assertEquals(0, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
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

        Assert.assertEquals(0, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
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

        Assert.assertEquals(0, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_several_inputs_one_missing_signature() {
        // Create 1 signature
        byte[] sign1 = new byte[]{0x79};

        BtcTransaction btcTx = createPegOutTx(Arrays.asList(sign1, MISSING_SIGNATURE), 3);
        Assert.assertEquals(1, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
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
                BridgeUtils.isContractTx(
                        Transaction.builder().build()
                )
        );
        Assert.assertTrue(
            BridgeUtils.isContractTx(new org.ethereum.vm.program.InternalTransaction(
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
        BridgeUtils.getCoinFromBigInteger(new BigInteger("9223372036854775808"));
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void getCoinFromBigInteger_null_value() throws BridgeIllegalArgumentException {
        BridgeUtils.getCoinFromBigInteger(null);
    }

    @Test
    public void getCoinFromBigInteger() throws BridgeIllegalArgumentException {
        Assert.assertEquals(Coin.COIN, BridgeUtils.getCoinFromBigInteger(BigInteger.valueOf(Coin.COIN.getValue())));
    }

    @Test(expected = Exception.class)
    public void validateHeightAndConfirmations_invalid_height() throws Exception {
        Assert.assertFalse(BridgeUtils.validateHeightAndConfirmations(-1, 0, 0, null));
    }

    @Test
    public void validateHeightAndConfirmation_insufficient_confirmations() throws Exception {
        Assert.assertFalse(BridgeUtils.validateHeightAndConfirmations(2, 5, 10, Sha256Hash.of(Hex.decode("ab"))));
    }

    @Test
    public void validateHeightAndConfirmation_enough_confirmations() throws Exception {
        Assert.assertTrue(BridgeUtils.validateHeightAndConfirmations(
            2,
            5,
            3,
            Sha256Hash.of(Hex.decode("ab")))
        );
    }

    @Test(expected = Exception.class)
    public void calculateMerkleRoot_invalid_pmt() {
        BridgeUtils.calculateMerkleRoot(networkParameters, Hex.decode("ab"), null);
    }

    @Test
    public void calculateMerkleRoot_hashes_not_in_pmt() {
        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(PegTestUtils.createHash(2));

        BtcTransaction tx = new BtcTransaction(networkParameters);
        PartialMerkleTree pmt = new PartialMerkleTree(networkParameters, bits, hashes, 1);

        Assert.assertNull(BridgeUtils.calculateMerkleRoot(networkParameters, pmt.bitcoinSerialize(), tx.getHash()));
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
        Sha256Hash merkleRoot = BridgeUtils.calculateMerkleRoot(networkParameters, pmt.bitcoinSerialize(), tx.getHash());
        Assert.assertNotNull(merkleRoot);
    }

    @Test(expected = VerificationException.class)
    public void validateInputsCount_active_rskip() {
        BridgeUtils.validateInputsCount(Hex.decode("00000000000100"), true);
    }

    @Test(expected = VerificationException.class)
    public void validateInputsCount_inactive_rskip() {
        BtcTransaction tx = new BtcTransaction(networkParameters);
        BridgeUtils.validateInputsCount(tx.bitcoinSerialize(), false);
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
                networkParameters
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
        boolean isSigned = BridgeUtils.isInputSignedByThisFederator(federator1Key, sighash, txInput);

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
                networkParameters
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
        boolean isSigned = BridgeUtils.isInputSignedByThisFederator(federator2Key, sighash, txInput);

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
                networkParameters
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
        boolean isSigned = BridgeUtils.isInputSignedByThisFederator(federator1Key, sighash, txInput);

        // Assert
        Assert.assertFalse(isSigned);
    }

    @Test
    public void extractAddressVersionFromBytes() throws BridgeIllegalArgumentException {
        byte[] addressBytes = Hex.decode("6f0febdbf4739e9fe6724370a7e99cb25d7be5ca99");
        int obtainedVersion = BridgeUtils.extractAddressVersionFromBytes(addressBytes);
        Assert.assertEquals(111, obtainedVersion);
    }

    @Test
    public void extractHash160FromBytes() throws BridgeIllegalArgumentException {
        byte[] addressBytes = Hex.decode("6f0febdbf4739e9fe6724370a7e99cb25d7be5ca99");
        byte[] hash160 = Hex.decode("0febdbf4739e9fe6724370a7e99cb25d7be5ca99");
        byte[] obtainedHash160 = BridgeUtils.extractHash160FromBytes(addressBytes);
        Assert.assertArrayEquals(hash160, obtainedHash160);
    }

    @Test
    public void calculatePegoutTxSize() {
        Federation fed = new Federation(
            Arrays.asList(FederationMember.getFederationMemberFromKey(new BtcECKey())),
            Instant.now(),
            0,
            networkParameters
        );

        int pegoutTxSize = BridgeUtils.calculatePegoutTxSize(fed, 1, 1, 1, 1);

        // 58 accounts for all the added values as part of a peg-out tx
        int calculation = fed.getRedeemScript().getProgram().length+ 58;
        assertEquals(calculation, pegoutTxSize);
    }

    @Test
    public void getRegularPegoutTxSize_has_proper_calculations() {
        BtcECKey key1 = new BtcECKey();
        BtcECKey key2 = new BtcECKey();
        BtcECKey key3 = new BtcECKey();
        List<BtcECKey> keys = Arrays.asList(key1, key2, key3);
        Federation fed = new Federation(
            FederationMember.getFederationMembersFromKeys(keys),
            Instant.now(),
            0,
            networkParameters
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

        int pegoutTxSize = BridgeUtils.getRegularPegoutTxSize(fed);

        // The difference between the calculated size and a real tx size should be smaller than 10% in any direction
        int difference = pegoutTx.bitcoinSerialize().length - pegoutTxSize;
        double tolerance = pegoutTxSize * .1;
        assertTrue(difference < tolerance && difference > -tolerance);
    }

    @Test
    public void scriptCorrectlySpends_fromGenesisFederation_ok() {
        Federation genesisFederation = bridgeConstants.getGenesisFederation();
        Address destinationAddress = PegTestUtils.createRandomBtcAddress();

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

        assertTrue(BridgeUtils.scriptCorrectlySpendsTx(tx, 0, genesisFederation.getP2SHScript()));
    }

    @Test
    public void scriptCorrectlySpends_invalidScript() {
        Federation genesisFederation = bridgeConstants.getGenesisFederation();
        Address destinationAddress = PegTestUtils.createRandomBtcAddress();

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

        assertFalse(BridgeUtils.scriptCorrectlySpendsTx(tx, 0, genesisFederation.getP2SHScript()));
    }

    private void test_getSpendWallet(boolean isFastBridgeCompatible) throws UTXOProviderException {
        Federation federation = new Federation(FederationTestUtils.getFederationMembersWithBtcKeys(Arrays.asList(
            BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
            BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")))),
            Instant.ofEpochMilli(5005L),
            0L,
            networkParameters);
        Context mockedBtcContext = mock(Context.class);
        when(mockedBtcContext.getParams()).thenReturn(networkParameters);

        List<UTXO> mockedUtxos = new ArrayList<>();
        mockedUtxos.add(mock(UTXO.class));
        mockedUtxos.add(mock(UTXO.class));
        mockedUtxos.add(mock(UTXO.class));

        Wallet wallet = BridgeUtils.getFederationSpendWallet(mockedBtcContext, federation, mockedUtxos, isFastBridgeCompatible, null);

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
            networkParameters);
        Context mockedBtcContext = mock(Context.class);
        when(mockedBtcContext.getParams()).thenReturn(networkParameters);

        Wallet wallet = BridgeUtils.getFederationNoSpendWallet(mockedBtcContext, federation, isFastBridgeCompatible, null);

        if (isFastBridgeCompatible) {
            Assert.assertEquals(FastBridgeCompatibleBtcWalletWithStorage.class, wallet.getClass());
        } else {
            Assert.assertEquals(BridgeBtcWallet.class, wallet.getClass());
        }

        assertIsWatching(federation.getAddress(), wallet, networkParameters);
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
                activationConfig);

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
        Assert.assertEquals(expected, BridgeUtils.isFreeBridgeTx(rskTx, constants, activationConfig.forBlock(rskExecutionBlock.getNumber())));
    }

    private Genesis getGenesisInstance(TrieStore trieStore) {
        return new TestGenesisLoader(trieStore, "frontier.json", constants.getInitialNonce(), false, true, true).load();
    }

    private ErpFederation createErpFederation() {
        Federation genesisFederation = bridgeConstants.getGenesisFederation();
        return new ErpFederation(
            genesisFederation.getMembers(),
            genesisFederation.getCreationTime(),
            genesisFederation.getCreationBlockNumber(),
            genesisFederation.getBtcParams(),
            bridgeConstants.getErpFedPubKeysList(),
            bridgeConstants.getErpFedActivationDelay()
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
