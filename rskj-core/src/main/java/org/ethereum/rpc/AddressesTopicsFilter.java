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
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;

import java.util.ArrayList;
import java.util.List;

public class AddressesTopicsFilter {
    private List<Topic[]> topics = new ArrayList<>();  //  [[addr1, addr2], null, [A, B], [C]]
    private RskAddress[] addresses;
    private Bloom[][] filterBlooms;

    public AddressesTopicsFilter(RskAddress[] addresses, Topic[][] topics) {
        if (topics != null) {
            for (int nt = 0; nt < topics.length; nt++) {
                this.topics.add(topics[nt]);
            }
        }

        this.addresses = addresses == null? new RskAddress[0]: addresses;

        initBlooms();
    }

    private void initBlooms() {
        if (filterBlooms != null) {
            return;
        }

        List<byte[][]> addrAndTopics = new ArrayList<>(2);

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
                    if (orTopics[j] != null) {
                        filterBlooms[i][j] = Bloom.create(Keccak256Helper.keccak256(orTopics[j]));
                    }
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
            if (logTopics.size() <= i) {
                if (topics.get(i).length == 0) {
                    continue;
                }

                return false;
            }

            DataWord logTopic = logTopics.get(i);
            Topic[] orTopics = topics.get(i);

            if (orTopics != null && orTopics.length > 0) {
                if (!matchTopic(logTopic, orTopics)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean matchTopic(DataWord logTopic, Topic[] orTopics) {
        for (Topic orTopic : orTopics) {
            if (DataWord.valueOf(orTopic.getBytes()).equals(logTopic)) {
                return true;
            }
        }

        return false;
    }
}
