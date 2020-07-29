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
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.wallet.CoinSelector;
import co.rsk.bitcoinj.wallet.RedeemData;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import co.rsk.core.genesis.TestGenesisLoader;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.peg.bitcoin.RskAllowUnconfirmedCoinSelector;
import co.rsk.peg.btcLockSender.BtcLockSender;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
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

import static co.rsk.peg.PegTestUtils.createBaseRedeemScriptThatSpendsFromTheFederation;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BridgeUtilsTest {
    private static final String TO_ADDRESS = "0000000000000000000000000000000000000006";
    private static final BigInteger AMOUNT = new BigInteger("1");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";
    private Constants constants;
    private ActivationConfig activationConfig;

    @Before
    public void setupConfig(){
        constants = Constants.regtest();
        activationConfig = spy(ActivationConfigsForTest.all());
    }

    @Test
    public void testIsLock() {
        // Lock is for the genesis federation ATM
        NetworkParameters params = RegTestParams.get();
        Context btcContext = new Context(params);
        BridgeRegTestConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        Federation federation = bridgeConstants.getGenesisFederation();
        Wallet wallet = new BridgeBtcWallet(btcContext, Collections.singletonList(federation));
        Address federationAddress = federation.getAddress();
        wallet.addWatchedAddress(federationAddress, federation.getCreationTime().toEpochMilli());

        // Tx sending less than the minimum allowed, not a lock tx
        Coin minimumLockValue = bridgeConstants.getMinimumLockTxValue();
        BtcTransaction tx = new BtcTransaction(params);
        tx.addOutput(minimumLockValue.subtract(Coin.CENT), federationAddress);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(BridgeUtils.isLockTx(tx, federation, btcContext, bridgeConstants));

        // Tx sending 1 btc to the federation, but also spending from the federation addres, the typical release tx, not a lock tx.
        BtcTransaction tx2 = new BtcTransaction(params);
        tx2.addOutput(Coin.COIN, federationAddress);
        TransactionInput txIn = new TransactionInput(params, tx2, new byte[]{}, new TransactionOutPoint(params, 0, Sha256Hash.ZERO_HASH));
        tx2.addInput(txIn);
        signWithNecessaryKeys(bridgeConstants.getGenesisFederation(), BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS, txIn, tx2, bridgeConstants);
        assertFalse(BridgeUtils.isLockTx(tx2, federation, btcContext, bridgeConstants));

        // Tx sending 1 btc to the federation, is a lock tx
        BtcTransaction tx3 = new BtcTransaction(params);
        tx3.addOutput(Coin.COIN, federationAddress);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isLockTx(tx3, federation, btcContext, bridgeConstants));

        // Tx sending 50 btc to the federation, is a lock tx
        BtcTransaction tx4 = new BtcTransaction(params);
        tx4.addOutput(Coin.FIFTY_COINS, federationAddress);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isLockTx(tx4, federation, btcContext, bridgeConstants));
    }

    @Test
    public void testIsLockForTwoFederations() {
        BridgeRegTestConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        NetworkParameters parameters = bridgeConstants.getBtcParams();
        Context btcContext = new Context(parameters);

        List<BtcECKey> federation1Keys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fa01")),
                BtcECKey.fromPrivate(Hex.decode("fa02")));
        federation1Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation1 = new Federation(FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys), Instant.ofEpochMilli(1000L), 0L, parameters);

        List<BtcECKey> federation2Keys = Arrays.asList(
                BtcECKey.fromPrivate(Hex.decode("fb01")),
                BtcECKey.fromPrivate(Hex.decode("fb02")),
                BtcECKey.fromPrivate(Hex.decode("fb03")));
        federation2Keys.sort(BtcECKey.PUBKEY_COMPARATOR);
        Federation federation2 = new Federation(FederationTestUtils.getFederationMembersWithBtcKeys(federation2Keys), Instant.ofEpochMilli(2000L), 0L, parameters);

        Address address1 = federation1.getAddress();
        Address address2 = federation2.getAddress();

        List<Federation> federations = Arrays.asList(federation1, federation2);
        List<Address> addresses = Arrays.asList(address1, address2);

        // Tx sending less than 1 btc to the first federation, not a lock tx
        BtcTransaction tx = new BtcTransaction(parameters);
        tx.addOutput(Coin.CENT, address1);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(BridgeUtils.isLockTx(tx, federations, btcContext, bridgeConstants));

        // Tx sending less than 1 btc to the second federation, not a lock tx
        tx = new BtcTransaction(parameters);
        tx.addOutput(Coin.CENT, address2);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(BridgeUtils.isLockTx(tx, federations, btcContext, bridgeConstants));

        // Tx sending less than 1 btc to both federations, not a lock tx
        tx = new BtcTransaction(parameters);
        tx.addOutput(Coin.CENT, address1);
        tx.addOutput(Coin.CENT, address2);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(BridgeUtils.isLockTx(tx, federations, btcContext, bridgeConstants));

        // Tx sending 1 btc to the first federation, but also spending from the first federation address, the typical release tx, not a lock tx.
        BtcTransaction tx2 = new BtcTransaction(parameters);
        tx2.addOutput(Coin.COIN, address1);
        TransactionInput txIn = new TransactionInput(parameters, tx2, new byte[]{}, new TransactionOutPoint(parameters, 0, Sha256Hash.ZERO_HASH));
        tx2.addInput(txIn);
        signWithNecessaryKeys(federation1, federation1Keys, txIn, tx2, bridgeConstants);
        assertFalse(BridgeUtils.isLockTx(tx2, federations, btcContext, bridgeConstants));

        // Tx sending 1 btc to the second federation, but also spending from the second federation address, the typical release tx, not a lock tx.
        tx2 = new BtcTransaction(parameters);
        tx2.addOutput(Coin.COIN, address2);
        txIn = new TransactionInput(parameters, tx2, new byte[]{}, new TransactionOutPoint(parameters, 0, Sha256Hash.ZERO_HASH));
        tx2.addInput(txIn);
        signWithNecessaryKeys(federation2, federation2Keys, txIn, tx2, bridgeConstants);
        assertFalse(BridgeUtils.isLockTx(tx2, federations, btcContext, bridgeConstants));

        // Tx sending 1 btc to both federations, but also spending from the first federation address, the typical release tx, not a lock tx.
        tx2 = new BtcTransaction(parameters);
        tx2.addOutput(Coin.COIN, address1);
        tx2.addOutput(Coin.COIN, address2);
        txIn = new TransactionInput(parameters, tx2, new byte[]{}, new TransactionOutPoint(parameters, 0, Sha256Hash.ZERO_HASH));
        tx2.addInput(txIn);
        signWithNecessaryKeys(federation1, federation1Keys, txIn, tx2, bridgeConstants);
        assertFalse(BridgeUtils.isLockTx(tx2, federations, btcContext, bridgeConstants));

        // Tx sending 1 btc to both federations, but also spending from the second federation address, the typical release tx, not a lock tx.
        tx2 = new BtcTransaction(parameters);
        tx2.addOutput(Coin.COIN, address1);
        tx2.addOutput(Coin.COIN, address2);
        txIn = new TransactionInput(parameters, tx2, new byte[]{}, new TransactionOutPoint(parameters, 0, Sha256Hash.ZERO_HASH));
        tx2.addInput(txIn);
        signWithNecessaryKeys(federation2, federation2Keys, txIn, tx2, bridgeConstants);
        assertFalse(BridgeUtils.isLockTx(tx2, federations, btcContext, bridgeConstants));

        // Tx sending 1 btc to the first federation, is a lock tx
        BtcTransaction tx3 = new BtcTransaction(parameters);
        tx3.addOutput(Coin.COIN, address1);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isLockTx(tx3, federations, btcContext, bridgeConstants));

        // Tx sending 1 btc to the second federation, is a lock tx
        tx3 = new BtcTransaction(parameters);
        tx3.addOutput(Coin.COIN, address2);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isLockTx(tx3, federations, btcContext, bridgeConstants));

        // Tx sending 1 btc to the both federations, is a lock tx
        tx3 = new BtcTransaction(parameters);
        tx3.addOutput(Coin.COIN, address1);
        tx3.addOutput(Coin.COIN, address2);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isLockTx(tx3, federations, btcContext, bridgeConstants));

        // Tx sending 50 btc to the first federation, is a lock tx
        BtcTransaction tx4 = new BtcTransaction(parameters);
        tx4.addOutput(Coin.FIFTY_COINS, address1);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isLockTx(tx4, federations, btcContext, bridgeConstants));

        // Tx sending 50 btc to the second federation, is a lock tx
        tx4 = new BtcTransaction(parameters);
        tx4.addOutput(Coin.FIFTY_COINS, address2);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isLockTx(tx4, federations, btcContext, bridgeConstants));

        // Tx sending 50 btc to the both federations, is a lock tx
        tx4 = new BtcTransaction(parameters);
        tx4.addOutput(Coin.FIFTY_COINS, address1);
        tx4.addOutput(Coin.FIFTY_COINS, address2);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isLockTx(tx4, federations, btcContext, bridgeConstants));
    }

    @Test
    public void testTxIsProcessable() {
        // Before Hardfork
        ActivationConfig.ForBlock actForBlock = mock(ActivationConfig.ForBlock.class);
        when(actForBlock.isActive(ConsensusRule.RSKIP143)).thenReturn(false);
        assertTrue(BridgeUtils.txIsProcessable(BtcLockSender.TxType.P2PKH, actForBlock));
        assertFalse(BridgeUtils.txIsProcessable(BtcLockSender.TxType.P2SHP2WPKH, actForBlock));
        assertFalse(BridgeUtils.txIsProcessable(BtcLockSender.TxType.P2SHMULTISIG, actForBlock));
        assertFalse(BridgeUtils.txIsProcessable(BtcLockSender.TxType.P2SHP2WSH, actForBlock));

        // After Hardfork
        when(actForBlock.isActive(ConsensusRule.RSKIP143)).thenReturn(true);
        assertTrue(BridgeUtils.txIsProcessable(BtcLockSender.TxType.P2PKH, actForBlock));
        assertTrue(BridgeUtils.txIsProcessable(BtcLockSender.TxType.P2SHP2WPKH, actForBlock));
        assertTrue(BridgeUtils.txIsProcessable(BtcLockSender.TxType.P2SHMULTISIG, actForBlock));
        assertTrue(BridgeUtils.txIsProcessable(BtcLockSender.TxType.P2SHP2WSH, actForBlock));
    }

    @Test
    public void testIsMigrationTx() {
        BridgeRegTestConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        NetworkParameters parameters = bridgeConstants.getBtcParams();
        Context btcContext = new Context(parameters);

        List<BtcECKey> activeFederationKeys = Stream.of(
            BtcECKey.fromPrivate(Hex.decode("fa01")),
            BtcECKey.fromPrivate(Hex.decode("fa02"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        Federation activeFederation = new Federation(FederationTestUtils.getFederationMembersWithBtcKeys(activeFederationKeys), Instant.ofEpochMilli(2000L), 2L, parameters);

        List<BtcECKey> retiringFederationKeys = Stream.of(
                BtcECKey.fromPrivate(Hex.decode("fb01")),
                BtcECKey.fromPrivate(Hex.decode("fb02")),
                BtcECKey.fromPrivate(Hex.decode("fb03"))
        ).sorted(BtcECKey.PUBKEY_COMPARATOR).collect(Collectors.toList());
        Federation retiringFederation = new Federation(FederationTestUtils.getFederationMembersWithBtcKeys(retiringFederationKeys), Instant.ofEpochMilli(1000L), 1L, parameters);

        Address activeFederationAddress = activeFederation.getAddress();

        BtcTransaction migrationTx = new BtcTransaction(parameters);
        migrationTx.addOutput(Coin.COIN, activeFederationAddress);
        TransactionInput migrationTxInput = new TransactionInput(parameters, migrationTx, new byte[]{}, new TransactionOutPoint(parameters, 0, Sha256Hash.ZERO_HASH));
        migrationTx.addInput(migrationTxInput);
        signWithNecessaryKeys(retiringFederation, retiringFederationKeys, migrationTxInput, migrationTx, bridgeConstants);
        assertThat(BridgeUtils.isMigrationTx(migrationTx, activeFederation, retiringFederation, btcContext, bridgeConstants), is(true));

        BtcTransaction toActiveFederationTx = new BtcTransaction(parameters);
        toActiveFederationTx.addOutput(Coin.COIN, activeFederationAddress);
        toActiveFederationTx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertThat(BridgeUtils.isMigrationTx(toActiveFederationTx, activeFederation, retiringFederation, btcContext, bridgeConstants), is(false));

        Address randomAddress = Address.fromBase58(
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST),
            "n3PLxDiwWqa5uH7fSbHCxS6VAjD9Y7Rwkj"
        );
        BtcTransaction fromRetiringFederationTx = new BtcTransaction(parameters);
        fromRetiringFederationTx.addOutput(Coin.COIN, randomAddress);
        TransactionInput fromRetiringFederationTxInput = new TransactionInput(parameters, fromRetiringFederationTx, new byte[]{}, new TransactionOutPoint(parameters, 0, Sha256Hash.ZERO_HASH));
        fromRetiringFederationTx.addInput(fromRetiringFederationTxInput);
        signWithNecessaryKeys(retiringFederation, retiringFederationKeys, fromRetiringFederationTxInput, fromRetiringFederationTx, bridgeConstants);
        assertThat(BridgeUtils.isMigrationTx(fromRetiringFederationTx, activeFederation, retiringFederation, btcContext, bridgeConstants), is(false));

        assertThat(BridgeUtils.isMigrationTx(migrationTx, activeFederation, null, btcContext, bridgeConstants), is(false));
    }

    @Test
    public void getAddressFromEthTransaction() {
        org.ethereum.core.Transaction tx = new org.ethereum.core.Transaction(TO_ADDRESS, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA, constants.getChainId());
        byte[] privKey = generatePrivKey();
        tx.sign(privKey);

        Address expectedAddress = BtcECKey.fromPrivate(privKey).toAddress(RegTestParams.get());
        Address result = BridgeUtils.recoverBtcAddressFromEthTransaction(tx, RegTestParams.get());

        assertEquals(expectedAddress, result);
    }

    @Test(expected = Exception.class)
    public void getAddressFromEthNotSignTransaction() {
        org.ethereum.core.Transaction tx = new org.ethereum.core.Transaction(TO_ADDRESS, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA, constants.getChainId());
        BridgeUtils.recoverBtcAddressFromEthTransaction(tx, RegTestParams.get());
    }

    @Test
    public void hasEnoughSignatures_two_signatures() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createReleaseTx(Arrays.asList(sign1, sign2), 1);
        Assert.assertTrue(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void hasEnoughSignatures_one_signature() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] MISSING_SIGNATURE = new byte[0];

        BtcTransaction btcTx = createReleaseTx(Arrays.asList(sign1, MISSING_SIGNATURE), 1);
        Assert.assertFalse(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void hasEnoughSignatures_no_signatures() {
        BtcTransaction btcTx = createReleaseTx(Collections.emptyList(), 1);
        Assert.assertFalse(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void hasEnoughSignatures_several_inputs_all_signed() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createReleaseTx(Arrays.asList(sign1, sign2), 3);
        Assert.assertTrue(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void hasEnoughSignatures_several_inputs_one_missing_signature() {
        // Create 1 signature
        byte[] sign1 = new byte[]{0x79};
        byte[] MISSING_SIGNATURE = new byte[0];

        BtcTransaction btcTx = createReleaseTx(Arrays.asList(sign1, MISSING_SIGNATURE), 3);
        Assert.assertFalse(BridgeUtils.hasEnoughSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_two_signatures() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createReleaseTx(Arrays.asList(sign1, sign2), 1);
        Assert.assertEquals(0, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_one_signature() {
        // Add 1 signature
        byte[] sign1 = new byte[]{0x79};
        byte[] MISSING_SIGNATURE = new byte[0];

        BtcTransaction btcTx = createReleaseTx(Arrays.asList(sign1, MISSING_SIGNATURE), 1);
        Assert.assertEquals(1, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_no_signatures() {
        // As no signature was added, missing signatures is 2
        BtcTransaction btcTx = createReleaseTx(Collections.emptyList(), 1);
        Assert.assertEquals(2, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_several_inputs_all_signed() {
        // Create 2 signatures
        byte[] sign1 = new byte[]{0x79};
        byte[] sign2 = new byte[]{0x78};

        BtcTransaction btcTx = createReleaseTx(Arrays.asList(sign1, sign2), 3);
        Assert.assertEquals(0, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    @Test
    public void countMissingSignatures_several_inputs_one_missing_signature() {
        // Create 1 signature
        byte[] sign1 = new byte[]{0x79};
        byte[] MISSING_SIGNATURE = new byte[0];

        BtcTransaction btcTx = createReleaseTx(Arrays.asList(sign1, MISSING_SIGNATURE), 3);
        Assert.assertEquals(1, BridgeUtils.countMissingSignatures(mock(Context.class), btcTx));
    }

    private BtcTransaction createReleaseTx(List<byte[]> signatures, int inputsToAdd) {
        // Setup
        BridgeRegTestConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        NetworkParameters btcParams = RegTestParams.get();
        Federation federation = bridgeConstants.getGenesisFederation();

        // Build prev btc tx
        BtcTransaction prevTx = new BtcTransaction(btcParams);
        TransactionOutput prevOut = new TransactionOutput(btcParams, prevTx, Coin.FIFTY_COINS, federation.getAddress());
        prevTx.addOutput(prevOut);

        // Build btc tx to be signed
        BtcTransaction btcTx = new BtcTransaction(btcParams);

        // Add inputs
        for (int i=0; i < inputsToAdd; i++) {
            btcTx.addInput(prevOut);
        }

        // Create federation redeem script
        Script redeemScript = createBaseRedeemScriptThatSpendsFromTheFederation(federation);
        Script redeemDataScript = RedeemData.of(federation.getBtcPublicKeys(), redeemScript).redeemScript;
        Script scriptSig;

        if (signatures.isEmpty()) {
            scriptSig = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
        } else {
            scriptSig = ScriptBuilder.createMultiSigInputScriptBytes(signatures, redeemDataScript.getProgram());
        }

        // Sign inputs
        for (int i=0; i < inputsToAdd; i++) {
            btcTx.getInput(i).setScriptSig(scriptSig);
        }

        TransactionOutput output = new TransactionOutput(btcParams, btcTx, Coin.COIN, new BtcECKey().toAddress(btcParams));
        btcTx.addOutput(output);

        return btcTx;
    }

    private byte[] generatePrivKey() {
        SecureRandom random = new SecureRandom();
        byte[] privKey = new byte[32];
        random.nextBytes(privKey);
        return privKey;
    }

    private void signWithNecessaryKeys(Federation federation, List<BtcECKey> privateKeys, TransactionInput txIn, BtcTransaction tx, BridgeRegTestConstants bridgeConstants) {
        signWithNKeys(federation, privateKeys, txIn, tx, bridgeConstants, federation.getNumberOfSignaturesRequired());
    }

    private void signWithNKeys(Federation federation, List<BtcECKey> privateKeys, TransactionInput txIn, BtcTransaction tx, BridgeRegTestConstants bridgeConstants, int numberOfSignatures) {
        Script redeemScript = createBaseRedeemScriptThatSpendsFromTheFederation(federation);
        Script inputScript = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(federation);
        txIn.setScriptSig(inputScript);

        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);

        for (int i = 0; i < numberOfSignatures; i++) {
            inputScript = signWithOneKey(federation, privateKeys, inputScript, sighash, i, bridgeConstants);
        }
        txIn.setScriptSig(inputScript);
    }


    private Script signWithOneKey(Federation federation, List<BtcECKey> privateKeys, Script inputScript, Sha256Hash sighash, int federatorIndex, BridgeRegTestConstants bridgeConstants) {
        BtcECKey federatorPrivKey = privateKeys.get(federatorIndex);
        BtcECKey federatorPublicKey = federation.getBtcPublicKeys().get(federatorIndex);

        BtcECKey.ECDSASignature sig = federatorPrivKey.sign(sighash);
        TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);

        int sigIndex = inputScript.getSigInsertionIndex(sighash, federatorPublicKey);
        inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSig.encodeToBitcoin(), sigIndex, 1, 1);
        return inputScript;
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
        NetworkParameters regTestParameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        Federation federation = new Federation(FederationTestUtils.getFederationMembersWithBtcKeys(Arrays.asList(
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")))),
                Instant.ofEpochMilli(5005L),
                0L,
                regTestParameters);
        Context mockedBtcContext = mock(Context.class);
        when(mockedBtcContext.getParams()).thenReturn(regTestParameters);

        Wallet wallet = BridgeUtils.getFederationNoSpendWallet(mockedBtcContext, federation);
        Assert.assertEquals(BridgeBtcWallet.class, wallet.getClass());
        assertIsWatching(federation.getAddress(), wallet, regTestParameters);
    }

    @Test
    public void getFederationSpendWallet() throws UTXOProviderException {
        NetworkParameters regTestParameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        Federation federation = new Federation(FederationTestUtils.getFederationMembersWithBtcKeys(Arrays.asList(
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5")))),
                Instant.ofEpochMilli(5005L),
                0L,
                regTestParameters);
        Context mockedBtcContext = mock(Context.class);
        when(mockedBtcContext.getParams()).thenReturn(regTestParameters);

        List<UTXO> mockedUtxos = new ArrayList<>();
        mockedUtxos.add(mock(UTXO.class));
        mockedUtxos.add(mock(UTXO.class));
        mockedUtxos.add(mock(UTXO.class));

        Wallet wallet = BridgeUtils.getFederationSpendWallet(mockedBtcContext, federation, mockedUtxos);
        Assert.assertEquals(BridgeBtcWallet.class, wallet.getClass());
        assertIsWatching(federation.getAddress(), wallet, regTestParameters);
        CoinSelector selector = wallet.getCoinSelector();
        Assert.assertEquals(RskAllowUnconfirmedCoinSelector.class, selector.getClass());
        UTXOProvider utxoProvider = wallet.getUTXOProvider();
        Assert.assertEquals(RskUTXOProvider.class, utxoProvider.getClass());
        Assert.assertEquals(mockedUtxos, utxoProvider.getOpenTransactionOutputs(Collections.emptyList()));
    }

    @Test
    public void testIsRelease() {
        NetworkParameters params = RegTestParams.get();
        BridgeRegTestConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        Federation federation = bridgeConstants.getGenesisFederation();
        List<BtcECKey> federationPrivateKeys = BridgeRegTestConstants.REGTEST_FEDERATION_PRIVATE_KEYS;
        Address randomAddress = new Address(params, Hex.decode("4a22c3c4cbb31e4d03b15550636762bda0baf85a"));

        BtcTransaction releaseTx1 = new BtcTransaction(params);
        releaseTx1.addOutput(Coin.COIN, randomAddress);
        TransactionInput releaseInput1 = new TransactionInput(params, releaseTx1, new byte[]{}, new TransactionOutPoint(params, 0, Sha256Hash.ZERO_HASH));
        releaseTx1.addInput(releaseInput1);
        signWithNecessaryKeys(federation, federationPrivateKeys, releaseInput1, releaseTx1, bridgeConstants);
        assertThat(BridgeUtils.isReleaseTx(releaseTx1, Collections.singletonList(federation)), is(true));

        BtcTransaction releaseTx2 = new BtcTransaction(params);
        releaseTx2.addOutput(Coin.COIN, randomAddress);
        TransactionInput releaseInput2 = new TransactionInput(params, releaseTx2, new byte[]{}, new TransactionOutPoint(params, 0, Sha256Hash.ZERO_HASH));
        releaseTx2.addInput(releaseInput2);
        signWithNKeys(federation, federationPrivateKeys, releaseInput2, releaseTx2, bridgeConstants, 1);
        assertThat(BridgeUtils.isReleaseTx(releaseTx2, Collections.singletonList(federation)), is(false));
    }

    @Test
    public void testChangeBetweenFederations() {
        NetworkParameters params = RegTestParams.get();
        BridgeRegTestConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        Address randomAddress = new Address(params, Hex.decode("4a22c3c4cbb31e4d03b15550636762bda0baf85a"));
        Context btcContext = new Context(params);

        List<BtcECKey> federation1Keys = Stream.of("fa01", "fa02")
                .map(Hex::decode)
                .map(BtcECKey::fromPrivate)
                .sorted(BtcECKey.PUBKEY_COMPARATOR)
                .collect(Collectors.toList());
        Federation federation1 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation1Keys),
                Instant.ofEpochMilli(1000L), 0L, params
        );

        List<BtcECKey> federation2Keys = Stream.of("fb01", "fb02", "fb03")
                .map(Hex::decode)
                .map(BtcECKey::fromPrivate)
                .sorted(BtcECKey.PUBKEY_COMPARATOR)
                .collect(Collectors.toList());
        Federation federation2 = new Federation(
                FederationTestUtils.getFederationMembersWithBtcKeys(federation2Keys),
                Instant.ofEpochMilli(2000L), 0L, params
        );

        Address federation2Address = federation2.getAddress();

        List<Federation> federations = Arrays.asList(federation1, federation2);

        BtcTransaction releaseWithChange = new BtcTransaction(params);
        releaseWithChange.addOutput(Coin.COIN, randomAddress);
        releaseWithChange.addOutput(Coin.COIN, federation2Address);
        TransactionInput releaseFromFederation2 = new TransactionInput(params, releaseWithChange, new byte[]{}, new TransactionOutPoint(params, 0, Sha256Hash.ZERO_HASH));
        releaseWithChange.addInput(releaseFromFederation2);
        signWithNecessaryKeys(federation2, federation2Keys, releaseFromFederation2, releaseWithChange, bridgeConstants);
        assertThat(BridgeUtils.isLockTx(releaseWithChange, federations, btcContext, bridgeConstants), is(false));
        assertThat(BridgeUtils.isReleaseTx(releaseWithChange, federations), is(true));
    }

    @Test
    public void testIsContractTx() {
        Assert.assertFalse(BridgeUtils.isContractTx(new Transaction((byte[]) null, null, null, null, null, null)));
        Assert.assertTrue(BridgeUtils.isContractTx(new org.ethereum.vm.program.InternalTransaction(null, 0, 0, null, null, null, null, null, null, null, null)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsContractTxInvalidTx() {
        new ImmutableTransaction(null);
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void getCoinFromBigInteger_bigger_than_long_value() {
        BridgeUtils.getCoinFromBigInteger(new BigInteger("9223372036854775808"));
    }

    @Test(expected = BridgeIllegalArgumentException.class)
    public void getCoinFromBigInteger_null_value() {
        BridgeUtils.getCoinFromBigInteger(null);
    }

    @Test
    public void getCoinFromBigInteger() {
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
        Assert.assertTrue(BridgeUtils.validateHeightAndConfirmations(2, 5, 3, Sha256Hash.of(Hex.decode("ab"))));
    }

    @Test(expected = Exception.class)
    public void calculateMerkleRoot_invalid_pmt() {
        NetworkParameters networkParameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        BridgeUtils.calculateMerkleRoot(networkParameters, Hex.decode("ab"), null);
    }

    @Test
    public void calculateMerkleRoot_hashes_not_in_pmt() {
        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(PegTestUtils.createHash());

        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        NetworkParameters networkParameters = bridgeConstants.getBtcParams();

        BtcTransaction tx = new BtcTransaction(networkParameters);
        PartialMerkleTree pmt = new PartialMerkleTree(networkParameters, bits, hashes, 1);

        Assert.assertNull(BridgeUtils.calculateMerkleRoot(networkParameters, pmt.bitcoinSerialize(), tx.getHash()));
    }

    @Test
    public void calculateMerkleRoot_hashes_in_pmt() {
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        NetworkParameters networkParameters = bridgeConstants.getBtcParams();

        BtcTransaction tx = new BtcTransaction(networkParameters);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(networkParameters, bits, hashes, 1);

        Assert.assertEquals(Sha256Hash.wrap("d21633ba23f70118185227be58a63527675641ad37967e2aa461559f577aec43"), BridgeUtils.calculateMerkleRoot(networkParameters, pmt.bitcoinSerialize(), tx.getHash()));
    }

    @Test(expected = VerificationException.class)
    public void validateInputsCount_active_rskip() {
        BridgeUtils.validateInputsCount(Hex.decode("00000000000100"), true);
    }

    @Test(expected = VerificationException.class)
    public void validateInputsCount_inactive_rskip() {
        BridgeConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        NetworkParameters networkParameters = bridgeConstants.getBtcParams();

        BtcTransaction tx = new BtcTransaction(networkParameters);

        BridgeUtils.validateInputsCount(tx.bitcoinSerialize(), false);
    }

    @Test
    public void isInputSignedByThisFederator_isSigned() {
        NetworkParameters params = RegTestParams.get();

        // Arrange
        BtcECKey federator1Key = new BtcECKey();
        BtcECKey federator2Key = new BtcECKey();
        Federation federation = new Federation(
                FederationMember.getFederationMembersFromKeys(Arrays.asList(federator1Key, federator2Key)),
                Instant.now(),
                0,
                params
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction tx = new BtcTransaction(params);
        TransactionInput txInput = new TransactionInput(
                params,
                tx,
                new byte[]{},
                new TransactionOutPoint(params, 0, Sha256Hash.ZERO_HASH)
        );

        // Create script to be signed by federation members
        Script inputScript = createBaseInputScriptThatSpendsFromTheFederation(federation);
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
        NetworkParameters params = RegTestParams.get();

        // Arrange
        BtcECKey federator1Key = new BtcECKey();
        BtcECKey federator2Key = new BtcECKey();
        Federation federation = new Federation(
                FederationMember.getFederationMembersFromKeys(Arrays.asList(federator1Key, federator2Key)),
                Instant.now(),
                0,
                params
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction tx = new BtcTransaction(params);
        TransactionInput txInput = new TransactionInput(
                params,
                tx,
                new byte[]{},
                new TransactionOutPoint(params, 0, Sha256Hash.ZERO_HASH)
        );

        // Create script to be signed by federation members
        Script inputScript = createBaseInputScriptThatSpendsFromTheFederation(federation);
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
        NetworkParameters params = RegTestParams.get();

        // Arrange
        BtcECKey federator1Key = new BtcECKey();
        BtcECKey federator2Key = new BtcECKey();
        Federation federation = new Federation(
                FederationMember.getFederationMembersFromKeys(Arrays.asList(federator1Key, federator2Key)),
                Instant.now(),
                0,
                params
        );

        // Create a tx from the Fed to a random btc address
        BtcTransaction tx = new BtcTransaction(params);
        TransactionInput txInput = new TransactionInput(
                params,
                tx,
                new byte[]{},
                new TransactionOutPoint(params, 0, Sha256Hash.ZERO_HASH)
        );

        // Create script to be signed by federation members
        Script inputScript = createBaseInputScriptThatSpendsFromTheFederation(federation);
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

    private Script createBaseInputScriptThatSpendsFromTheFederation(Federation federation) {
        Script scriptPubKey = federation.getP2SHScript();
        Script redeemScript = federation.getRedeemScript();
        RedeemData redeemData = RedeemData.of(federation.getBtcPublicKeys(), redeemScript);
        Script inputScript = scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);

        return inputScript;
    }
}
