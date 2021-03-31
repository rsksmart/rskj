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

package org.ethereum.jsontestsuite.validators;

import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.LogInfo;

import java.util.ArrayList;
import java.util.List;

public class LogsValidator {

    public static List<String> valid(List<LogInfo> origLogs, List<LogInfo> postLogs,ValidationStats vStats) {

        List<String> results = new ArrayList<>();

        int i = 0;
        for (LogInfo postLog : postLogs) {
            if (vStats!=null) vStats.logChecks++;
            if (origLogs == null || origLogs.size() - 1 < i){
                String formattedString = String.format("Log: %s: was expected but doesn't exist: address: %s",
                        i, ByteUtil.toHexString(postLog.getAddress()));
                results.add(formattedString);

                continue;
            }

            LogInfo realLog = origLogs.get(i);

            String postAddress = ByteUtil.toHexString(postLog.getAddress());
            String realAddress = ByteUtil.toHexString(realLog.getAddress());
            if (vStats!=null) vStats.logChecks++;
            if (!postAddress.equals(realAddress)) {

                String formattedString = String.format("Log: %s: has unexpected address, expected address: %s found address: %s",
                        i, postAddress, realAddress);
                results.add(formattedString);
            }

            String postData = ByteUtil.toHexString(postLog.getData());
            String realData = ByteUtil.toHexString(realLog.getData());
            if (vStats!=null) vStats.logChecks++;
            if (!postData.equals(realData)) {

                String formattedString = String.format("Log: %s: has unexpected data, expected data: %s found data: %s",
                        i, postData, realData);
                results.add(formattedString);
            }

            String postBloom = ByteUtil.toHexString(postLog.getBloom().getData());
            String realBloom = ByteUtil.toHexString(realLog.getBloom().getData());
            if (vStats!=null) vStats.logChecks++;
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
                if (vStats!=null) vStats.logChecks++;
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
