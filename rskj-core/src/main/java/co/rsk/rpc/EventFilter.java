package co.rsk.rpc;
/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

import co.rsk.core.bc.EventInfo;
import org.ethereum.core.Bloom;
import org.ethereum.crypto.SHA3Helper;
import org.ethereum.vm.DataWord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EventFilter {

    private List<byte[][]> topics = new ArrayList<>();  //  [[addr1, addr2], null, [A, B], [C]]
    private byte[][] contractAddresses = new byte[0][];
    private Bloom[][] filterBlooms;

    public EventFilter withContractAddress(byte[] ... orAddress) {
        contractAddresses = orAddress;
        return this;
    }

    public EventFilter withTopic(byte[] ... orTopic) {
        topics.add(orTopic);
        return this;
    }

    private void initBlooms() {
        if (filterBlooms != null) {
            return;
        }

        List<byte[][]> addrAndTopics = new ArrayList<>(topics);

        addrAndTopics.add(contractAddresses);

        filterBlooms = new Bloom[addrAndTopics.size()][];
        for (int i = 0; i < addrAndTopics.size(); i++) {
            byte[][] orTopics = addrAndTopics.get(i);
            if (orTopics == null || orTopics.length == 0) {
                filterBlooms[i] = new Bloom[] {new Bloom()}; // always matches
            } else {
                filterBlooms[i] = new Bloom[orTopics.length];
                for (int j = 0; j < orTopics.length; j++) {
                    filterBlooms[i][j] = Bloom.create(SHA3Helper.sha3(orTopics[j]));
                }
            }
        }
    }

    public boolean matchBloom(Bloom blockBloom) {
        initBlooms();
        for (Bloom[] andBloom : filterBlooms) {
            boolean orMatches = false;
            for (Bloom orBloom : andBloom) {
                if (blockBloom.matches(orBloom)) {
                    orMatches = true;
                    break;
                }
            }
            if (!orMatches) {
                return false;
            }
        }
        return true;
    }

    boolean matchesContractAddress(byte[] toAddr) {
        initBlooms();
        for (byte[] address : contractAddresses) {
            if (Arrays.equals(address, toAddr)) {
                return true;
            }
        }
        return contractAddresses.length == 0;
    }

    public boolean matchesExactly(EventInfo eventInfo) {
        initBlooms();
        //if (!matchesContractAddress(eventInfo.getAddress())) {
        //    return false;
        //}

        List<DataWord> logTopics = eventInfo.getTopics();
        for (int i = 0; i < this.topics.size(); i++) {
            if (i >= logTopics.size()) {
                return false;
            }

            byte[][] orTopics = topics.get(i);
            if (orTopics != null && orTopics.length > 0) {
                boolean orMatches = false;
                DataWord logTopic = logTopics.get(i);
                for (byte[] orTopic : orTopics) {
                    if (new DataWord(orTopic).equals(logTopic)) {
                        orMatches = true;
                        break;
                    }
                }
                if (!orMatches) {
                    return false;
                }
            }
        }
        return true;
    }
}
