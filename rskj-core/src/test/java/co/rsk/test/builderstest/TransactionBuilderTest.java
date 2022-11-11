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

package co.rsk.test.builderstest;

import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import org.ethereum.core.Account;
import org.ethereum.core.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by ajlopez on 08/08/2016.
 */
class TransactionBuilderTest {
    @Test
    void buildTransaction() {
        Account sender = new AccountBuilder().name("sender").build();
        Account receiver = new AccountBuilder().name("receiver").build();

        Transaction tx = new TransactionBuilder()
                .sender(sender)
                .receiver(receiver)
                .value(BigInteger.TEN)
                .nonce(2)
                .build();

        Assertions.assertNotNull(tx);
        Assertions.assertArrayEquals(sender.getAddress().getBytes(), tx.getSender().getBytes());
        Assertions.assertArrayEquals(receiver.getAddress().getBytes(), tx.getReceiveAddress().getBytes());
        Assertions.assertEquals(BigInteger.TEN, tx.getValue().asBigInteger());
        Assertions.assertEquals(BigInteger.ONE, tx.getGasPrice().asBigInteger());
        Assertions.assertEquals(BigInteger.valueOf(2), new BigInteger(1, tx.getNonce()));
        Assertions.assertEquals(BigInteger.valueOf(21000), new BigInteger(1, tx.getGasLimit()));
        Assertions.assertNotNull(tx.getData());
        Assertions.assertEquals(0, tx.getData().length);
    }

    @Test
    void buildValidRandomTransactions() {
        List<String> randomTxsHashes = IntStream.range(0, 100)
                .mapToObj(i -> new TransactionBuilder().buildRandomTransaction(i).getHash().toJsonString())
                .collect(Collectors.toList());

        Set<String> hashesWithoutDuplicates = randomTxsHashes.stream().collect(Collectors.toSet());

        Assertions.assertEquals(randomTxsHashes.size(), hashesWithoutDuplicates.size());
    }
}
