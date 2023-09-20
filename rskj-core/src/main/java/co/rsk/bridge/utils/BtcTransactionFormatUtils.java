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

package co.rsk.bridge.utils;

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.VarInt;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.config.blockchain.upgrades.ConsensusRule;

public class BtcTransactionFormatUtils {

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
            (!activations.isActive(ConsensusRule.RSKIP124) && size >= MIN_BLOCK_HEADER_SIZE
                && size <= MAX_BLOCK_HEADER_SIZE);
    }
}
