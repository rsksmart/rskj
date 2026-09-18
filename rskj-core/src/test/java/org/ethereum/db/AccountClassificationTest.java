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
package org.ethereum.db;

import static co.rsk.RskTestUtils.createRepository;
import static co.rsk.RskTestUtils.generateAddress;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.core.RskAddress;
import org.ethereum.core.DelegationCodeResolver;
import org.ethereum.core.Repository;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.jupiter.api.Test;


class AccountClassificationTest {

    private static final byte[] REGULAR_CODE = new byte[] {0x60, 0x00, (byte) 0xf3};

    @Test
    void freshAccount_hasNoDelegationAuthorityMarkerByDefault() {
        Repository repository = createRepository();
        RskAddress addr = generateAddress("fresh");
        repository.createAccount(addr);
        assertFalse(repository.hasDelegationAuthorityMarker(addr));
    }

    @Test
    void nonExistentAccount_hasNoDelegationAuthorityMarker() {
        Repository repository = createRepository();
        RskAddress addr = generateAddress("never-created");

        assertFalse(repository.isExist(addr));
        assertFalse(repository.hasDelegationAuthorityMarker(addr));
    }

    @Test
    void initializeDelegationAuthority_setsMarkerPersistently() {
        Repository repository = createRepository();
        RskAddress addr = generateAddress("authority");
        repository.createAccount(addr);

        repository.initializeDelegationAuthority(addr);

        assertTrue(repository.hasDelegationAuthorityMarker(addr));
    }

    @Test
    void clearingDelegationCode_doesNotRemoveTheAuthorityMarker() {
        Repository repository = createRepository();
        RskAddress addr = generateAddress("cleared-marker");
        repository.createAccount(addr);

        repository.initializeDelegationAuthority(addr);
        repository.saveCode(addr, DelegationCodeResolver.createDelegatedCode(generateAddress("delegate")));

        repository.saveCode(addr, new byte[0]);

        assertTrue(repository.hasDelegationAuthorityMarker(addr), "The authority marker must survive delegation clearing - only account deletion removes it");
    }

    @Test
    void delegationAuthorityMarker_isIndependentFromStorageInitMarker() {
        Repository repository = createRepository();
        RskAddress addr = generateAddress("independent");
        repository.createAccount(addr);

        repository.initializeDelegationAuthority(addr);

        assertFalse(repository.hasInitializedStorage(addr), "Setting the authority marker must not, by itself, initialize storage");
    }

    @Test
    void plainEOA_matchesOnlyIsPlainEOA() {
        Repository repository = createRepository();
        RskAddress addr = generateAddress("plain-eoa");
        repository.createAccount(addr);

        assertAll("plain EOA: no storage, no marker, no code",
                () -> assertTrue(repository.isPlainEOA(addr)),
                () -> assertTrue(repository.isEOA(addr)),
                () -> assertFalse(repository.isActiveDelegatedEOA(addr)),
                () -> assertFalse(repository.isClearedDelegatedEOA(addr)),
                () -> assertFalse(repository.isRegularContract(addr))

        );
    }

    @Test
    void activeDelegatedEOA_matchesOnlyIsActiveDelegatedEOA() {
        Repository repository = createRepository();
        RskAddress addr = generateAddress("active-delegated");
        RskAddress delegate = generateAddress("delegate-1");
        repository.createAccount(addr);
        repository.initializeStorage(addr);
        repository.initializeDelegationAuthority(addr);
        repository.saveCode(addr, DelegationCodeResolver.createDelegatedCode(delegate));

        assertAll("active delegated EOA: storage initialized, marker set, current code is the designator",
                () -> assertFalse(repository.isPlainEOA(addr)),
                () -> assertTrue(repository.isActiveDelegatedEOA(addr)),
                () -> assertFalse(repository.isClearedDelegatedEOA(addr)),
                () -> assertFalse(repository.isRegularContract(addr)),
                () -> assertFalse(repository.isEOA(addr), "isEOA() must exclude the active-delegation case (RSKIP-545 review point 1)")
        );
    }

    @Test
    void clearedDelegatedEOA_matchesOnlyIsClearedDelegatedEOA() {
        Repository repository = createRepository();
        RskAddress addr = generateAddress("cleared-delegated");
        RskAddress delegate = generateAddress("delegate-2");
        repository.createAccount(addr);
        repository.initializeStorage(addr);
        repository.initializeDelegationAuthority(addr);
        repository.saveCode(addr, DelegationCodeResolver.createDelegatedCode(delegate));

        repository.saveCode(addr, new byte[0]);

        assertAll("cleared delegated EOA: storage + marker survive, current code no longer the designator",
                () -> assertFalse(repository.isPlainEOA(addr)),
                () -> assertFalse(repository.isActiveDelegatedEOA(addr)),
                () -> assertTrue(repository.isClearedDelegatedEOA(addr)),
                () -> assertFalse(repository.isRegularContract(addr)),
                () -> assertTrue(repository.isEOA(addr))
        );
    }

    @Test
    void regularContract_matchesOnlyIsRegularContract() {
        Repository repository = createRepository();
        RskAddress addr = generateAddress("regular-contract");
        repository.createAccount(addr);
        repository.initializeStorage(addr);
        repository.saveCode(addr, REGULAR_CODE);

        assertAll("regular contract: storage initialized, never a delegation authority",
                () -> assertFalse(repository.isPlainEOA(addr)),
                () -> assertFalse(repository.isActiveDelegatedEOA(addr)),
                () -> assertFalse(repository.isClearedDelegatedEOA(addr)),
                () -> assertTrue(repository.isRegularContract(addr)),
                () -> assertFalse(repository.isEOA(addr))
        );
    }

    @Test
    void regularContractWithEmptyRuntimeCode_isNotConfusedWithClearedDelegatedEOA() {
        Repository repository = createRepository();
        RskAddress addr = generateAddress("empty-runtime-contract");
        repository.createAccount(addr);
        repository.initializeStorage(addr);
        repository.saveCode(addr, new byte[0]);

        assertAll("empty-runtime-code contract must classify as a regular contract, not an EOA",
                () -> assertTrue(repository.isRegularContract(addr)),
                () -> assertFalse(repository.isClearedDelegatedEOA(addr)),
                () -> assertFalse(repository.isActiveDelegatedEOA(addr)),
                () -> assertFalse(repository.isEOA(addr))
        );
    }

    @Test
    void deletingTheAccount_removesTheAuthorityMarkerToo() {
        Repository repository = createRepository();
        RskAddress addr = generateAddress("deleted-account");
        repository.createAccount(addr);
        repository.initializeStorage(addr);
        repository.initializeDelegationAuthority(addr);
        repository.saveCode(addr, DelegationCodeResolver.createDelegatedCode(generateAddress("delegate-3")));

        repository.delete(addr);

        assertAll("full account deletion must remove every marker, not just the code",
                () -> assertFalse(repository.isExist(addr)),
                () -> assertFalse(repository.hasInitializedStorage(addr)),
                () -> assertFalse(repository.hasDelegationAuthorityMarker(addr))
        );
    }

    @Test
    void precompileWithInitializedStorageAndNoCode_matchesOnlyIsRegularContract() {
        Repository repository = createRepository();
        RskAddress bridge = PrecompiledContracts.BRIDGE_ADDR;
        repository.createAccount(bridge);
        repository.initializeStorage(bridge);

        assertAll("a precompile with initialized storage and no code is a regular contract, never an EOA state",
                () -> assertTrue(repository.isRegularContract(bridge)),
                () -> assertFalse(repository.isPlainEOA(bridge)),
                () -> assertFalse(repository.isActiveDelegatedEOA(bridge)),
                () -> assertFalse(repository.isClearedDelegatedEOA(bridge)),
                () -> assertFalse(repository.isEOA(bridge))
        );
    }

    @Test
    void codeShapedAsDelegation_withoutTheMarker_isNotClassifiedAsActiveDelegatedEOA() {
        Repository repository = createRepository();
        RskAddress addr = generateAddress("bypassed-install-path");
        RskAddress delegate = generateAddress("delegate-x");
        repository.createAccount(addr);

        // saveCode() called directly, bypassing writeDelegation() -
        // no initializeStorage(), no initializeDelegationAuthority().
        repository.saveCode(addr, DelegationCodeResolver.createDelegatedCode(delegate));

        assertFalse(repository.isActiveDelegatedEOA(addr),
                "the code shape alone must not be enough - without the persistent marker "
                        + "the account must not classify as an active delegated EOA");
    }

    @Test
    void nonExistentAccount_matchesNoClassification() {
        Repository repository = createRepository();
        RskAddress addr = generateAddress("does-not-exist");

        assertAll("an account that was never created must not match any classification",
                () -> assertFalse(repository.isPlainEOA(addr)),
                () -> assertFalse(repository.isActiveDelegatedEOA(addr)),
                () -> assertFalse(repository.isClearedDelegatedEOA(addr)),
                () -> assertFalse(repository.isRegularContract(addr)),
                () -> assertFalse(repository.isEOA(addr))
        );
    }
}
