/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
package org.ethereum.vm.program;

import co.rsk.config.TestSystemProperties;
import java.math.BigInteger;
import java.util.Set;

import org.ethereum.core.BlockTxSignatureCache;
import org.ethereum.core.ReceivedTxSignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProgramResultTest {

    private static final DataWord CREATED_A = DataWord.valueOf(0xA1);
    private static final DataWord CREATED_B = DataWord.valueOf(0xB2);

    private final TestSystemProperties config = new TestSystemProperties();

    @Test
    void add_internal_tx_one_level_Ok() {
        Transaction originTx = getOriginTransaction();
        ProgramResult programResult = new ProgramResult();
        InternalTransaction internalTx = programResult.addInternalTransaction(
            originTx,
            0,
            DataWord.ONE.getByteArrayForStorage(),
            DataWord.ONE,
            DataWord.ONE,
            new ECKey().getAddress(),
            new ECKey().getAddress(),
            new byte[] {},
            new byte[] {},
            "",
            new BlockTxSignatureCache(new ReceivedTxSignatureCache())
        );

        Assertions.assertArrayEquals(originTx.getHash().getBytes(), internalTx.getOriginHash());
        Assertions.assertArrayEquals(originTx.getHash().getBytes(), internalTx.getParentHash());
    }

    @Test
    void add_interenal_tx_two_levels_Ok() {
        Transaction originTx = getOriginTransaction();
        ProgramResult programResult = new ProgramResult();
        InternalTransaction internalTx1 = programResult.addInternalTransaction(
            originTx,
            0,
            DataWord.ONE.getByteArrayForStorage(),
            DataWord.ONE,
            DataWord.ONE,
            new ECKey().getAddress(),
            new ECKey().getAddress(),
            new byte[] {},
            new byte[] {},
            "",
            new BlockTxSignatureCache(new ReceivedTxSignatureCache())
        );
        InternalTransaction internalTx2 = programResult.addInternalTransaction(
            internalTx1,
            0,
            DataWord.ONE.getByteArrayForStorage(),
            DataWord.ONE,
            DataWord.ONE,
            new ECKey().getAddress(),
            new ECKey().getAddress(),
            new byte[] {},
            new byte[] {},
            "",
            new BlockTxSignatureCache(new ReceivedTxSignatureCache())
        );

        Assertions.assertArrayEquals(originTx.getHash().getBytes(), internalTx2.getOriginHash());
        Assertions.assertArrayEquals(internalTx1.getHash().getBytes(), internalTx2.getParentHash());
    }

    @Test
    void add_interenal_tx_many_levels_Ok() {
        Transaction originTx = getOriginTransaction();
        ProgramResult programResult = new ProgramResult();

        Transaction internalTxN = originTx;
        for (int i = 0; i < 3; i++) {
            internalTxN = programResult.addInternalTransaction(
                internalTxN,
                0,
                DataWord.ONE.getByteArrayForStorage(),
                DataWord.ONE,
                DataWord.ONE,
                new ECKey().getAddress(),
                new ECKey().getAddress(),
                new byte[] {},
                new byte[] {},
                "",
                new BlockTxSignatureCache(new ReceivedTxSignatureCache())
            );
        }
        InternalTransaction result = (InternalTransaction)internalTxN;

        Assertions.assertArrayEquals(originTx.getHash().getBytes(), result.getOriginHash());
    }

    @Test
    void merge_keeps_created_accounts_of_a_successful_child_and_drops_those_of_a_reverted_one() {
        ProgramResult parent = new ProgramResult();
        ProgramResult succeeded = childThatCreatedAndMarked(CREATED_A);
        ProgramResult reverted = childThatCreatedAndMarked(CREATED_B);
        reverted.setRevert();

        parent.merge(succeeded);
        parent.merge(reverted);

        Assertions.assertEquals(Set.of(CREATED_A), parent.getCreatedAccounts());
        Assertions.assertEquals(Set.of(CREATED_A), parent.getDeleteAccounts());
    }

    @Test
    void merge_drops_created_accounts_of_a_child_that_failed_with_an_exception() {
        ProgramResult parent = new ProgramResult();
        ProgramResult failed = childThatCreatedAndMarked(CREATED_A);
        failed.setException(new RuntimeException("halt"));

        parent.merge(failed);

        Assertions.assertTrue(parent.getCreatedAccounts().isEmpty());
        Assertions.assertTrue(parent.getDeleteAccounts().isEmpty());
    }

    @Test
    void clear_fields_on_exception_drops_created_accounts_merged_earlier() {
        ProgramResult parent = new ProgramResult();
        parent.merge(childThatCreatedAndMarked(CREATED_A));

        parent.clearFieldsOnException();

        Assertions.assertTrue(parent.getCreatedAccounts().isEmpty());
        Assertions.assertTrue(parent.getDeleteAccounts().isEmpty());
    }

    private static ProgramResult childThatCreatedAndMarked(DataWord address) {
        ProgramResult child = new ProgramResult();
        child.addCreatedAccount(address);
        child.addDeleteAccount(address);
        return child;
    }

    private Transaction getOriginTransaction() {
        return Transaction.builder()
            .nonce(BigInteger.ONE.toByteArray())
            .gasPrice(BigInteger.ONE)
            .gasLimit(BigInteger.valueOf(21000))
            .receiveAddress(PrecompiledContracts.BRIDGE_ADDR)
            .chainId(config.getNetworkConstants().getChainId())
            .value(BigInteger.TEN)
            .build();
    }

}
