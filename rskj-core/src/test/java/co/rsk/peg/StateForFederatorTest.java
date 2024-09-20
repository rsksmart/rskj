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

package co.rsk.peg;

import static co.rsk.RskTestUtils.createHash;
import static org.ethereum.TestUtils.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.crypto.Keccak256;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class StateForFederatorTest {

    private static final NetworkParameters NETWORK_PARAMETERS = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

    @Test
    void stateForFederator_whenSerializeAndDeserialize_shouldHaveEqualState() {
        // Arrange
        Keccak256 rskTxHash1 = createHash(1);
        Keccak256 rskTxHash2 = createHash(2);

        BtcTransaction tx1 = new BtcTransaction(NETWORK_PARAMETERS);
        BtcTransaction tx2 = new BtcTransaction(NETWORK_PARAMETERS);

        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = new TreeMap<>();
        rskTxsWaitingForSignatures.put(rskTxHash1, tx1);
        rskTxsWaitingForSignatures.put(rskTxHash2, tx2);

        // Act
        StateForFederator stateForFederator = 
            new StateForFederator(rskTxsWaitingForSignatures);
        StateForFederator deserializedStateForFederator = 
            new StateForFederator(stateForFederator.encodeToRlp(), NETWORK_PARAMETERS);

        // Assert
        assertNotNull(deserializedStateForFederator);
        assertEquals(rskTxsWaitingForSignatures,
            deserializedStateForFederator.getRskTxsWaitingForSignatures());
    }

    @Test
    void stateForFederator_whenEmptyMapAndSerializeAndDeserialize_shouldHaveEqualState() {
        // Arrange
        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = new TreeMap<>();

        // Act
        StateForFederator stateForFederator = 
            new StateForFederator(rskTxsWaitingForSignatures);
        StateForFederator deserializedStateForFederator = 
            new StateForFederator(stateForFederator.encodeToRlp(), NETWORK_PARAMETERS);

        // Assert
        assertNotNull(deserializedStateForFederator);
        assertEquals(rskTxsWaitingForSignatures,
            deserializedStateForFederator.getRskTxsWaitingForSignatures());
    }
    
    @Test
    void stateForFederator_whenNullValueAndSerializeAndDeserialize_shouldThrowNullPointerException() {
        // Assert
        assertThrows(NullPointerException.class, () -> new StateForFederator(null));
        assertThrows(NullPointerException.class, () -> new StateForFederator(null, NETWORK_PARAMETERS));
    }
}
