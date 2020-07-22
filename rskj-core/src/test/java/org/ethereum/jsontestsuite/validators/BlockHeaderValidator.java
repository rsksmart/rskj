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

import org.ethereum.core.BlockHeader;

import java.math.BigInteger;
import java.util.ArrayList;

import static org.ethereum.util.ByteUtil.toHexStringOrEmpty;

public class BlockHeaderValidator {


    public static ArrayList<String> valid(BlockHeader orig, BlockHeader valid,ValidationStats vStats) {

        ArrayList<String> outputSummary = new ArrayList<>();
        if (vStats!=null) vStats.blockChecks++;
        if (!orig.getParentHash().equals(valid.getParentHash())) {

            String output =
                    String.format("wrong block.parentHash: \n expected: %s \n got: %s",
                            toHexStringOrEmpty(valid.getParentHash().getBytes()),
                            toHexStringOrEmpty(orig.getParentHash().getBytes())
                    );

            outputSummary.add(output);
        }
        if (vStats!=null) vStats.blockChecks++;
        if (!toHexStringOrEmpty(orig.getUnclesHash())
                .equals(toHexStringOrEmpty(valid.getUnclesHash()))) {

            String output =
                    String.format("wrong block.unclesHash: \n expected: %s \n got: %s",
                            toHexStringOrEmpty(valid.getUnclesHash()),
                            toHexStringOrEmpty(orig.getUnclesHash())
                    );

            outputSummary.add(output);
        }
        if (vStats!=null) vStats.blockChecks++;
        if (!orig.getCoinbase().equals(valid.getCoinbase())) {

            String output =
                    String.format("wrong block.coinbase: \n expected: %s \n got: %s",
                            valid.getCoinbase(),
                            orig.getCoinbase()
                    );

            outputSummary.add(output);
        }
        if (vStats!=null) vStats.blockChecks++;
        if (!toHexStringOrEmpty(orig.getStateRoot())
                .equals(toHexStringOrEmpty(valid.getStateRoot()))) {

            String output =
                    String.format("wrong block.stateRoot: \n expected: %s \n got: %s",
                            toHexStringOrEmpty(valid.getStateRoot()),
                            toHexStringOrEmpty(orig.getStateRoot())
                    );

            outputSummary.add(output);
        }
        if (vStats!=null) vStats.blockChecks++;
        if (!toHexStringOrEmpty(orig.getTxTrieRoot())
                .equals(toHexStringOrEmpty(valid.getTxTrieRoot()))) {

            String output =
                    String.format("wrong block.txTrieRoot: \n expected: %s \n got: %s",
                            toHexStringOrEmpty(valid.getTxTrieRoot()),
                            toHexStringOrEmpty(orig.getTxTrieRoot())
                    );

            outputSummary.add(output);
        }
        if (vStats!=null) vStats.blockChecks++;
        if (!toHexStringOrEmpty(orig.getReceiptsRoot())
                .equals(toHexStringOrEmpty(valid.getReceiptsRoot()))) {

            String output =
                    String.format("wrong block.receiptsRoot: \n expected: %s \n got: %s",
                            toHexStringOrEmpty(valid.getReceiptsRoot()),
                            toHexStringOrEmpty(orig.getReceiptsRoot())
                    );

            outputSummary.add(output);
        }
        if (vStats!=null) vStats.blockChecks++;
        if (!toHexStringOrEmpty(orig.getLogsBloom())
                .equals(toHexStringOrEmpty(valid.getLogsBloom()))) {

            String output =
                    String.format("wrong block.logsBloom: \n expected: %s \n got: %s",
                            toHexStringOrEmpty(valid.getLogsBloom()),
                            toHexStringOrEmpty(orig.getLogsBloom())
                    );

            outputSummary.add(output);
        }
        if (vStats!=null) vStats.blockChecks++;
        if (!orig.getDifficulty().equals(valid.getDifficulty())) {

            String output =
                    String.format("wrong block.difficulty: \n expected: %s \n got: %s",
                            valid.getDifficulty(),
                            orig.getDifficulty()
                    );

            outputSummary.add(output);
        }
        if (vStats!=null) vStats.blockChecks++;
        if (orig.getTimestamp() != valid.getTimestamp()) {

            String output =
                    String.format("wrong block.timestamp: \n expected: %d \n got: %d",
                            valid.getTimestamp(),
                            orig.getTimestamp()
                    );

            outputSummary.add(output);
        }
        if (vStats!=null) vStats.blockChecks++;
        if (orig.getNumber() != valid.getNumber()) {

            String output =
                    String.format("wrong block.number: \n expected: %d \n got: %d",
                            valid.getNumber(),
                            orig.getNumber()
                    );

            outputSummary.add(output);
        }
        if (vStats!=null) vStats.blockChecks++;
        if (!new BigInteger(1, orig.getGasLimit()).equals(new BigInteger(1, valid.getGasLimit()))) {

            String output =
                    String.format("wrong block.gasLimit: \n expected: %d \n got: %d",
                            new BigInteger(1, valid.getGasLimit()),
                            new BigInteger(1, orig.getGasLimit())
                    );

            outputSummary.add(output);
        }
        if (vStats!=null) vStats.blockChecks++;
        if (orig.getGasUsed() != valid.getGasUsed()) {

            String output =
                    String.format("wrong block.gasUsed: \n expected: %d \n got: %d",
                            valid.getGasUsed(),
                            orig.getGasUsed()
                    );

            outputSummary.add(output);
        }
        if (vStats!=null) vStats.blockChecks++;
        if (!toHexStringOrEmpty(orig.getExtraData())
                .equals(toHexStringOrEmpty(valid.getExtraData()))) {

            String output =
                    String.format("wrong block.extraData: \n expected: %s \n got: %s",
                            toHexStringOrEmpty(valid.getExtraData()),
                            toHexStringOrEmpty(orig.getExtraData())
                    );

            outputSummary.add(output);
        }

        return outputSummary;
    }
}
