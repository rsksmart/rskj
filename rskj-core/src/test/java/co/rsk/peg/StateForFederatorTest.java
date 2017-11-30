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

import co.rsk.config.RskSystemProperties;
import co.rsk.crypto.Sha3Hash;
import org.apache.commons.lang3.tuple.Pair;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.core.BtcTransaction;
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

    private static final NetworkParameters NETWORK_PARAMETERS = RskSystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();

    @Test
    public void serialize() {
        Sha3Hash sha3Hash1 = new Sha3Hash(SHA3_1);
        Sha3Hash sha3Hash2 = new Sha3Hash(SHA3_2);
        Sha3Hash sha3Hash3 = new Sha3Hash(SHA3_3);
        Sha3Hash sha3Hash4 = new Sha3Hash(SHA3_4);

        BtcTransaction tx1 = new BtcTransaction(NETWORK_PARAMETERS);
        BtcTransaction tx2 = new BtcTransaction(NETWORK_PARAMETERS);
        BtcTransaction tx3 = new BtcTransaction(NETWORK_PARAMETERS);
        BtcTransaction tx4 = new BtcTransaction(NETWORK_PARAMETERS);

        SortedMap<Sha3Hash, BtcTransaction> rskTxsWaitingForSignatures = new TreeMap<>();
        rskTxsWaitingForSignatures.put(sha3Hash1, tx1);
        rskTxsWaitingForSignatures.put(sha3Hash2, tx2);

        SortedMap<Sha3Hash, Pair<BtcTransaction, Long>> rskTxsWaitingForBroadcasting = new TreeMap<>();
        rskTxsWaitingForBroadcasting.put(sha3Hash3, Pair.of(tx3, 3L));
        rskTxsWaitingForBroadcasting.put(sha3Hash4, Pair.of(tx4, 4L));

        StateForFederator stateForFederator = new StateForFederator(rskTxsWaitingForSignatures);

        byte[] encoded = stateForFederator.getEncoded();

        Assert.assertTrue(encoded.length > 0);

        StateForFederator reverseResult = new StateForFederator(encoded, NETWORK_PARAMETERS);

        Assert.assertNotNull(reverseResult);
        Assert.assertEquals(2, reverseResult.getRskTxsWaitingForSignatures().size());

        Assert.assertEquals(tx1, reverseResult.getRskTxsWaitingForSignatures().get(sha3Hash1));
        Assert.assertEquals(tx2, reverseResult.getRskTxsWaitingForSignatures().get(sha3Hash2));

        Assert.assertTrue(checkKeys(reverseResult.getRskTxsWaitingForSignatures().keySet(), sha3Hash1, sha3Hash2));
    }

    private boolean checkKeys(Set<Sha3Hash> sha3Hashes, Sha3Hash ... keys) {
        for(Sha3Hash sha3 : keys)
            if(!sha3Hashes.contains(sha3))
                return false;
        return true;
    }
}
