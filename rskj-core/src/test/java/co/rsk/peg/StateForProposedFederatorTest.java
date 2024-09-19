/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.crypto.Keccak256;
import java.util.AbstractMap;
import java.util.Map;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;
import org.junit.jupiter.api.Test;

class StateForProposedFederatorTest {

    private static final NetworkParameters NETWORK_PARAMETERS = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

    @Test
    void stateForProposedFederator_whenSerializeAndDeserialize_shouldHaveEqualState() {
        // Arrange
        Keccak256 rskTxHash = createHash3(1);
        BtcTransaction tx = new BtcTransaction(NETWORK_PARAMETERS);

        Map.Entry<Keccak256, BtcTransaction> rskTxWaitingForSignatures = new AbstractMap.SimpleEntry<>(rskTxHash, tx);

        StateForProposedFederator stateForProposedFederator = new StateForProposedFederator(rskTxWaitingForSignatures);

        // Act
        byte[] rlpData = stateForProposedFederator.encodeToRlp();
        StateForProposedFederator deserializedStateForProposedFederator = fromRlpData(rlpData, NETWORK_PARAMETERS);

        // Assert
        assertNotNull(deserializedStateForProposedFederator);
        assertEquals(rskTxWaitingForSignatures, deserializedStateForProposedFederator.getRskTxWaitingForSignatures());
    }

    private static StateForProposedFederator fromRlpData(byte[] rlpData, NetworkParameters parameters) {
        RLPList rlpList = (RLPList) RLP.decode2(rlpData).get(0);
        byte[] encodedRskTxsWaitingForSignatures = rlpList.get(0).getRLPData();
        Map.Entry<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = 
            BridgeSerializationUtils.deserializeRskTxWaitingForSignatures(encodedRskTxsWaitingForSignatures, parameters);

        return new StateForProposedFederator(rskTxsWaitingForSignatures);
    }
}
