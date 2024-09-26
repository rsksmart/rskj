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

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.crypto.Keccak256;
import java.util.Collections;
import java.util.Objects;
import java.util.SortedMap;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

public class StateForFederator {

    private final SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures;

    public StateForFederator(SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures) {
        Objects.requireNonNull(rskTxsWaitingForSignatures);

        this.rskTxsWaitingForSignatures = Collections.unmodifiableSortedMap(rskTxsWaitingForSignatures);
    }

    public StateForFederator(byte[] rlpData, NetworkParameters networkParameters) {
        this(
            BridgeSerializationUtils.deserializeRskTxsWaitingForSignatures(
                decodeRlpToMap(rlpData), networkParameters));
    }

    public SortedMap<Keccak256, BtcTransaction> getRskTxsWaitingForSignatures() {
        return rskTxsWaitingForSignatures;
    }

    /**
     * Encodes the current state into RLP format.
     * 
     * @return The RLP-encoded byte array representing the current state.
     */
    public byte[] encodeToRlp() {
        byte[] serializedRskTxsWaitingForSignatures = 
            BridgeSerializationUtils.serializeRskTxsWaitingForSignatures(rskTxsWaitingForSignatures);
        return RLP.encodeList(serializedRskTxsWaitingForSignatures);
    }

    private static byte[] decodeRlpToMap(byte[] rlpData) {
        Objects.requireNonNull(rlpData);

        RLPList rlpList = (RLPList) RLP.decode2(rlpData).get(0);
        return rlpList.get(0).getRLPData();
    }
}
