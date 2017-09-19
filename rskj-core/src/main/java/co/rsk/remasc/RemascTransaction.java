/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.remasc;

import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.PrecompiledContracts;
import org.spongycastle.util.encoders.Hex;

/**
 * Tx that invokes Remasc's processMinersFees method.
 * @author Oscar Guindzberg
 */
public class RemascTransaction extends Transaction {
    private static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};

    public RemascTransaction(byte[] rawData) {
        super(rawData);
    }

    public RemascTransaction(long blockNumber) {
        super(ByteUtil.longToBytesNoLeadZeroes(blockNumber - 1),
                ZERO_BYTE_ARRAY,
                ZERO_BYTE_ARRAY,
                Hex.decode(PrecompiledContracts.REMASC_ADDR),
                ZERO_BYTE_ARRAY,
                null,
                (byte) 0);
    }

    @Override
    public long transactionCost(Block block) {
        // RemascTransaction does not pay any fees
        return 0;
    }

    @Override
    public byte[] getSender() {
        // RemascTransaction is not signed so has no sender
        return new byte[]{0};
    }

    @Override
    public boolean acceptTransactionSignature() {
        // RemascTransaction is not signed and not signature validation should be done
        return true;
    }
}
