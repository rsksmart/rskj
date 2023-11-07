package org.ethereum.vm;
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


import org.ethereum.core.Bloom;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Roman Mandeleil
 * @since 19.11.2014
 */
public class LogInfo {

    byte[] address = new byte[]{};
    List<DataWord> topics = new ArrayList<>();
    byte[] data = new byte[]{};
    private boolean rejected = false;

    /* Log info in encoded form */
    private byte[] rlpEncoded;

    public LogInfo(byte[] rlp) {

        RLPList logInfo = RLP.decodeList(rlp);

        RLPItem address = (RLPItem) logInfo.get(0);
        RLPList topics = (RLPList) logInfo.get(1);
        RLPItem data = (RLPItem) logInfo.get(2);

        this.address = address.getRLPData() != null ? address.getRLPData() : new byte[]{};
        this.data = data.getRLPData() != null ? data.getRLPData() : new byte[]{};

        for (int k = 0; k < topics.size(); k++) {
            RLPElement topic1 = topics.get(k);
            byte[] topic = topic1.getRLPData();
            this.topics.add(DataWord.valueOf(topic));
        }

        rlpEncoded = rlp;
    }

    public LogInfo(byte[] address, List<DataWord> topics, byte[] data) {
        this.address = (address != null) ? address : new byte[]{};
        this.topics = (topics != null) ? topics : new ArrayList<DataWord>();
        this.data = (data != null) ? data : new byte[]{};
    }

    public byte[] getAddress() {
        return address;
    }

    public List<DataWord> getTopics() {
        return topics;
    }

    public byte[] getData() {
        return data;
    }

    /*  [address, [topic, topic ...] data] */
    public byte[] getEncoded() {

        byte[] addressEncoded = RLP.encodeElement(this.address);

        byte[][] topicsEncoded = null;
        if (topics != null) {
            topicsEncoded = new byte[topics.size()][];
            int i = 0;
            for (DataWord topic : topics) {
                byte[] topicData = topic.getData();
                topicsEncoded[i] = RLP.encodeElement(topicData);
                ++i;
            }
        }

        byte[] dataEncoded = RLP.encodeElement(data);
        return RLP.encodeList(addressEncoded, RLP.encodeList(topicsEncoded), dataEncoded);
    }

    public Bloom getBloom() {
        Bloom ret = Bloom.create(HashUtil.keccak256(address));
        for (DataWord topic : topics) {
            byte[] topicData = topic.getData();
            ret.or(Bloom.create(HashUtil.keccak256(topicData)));
        }
        return ret;
    }

    public boolean isRejected() {
        return rejected;
    }

    public void reject() {
        this.rejected = true;
    }

    public static List<DataWord> byteArrayToList(byte[][] data) {
        return Arrays.stream(data).map(DataWord::valueOf).collect(Collectors.toList());
    }

    @Override
    public String toString() {

        StringBuilder topicsStr = new StringBuilder();
        topicsStr.append("[");

        for (DataWord topic : topics) {
            String topicStr = ByteUtil.toHexString(topic.getData());
            topicsStr.append(topicStr).append(" ");
        }
        topicsStr.append("]");


        return "LogInfo{" +
                "address=" + ByteUtil.toHexString(address) +
                ", topics=" + topicsStr +
                ", data=" + ByteUtil.toHexString(data) +
                '}';
    }


}
