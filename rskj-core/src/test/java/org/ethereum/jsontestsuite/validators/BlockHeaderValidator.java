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

import static org.ethereum.util.ByteUtil.toHexString;

import java.math.BigInteger;
import java.util.ArrayList;
import org.ethereum.core.BlockHeader;

public class BlockHeaderValidator {

    public static ArrayList<String> valid(
            BlockHeader orig, BlockHeader valid, ValidationStats vStats) {

        ArrayList<String> outputSummary = new ArrayList<>();
        if (vStats != null) vStats.blockChecks++;
        if (!orig.getParentHash().equals(valid.getParentHash())) {

            String output =
                    String.format(
                            "wrong block.parentHash: \n expected: %s \n got: %s",
                            toHexString(valid.getParentHash().getBytes()),
                            toHexString(orig.getParentHash().getBytes()));

            outputSummary.add(output);
        }
        if (vStats != null) vStats.blockChecks++;
        if (!toHexString(orig.getUnclesHash()).equals(toHexString(valid.getUnclesHash()))) {

            String output =
                    String.format(
                            "wrong block.unclesHash: \n expected: %s \n got: %s",
                            toHexString(valid.getUnclesHash()), toHexString(orig.getUnclesHash()));

            outputSummary.add(output);
        }
        if (vStats != null) vStats.blockChecks++;
        if (!orig.getCoinbase().equals(valid.getCoinbase())) {

            String output =
                    String.format(
                            "wrong block.coinbase: \n expected: %s \n got: %s",
                            valid.getCoinbase(), orig.getCoinbase());

            outputSummary.add(output);
        }
        if (vStats != null) vStats.blockChecks++;
        if (!toHexString(orig.getStateRoot()).equals(toHexString(valid.getStateRoot()))) {

            String output =
                    String.format(
                            "wrong block.stateRoot: \n expected: %s \n got: %s",
                            toHexString(valid.getStateRoot()), toHexString(orig.getStateRoot()));

            outputSummary.add(output);
        }
        if (vStats != null) vStats.blockChecks++;
        if (!toHexString(orig.getTxTrieRoot()).equals(toHexString(valid.getTxTrieRoot()))) {

            String output =
                    String.format(
                            "wrong block.txTrieRoot: \n expected: %s \n got: %s",
                            toHexString(valid.getTxTrieRoot()), toHexString(orig.getTxTrieRoot()));

            outputSummary.add(output);
        }
        if (vStats != null) vStats.blockChecks++;
        if (!toHexString(orig.getReceiptsRoot()).equals(toHexString(valid.getReceiptsRoot()))) {

            String output =
                    String.format(
                            "wrong block.receiptsRoot: \n expected: %s \n got: %s",
                            toHexString(valid.getReceiptsRoot()),
                            toHexString(orig.getReceiptsRoot()));

            outputSummary.add(output);
        }
        if (vStats != null) vStats.blockChecks++;
        if (!toHexString(orig.getLogsBloom()).equals(toHexString(valid.getLogsBloom()))) {

            String output =
                    String.format(
                            "wrong block.logsBloom: \n expected: %s \n got: %s",
                            toHexString(valid.getLogsBloom()), toHexString(orig.getLogsBloom()));

            outputSummary.add(output);
        }
        if (vStats != null) vStats.blockChecks++;
        if (!orig.getDifficulty().equals(valid.getDifficulty())) {

            String output =
                    String.format(
                            "wrong block.difficulty: \n expected: %s \n got: %s",
                            valid.getDifficulty(), orig.getDifficulty());

            outputSummary.add(output);
        }
        if (vStats != null) vStats.blockChecks++;
        if (orig.getTimestamp() != valid.getTimestamp()) {

            String output =
                    String.format(
                            "wrong block.timestamp: \n expected: %d \n got: %d",
                            valid.getTimestamp(), orig.getTimestamp());

            outputSummary.add(output);
        }
        if (vStats != null) vStats.blockChecks++;
        if (orig.getNumber() != valid.getNumber()) {

            String output =
                    String.format(
                            "wrong block.number: \n expected: %d \n got: %d",
                            valid.getNumber(), orig.getNumber());

            outputSummary.add(output);
        }
        if (vStats != null) vStats.blockChecks++;
        if (!new BigInteger(1, orig.getGasLimit()).equals(new BigInteger(1, valid.getGasLimit()))) {

            String output =
                    String.format(
                            "wrong block.gasLimit: \n expected: %d \n got: %d",
                            new BigInteger(1, valid.getGasLimit()),
                            new BigInteger(1, orig.getGasLimit()));

            outputSummary.add(output);
        }
        if (vStats != null) vStats.blockChecks++;
        if (orig.getGasUsed() != valid.getGasUsed()) {

            String output =
                    String.format(
                            "wrong block.gasUsed: \n expected: %d \n got: %d",
                            valid.getGasUsed(), orig.getGasUsed());

            outputSummary.add(output);
        }
        if (vStats != null) vStats.blockChecks++;
        if (!toHexString(orig.getExtraData()).equals(toHexString(valid.getExtraData()))) {

            String output =
                    String.format(
                            "wrong block.extraData: \n expected: %s \n got: %s",
                            toHexString(valid.getExtraData()), toHexString(orig.getExtraData()));

            outputSummary.add(output);
        }

        return outputSummary;
    }
}
