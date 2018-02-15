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

package co.rsk.net.handler;

import co.rsk.TestHelpers.Tx;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.peg.Federation;
import org.ethereum.config.blockchain.RegTestConfig;
import org.ethereum.core.*;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.util.*;

import static org.mockito.Matchers.eq;

public class TxValidatorTest {


    private TestSystemProperties config;

    Random hashes = new Random(0);

    @Before
    public void setup(){
        config = new TestSystemProperties();
    }

    @Test
    public void oneTestToRuleThemAll() {
        List<Transaction> txs;
        List<Transaction> result;
        Repository repository = Mockito.mock(Repository.class);
        final long blockGasLimit = 100000;
        Blockchain blockchain = Mockito.mock(Blockchain.class);
        Block block = Mockito.mock(Block.class);
        Mockito.when(blockchain.getBestBlock()).thenReturn(block);
        Mockito.when(block.getGasLimit()).thenReturn(BigInteger.valueOf(blockGasLimit).toByteArray());
        Mockito.when(block.getMinimumGasPrice()).thenReturn(Coin.valueOf(1L));

        List<Transaction> vtxs = new LinkedList<>();
        List<Transaction> itxs = new LinkedList<>();

        // valid everything
        vtxs.add(createTransaction(1, 50000, 1, 0, 0, 0));
        createAccountState(vtxs.get(vtxs.size() - 1), repository, 50000, 0);

        // not enough balance
        itxs.add(createTransaction(1, 50000, 1, 0, 0, 1));
        createAccountState(itxs.get(itxs.size() - 1), repository, 49999, 0);

        // bad nonce
        vtxs.add(createTransaction(1, 50000, 1, 1, 0, 2));
        createAccountState(vtxs.get(vtxs.size() - 1), repository, 50000, 0);

        // enough balance for txs
        vtxs.add(createTransaction(1, 50000, 1, 0, 0, 3));
        createAccountState(vtxs.get(vtxs.size() - 1), repository, 100000, 0);
        vtxs.add(createTransaction(1, 50000, 1, 1, 0, 3));
        vtxs.add(createTransaction(1, 50000, 1, 2, 0, 3));

        // enough balance for 3 txs
        vtxs.add(createTransaction(1, 50000, 1, 0, 0, 4));
        createAccountState(vtxs.get(vtxs.size() - 1), repository, 150002, 0);
        vtxs.add(createTransaction(1, 50000, 1, 1, 0, 4));
        vtxs.add(createTransaction(1, 50000, 1, 2, 0, 4));

        // bad intrinsic gas
        itxs.add(createTransaction(1, 20990, 1, 0, 1000, 5));
        createAccountState(itxs.get(itxs.size() - 1), repository, 150000, 0);
        itxs.add(createTransaction(1, 21900, 1, 0, 1000, 5));

        // no account
        itxs.add(createTransaction(1, 50000, 0, 0, 0, 6));

        // all possible nonces
        vtxs.add(createTransaction(1, 50000, 1, 0, 0, 7));
        createAccountState(vtxs.get(vtxs.size() - 1), repository, 1000000, 0);
        vtxs.add(createTransaction(1, 50000, 1, 1, 0, 7));
        vtxs.add(createTransaction(1, 50000, 1, 2, 0, 7));
        vtxs.add(createTransaction(1, 50000, 1, 3, 0, 7));
        vtxs.add(createTransaction(1, 50000, 1, 4, 0, 7));
        itxs.add(createTransaction(1, 50000, 1, 5, 0, 7));

        // all possible nonces starting in non zero
        vtxs.add(createTransaction(1, 50000, 1, 6, 0, 8));
        createAccountState(vtxs.get(vtxs.size() - 1), repository, 1000000, 6);
        vtxs.add(createTransaction(1, 50000, 1, 7, 0, 8));
        vtxs.add(createTransaction(1, 50000, 1, 8, 0, 8));
        vtxs.add(createTransaction(1, 50000, 1, 9, 0, 8));
        vtxs.add(createTransaction(1, 50000, 1, 10, 0, 8));
        itxs.add(createTransaction(1, 50000, 1, 11, 0, 8));

        // bad balance
        itxs.add(createTransaction(1, 200000, 5, 0, 0, 9));
        createAccountState(itxs.get(itxs.size() - 1), repository, 999999, 0);

        // just enough balance
        itxs.add(createTransaction(1, 200000, 5, 0, 0, 10));
        createAccountState(itxs.get(itxs.size() - 1), repository, 1000000, 0);

        txs = new LinkedList<>();
        txs.addAll(vtxs);
        txs.addAll(itxs);

        TxValidator txValidator = new TxValidator(config, repository, blockchain);

        result = txValidator.filterTxs(txs);

        Assert.assertEquals(vtxs.size(), result.size());
        Assert.assertTrue(vtxs.stream().allMatch(t -> result.contains(t)));
    }

    
    @Test
    public void brigdeTxTest() {
        config.setBlockchainConfig(new RegTestConfig());

        List<Transaction> txs = new LinkedList<>();
        //Bridge Tx
        txs.add(createBridgeTx(config, 1, 0, 1, 0, 0, 6));

        Repository repository = Mockito.mock(Repository.class);
        final long blockGasLimit = 100000;
        Blockchain blockchain = Mockito.mock(Blockchain.class);
        Block block = Mockito.mock(Block.class);
        Mockito.when(blockchain.getBestBlock()).thenReturn(block);
        Mockito.when(block.getGasLimit()).thenReturn(BigInteger.valueOf(blockGasLimit).toByteArray());
        Mockito.when(block.getMinimumGasPrice()).thenReturn(Coin.valueOf(1L));
        createAccountState(txs.get(0), repository, 0, 0);

        TxValidator txValidator = new TxValidator(config, repository, blockchain);
        List<Transaction> result = txValidator.filterTxs(txs);
        Assert.assertTrue(result.size() == 1);
    }

    private Transaction createTransaction(long value, long gaslimit, long gasprice, long nonce, long data, long sender) {
        return Tx.create(config, value, gaslimit, gasprice, nonce, data, sender);
    }

    private void createAccountState(Transaction tx, Repository repository, long balance, long nonce) {
        AccountState as = Mockito.mock(AccountState.class);
        Mockito.when(as.getBalance()).thenReturn(Coin.valueOf(balance));
        Mockito.when(as.getNonce()).thenReturn(BigInteger.valueOf(nonce));
        Mockito.when(repository.getAccountState(tx.getSender())).thenReturn(as);
    }

    public static Transaction createBridgeTx(
            TestSystemProperties config,
            long value,
            long gaslimit,
            long gasprice,
            long nonce,
            long data,
            long sender) {
        Transaction transaction = Tx.create(config, value, gaslimit, gasprice, nonce, data, sender);
        Mockito.when(transaction.getReceiveAddress()).thenReturn(PrecompiledContracts.BRIDGE_ADDR);
        Mockito.when(transaction.getSignature()).thenReturn(new ECKey.ECDSASignature(BigInteger.ONE, BigInteger.ONE));
        Mockito.when(transaction.transactionCost(eq(config), Mockito.any())).thenReturn(new Long(0));
        Mockito.when(transaction.getGasLimitAsInteger()).thenReturn(BigInteger.ZERO);
        // Federation is the genesis federation ATM
        Federation federation = config.getBlockchainConfig().getCommonConstants().getBridgeConstants().getGenesisFederation();
        byte[] federator0PubKey = federation.getBtcPublicKeys().get(0).getPubKey();
        Mockito.when(transaction.getKey()).thenReturn(ECKey.fromPublicOnly(federator0PubKey));
        return transaction;
    }


}
