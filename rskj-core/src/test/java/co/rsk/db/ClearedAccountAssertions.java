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

package co.rsk.db;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.db.MutableRepository;
import org.ethereum.vm.DataWord;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ClearedAccountAssertions {

    private ClearedAccountAssertions() {
    }

    /**
     * Asserts the RSKIP701 cleared shape: the account exists with the expected nonce, a zero balance,
     * no code and no storage.
     */
    public static void assertClearedAccount(RepositorySnapshot repository, RskAddress address, BigInteger expectedNonce) {
        assertTrue(repository.isExist(address));
        assertEquals(expectedNonce, repository.getNonce(address));
        assertEquals(Coin.ZERO, repository.getBalance(address));
        assertEquals(0, repository.getCodeLength(address));
        assertEquals(0, repository.getStorageKeysCount(address));
        assertFalse(repository.hasInitializedStorage(address));
        assertNull(repository.getStorageValue(address, DataWord.ZERO));
        assertEquals(MutableRepository.KECCAK_256_OF_EMPTY_ARRAY, repository.getCodeHashStandard(address));
    }
}
