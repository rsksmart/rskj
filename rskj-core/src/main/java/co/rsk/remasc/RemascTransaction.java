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

import co.rsk.core.RskAddress;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;
import org.ethereum.vm.PrecompiledContracts;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * Tx that invokes Remasc's processMinersFees method.
 * @author Oscar Guindzberg
 */
public class RemascTransaction extends Transaction {
    private static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};

    /**
     * The Remasc transaction is not signed so it has no sender.
     * Due to a bug in the implementation before mainnet release, this address has a special encoding.
     * Instead of the empty array, it is encoded as the array with just one zero.
     * This instance should not be used for any other reason.
     */
    public static final RskAddress REMASC_ADDRESS = new RskAddress(new byte[20]) {
        @Override
        public byte[] getBytes() {
            return ZERO_BYTE_ARRAY;
        }
    };

    public RemascTransaction(byte[] rawData) {
        super(rawData);
    }

    public RemascTransaction(long blockNumber) {
        super(ByteUtil.longToBytesNoLeadZeroes(blockNumber - 1),
                ZERO_BYTE_ARRAY,
                ZERO_BYTE_ARRAY,
                PrecompiledContracts.REMASC_ADDR.getBytes(),
                ZERO_BYTE_ARRAY,
                null,
                (byte) 0);
    }

    @Override
    public long transactionCost(Block block, BlockchainNetConfig netConfig) {
        // RemascTransaction does not pay any fees
        return 0;
    }

    @Override
    public RskAddress getSender() {
        return REMASC_ADDRESS;
    }

    @Override
    public boolean acceptTransactionSignature(byte chainId) {
        // RemascTransaction is not signed and not signature validation should be done
        return true;
    }

    @Override
    protected byte[] calculateEncodedRaw() {
        return encode(null, null, null);
    }

    @Override
    protected byte[] calculateEncoded() {
        // this is encoded as pre EIP-155 for compatibility, because the Remasc transaction chainId is hardcoded to 0
        byte[] v = RLP.encodeElement(EMPTY_BYTE_ARRAY);
        byte[] r = RLP.encodeElement(EMPTY_BYTE_ARRAY);
        byte[] s = RLP.encodeElement(EMPTY_BYTE_ARRAY);

        return encode(v, r, s);
    }
}
