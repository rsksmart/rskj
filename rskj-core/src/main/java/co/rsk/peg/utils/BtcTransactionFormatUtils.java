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

import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.bitcoinj.core.VarInt;

public class BtcTransactionFormatUtils {
    private static int MIN_BLOCK_HEADER_SIZE = 80;
    private static int MAX_BLOCK_HEADER_SIZE = 85;

    public static Sha256Hash calculateBtcTxHash(byte[] btcTxSerialized) {
        return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(btcTxSerialized));
    }

    public static long getInputsCount(byte[] btcTxSerialized) {
        VarInt inputsCounter = new VarInt(btcTxSerialized, 4);
        return inputsCounter.value;
    }

    public static boolean isBlockHeaderSize(int size) {
        return size >= MIN_BLOCK_HEADER_SIZE && size <= MAX_BLOCK_HEADER_SIZE;
    }
}
