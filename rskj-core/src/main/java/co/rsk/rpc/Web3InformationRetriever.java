/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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

import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.db.RepositoryLocator;
import org.ethereum.core.Block;
import org.ethereum.core.Blockchain;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionPool;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.util.List;
import java.util.Optional;

import static org.ethereum.rpc.TypeConverter.stringHexToBigInteger;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.blockNotFound;
import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

/**
 * Retrieves information requested by web3 based on the block identifier:
 * <p>
 * HEX String  - an integer block number
 * String "earliest"  for the earliest/genesis block
 * String "latest"  - for the latest mined block
 * String "pending"  - for the pending state/transactions
 */
public class Web3InformationRetriever {

    private static final String EARLIEST = "earliest";
    private static final String LATEST = "latest";
    private static final String PENDING = "pending";

    private final TransactionPool transactionPool;
    private final Blockchain blockchain;
    private final RepositoryLocator locator;
    private final ExecutionBlockRetriever executionBlockRetriever;

    public Web3InformationRetriever(TransactionPool transactionPool,
                                    Blockchain blockchain,
                                    RepositoryLocator locator,
                                    ExecutionBlockRetriever executionBlockRetriever) {
        this.transactionPool = transactionPool;
        this.blockchain = blockchain;
        this.locator = locator;
        this.executionBlockRetriever = executionBlockRetriever;
    }

    /**
     * Retrieves the block based on the identifier.
     * @param identifier {@link Web3InformationRetriever}
     * @return An optional containing the block if found.
     * @throws RskJsonRpcRequestException if the identifier is an invalid block identifier.
     */
    public Optional<Block> getBlock(String identifier) {
        Block block;
        if (PENDING.equals(identifier)) {
            block = executionBlockRetriever.getExecutionBlock_workaround(identifier).getBlock();
        } else if (LATEST.equals(identifier)) {
            block = blockchain.getBestBlock();
        } else if (EARLIEST.equals(identifier)) {
            block = blockchain.getBlockByNumber(0);
        } else {
            block = this.blockchain.getBlockByNumber(getBlockNumber(identifier));
        }

        return Optional.ofNullable(block);
    }

    /**
     * Retrieves an {@link AccountInformationProvider} based on the identifier.
     * @param identifier {@link Web3InformationRetriever}
     * @return The {@link AccountInformationProvider}
     * @throws RskJsonRpcRequestException if the block indicated by the identifier is not found or the state for the
     * block was not found.
     */
    public AccountInformationProvider getInformationProvider(String identifier) {
        if (PENDING.equals(identifier)) {
            return transactionPool.getPendingState();
        }

        Optional<Block> optBlock = getBlock(identifier);
        if (!optBlock.isPresent()) {
            throw blockNotFound(String.format("Block %s not found", identifier));
        }

        Block block = optBlock.get();
        return locator.findSnapshotAt(block.getHeader()).orElseThrow(() -> RskJsonRpcRequestException
                .stateNotFound(String.format("State not found for block with hash %s", block.getHash())));
    }

    /**
     * Retrieves an list of {@link Transaction} based on the identifier
     * @param identifier {@link Web3InformationRetriever}
     * @return The {@link Transaction Transactions} of {@link TransactionPool} if the identifier is {@link #PENDING},
     * if not, the ones on the specified block.
     * @throws RskJsonRpcRequestException if the block indicated by the identifier is not foundl.
     */
    public List<Transaction> getTransactions(String identifier) {
        if (PENDING.equals(identifier)) {
            return transactionPool.getPendingTransactions();
        }

        Optional<Block> block = getBlock(identifier);

        return block.map(Block::getTransactionsList)
                .orElseThrow(() ->
                        blockNotFound(String.format("Block %s not found", identifier)));

    }

    private long getBlockNumber(String identifier) {
        long blockNumber;
        try {
            blockNumber = stringHexToBigInteger(identifier).longValue();
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            throw invalidParamError(String.format("invalid blocknumber %s", identifier));
        }
        return blockNumber;
    }
}
