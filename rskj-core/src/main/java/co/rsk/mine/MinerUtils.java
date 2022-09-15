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
import co.rsk.core.bc.PendingState;
import co.rsk.crypto.Keccak256;
import co.rsk.db.RepositorySnapshot;
import co.rsk.remasc.RemascTransaction;
import co.rsk.util.HexUtils;
import co.rsk.validators.TxGasPriceCap;
import org.bouncycastle.util.Arrays;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class MinerUtils {
    private static final Logger logger = LoggerFactory.getLogger("minerserver");

    private final SignatureCache signatureCache;

    public MinerUtils(SignatureCache signatureCache) {
        this.signatureCache = signatureCache;
    }

    public static co.rsk.bitcoinj.core.BtcTransaction getBitcoinMergedMiningCoinbaseTransaction(co.rsk.bitcoinj.core.NetworkParameters params, MinerWork work) {
        return getBitcoinMergedMiningCoinbaseTransaction(params, HexUtils.stringHexToByteArray(work.getBlockHashForMergedMining()));
    }

    public static co.rsk.bitcoinj.core.BtcTransaction getBitcoinMergedMiningCoinbaseTransaction(co.rsk.bitcoinj.core.NetworkParameters params, byte[] blockHashForMergedMining) {
        SecureRandom random = new SecureRandom();
        byte[] prefix = new byte[random.nextInt(1000)];
        random.nextBytes(prefix);
        byte[] bytes = Arrays.concatenate(prefix, RskMiningConstants.RSK_TAG, blockHashForMergedMining);

        return getBitcoinCoinbaseTransaction(params, bytes);
    }

    public static co.rsk.bitcoinj.core.BtcTransaction getBitcoinCoinbaseTransaction(co.rsk.bitcoinj.core.NetworkParameters params, byte[] additionalData) {

        co.rsk.bitcoinj.core.BtcTransaction coinbaseTransaction = new co.rsk.bitcoinj.core.BtcTransaction(params);

        // Add the data to the scriptSig of first input
        co.rsk.bitcoinj.core.TransactionInput ti = new co.rsk.bitcoinj.core.TransactionInput(params, coinbaseTransaction, new byte[0]);
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

        coinbaseTransaction.addOutput(new co.rsk.bitcoinj.core.TransactionOutput(params, coinbaseTransaction, co.rsk.bitcoinj.core.Coin.valueOf(0), additionalData));

        return coinbaseTransaction;
    }

    public static co.rsk.bitcoinj.core.BtcTransaction getBitcoinMergedMiningCoinbaseTransactionWithTwoTags(
            co.rsk.bitcoinj.core.NetworkParameters params,
            MinerWork work,
            MinerWork work2) {
        return getBitcoinMergedMiningCoinbaseTransactionWithTwoTags(
                params,
                HexUtils.stringHexToByteArray(work.getBlockHashForMergedMining()),
                HexUtils.stringHexToByteArray(work2.getBlockHashForMergedMining()));
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

    public static co.rsk.bitcoinj.core.BtcBlock getBitcoinMergedMiningBlock(co.rsk.bitcoinj.core.NetworkParameters params, BtcTransaction transaction) {
        return getBitcoinMergedMiningBlock(params, Collections.singletonList(transaction));
    }

    public static co.rsk.bitcoinj.core.BtcBlock getBitcoinMergedMiningBlock(co.rsk.bitcoinj.core.NetworkParameters params, List<BtcTransaction> transactions) {
        co.rsk.bitcoinj.core.Sha256Hash prevBlockHash = co.rsk.bitcoinj.core.Sha256Hash.ZERO_HASH;
        long time = System.currentTimeMillis() / 1000L;
        long difficultyTarget = co.rsk.bitcoinj.core.Utils.encodeCompactBits(params.getMaxTarget());
        return new co.rsk.bitcoinj.core.BtcBlock(params, params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT), prevBlockHash, null, time, difficultyTarget, 0, transactions);
    }

    /**
     * Takes in a proofBuilderFunction (e.g. buildFromTxHashes)
     * and executes it on the builder corresponding to this block number.
     */
    public static byte[] buildMerkleProof(
            ActivationConfig activationConfig,
            Function<MerkleProofBuilder, byte[]> proofBuilderFunction,
            long blockNumber) {
        if (activationConfig.isActive(ConsensusRule.RSKIP92, blockNumber)) {
            return proofBuilderFunction.apply(new Rskip92MerkleProofBuilder());
        } else {
            return proofBuilderFunction.apply(new GenesisMerkleProofBuilder());
        }
    }

    public List<org.ethereum.core.Transaction> getAllTransactions(TransactionPool transactionPool) {

        List<Transaction> txs = transactionPool.getPendingTransactions();

        return PendingState.sortByPriceTakingIntoAccountSenderAndNonce(txs, signatureCache);
    }

    public List<org.ethereum.core.Transaction> filterTransactions(List<Transaction> txsToRemove, List<Transaction> txs, Map<RskAddress, BigInteger> accountNonces, RepositorySnapshot originalRepo, Coin minGasPrice, boolean isRskip252Enabled) {
        List<org.ethereum.core.Transaction> txsResult = new ArrayList<>();
        for (org.ethereum.core.Transaction tx : txs) {
            try {
                Keccak256 hash = tx.getHash();
                Coin txValue = tx.getValue();
                BigInteger txNonce = new BigInteger(1, tx.getNonce());
                RskAddress txSender = tx.getSender(signatureCache);
                logger.debug("Examining tx={} sender: {} value: {} nonce: {}", hash, txSender, txValue, txNonce);

                BigInteger expectedNonce;

                if (accountNonces.containsKey(txSender)) {
                    expectedNonce = accountNonces.get(txSender).add(BigInteger.ONE);
                } else {
                    expectedNonce = originalRepo.getNonce(txSender);
                }

                if (isLowGasPriced(minGasPrice, tx)) {
                    txsToRemove.add(tx);
                    logger.warn("Rejected tx={} because of low gas account {}, removing tx from pending state.", hash, txSender);
                    continue;
                }

                if (isRskip252Enabled && isHighGasPriced(tx, minGasPrice)) {
                    txsToRemove.add(tx);
                    logger.warn("Rejected tx={} because gas price cap was surpassed {}, removing tx from pending state.", hash, txSender);
                    continue;
                }

                if (!expectedNonce.equals(txNonce)) {
                    logger.warn("Invalid nonce, expected {}, found {}, tx={}", expectedNonce, txNonce, hash);
                    continue;
                }

                accountNonces.put(txSender, txNonce);

                logger.debug("Accepted tx={} sender: {} value: {} nonce: {}", hash, txSender, txValue, txNonce);
            } catch (Exception e) {
                // Txs that can't be selected by any reason should be removed from pending state
                logger.warn(String.format("Error when processing tx=%s", tx.getHash()), e);
                txsToRemove.add(tx);
                continue;
            }

            txsResult.add(tx);
        }

        logger.debug("Ending getTransactions {}", txsResult.size());

        return txsResult;
    }

    private boolean isLowGasPriced(Coin minGasPrice, Transaction tx) {
        if (tx instanceof RemascTransaction) {
            return false;
        }

        return tx.getGasPrice().compareTo(minGasPrice) < 0;
    }

    private boolean isHighGasPriced(Transaction tx, Coin minGasPrice) {
        return TxGasPriceCap.FOR_BLOCK.isSurpassed(tx, minGasPrice);
    }
}
