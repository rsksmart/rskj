/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
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

package co.rsk.rpc.netty;

import co.rsk.util.MaxSizeHashMap;
import org.ethereum.TestUtils;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class JsonRpcWeb3FilterHandlerTest {

    @Test
    void testGetAddress() throws UnknownHostException {
        JsonRpcWeb3FilterHandler jsonRpcWeb3FilterHandler = new JsonRpcWeb3FilterHandler(null, InetAddress.getByName("127.0.0.1"), null);
        MaxSizeHashMap<String, InetAddress> addressCache = spy(new MaxSizeHashMap<>(1, true));
        TestUtils.setInternalState(jsonRpcWeb3FilterHandler, "addressCache", addressCache);

        String node1 = "localhost";
        InetAddress host1Address = InetAddress.getByName(node1);

        String node2 = "127.0.0.2";
        InetAddress host2Address = InetAddress.getByName(node2);

        assertNull(addressCache.get(node1));
        assertNull(addressCache.get(node2));

        verify(addressCache, never()).put(node1, host1Address);
        InetAddress retrievedAddressNode1 = jsonRpcWeb3FilterHandler.getInetAddress(node1);

        assertEquals(retrievedAddressNode1, host1Address);
        assertEquals(retrievedAddressNode1, addressCache.get(node1));
        // put was called this time, not yet in cache
        verify(addressCache, times(1)).put(node1, host1Address);

        retrievedAddressNode1 = jsonRpcWeb3FilterHandler.getInetAddress(node1);
        assertEquals(retrievedAddressNode1, host1Address);
        assertEquals(retrievedAddressNode1, addressCache.get(node1));
        // put was not called this time, already in cache
        verify(addressCache, times(1)).put(node1, host1Address);

        verify(addressCache, never()).put(node2, host2Address);
        InetAddress retrievedAddressNode2 = jsonRpcWeb3FilterHandler.getInetAddress(node2);
        assertEquals(retrievedAddressNode2, host2Address);
        assertEquals(retrievedAddressNode2, addressCache.get(node2));
        // put was called this time, not yet in cache as it was full
        verify(addressCache, times(1)).put(node2, host2Address);
    }
}