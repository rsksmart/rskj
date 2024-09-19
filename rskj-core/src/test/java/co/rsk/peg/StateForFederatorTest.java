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

import static co.rsk.peg.PegTestUtils.createHash3;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.crypto.Keccak256;
import java.util.Arrays;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;

class StateForFederatorTest {

    private static final NetworkParameters NETWORK_PARAMETERS = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

    @Test
    void stateForFederator_whenSerializeAndDeserialize_shouldHaveEqualState() {
        // Arrange
        Keccak256 rskTxHash1 = createHash3(1);
        Keccak256 rskTxHash2 = createHash3(2);

        BtcTransaction tx1 = new BtcTransaction(NETWORK_PARAMETERS);
        BtcTransaction tx2 = new BtcTransaction(NETWORK_PARAMETERS);

        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = new TreeMap<>();
        rskTxsWaitingForSignatures.put(rskTxHash1, tx1);
        rskTxsWaitingForSignatures.put(rskTxHash2, tx2);

        StateForFederator stateForFederator = new StateForFederator(rskTxsWaitingForSignatures);

        // Act
        byte[] rlpData = stateForFederator.encodeToRlp();
        StateForFederator deserializedStateForFederator = fromRlpData(rlpData, NETWORK_PARAMETERS);

        // Assert
        assertNotNull(deserializedStateForFederator);
        assertEquals(2, deserializedStateForFederator.getRskTxsWaitingForSignatures().size());
        assertEquals(tx1, deserializedStateForFederator.getRskTxsWaitingForSignatures().get(rskTxHash1));
        assertEquals(tx2, deserializedStateForFederator.getRskTxsWaitingForSignatures().get(rskTxHash2));
        assertTrue(containsRskTxHashes(
            deserializedStateForFederator.getRskTxsWaitingForSignatures().keySet(),
            rskTxHash1, rskTxHash2));
    }

    private static StateForFederator fromRlpData(byte[] rlpData, NetworkParameters parameters) {
        RLPList rlpList = (RLPList) RLP.decode2(rlpData).get(0);
        byte[] encodedRskTxsWaitingForSignatures = rlpList.get(0).getRLPData();
        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = 
            BridgeSerializationUtils.deserializeRskTxsWaitingForSignatures(encodedRskTxsWaitingForSignatures, parameters);

        return new StateForFederator(rskTxsWaitingForSignatures);
    }

    private static boolean containsRskTxHashes(Set<Keccak256> txHashes, Keccak256... requiredHashes) {
        return Arrays.stream(requiredHashes).allMatch(txHashes::contains);
    }
}
