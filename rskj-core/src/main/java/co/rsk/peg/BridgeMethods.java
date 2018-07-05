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
package co.rsk.peg;

import org.ethereum.config.BlockchainConfig;
import org.ethereum.core.CallTransaction;
import org.ethereum.db.ByteArrayWrapper;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This enum holds the basic information of the Bridge contract methods: the ABI, the cost and the implementation.
 */
public enum BridgeMethods {
    ADD_FEDERATOR_PUBLIC_KEY(
            CallTransaction.Function.fromSignature(
                    "addFederatorPublicKey",
                    new String[]{"bytes"},
                    new String[]{"int256"}
            ),
            13000L,
            (BridgeMethodExecutorTyped) Bridge::addFederatorPublicKey
    ),
    ADD_LOCK_WHITELIST_ADDRESS(
            CallTransaction.Function.fromSignature(
                    "addLockWhitelistAddress",
                    new String[]{"string", "int256"},
                    new String[]{"int256"}
            ),
            25000L,
            (BridgeMethodExecutorTyped) Bridge::addLockWhitelistAddress
    ),
    ADD_SIGNATURE(
            CallTransaction.Function.fromSignature(
                    "addSignature",
                    new String[]{"bytes", "bytes[]", "bytes"},
                    new String[]{}
            ),
            70000L,
            Bridge.activeAndRetiringFederationOnly((BridgeMethodExecutorVoid) Bridge::addSignature, "addSignature")
    ),
    COMMIT_FEDERATION(
            CallTransaction.Function.fromSignature(
                    "commitFederation",
                    new String[]{"bytes"},
                    new String[]{"int256"}
            ),
            38000L,
            (BridgeMethodExecutorTyped) Bridge::commitFederation
    ),
    CREATE_FEDERATION(
            CallTransaction.Function.fromSignature(
                    "createFederation",
                    new String[]{},
                    new String[]{"int256"}
            ),
            11000L,
            (BridgeMethodExecutorTyped) Bridge::createFederation
    ),
    GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT(
            CallTransaction.Function.fromSignature(
                    "getBtcBlockchainBestChainHeight",
                    new String[]{},
                    new String[]{"int"}
            ),
            19000L,
            (BridgeMethodExecutorTyped) Bridge::getBtcBlockchainBestChainHeight
    ),
    GET_BTC_BLOCKCHAIN_INITIAL_BLOCK_HEIGHT(
            CallTransaction.Function.fromSignature(
                    "getBtcBlockchainInitialBlockHeight",
                    new String[]{},
                    new String[]{"int"}
            ),
            20000L,
            (BridgeMethodExecutorTyped) Bridge::getBtcBlockchainInitialBlockHeight,
            (blockchainConfig -> blockchainConfig.isRfs55())
    ),
    GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR(
            CallTransaction.Function.fromSignature(
                    "getBtcBlockchainBlockLocator",
                    new String[]{},
                    new String[]{"string[]"}
            ),
            76000L,
            (BridgeMethodExecutorTyped) Bridge::getBtcBlockchainBlockLocator
    ),
    GET_BTC_BLOCKCHAIN_BLOCK_HASH_AT_DEPTH(
            CallTransaction.Function.fromSignature(
                    "getBtcBlockchainBlockHashAtDepth",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            20000L,
            (BridgeMethodExecutorTyped) Bridge::getBtcBlockchainBlockHashAtDepth,
            (blockchainConfig -> blockchainConfig.isRfs55())
    ),
    GET_BTC_TX_HASH_PROCESSED_HEIGHT(
            CallTransaction.Function.fromSignature(
                    "getBtcTxHashProcessedHeight",
                    new String[]{"string"},
                    new String[]{"int64"}
            ),
            22000L,
            (BridgeMethodExecutorTyped) Bridge::getBtcTxHashProcessedHeight
    ),
    GET_FEDERATION_ADDRESS(
            CallTransaction.Function.fromSignature(
                    "getFederationAddress",
                    new String[]{},
                    new String[]{"string"}
            ),
            11000L,
            (BridgeMethodExecutorTyped) Bridge::getFederationAddress
    ),
    GET_FEDERATION_CREATION_BLOCK_NUMBER(
            CallTransaction.Function.fromSignature(
                    "getFederationCreationBlockNumber",
                    new String[]{},
                    new String[]{"int256"}
            ),
            10000L,
            (BridgeMethodExecutorTyped) Bridge::getFederationCreationBlockNumber
    ),
    GET_FEDERATION_CREATION_TIME(
            CallTransaction.Function.fromSignature(
                    "getFederationCreationTime",
                    new String[]{},
                    new String[]{"int256"}
            ),
            10000L,
            (BridgeMethodExecutorTyped) Bridge::getFederationCreationTime
    ),
    GET_FEDERATION_SIZE(
            CallTransaction.Function.fromSignature(
                    "getFederationSize",
                    new String[]{},
                    new String[]{"int256"}
            ),
            10000L,
            (BridgeMethodExecutorTyped) Bridge::getFederationSize
    ),
    GET_FEDERATION_THRESHOLD(
            CallTransaction.Function.fromSignature(
                    "getFederationThreshold",
                    new String[]{},
                    new String[]{"int256"}
            ),
            11000L,
            (BridgeMethodExecutorTyped) Bridge::getFederationThreshold
    ),
    GET_FEDERATOR_PUBLIC_KEY(
            CallTransaction.Function.fromSignature(
                    "getFederatorPublicKey",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            10000L,
            (BridgeMethodExecutorTyped) Bridge::getFederatorPublicKey
    ),
    GET_FEE_PER_KB(
            CallTransaction.Function.fromSignature(
                    "getFeePerKb",
                    new String[]{},
                    new String[]{"int256"}
            ),
            2000L,
            (BridgeMethodExecutorTyped) Bridge::getFeePerKb
    ),
    GET_LOCK_WHITELIST_ADDRESS(
            CallTransaction.Function.fromSignature(
                    "getLockWhitelistAddress",
                    new String[]{"int256"},
                    new String[]{"string"}
            ),
            16000L,
            (BridgeMethodExecutorTyped) Bridge::getLockWhitelistAddress
    ),
    GET_LOCK_WHITELIST_SIZE(
            CallTransaction.Function.fromSignature(
                    "getLockWhitelistSize",
                    new String[]{},
                    new String[]{"int256"}
            ),
            16000L,
            (BridgeMethodExecutorTyped) Bridge::getLockWhitelistSize
    ),
    GET_MINIMUM_LOCK_TX_VALUE(
            CallTransaction.Function.fromSignature(
                    "getMinimumLockTxValue",
                    new String[]{},
                    new String[]{"int"}
            ),
            2000L,
            (BridgeMethodExecutorTyped) Bridge::getMinimumLockTxValue
    ),
    GET_PENDING_FEDERATION_HASH(
            CallTransaction.Function.fromSignature(
                    "getPendingFederationHash",
                    new String[]{},
                    new String[]{"bytes"}
            ),
            3000L,
            (BridgeMethodExecutorTyped) Bridge::getPendingFederationHash
    ),
    GET_PENDING_FEDERATION_SIZE(
            CallTransaction.Function.fromSignature(
                    "getPendingFederationSize",
                    new String[]{},
                    new String[]{"int256"}
            ),
            3000L,
            (BridgeMethodExecutorTyped) Bridge::getPendingFederationSize
    ),
    GET_PENDING_FEDERATOR_PUBLIC_KEY(
            CallTransaction.Function.fromSignature(
                    "getPendingFederatorPublicKey",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            3000L,
            (BridgeMethodExecutorTyped) Bridge::getPendingFederatorPublicKey
    ),
    GET_RETIRING_FEDERATION_ADDRESS(
            CallTransaction.Function.fromSignature(
                    "getRetiringFederationAddress",
                    new String[]{},
                    new String[]{"string"}
            ),
            3000L,
            (BridgeMethodExecutorTyped) Bridge::getRetiringFederationAddress
    ),
    GET_RETIRING_FEDERATION_CREATION_BLOCK_NUMBER(
            CallTransaction.Function.fromSignature(
                    "getRetiringFederationCreationBlockNumber",
                    new String[]{},
                    new String[]{"int256"}
            ),
            3000L,
            (BridgeMethodExecutorTyped) Bridge::getRetiringFederationCreationBlockNumber
    ),
    GET_RETIRING_FEDERATION_CREATION_TIME(
            CallTransaction.Function.fromSignature(
                    "getRetiringFederationCreationTime",
                    new String[]{},
                    new String[]{"int256"}
            ),
            3000L,
            (BridgeMethodExecutorTyped) Bridge::getRetiringFederationCreationTime
    ),
    GET_RETIRING_FEDERATION_SIZE(
            CallTransaction.Function.fromSignature(
                    "getRetiringFederationSize",
                    new String[]{},
                    new String[]{"int256"}
            ),
            3000L,
            (BridgeMethodExecutorTyped) Bridge::getRetiringFederationSize
    ),
    GET_RETIRING_FEDERATION_THRESHOLD(
            CallTransaction.Function.fromSignature(
                    "getRetiringFederationThreshold",
                    new String[]{},
                    new String[]{"int256"}
            ),
            3000L,
            (BridgeMethodExecutorTyped) Bridge::getRetiringFederationThreshold
    ),
    GET_RETIRING_FEDERATOR_PUBLIC_KEY(
            CallTransaction.Function.fromSignature(
                    "getRetiringFederatorPublicKey",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            3000L,
            (BridgeMethodExecutorTyped) Bridge::getRetiringFederatorPublicKey
    ),
    GET_STATE_FOR_BTC_RELEASE_CLIENT(
            CallTransaction.Function.fromSignature(
                    "getStateForBtcReleaseClient",
                    new String[]{},
                    new String[]{"bytes"}
            ),
            4000L,
            (BridgeMethodExecutorTyped) Bridge::getStateForBtcReleaseClient
    ),
    GET_STATE_FOR_DEBUGGING(
            CallTransaction.Function.fromSignature(
                    "getStateForDebugging",
                    new String[]{},
                    new String[]{"bytes"}
            ),
            3_000_000L,
            (BridgeMethodExecutorTyped) Bridge::getStateForDebugging
    ),
    IS_BTC_TX_HASH_ALREADY_PROCESSED(
            CallTransaction.Function.fromSignature(
                    "isBtcTxHashAlreadyProcessed",
                    new String[]{"string"},
                    new String[]{"bool"}
            ),
            23000L,
            (BridgeMethodExecutorTyped) Bridge::isBtcTxHashAlreadyProcessed
    ),
    RECEIVE_HEADERS(
            CallTransaction.Function.fromSignature(
                    "receiveHeaders",
                    new String[]{"bytes[]"},
                    new String[]{}
            ),
            22000L,
            Bridge.activeAndRetiringFederationOnly((BridgeMethodExecutorVoid) Bridge::receiveHeaders, "receiveHeaders")
    ),
    REGISTER_BTC_TRANSACTION(
            CallTransaction.Function.fromSignature(
                    "registerBtcTransaction",
                    new String[]{"bytes", "int", "bytes"},
                    new String[]{}
            ),
            22000L,
            Bridge.activeAndRetiringFederationOnly((BridgeMethodExecutorVoid) Bridge::registerBtcTransaction, "registerBtcTransaction")
    ),
    RELEASE_BTC(
            CallTransaction.Function.fromSignature(
                    "releaseBtc",
                    new String[]{},
                    new String[]{}
            ),
            23000L,
            (BridgeMethodExecutorVoid) Bridge::releaseBtc
    ),
    REMOVE_LOCK_WHITELIST_ADDRESS(
            CallTransaction.Function.fromSignature(
                    "removeLockWhitelistAddress",
                    new String[]{"string"},
                    new String[]{"int256"}
            ),
            24000L,
            (BridgeMethodExecutorTyped) Bridge::removeLockWhitelistAddress
    ),
    ROLLBACK_FEDERATION(
            CallTransaction.Function.fromSignature(
                    "rollbackFederation",
                    new String[]{},
                    new String[]{"int256"}
            ),
            12000L,
            (BridgeMethodExecutorTyped) Bridge::rollbackFederation
    ),
    SET_LOCK_WHITELIST_DISABLE_BLOCK_DELAY(
            CallTransaction.Function.fromSignature(
                    "setLockWhitelistDisableBlockDelay",
                    new String[]{"int256"},
                    new String[]{"int256"}
            ),
            24000L,
            (BridgeMethodExecutorTyped) Bridge::setLockWhitelistDisableBlockDelay
    ),
    UPDATE_COLLECTIONS(
            CallTransaction.Function.fromSignature(
                    "updateCollections",
                    new String[]{},
                    new String[]{}
            ),
            48000L,
            Bridge.activeAndRetiringFederationOnly((BridgeMethodExecutorVoid) Bridge::updateCollections, "updateCollections")
    ),
    VOTE_FEE_PER_KB(
            CallTransaction.Function.fromSignature(
                    "voteFeePerKbChange",
                    new String[]{"int256"},
                    new String[]{"int256"}
            ),
            10000L,
            (BridgeMethodExecutorTyped) Bridge::voteFeePerKbChange
    );

    private static final Map<ByteArrayWrapper, BridgeMethods> SIGNATURES = Stream.of(BridgeMethods.values())
                        .collect(Collectors.toMap(
                            m -> new ByteArrayWrapper(m.getFunction().encodeSignature()),
                            Function.identity()
                        ));
    private final CallTransaction.Function function;
    private final long cost;
    private final Function<BlockchainConfig, Boolean> _isEnabled;
    private final BridgeMethodExecutor executor;

    BridgeMethods(CallTransaction.Function function, long cost, BridgeMethodExecutor executor) {
        this(function, cost, executor, (blockchainConfig) -> true);
    }

    BridgeMethods(CallTransaction.Function function, long cost, BridgeMethodExecutor executor, Function<BlockchainConfig, Boolean> isEnabled) {
        this.function = function;
        this.cost = cost;
        this.executor = executor;
        this._isEnabled = isEnabled;
    }

    public static Optional<BridgeMethods> findBySignature(byte[] encoding) {
        return Optional.ofNullable(SIGNATURES.get(new ByteArrayWrapper(encoding)));
    }

    public CallTransaction.Function getFunction() {
        return function;
    }
    public Boolean isEnabled(BlockchainConfig blockchainConfig) {
        return this._isEnabled.apply(blockchainConfig);
    }
    public long getCost() {
        return cost;
    }

    public BridgeMethodExecutor getExecutor() {
        return executor;
    }

    public interface BridgeMethodExecutor {
        Optional<?> execute(Bridge self, Object[] args) throws Exception;
    }

    private interface BridgeMethodExecutorTyped<T> extends BridgeMethodExecutor {
        @Override
        default Optional<T> execute(Bridge self, Object[] args) throws Exception {
            return Optional.ofNullable(executeTyped(self, args));
        }

        T executeTyped(Bridge self, Object[] args) throws Exception;
    }

    private interface BridgeMethodExecutorVoid extends BridgeMethodExecutor {
        @Override
        default Optional<?> execute(Bridge self, Object[] args) throws Exception {
            executeVoid(self, args);
            return Optional.empty();
        }

        void executeVoid(Bridge self, Object[] args) throws Exception;
    }
}
