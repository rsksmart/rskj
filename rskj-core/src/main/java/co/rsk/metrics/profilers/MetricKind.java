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

package co.rsk.metrics.profilers;

/**
 * List of possible measurement categories (or types).
 * Depending on what is actually being profiled, new categories can be added or
 * categories not needed can be removed
 */
public enum MetricKind {
    // BLOCK_CONNECTION - BLOCK_EXECUTE = Time consumed fetching the block and, after block execution, saving the data
    // that means some DB_READ and DB_WRITE will be included here (and contained in the DB_READ and DB_WRITE categories again)
    BLOCK_CONNECTION,
    BLOCK_EXECUTE,
    PRECOMPILED_CONTRACT_INIT,
    PRECOMPILED_CONTRACT_EXECUTE,
    VM_EXECUTE,
    BLOCK_VALIDATION, //Note some validators call TRIE_GET_HASH
    BLOCK_TXS_VALIDATION, //Note that it internally calls KEY_RECOV_FROM_SIG
    BLOCK_FINAL_STATE_VALIDATION,
    KEY_RECOV_FROM_SIG,
    DB_READ,
    DB_WRITE,
    FILLING_EXECUTED_BLOCK,
    DB_INIT,
    DB_CLOSE,
    DB_DESTROY,
    TRIE_GET_VALUE_FROM_KEY,
    BEFORE_BLOCK_EXEC,
    AFTER_BLOCK_EXEC,
    BUILD_TRIE_FROM_MSG,
    TRIE_TO_MESSAGE, //Currently inactive, to measure, add the hooks in Trie::toMessage() and Trie::toMessageOrchid()
    TRIE_CONVERTER_GET_ACCOUNT_ROOT,
    BLOCKCHAIN_FLUSH,

    // Block header metrics
    BLOCK_HEADERS_REQUEST,
    BLOCK_HEADERS_RESPONSE,

    // SNAP metrics
    SNAP_STATUS_REQUEST,
    SNAP_STATUS_RESPONSE,
    SNAP_BLOCKS_REQUEST,
    SNAP_BLOCKS_RESPONSE,
    SNAP_STATE_CHUNK_REQUEST,
    SNAP_STATE_CHUNK_RESPONSE,
}
