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

import co.rsk.rpc.modules.rsk.RskModule;
import org.ethereum.rpc.dto.ProofResultDTO;
import org.ethereum.rpc.parameters.BlockRefParam;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDataArrayParam;
import org.ethereum.rpc.parameters.HexNumberParam;

public interface Web3RskModule {

    default String rsk_getRawTransactionReceiptByHash(String transactionHash) {
        return getRskModule().getRawTransactionReceiptByHash(transactionHash);
    }

    default String[] rsk_getTransactionReceiptNodesByHash(String blockHash, String transactionHash) {
        return getRskModule().getTransactionReceiptNodesByHash(blockHash, transactionHash);
    }

    default String rsk_getRawBlockHeaderByHash(String blockHash) {
        return getRskModule().getRawBlockHeaderByHash(blockHash);
    }

    default String rsk_getRawBlockHeaderByNumber(String bnOrId) {
        return getRskModule().getRawBlockHeaderByNumber(bnOrId);
    }

    default void rsk_shutdown() {
        getRskModule().shutdown();
    }

    default void rsk_flush() {
        getRskModule().flush();
    }

    RskModule getRskModule();

    String rsk_getStorageBytesAt(HexAddressParam address, HexNumberParam storageIdx, BlockRefParam blockRefParam);

    /**
     * Returns the account and storage values with Merkle proofs using RSKj's native unified trie format.
     * This differs from eth_getProof which returns Ethereum-compatible proofs.
     *
     * @param address The address of the account
     * @param storageKeys Array of storage keys to prove
     * @param blockRefParam Block identifier
     * @return ProofResultDTO containing account info and proofs
     */
    ProofResultDTO rsk_getProof(HexAddressParam address, HexDataArrayParam storageKeys, BlockRefParam blockRefParam) throws Exception;

}
