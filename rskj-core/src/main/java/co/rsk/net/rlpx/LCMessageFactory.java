/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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

package co.rsk.net.rlpx;

import co.rsk.net.light.LightClientMessageCodes;
import co.rsk.net.light.message.*;
import org.ethereum.core.BlockFactory;
import org.ethereum.net.message.Message;

public class LCMessageFactory {
    private BlockFactory blockFactory;

    public LCMessageFactory(BlockFactory blockFactory) {
        this.blockFactory = blockFactory;
    }

    public Message create(byte code, byte[] encoded) {
        LightClientMessageCodes receivedCommand = LightClientMessageCodes.fromByte(code);
        switch (receivedCommand) {
            case STATUS:
                return new StatusMessage(encoded);
            case GET_BLOCK_RECEIPTS:
                return new GetBlockReceiptsMessage(encoded);
            case BLOCK_RECEIPTS:
                return new BlockReceiptsMessage(encoded);
            case GET_TRANSACTION_INDEX:
                return new GetTransactionIndexMessage(encoded);
            case TRANSACTION_INDEX:
                return new TransactionIndexMessage(encoded);
            case GET_CODE:
                return new GetCodeMessage(encoded);
            case CODE:
                return new CodeMessage(encoded);
            case GET_ACCOUNTS:
                return new GetAccountsMessage(encoded);
            case ACCOUNTS:
                return new AccountsMessage(encoded);
            case GET_BLOCK_HEADER:
                return new GetBlockHeaderMessage(encoded);
            case BLOCK_HEADER:
                return new BlockHeaderMessage(encoded, blockFactory);
            case GET_BLOCK_BODY:
                return new GetBlockBodyMessage(encoded);
            case BLOCK_BODY:
                return new BlockBodyMessage(encoded, blockFactory);
            case GET_STORAGE:
                return new GetStorageMessage(encoded);
            case STORAGE:
                return new StorageMessage(encoded);
            default:
                throw new IllegalArgumentException("No such message");
        }
    }
}