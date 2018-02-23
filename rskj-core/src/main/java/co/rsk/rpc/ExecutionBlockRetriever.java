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

package co.rsk.rpc;

import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.rpc.exception.JsonRpcUnimplementedMethodException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Encapsulates the logic to retrieve or create an execution block
 * for Web3 calls.
 */
@Component
public class ExecutionBlockRetriever {
    private final Blockchain blockchain;

    @Autowired
    public ExecutionBlockRetriever(Blockchain blockchain) {
        this.blockchain = blockchain;
    }

    public Block getExecutionBlock(String bnOrId) {
        if ("latest".equals(bnOrId)) {
            return blockchain.getBestBlock();
        }

        throw new JsonRpcUnimplementedMethodException("Method only supports 'latest' as a parameter so far.");
    }
}