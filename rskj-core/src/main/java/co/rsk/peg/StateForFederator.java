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

import co.rsk.crypto.Sha3Hash;
import org.apache.commons.lang3.tuple.Pair;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.BtcTransaction;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPList;

import java.util.SortedMap;

/**
 * Created by mario on 20/04/17.
 */
public class StateForFederator {
    private SortedMap<Sha3Hash, BtcTransaction> rskTxsWaitingForSignatures;

    public StateForFederator(SortedMap<Sha3Hash, BtcTransaction> rskTxsWaitingForSignatures) {
        this.rskTxsWaitingForSignatures = rskTxsWaitingForSignatures;
    }

    public StateForFederator(byte[] rlpData, NetworkParameters parameters) {
        RLPList rlpList = (RLPList) RLP.decode2(rlpData).get(0);
        byte[] encodedWaitingForSign = rlpList.get(0).getRLPData();

        this.rskTxsWaitingForSignatures = BridgeSerializationUtils.deserializeMap(encodedWaitingForSign, parameters, false);
    }

    public SortedMap<Sha3Hash, BtcTransaction> getRskTxsWaitingForSignatures() {
        return rskTxsWaitingForSignatures;
    }

    public byte[] getEncoded() {
        byte[] encodedWaitingForSign = BridgeSerializationUtils.serializeMap(this.rskTxsWaitingForSignatures);
        return RLP.encodeList(encodedWaitingForSign);
    }
}
