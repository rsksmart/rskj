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

import co.rsk.config.RskSystemProperties;
import co.rsk.core.RskAddress;
import co.rsk.net.BlockProcessor;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.facade.Ethereum;
import org.ethereum.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * This component creates random txs and stores them to the memory pool.
 * It is used only for testing purposes.
 */
public class TxBuilderEx {

    private static final Logger logger = LoggerFactory.getLogger("txbuilderex");

    private final RskSystemProperties config;
    private final Ethereum ethereum;
    private final Repository repository;
    private final BlockProcessor nodeBlockProcessor;
    private final TransactionPool transactionPool;

    private final SecureRandom random = new SecureRandom();

    private volatile boolean stop = false;

    public TxBuilderEx(RskSystemProperties config,
                       Ethereum ethereum,
                       Repository repository,
                       BlockProcessor nodeBlockProcessor,
                       TransactionPool transactionPool) {
        this.config = config;
        this.ethereum = ethereum;
        this.repository = repository;
        this.nodeBlockProcessor = nodeBlockProcessor;
        this.transactionPool = transactionPool;
    }

    public void simulateTxs() {
        final byte[] privateKeyBytes = HashUtil.keccak256(config.simulateTxsExAccountSeed().getBytes(StandardCharsets.UTF_8));
        final ECKey key = ECKey.fromPrivate(privateKeyBytes);
        RskAddress addr = new RskAddress(key.getAddress());

        final Account targetAcc = new Account(new ECKey(Utils.getRandom()));
        final String targetAddress = targetAcc.getAddress().toString();

        final Account target2Acc = new Account(new ECKey(Utils.getRandom()));
        final String target2Address = target2Acc.getAddress().toString();

        logger.trace("Accounts {} {} {}", addr, targetAddress, target2Address);

        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    logger.error("Interrupted", e);
                }

                while (nodeBlockProcessor.hasBetterBlockToSync()) {
                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException e) {
                        logger.error("Interrupted", e);
                    }
                }

                for (int k = 0; k < 6; k++) {
                    try {
                        Thread.sleep(20000);
                    } catch (InterruptedException e) {
                        logger.error("Interrupted", e);
                    }
                    AccountState fromAccountState = repository.getAccountState(addr);

                    Transaction tx = createNewTransaction(privateKeyBytes, targetAddress, BigInteger.valueOf(config.simulateTxsExFounding()), fromAccountState.getNonce());
                    sendTransaction(tx);
                    logger.trace("Funding tx {} nonce {}", 10000000000L, getNonce(tx));
                }

                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    logger.error("Interrupted", e);
                }

                BigInteger nonce = BigInteger.ZERO;

                int value = 1;
                BigInteger lastNonce;

                while (!stop) {
                    Transaction tx = createNewTransaction(targetAcc.getEcKey().getPrivKeyBytes(), target2Address, BigInteger.valueOf(value), nonce);
                    sendTransaction(tx);
                    logger.trace("Send tx value {} nonce {}", value, getNonce(tx));
                    value += 2;
                    lastNonce = nonce;
                    nonce = nonce.add(BigInteger.ONE);
                    try {
                        SecureRandom r = new SecureRandom();
                        Thread.sleep(10000 + (long)r.nextInt(20000));

                        BigInteger accnonce = transactionPool.getPendingState().getNonce(targetAcc.getAddress());

                        if (accnonce.compareTo(lastNonce) < 0) {
                            tx = createNewTransaction(targetAcc.getEcKey().getPrivKeyBytes(), target2Address, BigInteger.valueOf(accnonce.intValue() * 2 + 1), accnonce);
                            logger.trace("Resend tx value {} nonce {}", accnonce.intValue() * 2 + 1, getNonce(tx));
                            sendTransaction(tx);
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }.start();
    }

    private static long getNonce(Transaction tx) {
        byte[] bytes = tx.getNonce();

        if (bytes == null || bytes.length == 0) {
            return 0;
        }

        return new BigInteger(1, bytes).longValue();
    }

    private void sendTransaction(Transaction tx) {
        //Adds created transaction to the local node's memory pool
        ethereum.submitTransaction(tx);
        logger.info("Added pending tx={}", tx.getHash());
    }

    private Transaction createNewTransaction(byte[] privateKey, String toAddress, BigInteger value, BigInteger nonce) {
        long gasLimit = 21000L + random.nextInt(100);
        Transaction tx = new Transaction(config, toAddress, value, nonce, BigInteger.ONE, BigInteger.valueOf(gasLimit));
        tx.sign(privateKey);
        return tx;
    }

    public void stop() {
        stop = true;
    }
}
