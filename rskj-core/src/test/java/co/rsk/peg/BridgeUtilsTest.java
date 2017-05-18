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
import co.rsk.bitcoinj.wallet.Wallet;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.SystemProperties;
import org.ethereum.config.blockchain.RegTestConfig;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Genesis;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.SecureRandom;

import static org.junit.Assert.*;

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
        NetworkParameters params = RegTestParams.get();
        Context btcContext = new Context(params);
        BridgeRegTestConstants bridgeConstants = BridgeRegTestConstants.getInstance();
        Wallet wallet = new BridgeBtcWallet(btcContext, bridgeConstants);
        wallet.addWatchedAddress(bridgeConstants.getFederationAddress(), bridgeConstants.getFederationAddressCreationTime());
        Address address = bridgeConstants.getFederationAddress();

        // Tx sending less than 1 btc to the federation, not a lock tx
        BtcTransaction tx = new BtcTransaction(params);
        tx.addOutput(Coin.CENT, address);
        tx.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertFalse(BridgeUtils.isLockTx(tx, wallet, bridgeConstants));

        // Tx sending 1 btc to the federation, but also spending from the federation addres, the typical release tx, not a lock tx.
        BtcTransaction tx2 = new BtcTransaction(params);
        tx2.addOutput(Coin.COIN, address);
        TransactionInput txIn = new TransactionInput(params, tx2, new byte[]{}, new TransactionOutPoint(params, 0, Sha256Hash.ZERO_HASH));
        tx2.addInput(txIn);
        signWithTwoKeys(txIn, tx2, bridgeConstants);
        assertFalse(BridgeUtils.isLockTx(tx2, wallet, bridgeConstants));

        // Tx sending 1 btc to the federation, is a lock tx
        BtcTransaction tx3 = new BtcTransaction(params);
        tx3.addOutput(Coin.COIN, address);
        tx3.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isLockTx(tx3, wallet, bridgeConstants));

        // Tx sending 50 btc to the federation, is a lock tx
        BtcTransaction tx4 = new BtcTransaction(params);
        tx4.addOutput(Coin.FIFTY_COINS, address);
        tx4.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[]{}));
        assertTrue(BridgeUtils.isLockTx(tx4, wallet, bridgeConstants));

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
        Script redeemScript = PegTestUtils.createBaseRedeemScriptThatSpendsFromTheFederation(bridgeConstants);
        Script inputScript = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(bridgeConstants);
        txIn.setScriptSig(inputScript);

        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);

        inputScript = signWithOneKey(inputScript, sighash, 0, bridgeConstants);
        inputScript = signWithOneKey(inputScript, sighash, 1, bridgeConstants);
        txIn.setScriptSig(inputScript);
    }

    private Script signWithOneKey(Script inputScript, Sha256Hash sighash, int federatorIndex, BridgeRegTestConstants bridgeConstants) {
        BtcECKey federatorPrivKey = bridgeConstants.getFederatorPrivateKeys().get(federatorIndex);
        BtcECKey federatorPublicKey = bridgeConstants.getFederatorPublicKeys().get(federatorIndex);

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

    @Test
    public void isFreeBridgeTxNonFederatorKey() {
        isFreeBridgeTx(false, PrecompiledContracts.BRIDGE_ADDR, new UnitTestBlockchainNetConfig(), new BtcECKey().getPrivKeyBytes());
    }



    private void isFreeBridgeTx(boolean expected, String destinationAddress, BlockchainNetConfig config, byte[] privKeyBytes) {
        BlockchainNetConfig blockchainNetConfigOriginal = SystemProperties.CONFIG.getBlockchainConfig();
        SystemProperties.CONFIG.setBlockchainConfig(config);

        Bridge bridge = new Bridge(PrecompiledContracts.BRIDGE_ADDR);

        org.ethereum.core.Transaction rskTx = CallTransaction.createCallTransaction(
                0,
                1,
                1,
                destinationAddress,
                0,
                Bridge.UPDATE_COLLECTIONS);
        rskTx.sign(privKeyBytes);

        org.ethereum.core.Block rskExecutionBlock = BlockGenerator.createChildBlock(Genesis.getInstance(SystemProperties.CONFIG));
        bridge.init(rskTx, rskExecutionBlock, null, null, null, null);
        Assert.assertEquals(expected, BridgeUtils.isFreeBridgeTx(rskTx, rskExecutionBlock.getNumber()));

        SystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }
}
