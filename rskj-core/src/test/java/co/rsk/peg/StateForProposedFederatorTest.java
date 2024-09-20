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

import static co.rsk.RskTestUtils.createHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.crypto.Keccak256;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StateForProposedFederatorTest {

    private static final NetworkParameters NETWORK_PARAMETERS = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);

    @ParameterizedTest
    @MethodSource("provideSvpSpendTxWaitingForSignatures")
    void stateForProposedFederator_whenSerializeAndDeserialize_shouldHaveEqualState(
          Map.Entry<Keccak256, BtcTransaction> svpSpendTxWaitingForSignatures) {
        // Act
        StateForProposedFederator stateForProposedFederator =
            new StateForProposedFederator(svpSpendTxWaitingForSignatures);
        StateForProposedFederator deserializedStateForProposedFederator =
            new StateForProposedFederator(stateForProposedFederator.encodeToRlp(), NETWORK_PARAMETERS);

        // Assert
        assertNotNull(deserializedStateForProposedFederator);
        assertEquals(svpSpendTxWaitingForSignatures,
            deserializedStateForProposedFederator.getSvpSpendTxWaitingForSignatures());
    }

    private static Stream<Arguments> provideSvpSpendTxWaitingForSignatures() {
        Keccak256 rskTxHash = createHash(1);
        BtcTransaction tx = new BtcTransaction(NETWORK_PARAMETERS);
        
        // Non-null entry
        Map.Entry<Keccak256, BtcTransaction> nonNullEntry =
            new AbstractMap.SimpleEntry<>(rskTxHash, tx);
        
        // Null entry
        Map.Entry<Keccak256, BtcTransaction> nullEntry = null;

        return Stream.of(
            Arguments.of(nonNullEntry),
            Arguments.of(nullEntry));
    }
}
