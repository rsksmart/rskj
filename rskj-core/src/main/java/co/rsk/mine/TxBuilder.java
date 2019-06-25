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

import co.rsk.core.RskAddress;
import co.rsk.net.BlockProcessor;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.config.Constants;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.facade.Ethereum;
import org.ethereum.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This component creates random txs and stores them to the memory pool. It is used only for testing
 * purposes.
 */
public class TxBuilder {

    private final Constants constants;
    private final Ethereum ethereum;
    private final BlockProcessor blockProcessor;
    private final Repository repository;

    private static final Logger logger = LoggerFactory.getLogger("txbuilder");
    private volatile boolean stop = false;

    private byte[] privateKeyBytes =
            HashUtil.keccak256("this is a seed".getBytes(StandardCharsets.UTF_8));
    private ECKey key;

    public TxBuilder(
            Constants constants,
            Ethereum ethereum,
            BlockProcessor blockProcessor,
            Repository repository) {
        this.constants = constants;
        this.ethereum = ethereum;
        this.blockProcessor = blockProcessor;
        this.repository = repository;
    }

    public void simulateTxs() {

        key = ECKey.fromPrivate(privateKeyBytes);

        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(60000);

                    while (blockProcessor.hasBetterBlockToSync()) {
                        Thread.sleep(60000);
                    }

                    SecureRandom random = new SecureRandom();

                    RskAddress addr = new RskAddress(key.getAddress());
                    AccountState accountState = repository.getAccountState(addr);
                    BigInteger nonce = accountState.getNonce();

                    while (!stop) {
                        if ((random.nextInt() % 10) == 0) {
                            nonce = repository.getAccountState(addr).getNonce();
                        }

                        TxBuilder.this.createNewTx(nonce);

                        Thread.sleep(random.nextInt(51000));
                        nonce = nonce.add(BigInteger.ONE);
                    }

                } catch (InterruptedException e) {
                    logger.error("TxBuild Thread was interrupted", e);
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }.start();
    }

    public void createNewTx(BigInteger txNonce) throws InterruptedException {

        Transaction tx =
                this.createNewTransaction(
                        BigInteger.valueOf(1), BigInteger.valueOf(21000), txNonce);

        // Adds created transaction to the local node's memory pool
        ethereum.submitTransaction(tx);

        logger.info("Added pending tx={}", tx.getHash());

        SecureRandom random = new SecureRandom();
        Thread.sleep(random.nextInt(51000));
    }

    public Transaction createNewTransaction(
            BigInteger gasPrice, BigInteger gasLimit, BigInteger txNonce) {
        String toAddress = Hex.toHexString(new ECKey(Utils.getRandom()).getAddress());

        Transaction tx =
                new Transaction(
                        toAddress,
                        BigInteger.valueOf(1000),
                        txNonce,
                        gasPrice,
                        gasLimit,
                        constants.getChainId());
        tx.sign(privateKeyBytes);

        return tx;
    }

    public void stop() {
        stop = true;
    }
}
