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
import org.ethereum.core.Account;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.facade.Ethereum;
import org.ethereum.manager.WorldManager;
import org.ethereum.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * This component creates random txs and stores them to the memory pool.
 * It is used only for testing purposes.
 */

@Component
public class TxBuilderEx {

    private static final Logger logger = LoggerFactory.getLogger("txbuilderex");
    private volatile boolean stop = false;
    private SecureRandom random = new SecureRandom();

    public void simulateTxs(final Ethereum ethereum,
                            WorldManager worldManager,
                            RskSystemProperties properties,
                            final Repository repository) {
        final byte[] privateKeyBytes = HashUtil.sha3(properties.simulateTxsExAccountSeed().getBytes(StandardCharsets.UTF_8));
        final ECKey key = ECKey.fromPrivate(privateKeyBytes);

        final Account targetAcc = new Account(new ECKey(Utils.getRandom()));
        final String targetAddress = Hex.toHexString(targetAcc.getAddress());

        final Account target2Acc = new Account(new ECKey(Utils.getRandom()));
        final String target2Address = Hex.toHexString(target2Acc.getAddress());

        logger.trace("Accounts {} {} {}", Hex.toHexString(key.getAddress()), targetAddress, target2Address);

        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    logger.error("Interrupted", e);
                }

                while (worldManager.getNodeBlockProcessor() != null && worldManager.getNodeBlockProcessor().hasBetterBlockToSync()) {
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
                    AccountState fromAccountState = repository.getAccountState(key.getAddress());

                    Transaction tx = createNewTransaction(privateKeyBytes, targetAddress, BigInteger.valueOf(properties.simulateTxsExFounding()), fromAccountState.getNonce());
                    sendTransaction(tx, ethereum);
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
                    sendTransaction(tx, ethereum);
                    logger.trace("Send tx value {} nonce {}", value, getNonce(tx));
                    value += 2;
                    lastNonce = nonce;
                    nonce = nonce.add(BigInteger.ONE);
                    try {
                        SecureRandom r = new SecureRandom();
                        Thread.sleep(10000 + (long)r.nextInt(20000));

                        Repository prepository = worldManager.getPendingState().getRepository();
                        AccountState accountState;

                        accountState = prepository.getAccountState(targetAcc.getAddress());

                        BigInteger accnonce = accountState.getNonce();

                        if (accnonce.compareTo(lastNonce) < 0) {
                            tx = createNewTransaction(targetAcc.getEcKey().getPrivKeyBytes(), target2Address, BigInteger.valueOf(accnonce.intValue() * 2 + 1), accnonce);
                            logger.trace("Resend tx value {} nonce {}", accnonce.intValue() * 2 + 1, getNonce(tx));
                            sendTransaction(tx, ethereum);
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

    private static void sendTransaction(Transaction tx, Ethereum ethereum) {
        //Adds created transaction to the local node's memory pool
        ethereum.submitTransaction(tx);
        logger.info("Added pending tx: " + tx.getHash());
    }

    private Transaction createNewTransaction(byte[] privateKey, String toAddress, BigInteger value, BigInteger nonce) {
        long gasLimit = 21000 + random.nextInt(100);
        Transaction tx = Transaction.create(toAddress, value, nonce, BigInteger.ONE, BigInteger.valueOf(gasLimit));
        tx.sign(privateKey);
        return tx;
    }

    public void stop() {
        stop = true;
    }
}
