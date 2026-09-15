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

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.RskTestUtils.generateAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.core.RskAddress;
import co.rsk.crypto.Keccak256;
import java.math.BigInteger;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.Repository;
import org.ethereum.core.Rskip545TestSupport;
import org.ethereum.core.SetCodeAuthorizationTransactionExecutor;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.junit.jupiter.api.Test;

class SetCodeDelegationRepositoryConsistencyTest {

    private static final BigInteger UNIVERSAL_CHAIN_ID = BigInteger.ZERO;
    private static final byte UNIVERSAL_CHAIN_ID_BYTE = 0;

    private final SetCodeAuthorizationTransactionExecutor executor = new SetCodeAuthorizationTransactionExecutor();

    @Test
    void installingNonzeroDelegation_mustInitializeRepositoryStorage() {
        Repository repository = createRepository();
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());
        repository.createAccount(authority);

        SetCodeAuthorization tuple = Rskip545TestSupport.createSignedAuthorization(authorityKey, generateAddress("delegate"), BigInteger.ZERO, UNIVERSAL_CHAIN_ID_BYTE);
        executor.processAuthorizationTuple(repository, UNIVERSAL_CHAIN_ID, tuple);

        assertTrue(repository.hasInitializedStorage(authority), "Installing a nonzero delegation must initialize repository storage for the authority");
    }

    @Test
    void installingNonzeroDelegation_mustSetThePersistentAuthorityMarker() {
        Repository repository = createRepository();
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());
        repository.createAccount(authority);

        SetCodeAuthorization tuple = Rskip545TestSupport.createSignedAuthorization(authorityKey, generateAddress("delegate"), BigInteger.ZERO, UNIVERSAL_CHAIN_ID_BYTE);
        executor.processAuthorizationTuple(repository, UNIVERSAL_CHAIN_ID, tuple);

        assertTrue(repository.hasDelegationAuthorityMarker(authority), "Installing a nonzero delegation must set the persistent EIP-7702 authority marker");
    }

    @Test
    void installingNonzeroDelegation_isAlreadyRecognizedAsActiveDelegatedEOA() {
        Repository repository = createRepository();
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());
        repository.createAccount(authority);

        SetCodeAuthorization tuple = Rskip545TestSupport.createSignedAuthorization(authorityKey, generateAddress("delegate"), BigInteger.ZERO, UNIVERSAL_CHAIN_ID_BYTE);
        executor.processAuthorizationTuple(repository, UNIVERSAL_CHAIN_ID, tuple);

        assertTrue(repository.isActiveDelegatedEOA(authority));
    }

    @Test
    void extcodehash_mustReflectDelegatedCodeWhileDelegationIsActive() {
        Repository repository = createRepository();
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());
        RskAddress delegate = generateAddress("delegate");
        repository.createAccount(authority);

        SetCodeAuthorization tuple = Rskip545TestSupport.createSignedAuthorization(
                authorityKey, delegate, BigInteger.ZERO, UNIVERSAL_CHAIN_ID_BYTE);

        executor.processAuthorizationTuple(repository, UNIVERSAL_CHAIN_ID, tuple);

        byte[] delegatedCode = DelegationCodeResolver.createDelegatedCode(delegate);
        Keccak256 expected = new Keccak256(HashUtil.keccak256(delegatedCode));

        assertEquals(expected, repository.getCodeHashStandard(authority), "EXTCODEHASH must hash the actual delegated code, not report the empty-code hash");
    }

    @Test
    void clearingDelegation_mustPreserveStorageAndAuthorityIdentity() {
        Repository repository = createRepository();
        ECKey authorityKey = new ECKey();
        RskAddress authority = new RskAddress(authorityKey.getAddress());
        repository.createAccount(authority);

        SetCodeAuthorization install = Rskip545TestSupport.createSignedAuthorization(authorityKey, generateAddress("delegate"), BigInteger.ZERO, UNIVERSAL_CHAIN_ID_BYTE);
        executor.processAuthorizationTuple(repository, UNIVERSAL_CHAIN_ID, install);

        SetCodeAuthorization clear = Rskip545TestSupport.createSignedAuthorization(authorityKey, RskAddress.ZERO_ADDRESS, BigInteger.ONE, UNIVERSAL_CHAIN_ID_BYTE);
        executor.processAuthorizationTuple(repository, UNIVERSAL_CHAIN_ID, clear);

        assertTrue(repository.hasInitializedStorage(authority), "Clearing a delegation must not un-initialize repository storage");
        assertTrue(repository.hasDelegationAuthorityMarker(authority), "Clearing a delegation must not remove the persistent authority marker");
        assertTrue(repository.isClearedDelegatedEOA(authority), "Account must classify as a cleared delegated EOA, not a plain EOA");
        assertFalse(repository.isPlainEOA(authority), "A previously-delegated authority must never collapse back into a plain EOA");
    }
}
