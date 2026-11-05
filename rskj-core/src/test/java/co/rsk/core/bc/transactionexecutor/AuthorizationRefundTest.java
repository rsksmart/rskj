/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.core.bc.transactionexecutor;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.Repository;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.SetCodeAuthorizationTransactionExecutor;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.crypto.ECKey;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.GasCost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationRefundTest {

    private static final long REFUND = GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST;
    private static final BigInteger CHAIN_ID = BigInteger.valueOf(Rskip545TestSupport.REGTEST_CHAIN_ID);

    private final SetCodeAuthorizationTransactionExecutor executor = new SetCodeAuthorizationTransactionExecutor();
    private Repository repository;
    private ECKey authorityKey;
    private RskAddress authority;

    @BeforeEach
    void setUp() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        repository = new MutableRepository(new MutableTrieImpl(trieStore, new Trie(trieStore)));
        authorityKey = new ECKey();
        authority = new RskAddress(authorityKey.getAddress());
    }

    @Test
    void absentAuthority_isNotRefunded() {
        assertFalse(repository.isExist(authority));

        assertEquals(0L, authorize(BigInteger.ZERO));
        assertDelegated();
    }

    @Test
    void existingEip161EmptyAuthority_isNotRefunded() {
        repository.createAccount(authority);
        assertTrue(repository.isExist(authority));

        assertEquals(0L, authorize(BigInteger.ZERO));
        assertDelegated();
    }

    @Test
    void firstDelegationOfFundedEoa_isRefunded() {
        repository.addBalance(authority, Coin.valueOf(1_000_000L));

        assertEquals(REFUND, authorize(BigInteger.ZERO));
        assertDelegated();
    }

    @Test
    void firstDelegationOfEoaWithNonceAndNoBalance_isRefunded() {
        repository.increaseNonce(authority);

        assertEquals(REFUND, authorize(BigInteger.ONE));
        assertDelegated();
    }

    @Test
    void reDelegationOfDelegatedAuthority_isRefunded() {
        assertEquals(0L, authorize(BigInteger.ZERO));

        assertEquals(REFUND, authorize(BigInteger.ONE));
        assertDelegated();
    }

    @Test
    void refundIsDecidedOnStateBeforeTheTupleIsApplied() {
        assertEquals(0L, authorize(BigInteger.ZERO));
        assertEquals(BigInteger.ONE, repository.getNonce(authority));
    }

    private long authorize(BigInteger nonce) {
        SetCodeAuthorization tuple = Rskip545TestSupport.createSignedAuthorization(authorityKey, Rskip545TestSupport.DEFAULT_DELEGATE, nonce, Rskip545TestSupport.REGTEST_CHAIN_ID);
        return executor.processAuthorizationTuple(repository, CHAIN_ID, tuple);
    }

    private void assertDelegated() {
        assertArrayEquals(DelegationCodeResolver.createDelegatedCode(Rskip545TestSupport.DEFAULT_DELEGATE), repository.getCode(authority));
    }
}
