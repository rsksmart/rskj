/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
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

import co.rsk.crypto.Keccak256;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.crypto.HashUtil;
import org.ethereum.datasource.KeyValueDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class StateRootsStoreImplTests {

    private static final byte[] KEY = HashUtil.keccak256(Hex.decode("aabbcc"));

    private static final Keccak256 RESULT = new Keccak256(HashUtil.keccak256(Hex.decode("123456")));

    private KeyValueDataSource dataSource;

    private StateRootsStoreImpl stateRootsStore;

    @BeforeEach
    public void setUp() {
        dataSource = mock(KeyValueDataSource.class);

        stateRootsStore = new StateRootsStoreImpl(dataSource);
    }

    @Test
    public void newInstance_WhenPassNull_ThenThrowException() {
        //noinspection ConstantConditions
        Assertions.assertThrows(NullPointerException.class, () -> new StateRootsStoreImpl(null));
    }

    @Test
    public void getItemByHash_WhenNotAddedKey_ThenShouldReturnNull() {
        doReturn(null).when(dataSource).get(eq(KEY));

        Keccak256 result = stateRootsStore.get(KEY);

        assertNull(result);
    }

    @Test
    public void getItemByHash_WhenAlreadyAddedKey_ThenShouldReturnItem() {
        doReturn(RESULT.getBytes()).when(dataSource).get(eq(KEY));

        Keccak256 actualResult = stateRootsStore.get(KEY);

        assertNotNull(actualResult);
        assertEquals(RESULT, actualResult);

        verify(dataSource, atLeastOnce()).get(eq(KEY));
    }

    @Test
    public void flush_WhenCalled_ThenShouldFlushNestedDataSource() {
        stateRootsStore.flush();

        verify(dataSource, times(1)).flush();
    }

    @Test
    public void close_WhenCalled_ThenShouldCloseNestedDataSource() {
        stateRootsStore.close();

        verify(dataSource, times(1)).close();
    }
}
