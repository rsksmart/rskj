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

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.params.RegTestParams;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.wallet.CoinSelector;
import co.rsk.bitcoinj.wallet.Wallet;
import co.rsk.config.RskSystemProperties;
import co.rsk.peg.bitcoin.RskAllowUnconfirmedCoinSelector;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.blockchain.RegTestConfig;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Genesis;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BridgeUtilsTest {

    private static final Logger logger = LoggerFactory.getLogger("test");

    private static final String TO_ADDRESS = "00000000000000000006";
    private static final BigInteger AMOUNT = new BigInteger("1");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";

    @Test
    public void testIsLock() throws Exception {
        // Lock is for the genesis federation ATM
        NetworkParameters params = RegTestParams.get();
        Context btcContext = new Context(params);
        BridgeRegTestConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        Federation federation = bridgeConstants.getGenesisFederation();
        Wallet wallet = new BridgeBtcWallet(btcContext, federation);
        wallet.addWatchedAddress(federation.getAddress(), federation.getCreationTime().toEpochMilli());
        Address address = federation.getAddress();

        // Tx sending less than 1 btc to the federation, not a lock tx
        BtcTransaction tx = new BtcTransaction(params);
        tx.addOutput(Coin.CENT, address);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(BridgeUtils.isLockTx(tx, federation, wallet, bridgeConstants));

        // Tx sending 1 btc to the federation, but also spending from the federation addres, the typical release tx, not a lock tx.
        BtcTransaction tx2 = new BtcTransaction(params);
        tx2.addOutput(Coin.COIN, address);
        TransactionInput txIn = new TransactionInput(params, tx2, new byte[]{}, new TransactionOutPoint(params, 0, Sha256Hash.ZERO_HASH));
        tx2.addInput(txIn);
        signWithTwoKeys(txIn, tx2, bridgeConstants);
        assertFalse(BridgeUtils.isLockTx(tx2, federation, wallet, bridgeConstants));

        // Tx sending 1 btc to the federation, is a lock tx
        BtcTransaction tx3 = new BtcTransaction(params);
        tx3.addOutput(Coin.COIN, address);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isLockTx(tx3, federation, wallet, bridgeConstants));

        // Tx sending 50 btc to the federation, is a lock tx
        BtcTransaction tx4 = new BtcTransaction(params);
        tx4.addOutput(Coin.FIFTY_COINS, address);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isLockTx(tx4, federation, wallet, bridgeConstants));

    }

    @Test
    public void getAddressFromEthTransaction() {
        org.ethereum.core.Transaction tx = org.ethereum.core.Transaction.create(TO_ADDRESS, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        byte[] privKey = generatePrivKey();
        tx.sign(privKey);

        Address expectedAddress = BtcECKey.fromPrivate(privKey).toAddress(RegTestParams.get());
        Address result = BridgeUtils.recoverBtcAddressFromEthTransaction(tx, RegTestParams.get());

        assertEquals(expectedAddress, result);
    }

    @Test(expected = Exception.class)
    public void getAddressFromEthNotSignTransaction() {
        org.ethereum.core.Transaction tx = org.ethereum.core.Transaction.create(TO_ADDRESS, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);
        BridgeUtils.recoverBtcAddressFromEthTransaction(tx, RegTestParams.get());
    }

    private byte[] generatePrivKey() {
        SecureRandom random = new SecureRandom();
        byte[] privKey = new byte[32];
        random.nextBytes(privKey);
        return privKey;
    }
    private void signWithTwoKeys(TransactionInput txIn, BtcTransaction tx, BridgeRegTestConstants bridgeConstants) {
        // Signing is on the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        Script redeemScript = PegTestUtils.createBaseRedeemScriptThatSpendsFromTheFederation(federation);
        Script inputScript = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(bridgeConstants);
        txIn.setScriptSig(inputScript);

        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);

        inputScript = signWithOneKey(inputScript, sighash, 0, bridgeConstants);
        inputScript = signWithOneKey(inputScript, sighash, 1, bridgeConstants);
        txIn.setScriptSig(inputScript);
    }

    private Script signWithOneKey(Script inputScript, Sha256Hash sighash, int federatorIndex, BridgeRegTestConstants bridgeConstants) {
        // Federation is the genesis federation ATM
        Federation federation = bridgeConstants.getGenesisFederation();
        BtcECKey federatorPrivKey = bridgeConstants.getFederatorPrivateKeys().get(federatorIndex);
        BtcECKey federatorPublicKey = federation.getPublicKeys().get(federatorIndex);

        BtcECKey.ECDSASignature sig = federatorPrivKey.sign(sighash);
        TransactionSignature txSig = new TransactionSignature(sig, BtcTransaction.SigHash.ALL, false);

        int sigIndex = inputScript.getSigInsertionIndex(sighash, federatorPublicKey);
        inputScript = ScriptBuilder.updateScriptWithSignature(inputScript, txSig.encodeToBitcoin(), sigIndex, 1, 1);
        return inputScript;
    }

    @Test
    public void isFreeBridgeTxTrue() {
        isFreeBridgeTx(true, PrecompiledContracts.BRIDGE_ADDR, new UnitTestBlockchainNetConfig(), BridgeRegTestConstants.getInstance().getFederatorPrivateKeys().get(0).getPrivKeyBytes());
    }

    @Test
    public void isFreeBridgeTxOtherContract() {
        isFreeBridgeTx(false, PrecompiledContracts.IDENTITY_ADDR, new UnitTestBlockchainNetConfig(), BridgeRegTestConstants.getInstance().getFederatorPrivateKeys().get(0).getPrivKeyBytes());
    }

    @Test
    public void isFreeBridgeTxFreeTxDisabled() {
        isFreeBridgeTx(false, PrecompiledContracts.BRIDGE_ADDR, new RegTestConfig() {
            @Override
            public boolean areBridgeTxsFree() {
                return false;
            }
        }, BridgeRegTestConstants.getInstance().getFederatorPrivateKeys().get(0).getPrivKeyBytes());
    }

    @Test @Ignore
    public void isFreeBridgeTxNonFederatorKey() {
        isFreeBridgeTx(false, PrecompiledContracts.BRIDGE_ADDR, new UnitTestBlockchainNetConfig(), new BtcECKey().getPrivKeyBytes());
    }

    @Test
    public void getFederationNoSpendWallet() {
        NetworkParameters regTestParameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        Federation federation = new Federation(1, Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        }), Instant.ofEpochMilli(5005L), regTestParameters);
        Context mockedBtcContext = mock(Context.class);
        when(mockedBtcContext.getParams()).thenReturn(regTestParameters);

        Wallet wallet = BridgeUtils.getFederationNoSpendWallet(mockedBtcContext, federation);
        Assert.assertEquals(BridgeBtcWallet.class, wallet.getClass());
        assertIsWatching(federation.getAddress(), wallet, regTestParameters);
    }

    @Test
    public void getFederationSpendWallet() throws UTXOProviderException {
        NetworkParameters regTestParameters = NetworkParameters.fromID(NetworkParameters.ID_REGTEST);
        Federation federation = new Federation(1, Arrays.asList(new BtcECKey[]{
                BtcECKey.fromPublicOnly(Hex.decode("036bb9eab797eadc8b697f0e82a01d01cabbfaaca37e5bafc06fdc6fdd38af894a")),
                BtcECKey.fromPublicOnly(Hex.decode("031da807c71c2f303b7f409dd2605b297ac494a563be3b9ca5f52d95a43d183cc5"))
        }), Instant.ofEpochMilli(5005L), regTestParameters);
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

    private void assertIsWatching(Address address, Wallet wallet, NetworkParameters parameters) {
        List<Script> watchedScripts = wallet.getWatchedScripts();
        Assert.assertEquals(1, watchedScripts.size());
        Script watchedScript = watchedScripts.get(0);
        Assert.assertTrue(watchedScript.isPayToScriptHash());
        Assert.assertEquals(address.toString(), watchedScript.getToAddress(parameters).toString());
    }


    private void isFreeBridgeTx(boolean expected, String destinationAddress, BlockchainNetConfig config, byte[] privKeyBytes) {
        BlockchainNetConfig blockchainNetConfigOriginal = RskSystemProperties.CONFIG.getBlockchainConfig();
        RskSystemProperties.CONFIG.setBlockchainConfig(config);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);

        org.ethereum.core.Transaction rskTx = CallTransaction.createCallTransaction(
                0,
                1,
                1,
                destinationAddress,
                0,
                Bridge.UPDATE_COLLECTIONS);
        rskTx.sign(privKeyBytes);

        org.ethereum.core.Block rskExecutionBlock = BlockGenerator.createChildBlock(Genesis.getInstance(RskSystemProperties.CONFIG));
        bridge.init(rskTx, rskExecutionBlock, null, null, null, null);
        Assert.assertEquals(expected, BridgeUtils.isFreeBridgeTx(rskTx, rskExecutionBlock.getNumber()));

        RskSystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }
}
