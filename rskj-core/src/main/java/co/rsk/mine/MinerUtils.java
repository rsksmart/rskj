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

package co.rsk.mine;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.config.RskMiningConstants;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.PendingStateImpl;
import co.rsk.remasc.RemascTransaction;
import com.google.common.collect.Lists;
import org.ethereum.core.PendingState;
import org.ethereum.core.Repository;
import org.ethereum.rpc.TypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.Arrays;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by oscar on 26/09/2016.
 */
public class MinerUtils {

    private static final Logger logger = LoggerFactory.getLogger("minerserver");

    public static co.rsk.bitcoinj.core.BtcTransaction getBitcoinMergedMiningCoinbaseTransaction(co.rsk.bitcoinj.core.NetworkParameters params, MinerWork work) {
        return getBitcoinMergedMiningCoinbaseTransaction(params, TypeConverter.stringHexToByteArray(work.getBlockHashForMergedMining()));
    }

    public static co.rsk.bitcoinj.core.BtcTransaction getBitcoinMergedMiningCoinbaseTransaction(co.rsk.bitcoinj.core.NetworkParameters params, byte[] blockHashForMergedMining) {
        co.rsk.bitcoinj.core.BtcTransaction coinbaseTransaction = new co.rsk.bitcoinj.core.BtcTransaction(params);
        //Add a random number of random bytes before the RSK tag
        SecureRandom random = new SecureRandom();
        byte[] prefix = new byte[random.nextInt(1000)];
        random.nextBytes(prefix);
        byte[] bytes = Arrays.concatenate(prefix, RskMiningConstants.RSK_TAG, blockHashForMergedMining);
        // Add the Tag to the scriptSig of first input
        co.rsk.bitcoinj.core.TransactionInput ti = new co.rsk.bitcoinj.core.TransactionInput(params, coinbaseTransaction, bytes);
        coinbaseTransaction.addInput(ti);
        ByteArrayOutputStream scriptPubKeyBytes = new ByteArrayOutputStream();
        co.rsk.bitcoinj.core.BtcECKey key = new co.rsk.bitcoinj.core.BtcECKey();
        try {
            co.rsk.bitcoinj.script.Script.writeBytes(scriptPubKeyBytes, key.getPubKey());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        scriptPubKeyBytes.write(co.rsk.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG);
        coinbaseTransaction.addOutput(new co.rsk.bitcoinj.core.TransactionOutput(params, coinbaseTransaction, co.rsk.bitcoinj.core.Coin.valueOf(50, 0), scriptPubKeyBytes.toByteArray()));
        return coinbaseTransaction;
    }

    public static co.rsk.bitcoinj.core.BtcTransaction getBitcoinMergedMiningCoinbaseTransactionWithTwoTags(
            co.rsk.bitcoinj.core.NetworkParameters params,
            MinerWork work,
            MinerWork work2) {
        return getBitcoinMergedMiningCoinbaseTransactionWithTwoTags(
                params,
                TypeConverter.stringHexToByteArray(work.getBlockHashForMergedMining()),
                TypeConverter.stringHexToByteArray(work2.getBlockHashForMergedMining()));
    }

    public static co.rsk.bitcoinj.core.BtcTransaction getBitcoinMergedMiningCoinbaseTransactionWithTwoTags(
            co.rsk.bitcoinj.core.NetworkParameters params,
            byte[] blockHashForMergedMining1,
            byte[] blockHashForMergedMining2) {
        co.rsk.bitcoinj.core.BtcTransaction coinbaseTransaction = new co.rsk.bitcoinj.core.BtcTransaction(params);
        //Add a random number of random bytes before the RSK tag
        SecureRandom random = new SecureRandom();
        byte[] prefix = new byte[random.nextInt(1000)];
        random.nextBytes(prefix);

        byte[] bytes0 = Arrays.concatenate(RskMiningConstants.RSK_TAG, blockHashForMergedMining1);
        // addsecond tag
        byte[] bytes1 = Arrays.concatenate(bytes0, RskMiningConstants.RSK_TAG, blockHashForMergedMining2);

        co.rsk.bitcoinj.core.TransactionInput ti = new co.rsk.bitcoinj.core.TransactionInput(params, coinbaseTransaction, prefix);
        coinbaseTransaction.addInput(ti);
        ByteArrayOutputStream scriptPubKeyBytes = new ByteArrayOutputStream();
        co.rsk.bitcoinj.core.BtcECKey key = new co.rsk.bitcoinj.core.BtcECKey();
        try {
            co.rsk.bitcoinj.script.Script.writeBytes(scriptPubKeyBytes, key.getPubKey());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        scriptPubKeyBytes.write(co.rsk.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG);
        coinbaseTransaction.addOutput(new co.rsk.bitcoinj.core.TransactionOutput(params, coinbaseTransaction, co.rsk.bitcoinj.core.Coin.valueOf(50, 0), scriptPubKeyBytes.toByteArray()));
        // add opreturn output with two tags
        ByteArrayOutputStream output2Bytes = new ByteArrayOutputStream();
        output2Bytes.write(co.rsk.bitcoinj.script.ScriptOpCodes.OP_RETURN);

        try {
            co.rsk.bitcoinj.script.Script.writeBytes(output2Bytes, bytes1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        coinbaseTransaction.addOutput(
                new co.rsk.bitcoinj.core.TransactionOutput(params, coinbaseTransaction, co.rsk.bitcoinj.core.Coin.valueOf(1), output2Bytes.toByteArray()));

        return coinbaseTransaction;
    }

    public static co.rsk.bitcoinj.core.BtcBlock getBitcoinMergedMiningBlock(co.rsk.bitcoinj.core.NetworkParameters params,
                                                                      co.rsk.bitcoinj.core.BtcTransaction coinbaseTransaction) {
        List<BtcTransaction> transactions = Lists.newArrayList(coinbaseTransaction);
        co.rsk.bitcoinj.core.Sha256Hash prevBlockHash = co.rsk.bitcoinj.core.Sha256Hash.ZERO_HASH;
        long time = System.currentTimeMillis() / 1000;
        long difficultyTarget = co.rsk.bitcoinj.core.Utils.encodeCompactBits(params.getMaxTarget());
        return new co.rsk.bitcoinj.core.BtcBlock(params, params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT), prevBlockHash, null, time, difficultyTarget, 0, transactions);
    }

    public List<org.ethereum.core.Transaction> getAllTransactions(PendingState pendingState) {
        //TODO: optimize this by considering GasPrice (order by GasPrice/Nonce)
        PendingStateImpl.TransactionSortedSet ret = new PendingStateImpl.TransactionSortedSet();

        List<org.ethereum.core.Transaction> pendingTransactions = new LinkedList<>(pendingState.getPendingTransactions());
        List<org.ethereum.core.Transaction> wireTransactions = new LinkedList<>(pendingState.getWireTransactions());

        ret.addAll(pendingTransactions);
        ret.addAll(wireTransactions);

        return new LinkedList<>(ret);
    }

    public List<org.ethereum.core.Transaction> filterTransactions(List<org.ethereum.core.Transaction> txsToRemove, List<org.ethereum.core.Transaction> txs, Map<RskAddress, BigInteger> accountNonces, Repository originalRepo, BigInteger minGasPrice) {
        List<org.ethereum.core.Transaction> txsResult = new ArrayList<>();
        for (org.ethereum.core.Transaction tx : txs) {
            try {
                String hexHash = Hex.toHexString(tx.getHash());
                Coin txValue = tx.getValue();
                BigInteger txNonce = new BigInteger(1, tx.getNonce());
                RskAddress txSender = tx.getSender();
                logger.debug("Examining tx={} sender: {} value: {} nonce: {}", hexHash, txSender, txValue, txNonce);


                BigInteger expectedNonce;

                if (accountNonces.containsKey(txSender)) {
                    expectedNonce = new BigInteger(1, accountNonces.get(txSender).toByteArray()).add(BigInteger.ONE);
                } else {
                    expectedNonce = originalRepo.getNonce(txSender);
                }

                if (!(tx instanceof RemascTransaction) && tx.getGasPrice().asBigInteger().compareTo(minGasPrice) < 0) {
                    logger.warn("Rejected tx={} because of low gas account {}, removing tx from pending state.", hexHash, txSender);

                    txsToRemove.add(tx);
                    continue;
                }

                if (!expectedNonce.equals(txNonce)) {
                    logger.warn("Invalid nonce, expected {}, found {}, tx={}", expectedNonce, txNonce, hexHash);
                    continue;
                }

                accountNonces.put(txSender, txNonce);

                logger.debug("Accepted tx={} sender: {} value: {} nonce: {}", hexHash, txSender, txValue, txNonce);
            } catch (Exception e) {
                // Txs that can't be selected by any reason should be removed from pending state
                String hash = null == tx.getHash() ? "" : Hex.toHexString(tx.getHash());
                logger.warn(String.format("Error when processing tx=%s", hash), e);
                if (txsToRemove != null) {
                    txsToRemove.add(tx);
                } else {
                    logger.error("Can't remove invalid txs from pending state.");
                }
                continue;
            }

            txsResult.add(tx);
        }

        logger.debug("Ending getTransactions {}", txsResult.size());

        return txsResult;
    }
}
