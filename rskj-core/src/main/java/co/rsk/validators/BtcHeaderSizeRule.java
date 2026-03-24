/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package co.rsk.validators;

import co.rsk.config.RskMiningConstants;

/**
 * Validation rule for BTC merged mining header size.
 *
 * - The Bitcoin merged mining header must be exactly 80 bytes for all
 * post-RSKIP98 blocks.
 * - Before RSKIP98, fallback mining was possible and the field stores an RLP-encoded signature instead of a BTC header.
 * - Genesis block (block 0) is also exempt from validation.
 */
public interface BtcHeaderSizeRule {

    default boolean isValidBtcMergedMiningHeaderSize(byte[] header, long blockNumber, boolean isRskip98Active) {
        if (blockNumber == 0 || !isRskip98Active) {
            return true;
        }

        if (header == null) {
            return false;
        }

        return header.length == RskMiningConstants.BTC_HEADER_SIZE;
    }
}
