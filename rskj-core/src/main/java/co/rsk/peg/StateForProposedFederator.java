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

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.crypto.Keccak256;
import java.util.Map;
import org.ethereum.util.RLP;

public class StateForProposedFederator {

    private final Map.Entry<Keccak256, BtcTransaction> rskTxWaitingForSignatures;

    public StateForProposedFederator(Map.Entry<Keccak256, BtcTransaction> rskTxsWaitingForSignatures) {
        this.rskTxWaitingForSignatures = rskTxsWaitingForSignatures;
    }

    public Map.Entry<Keccak256, BtcTransaction> getRskTxWaitingForSignatures() {
        return rskTxWaitingForSignatures;
    }

    /**
     * Encodes the current state into RLP format.
     * 
     * @return The RLP-encoded byte array representing the current state.
     */
    public byte[] encodeToRlp() {
        byte[] serializedRskTxWaitingForSignatures = 
            BridgeSerializationUtils.serializeRskTxWaitingForSignatures(rskTxWaitingForSignatures);
        return RLP.encodeList(serializedRskTxWaitingForSignatures);
    }
}
