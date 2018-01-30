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

package org.ethereum.rpc;

import co.rsk.core.RskAddress;
import org.ethereum.core.Bloom;
import org.ethereum.crypto.SHA3Helper;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;

import java.util.ArrayList;
import java.util.List;

public class AddressesTopicsFilter {
    private List<Topic[]> topics = new ArrayList<>();  //  [[addr1, addr2], null, [A, B], [C]]
    private RskAddress[] addresses = new RskAddress[0];
    private Bloom[][] filterBlooms;

    public AddressesTopicsFilter(RskAddress[] addresses, Topic[] topics) {
        if (topics != null) {
            this.topics.add(topics);
        }

        this.addresses = addresses;

        initBlooms();
    }

    private void initBlooms() {
        if (filterBlooms != null) {
            return;
        }

        List<byte[][]> addrAndTopics = new ArrayList<>();

        for (Topic[] toplist : topics) {
            byte[][] tops = new byte[toplist.length][];

            for (int k = 0; k < tops.length; k++) {
                tops[k] = toplist[k].getBytes();
            }

            addrAndTopics.add(tops);
        }

        byte[][] addrs = new byte[addresses.length][];

        for (int k = 0; k < addresses.length; k++) {
            addrs[k] = addresses[k].getBytes();
        }

        addrAndTopics.add(addrs);

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

    boolean matchesContractAddress(RskAddress toAddr) {
        for (RskAddress address : addresses) {
            if (address.equals(toAddr)) {
                return true;
            }
        }

        return addresses.length == 0;
    }

    public boolean matchesExactly(LogInfo logInfo) {
        if (!matchesContractAddress(new RskAddress(logInfo.getAddress()))) {
            return false;
        }

        List<DataWord> logTopics = logInfo.getTopics();

        for (int i = 0; i < this.topics.size(); i++) {
            if (i >= logTopics.size()) {
                return false;
            }

            Topic[] orTopics = topics.get(i);

            if (orTopics != null && orTopics.length > 0) {
                boolean orMatches = false;
                DataWord logTopic = logTopics.get(i);

                for (Topic orTopic : orTopics) {
                    if (new DataWord(orTopic.getBytes()).equals(logTopic)) {
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
