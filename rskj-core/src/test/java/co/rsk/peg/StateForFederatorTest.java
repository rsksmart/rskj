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
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.crypto.Keccak256;
import co.rsk.peg.utils.ScriptBuilderWrapper;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Created by mario on 20/04/17.
 */
public class StateForFederatorTest {

    private static final String SHA3_1 = "1111111111111111111111111111111111111111111111111111111111111111";
    private static final String SHA3_2 = "2222222222222222222222222222222222222222222222222222222222222222";
    private static final String SHA3_3 = "3333333333333333333333333333333333333333333333333333333333333333";
    private static final String SHA3_4 = "4444444444444444444444444444444444444444444444444444444444444444";

    private static final NetworkParameters NETWORK_PARAMETERS = BridgeRegTestConstants.getInstance().getBtcParams();

    private final ScriptBuilderWrapper scriptBuilderWrapper = ScriptBuilderWrapper.getInstance();
    private final BridgeSerializationUtils bridgeSerializationUtils = BridgeSerializationUtils.getInstance(scriptBuilderWrapper);

    @Test
    public void serialize() {
        Keccak256 hash1 = new Keccak256(SHA3_1);
        Keccak256 hash2 = new Keccak256(SHA3_2);
        Keccak256 hash3 = new Keccak256(SHA3_3);
        Keccak256 hash4 = new Keccak256(SHA3_4);

        BtcTransaction tx1 = new BtcTransaction(NETWORK_PARAMETERS);
        BtcTransaction tx2 = new BtcTransaction(NETWORK_PARAMETERS);
        BtcTransaction tx3 = new BtcTransaction(NETWORK_PARAMETERS);
        BtcTransaction tx4 = new BtcTransaction(NETWORK_PARAMETERS);

        SortedMap<Keccak256, BtcTransaction> rskTxsWaitingForSignatures = new TreeMap<>();
        rskTxsWaitingForSignatures.put(hash1, tx1);
        rskTxsWaitingForSignatures.put(hash2, tx2);

        SortedMap<Keccak256, Pair<BtcTransaction, Long>> rskTxsWaitingForBroadcasting = new TreeMap<>();
        rskTxsWaitingForBroadcasting.put(hash3, Pair.of(tx3, 3L));
        rskTxsWaitingForBroadcasting.put(hash4, Pair.of(tx4, 4L));

        StateForFederator stateForFederator = new StateForFederator(rskTxsWaitingForSignatures, bridgeSerializationUtils);

        byte[] encoded = stateForFederator.getEncoded();

        Assert.assertTrue(encoded.length > 0);

        StateForFederator reverseResult = new StateForFederator(encoded, NETWORK_PARAMETERS, bridgeSerializationUtils);

        Assert.assertNotNull(reverseResult);
        Assert.assertEquals(2, reverseResult.getRskTxsWaitingForSignatures().size());

        Assert.assertEquals(tx1, reverseResult.getRskTxsWaitingForSignatures().get(hash1));
        Assert.assertEquals(tx2, reverseResult.getRskTxsWaitingForSignatures().get(hash2));

        Assert.assertTrue(checkKeys(reverseResult.getRskTxsWaitingForSignatures().keySet(), hash1, hash2));
    }

    private boolean checkKeys(Set<Keccak256> keccak256s, Keccak256... keys) {
        for(Keccak256 sha3 : keys)
            if(!keccak256s.contains(sha3))
                return false;
        return true;
    }
}
