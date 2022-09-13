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

package org.ethereum.jsontestsuite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Logs {
    List<LogInfo> logs = new ArrayList<>();

    public Logs(ArrayNode jLogs) {

        for (Object jLog1 : jLogs) {

            ObjectNode jLog = (ObjectNode) jLog1;
            byte[] address = Hex.decode(jLog.get("address").asText());
            byte[] data = Hex.decode(jLog.get("data").asText().substring(2));

            List<DataWord> topics = new ArrayList<>();

            ArrayNode jTopics = (ArrayNode) jLog.get("topics");
            for (JsonNode t : jTopics) {
                byte[] topic = Hex.decode(t.asText());
                topics.add(DataWord.valueOf(topic));
            }

            LogInfo li = new LogInfo(address, topics, data);
            logs.add(li);
        }
    }


    public Iterator<LogInfo> getIterator() {
        return logs.iterator();
    }


    public List<String> compareToReal(List<LogInfo> logs) {

        List<String> results = new ArrayList<>();

        int i = 0;
        for (LogInfo postLog : this.logs) {

            LogInfo realLog = logs.get(i);

            String postAddress = ByteUtil.toHexString(postLog.getAddress());
            String realAddress = ByteUtil.toHexString(realLog.getAddress());

            if (!postAddress.equals(realAddress)) {

                String formattedString = String.format("Log: %s: has unexpected address, expected address: %s found address: %s",
                        i, postAddress, realAddress);
                results.add(formattedString);
            }

            String postData = ByteUtil.toHexString(postLog.getData());
            String realData = ByteUtil.toHexString(realLog.getData());

            if (!postData.equals(realData)) {

                String formattedString = String.format("Log: %s: has unexpected data, expected data: %s found data: %s",
                        i, postData, realData);
                results.add(formattedString);
            }

            String postBloom = ByteUtil.toHexString(postLog.getBloom().getData());
            String realBloom = ByteUtil.toHexString(realLog.getBloom().getData());

            if (!postData.equals(realData)) {

                String formattedString = String.format("Log: %s: has unexpected bloom, expected bloom: %s found bloom: %s",
                        i, postBloom, realBloom);
                results.add(formattedString);
            }

            List<DataWord> postTopics = postLog.getTopics();
            List<DataWord> realTopics = realLog.getTopics();

            int j = 0;
            for (DataWord postTopic : postTopics) {

                DataWord realTopic = realTopics.get(j);

                if (!postTopic.equals(realTopic)) {

                    String formattedString = String.format("Log: %s: has unexpected topic: %s, expected topic: %s found topic: %s",
                            i, j, postTopic, realTopic);
                    results.add(formattedString);
                }
                ++j;
            }

            ++i;
        }

        return results;
    }

}
