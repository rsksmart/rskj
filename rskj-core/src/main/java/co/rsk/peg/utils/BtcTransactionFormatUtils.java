/*
 * This file is part of RskJ
 * Copyright (C) 2018 RSK Labs Ltd.
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

package co.rsk.peg.utils;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.VarInt;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.peg.InvalidOpReturnOutputException;
import co.rsk.peg.NoOpReturnException;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BtcTransactionFormatUtils {
    private static final Logger logger = LoggerFactory.getLogger(BtcTransactionFormatUtils.class);

    private static final int MIN_BLOCK_HEADER_SIZE = 80;
    private static final int MAX_BLOCK_HEADER_SIZE = 85;

    public static Sha256Hash calculateBtcTxHash(byte[] btcTxSerialized) {
        return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(btcTxSerialized));
    }

    public static long getInputsCount(byte[] btcTxSerialized) {
        VarInt inputsCounter = new VarInt(btcTxSerialized, 4);
        return inputsCounter.value;
    }

    public static long getInputsCountForSegwit(byte[] btcTxSerialized) {
        VarInt inputsCounter = new VarInt(btcTxSerialized, 4);

        if (inputsCounter.value != 0) {
            return -1;
        }

        inputsCounter = new VarInt(btcTxSerialized, 5);

        if (inputsCounter.value != 1) {
            return -1;
        }

        inputsCounter = new VarInt(btcTxSerialized, 6);
        return inputsCounter.value;
    }

    public static boolean isBlockHeaderSize(int size, ActivationConfig.ForBlock activations) {
        return (activations.isActive(ConsensusRule.RSKIP124) && size == MIN_BLOCK_HEADER_SIZE) ||
                (!activations.isActive(ConsensusRule.RSKIP124) && size >= MIN_BLOCK_HEADER_SIZE && size <= MAX_BLOCK_HEADER_SIZE);
    }

    public static byte[] extractOpReturnData(BtcTransaction btcTx) throws NoOpReturnException, InvalidOpReturnOutputException {
        byte[] data = new byte[]{};
        int opReturnOccurrences = 0;

        logger.info("[getOpReturnOutput] Getting OP_RETURN data for btc tx: {}", btcTx.getHash());

        for (int i=0; i<btcTx.getOutputs().size(); i++) {
            List<ScriptChunk> chunksByOutput = btcTx.getOutput(i).getScriptPubKey().getChunks();
            if (chunksByOutput.get(0).opcode == ScriptOpCodes.OP_RETURN) {
                if (chunksByOutput.size() > 1) {
                    data = btcTx.getOutput(i).getScriptPubKey().getChunks().get(1).data;
                    opReturnOccurrences++;
                } else {
                    opReturnOccurrences++;
                    data = null;
                }
            }
        }

        if (opReturnOccurrences == 0) {
            String message = "No OP_RETURN output found for tx";
            logger.debug("[getOpReturnOutput] {}", message);
            throw new NoOpReturnException(message);
        }

        if (opReturnOccurrences > 1) {
            String message = "Only one output with OP_RETURN is allowed";
            logger.debug("[getOpReturnOutput] {}", message);
            throw new InvalidOpReturnOutputException(message);
        }

        return data;
    }
}
