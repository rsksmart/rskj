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

import static org.ethereum.config.blockchain.upgrades.ConsensusRule.*;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.CallTransaction;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.MessageCall.MsgType;

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
        fixedCost(13000L),
        (BridgeMethodExecutorTyped) Bridge::addFederatorPublicKey,
        activations -> !activations.isActive(RSKIP123),
        fixedPermission(false)
    ),
    ADD_FEDERATOR_PUBLIC_KEY_MULTIKEY(
        CallTransaction.Function.fromSignature(
            "addFederatorPublicKeyMultikey",
            new String[]{"bytes", "bytes", "bytes"},
            new String[]{"int256"}
        ),
        fixedCost(13000L),
        (BridgeMethodExecutorTyped) Bridge::addFederatorPublicKeyMultikey,
        activations -> activations.isActive(RSKIP123),
        fixedPermission(false)
    ),
    ADD_LOCK_WHITELIST_ADDRESS(
        CallTransaction.Function.fromSignature(
            "addLockWhitelistAddress",
            new String[]{"string", "int256"},
            new String[]{"int256"}
        ),
        fixedCost(25000L),
        (BridgeMethodExecutorTyped) Bridge::addOneOffLockWhitelistAddress,
        activations -> !activations.isActive(RSKIP87),
        fixedPermission(false)
    ),
    ADD_ONE_OFF_LOCK_WHITELIST_ADDRESS(
        CallTransaction.Function.fromSignature(
            "addOneOffLockWhitelistAddress",
            new String[]{"string", "int256"},
            new String[]{"int256"}
        ),
        fixedCost(25000L), // using same gas estimation as ADD_LOCK_WHITELIST_ADDRESS
        (BridgeMethodExecutorTyped) Bridge::addOneOffLockWhitelistAddress,
        activations -> activations.isActive(RSKIP87),
        fixedPermission(false)
    ),
    ADD_UNLIMITED_LOCK_WHITELIST_ADDRESS(
        CallTransaction.Function.fromSignature(
            "addUnlimitedLockWhitelistAddress",
            new String[]{"string"},
            new String[]{"int256"}
        ),
        fixedCost(25000L), // using same gas estimation as ADD_LOCK_WHITELIST_ADDRESS
        (BridgeMethodExecutorTyped) Bridge::addUnlimitedLockWhitelistAddress,
        activations -> activations.isActive(RSKIP87),
        fixedPermission(false)
    ),
    ADD_SIGNATURE(
        CallTransaction.Function.fromSignature(
            "addSignature",
            new String[]{"bytes", "bytes[]", "bytes"},
            new String[]{}
        ),
        fixedCost(70000L),
        Bridge.activeAndRetiringFederationOnly((BridgeMethodExecutorVoid) Bridge::addSignature, "addSignature"),
        fixedPermission(false)
    ),
    COMMIT_FEDERATION(
        CallTransaction.Function.fromSignature(
            "commitFederation",
            new String[]{"bytes"},
            new String[]{"int256"}
        ),
        fixedCost(38000L),
        (BridgeMethodExecutorTyped) Bridge::commitFederation,
        fixedPermission(false)
    ),
    CREATE_FEDERATION(
            CallTransaction.Function.fromSignature(
                    "createFederation",
                    new String[]{},
                    new String[]{"int256"}
            ),
            fixedCost(11000L),
            (BridgeMethodExecutorTyped) Bridge::createFederation,
            fixedPermission(false)
    ),
    GET_BTC_BLOCKCHAIN_BEST_CHAIN_HEIGHT(
            CallTransaction.Function.fromSignature(
                    "getBtcBlockchainBestChainHeight",
                    new String[]{},
                    new String[]{"int"}
            ),
            fixedCost(19000L),
            (BridgeMethodExecutorTyped) Bridge::getBtcBlockchainBestChainHeight,
            fromMethod(Bridge::getBtcBlockchainBestChainHeightOnlyAllowsLocalCalls),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_BTC_BLOCKCHAIN_INITIAL_BLOCK_HEIGHT(
            CallTransaction.Function.fromSignature(
                    "getBtcBlockchainInitialBlockHeight",
                    new String[]{},
                    new String[]{"int"}
            ),
            fixedCost(20000L),
            (BridgeMethodExecutorTyped) Bridge::getBtcBlockchainInitialBlockHeight,
            activations -> activations.isActive(RSKIP89),
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_BTC_BLOCKCHAIN_BLOCK_LOCATOR(
            CallTransaction.Function.fromSignature(
                    "getBtcBlockchainBlockLocator",
                    new String[]{},
                    new String[]{"string[]"}
            ),
            fixedCost(76000L),
            (BridgeMethodExecutorTyped) Bridge::getBtcBlockchainBlockLocator,
            activations -> !activations.isActive(RSKIP89),
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_BTC_BLOCKCHAIN_BLOCK_HASH_AT_DEPTH(
            CallTransaction.Function.fromSignature(
                    "getBtcBlockchainBlockHashAtDepth",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            fixedCost(20000L),
            (BridgeMethodExecutorTyped) Bridge::getBtcBlockchainBlockHashAtDepth,
            activations -> activations.isActive(RSKIP89),
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_BTC_TRANSACTION_CONFIRMATIONS(
            CallTransaction.Function.fromSignature(
                    "getBtcTransactionConfirmations",
                    new String[]{"bytes32", "bytes32", "uint256", "bytes32[]"},
                    new String[]{"int256"}
            ),
            fromMethod(Bridge::getBtcTransactionConfirmationsGetCost),
            (BridgeMethodExecutorTyped) Bridge::getBtcTransactionConfirmations,
            activations -> activations.isActive(RSKIP122),
            fixedPermission(false),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_BTC_TX_HASH_PROCESSED_HEIGHT(
            CallTransaction.Function.fromSignature(
                    "getBtcTxHashProcessedHeight",
                    new String[]{"string"},
                    new String[]{"int64"}
            ),
            fixedCost(22000L),
            (BridgeMethodExecutorTyped) Bridge::getBtcTxHashProcessedHeight,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_FEDERATION_ADDRESS(
            CallTransaction.Function.fromSignature(
                    "getFederationAddress",
                    new String[]{},
                    new String[]{"string"}
            ),
            fixedCost(11000L),
            (BridgeMethodExecutorTyped) Bridge::getFederationAddress,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_FEDERATION_CREATION_BLOCK_NUMBER(
            CallTransaction.Function.fromSignature(
                    "getFederationCreationBlockNumber",
                    new String[]{},
                    new String[]{"int256"}
            ),
            fixedCost(10000L),
            (BridgeMethodExecutorTyped) Bridge::getFederationCreationBlockNumber,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_FEDERATION_CREATION_TIME(
            CallTransaction.Function.fromSignature(
                    "getFederationCreationTime",
                    new String[]{},
                    new String[]{"int256"}
            ),
            fixedCost(10000L),
            (BridgeMethodExecutorTyped) Bridge::getFederationCreationTime,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_FEDERATION_SIZE(
            CallTransaction.Function.fromSignature(
                    "getFederationSize",
                    new String[]{},
                    new String[]{"int256"}
            ),
            fixedCost(10000L),
            (BridgeMethodExecutorTyped) Bridge::getFederationSize,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_FEDERATION_THRESHOLD(
            CallTransaction.Function.fromSignature(
                    "getFederationThreshold",
                    new String[]{},
                    new String[]{"int256"}
            ),
            fixedCost(11000L),
            (BridgeMethodExecutorTyped) Bridge::getFederationThreshold,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_FEDERATOR_PUBLIC_KEY(
            CallTransaction.Function.fromSignature(
                    "getFederatorPublicKey",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            fixedCost(10000L),
            (BridgeMethodExecutorTyped) Bridge::getFederatorPublicKey,
            activations -> !activations.isActive(RSKIP123),
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_FEDERATOR_PUBLIC_KEY_OF_TYPE(
            CallTransaction.Function.fromSignature(
                    "getFederatorPublicKeyOfType",
                    new String[]{"int256", "string"},
                    new String[]{"bytes"}
            ),
            fixedCost(10000L),
            (BridgeMethodExecutorTyped) Bridge::getFederatorPublicKeyOfType,
            activations -> activations.isActive(RSKIP123),
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_FEE_PER_KB(
            CallTransaction.Function.fromSignature(
                    "getFeePerKb",
                    new String[]{},
                    new String[]{"int256"}
            ),
            fixedCost(2000L),
            (BridgeMethodExecutorTyped) Bridge::getFeePerKb,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_LOCK_WHITELIST_ADDRESS(
            CallTransaction.Function.fromSignature(
                    "getLockWhitelistAddress",
                    new String[]{"int256"},
                    new String[]{"string"}
            ),
            fixedCost(16000L),
            (BridgeMethodExecutorTyped) Bridge::getLockWhitelistAddress,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_LOCK_WHITELIST_ENTRY_BY_ADDRESS(
            CallTransaction.Function.fromSignature(
                    "getLockWhitelistEntryByAddress",
                    new String[]{"string"},
                    new String[]{"int256"}
            ),
            fixedCost(16000L),
            (BridgeMethodExecutorTyped) Bridge::getLockWhitelistEntryByAddress,
            activations -> activations.isActive(RSKIP87),
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_LOCK_WHITELIST_SIZE(
            CallTransaction.Function.fromSignature(
                    "getLockWhitelistSize",
                    new String[]{},
                    new String[]{"int256"}
            ),
            fixedCost(16000L),
            (BridgeMethodExecutorTyped) Bridge::getLockWhitelistSize,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_MINIMUM_LOCK_TX_VALUE(
            CallTransaction.Function.fromSignature(
                    "getMinimumLockTxValue",
                    new String[]{},
                    new String[]{"int"}
            ),
            fixedCost(2000L),
            (BridgeMethodExecutorTyped) Bridge::getMinimumLockTxValue,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_PENDING_FEDERATION_HASH(
            CallTransaction.Function.fromSignature(
                    "getPendingFederationHash",
                    new String[]{},
                    new String[]{"bytes"}
            ),
            fixedCost(3000L),
            (BridgeMethodExecutorTyped) Bridge::getPendingFederationHash,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_PENDING_FEDERATION_SIZE(
            CallTransaction.Function.fromSignature(
                    "getPendingFederationSize",
                    new String[]{},
                    new String[]{"int256"}
            ),
            fixedCost(3000L),
            (BridgeMethodExecutorTyped) Bridge::getPendingFederationSize,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_PENDING_FEDERATOR_PUBLIC_KEY(
            CallTransaction.Function.fromSignature(
                    "getPendingFederatorPublicKey",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            fixedCost(3000L),
            (BridgeMethodExecutorTyped) Bridge::getPendingFederatorPublicKey,
            activations -> !activations.isActive(RSKIP123),
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_PENDING_FEDERATOR_PUBLIC_KEY_OF_TYPE(
            CallTransaction.Function.fromSignature(
                    "getPendingFederatorPublicKeyOfType",
                    new String[]{"int256", "string"},
                    new String[]{"bytes"}
            ),
            fixedCost(3000L),
            (BridgeMethodExecutorTyped) Bridge::getPendingFederatorPublicKeyOfType,
            activations -> activations.isActive(RSKIP123),
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_RETIRING_FEDERATION_ADDRESS(
            CallTransaction.Function.fromSignature(
                    "getRetiringFederationAddress",
                    new String[]{},
                    new String[]{"string"}
            ),
            fixedCost(3000L),
            (BridgeMethodExecutorTyped) Bridge::getRetiringFederationAddress,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_RETIRING_FEDERATION_CREATION_BLOCK_NUMBER(
            CallTransaction.Function.fromSignature(
                    "getRetiringFederationCreationBlockNumber",
                    new String[]{},
                    new String[]{"int256"}
            ),
            fixedCost(3000L),
            (BridgeMethodExecutorTyped) Bridge::getRetiringFederationCreationBlockNumber,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_RETIRING_FEDERATION_CREATION_TIME(
            CallTransaction.Function.fromSignature(
                    "getRetiringFederationCreationTime",
                    new String[]{},
                    new String[]{"int256"}
            ),
            fixedCost(3000L),
            (BridgeMethodExecutorTyped) Bridge::getRetiringFederationCreationTime,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_RETIRING_FEDERATION_SIZE(
            CallTransaction.Function.fromSignature(
                    "getRetiringFederationSize",
                    new String[]{},
                    new String[]{"int256"}
            ),
            fixedCost(3000L),
            (BridgeMethodExecutorTyped) Bridge::getRetiringFederationSize,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_RETIRING_FEDERATION_THRESHOLD(
            CallTransaction.Function.fromSignature(
                    "getRetiringFederationThreshold",
                    new String[]{},
                    new String[]{"int256"}
            ),
            fixedCost(3000L),
            (BridgeMethodExecutorTyped) Bridge::getRetiringFederationThreshold,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_RETIRING_FEDERATOR_PUBLIC_KEY(
            CallTransaction.Function.fromSignature(
                    "getRetiringFederatorPublicKey",
                    new String[]{"int256"},
                    new String[]{"bytes"}
            ),
            fixedCost(3000L),
            (BridgeMethodExecutorTyped) Bridge::getRetiringFederatorPublicKey,
            activations -> !activations.isActive(RSKIP123),
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_RETIRING_FEDERATOR_PUBLIC_KEY_OF_TYPE(
            CallTransaction.Function.fromSignature(
                    "getRetiringFederatorPublicKeyOfType",
                    new String[]{"int256", "string"},
                    new String[]{"bytes"}
            ),
            fixedCost(3000L),
            (BridgeMethodExecutorTyped) Bridge::getRetiringFederatorPublicKeyOfType,
            activations -> activations.isActive(RSKIP123),
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_STATE_FOR_BTC_RELEASE_CLIENT(
            CallTransaction.Function.fromSignature(
                    "getStateForBtcReleaseClient",
                    new String[]{},
                    new String[]{"bytes"}
            ),
            fixedCost(4000L),
            (BridgeMethodExecutorTyped) Bridge::getStateForBtcReleaseClient,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_STATE_FOR_DEBUGGING(
            CallTransaction.Function.fromSignature(
                    "getStateForDebugging",
                    new String[]{},
                    new String[]{"bytes"}
            ),
            fixedCost(3_000_000L),
            (BridgeMethodExecutorTyped) Bridge::getStateForDebugging,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_LOCKING_CAP(
            CallTransaction.Function.fromSignature(
                    "getLockingCap",
                    new String[]{},
                    new String[]{"int256"}
            ),
            fixedCost(3_000L),
            (BridgeMethodExecutorTyped) Bridge::getLockingCap,
            activations -> activations.isActive(RSKIP134),
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_ACTIVE_POWPEG_REDEEM_SCRIPT(
            CallTransaction.Function.fromSignature(
                    "getActivePowpegRedeemScript",
                    new String[]{},
                    new String[]{"bytes"}
            ),
            fixedCost(30_000L),
            (BridgeMethodExecutorTyped) Bridge::getActivePowpegRedeemScript,
            activations -> activations.isActive(RSKIP293),
            fixedPermission(false),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_ACTIVE_FEDERATION_CREATION_BLOCK_HEIGHT(
            CallTransaction.Function.fromSignature(
                    "getActiveFederationCreationBlockHeight",
                    new String[]{},
                    new String[]{"uint256"}
            ),
            fixedCost(3_000L),
            (BridgeMethodExecutorTyped) Bridge::getActiveFederationCreationBlockHeight,
            activations -> activations.isActive(RSKIP186),
            fixedPermission(false),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    INCREASE_LOCKING_CAP(
            CallTransaction.Function.fromSignature(
                    "increaseLockingCap",
                    new String[]{"int256"},
                    new String[]{"bool"}
            ),
            fixedCost(8_000L),
            (BridgeMethodExecutorTyped) Bridge::increaseLockingCap,
            activations -> activations.isActive(RSKIP134),
            fixedPermission(false)
    ),
    IS_BTC_TX_HASH_ALREADY_PROCESSED(
            CallTransaction.Function.fromSignature(
                    "isBtcTxHashAlreadyProcessed",
                    new String[]{"string"},
                    new String[]{"bool"}
            ),
            fixedCost(23000L),
            (BridgeMethodExecutorTyped) Bridge::isBtcTxHashAlreadyProcessed,
            fixedPermission(true),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    RECEIVE_HEADERS(
            CallTransaction.Function.fromSignature(
                    "receiveHeaders",
                    new String[]{"bytes[]"},
                    new String[]{}
            ),
            fromMethod(Bridge::receiveHeadersGetCost),
            Bridge.executeIfElse(
                    Bridge::receiveHeadersIsPublic,
                    (BridgeMethodExecutorVoid) Bridge::receiveHeaders,
                    Bridge.activeAndRetiringFederationOnly((BridgeMethodExecutorVoid) Bridge::receiveHeaders, "receiveHeaders")
            ),
            fixedPermission(false)
    ),
    RECEIVE_HEADER(
            CallTransaction.Function.fromSignature(
                    "receiveHeader",
                    new String[]{"bytes"},
                    new String[]{"int256"}
            ),
            fixedCost(10_600L),
            (BridgeMethodExecutorTyped) Bridge::receiveHeader,
            activations -> activations.isActive(RSKIP200),
            fixedPermission(false)
    ),
    REGISTER_BTC_TRANSACTION(
            CallTransaction.Function.fromSignature(
                    "registerBtcTransaction",
                    new String[]{"bytes", "int", "bytes"},
                    new String[]{}
            ),
            fixedCost(22000L),
            Bridge.executeIfElse(
                Bridge::registerBtcTransactionIsPublic,
                (BridgeMethodExecutorVoid) Bridge::registerBtcTransaction,
                Bridge.activeAndRetiringFederationOnly((BridgeMethodExecutorVoid) Bridge::registerBtcTransaction, "registerBtcTransaction")
            ),
            fixedPermission(false)
    ),
    RELEASE_BTC(
            CallTransaction.Function.fromSignature(
                    "releaseBtc",
                    new String[]{},
                    new String[]{}
            ),
            fixedCost(23000L),
            (BridgeMethodExecutorVoid) Bridge::releaseBtc,
            fixedPermission(false)
    ),
    REMOVE_LOCK_WHITELIST_ADDRESS(
            CallTransaction.Function.fromSignature(
                    "removeLockWhitelistAddress",
                    new String[]{"string"},
                    new String[]{"int256"}
            ),
            fixedCost(24000L),
            (BridgeMethodExecutorTyped) Bridge::removeLockWhitelistAddress,
            fixedPermission(false)
    ),
    ROLLBACK_FEDERATION(
            CallTransaction.Function.fromSignature(
                    "rollbackFederation",
                    new String[]{},
                    new String[]{"int256"}
            ),
            fixedCost(12000L),
            (BridgeMethodExecutorTyped) Bridge::rollbackFederation,
            fixedPermission(false)
    ),
    SET_LOCK_WHITELIST_DISABLE_BLOCK_DELAY(
            CallTransaction.Function.fromSignature(
                    "setLockWhitelistDisableBlockDelay",
                    new String[]{"int256"},
                    new String[]{"int256"}
            ),
            fixedCost(24000L),
            (BridgeMethodExecutorTyped) Bridge::setLockWhitelistDisableBlockDelay,
            fixedPermission(false)
    ),
    UPDATE_COLLECTIONS(
            CallTransaction.Function.fromSignature(
                    "updateCollections",
                    new String[]{},
                    new String[]{}
            ),
            fixedCost(48000L),
            Bridge.activeAndRetiringFederationOnly((BridgeMethodExecutorVoid) Bridge::updateCollections, "updateCollections"),
            fixedPermission(false)
    ),
    VOTE_FEE_PER_KB(
            CallTransaction.Function.fromSignature(
                    "voteFeePerKbChange",
                    new String[]{"int256"},
                    new String[]{"int256"}
            ),
            fixedCost(10000L),
            (BridgeMethodExecutorTyped) Bridge::voteFeePerKbChange,
            fixedPermission(false)
    ),
    REGISTER_BTC_COINBASE_TRANSACTION(
            CallTransaction.Function.fromSignature(
            "registerBtcCoinbaseTransaction",
                    new String[]{"bytes", "bytes32", "bytes", "bytes32", "bytes32"},
                    new String[]{}
            ),
            fixedCost(10000L),
            (BridgeMethodExecutorVoid) Bridge::registerBtcCoinbaseTransaction,
            activations -> activations.isActive(RSKIP143),
            fixedPermission(false)
    ),
    HAS_BTC_BLOCK_COINBASE_TRANSACTION_INFORMATION(
            CallTransaction.Function.fromSignature(
                    "hasBtcBlockCoinbaseTransactionInformation",
                    new String[]{"bytes32"},
                    new String[]{"bool"}
            ),
            fixedCost(5000L),
            (BridgeMethodExecutorTyped) Bridge::hasBtcBlockCoinbaseTransactionInformation,
            activations -> activations.isActive(RSKIP143),
            fixedPermission(false),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    REGISTER_FAST_BRIDGE_BTC_TRANSACTION(
            CallTransaction.Function.fromSignature(
                    "registerFastBridgeBtcTransaction",
                    new String[]{"bytes", "uint256", "bytes", "bytes32", "bytes", "address", "bytes", "bool"},
                    new String[]{"int256"}
            ),
            fixedCost(25_000L),
            (BridgeMethodExecutorTyped) Bridge::registerFlyoverBtcTransaction,
            activations -> activations.isActive(RSKIP176),
            fixedPermission(false)
    ),
    GET_BTC_BLOCKCHAIN_BEST_BLOCK_HEADER(
            CallTransaction.Function.fromSignature(
                    "getBtcBlockchainBestBlockHeader",
                    new String[0],
                    new String[]{"bytes"}
            ),
            fixedCost(3_800L),
            (BridgeMethodExecutorTyped) Bridge::getBtcBlockchainBestBlockHeader,
            activations -> activations.isActive(RSKIP220),
            fixedPermission(false),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_BTC_BLOCKCHAIN_BLOCK_HEADER_BY_HASH(
            CallTransaction.Function.fromSignature(
                    "getBtcBlockchainBlockHeaderByHash",
                    new String[]{"bytes32"},
                    new String[]{"bytes"}
            ),
            fixedCost(4_600L),
            (BridgeMethodExecutorTyped) Bridge::getBtcBlockchainBlockHeaderByHash,
            activations -> activations.isActive(RSKIP220),
            fixedPermission(false),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_BTC_BLOCKCHAIN_BLOCK_HEADER_BY_HEIGHT(
        CallTransaction.Function.fromSignature(
            "getBtcBlockchainBlockHeaderByHeight",
            new String[]{"uint256"},
            new String[]{"bytes"}
        ),
        fixedCost(5_000L),
        (BridgeMethodExecutorTyped) Bridge::getBtcBlockchainBlockHeaderByHeight,
        activations -> activations.isActive(RSKIP220),
        fixedPermission(false),
        CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_BTC_BLOCKCHAIN_PARENT_BLOCK_HEADER_BY_HASH(
            CallTransaction.Function.fromSignature(
                    "getBtcBlockchainParentBlockHeaderByHash",
                    new String[]{"bytes32"},
                    new String[]{"bytes"}
            ),
            fixedCost(4_900L),
            (BridgeMethodExecutorTyped) Bridge::getBtcBlockchainParentBlockHeaderByHash,
            activations -> activations.isActive(RSKIP220),
            fixedPermission(false),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_NEXT_PEGOUT_CREATION_BLOCK_NUMBER(
            CallTransaction.Function.fromSignature(
                    "getNextPegoutCreationBlockNumber",
                    new String[]{},
                    new String[]{"uint256"}
            ),
            fixedCost(3_000L),
            (BridgeMethodExecutorTyped) Bridge::getNextPegoutCreationBlockNumber,
            activations -> activations.isActive(RSKIP271),
            fixedPermission(false),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_QUEUED_PEGOUTS_COUNT(
            CallTransaction.Function.fromSignature(
                    "getQueuedPegoutsCount",
                    new String[]{},
                    new String[]{"uint256"}
            ),
            fixedCost(3_000L),
            (BridgeMethodExecutorTyped) Bridge::getQueuedPegoutsCount,
            activations -> activations.isActive(RSKIP271),
            fixedPermission(false),
            CallTypeHelper.ALLOW_STATIC_CALL
    ),
    GET_ESTIMATED_FEES_FOR_NEXT_PEGOUT_EVENT(
            CallTransaction.Function.fromSignature(
                    "getEstimatedFeesForNextPegOutEvent",
                    new String[]{},
                    new String[]{"uint256"}
            ),
            fixedCost(10_000L),
            (BridgeMethodExecutorTyped) Bridge::getEstimatedFeesForNextPegOutEvent,
            activations -> activations.isActive(RSKIP271),
            fixedPermission(false),
            CallTypeHelper.ALLOW_STATIC_CALL
    );

    private static class CallTypeHelper {
        private static final Predicate<MsgType> ALLOW_STATIC_CALL = callType ->
            callType == MsgType.CALL || callType == MsgType.STATICCALL;
        private static final Predicate<MsgType> RESTRICTED_TO_CALL =  callType -> callType == MsgType.CALL;
    }

    private final CallTransaction.Function function;
    private final CostProvider costProvider;
    private final Function<ActivationConfig.ForBlock, Boolean> isEnabledFunction;
    private final BridgeMethodExecutor executor;
    private final BridgeCallPermissionProvider callPermissionProvider;
    private final Predicate<MsgType> callTypeVerifier;

    BridgeMethods(
        CallTransaction.Function function,
        CostProvider costProvider,
        BridgeMethodExecutor executor,
        BridgeCallPermissionProvider callPermissionProvider) {

        this(
            function,
            costProvider,
            executor,
            activations -> Boolean.TRUE,
            callPermissionProvider,
            CallTypeHelper.RESTRICTED_TO_CALL
        );
    }

    BridgeMethods(
        CallTransaction.Function function,
        CostProvider costProvider,
        BridgeMethodExecutor executor,
        BridgeCallPermissionProvider callPermissionProvider,
        Predicate<MsgType> callTypeVerifier) {

        this(
            function,
            costProvider,
            executor,
            activations -> Boolean.TRUE,
            callPermissionProvider,
            callTypeVerifier
        );
    }

    BridgeMethods(
        CallTransaction.Function function,
        CostProvider costProvider,
        BridgeMethodExecutor executor,
        Function<ActivationConfig.ForBlock, Boolean> isEnabled,
        BridgeCallPermissionProvider callPermissionProvider,
        Predicate<MsgType> callTypeVerifier) {

        this.function = function;
        this.costProvider = costProvider;
        this.executor = executor;
        this.isEnabledFunction = isEnabled;
        this.callPermissionProvider = callPermissionProvider;
        this.callTypeVerifier = callTypeVerifier;
    }

    BridgeMethods(
        CallTransaction.Function function,
        CostProvider costProvider,
        BridgeMethodExecutor executor,
        Function<ActivationConfig.ForBlock, Boolean> isEnabled,
        BridgeCallPermissionProvider callPermissionProvider) {

        this(
            function,
            costProvider,
            executor,
            isEnabled,
            callPermissionProvider,
            CallTypeHelper.RESTRICTED_TO_CALL
        );
    }

    public static Optional<BridgeMethods> findBySignature(byte[] encoding) {
        return Optional.ofNullable(SIGNATURES.get(new ByteArrayWrapper(encoding)));
    }

    public CallTransaction.Function getFunction() {
        return function;
    }

    public Boolean isEnabled(ActivationConfig.ForBlock activations) {
        return this.isEnabledFunction.apply(activations);
    }

    public long getCost(Bridge bridge, ActivationConfig.ForBlock config, Object[] args) {
        return costProvider.getCost(bridge, config, args);
    }

    public BridgeMethodExecutor getExecutor() {
        return executor;
    }

    public boolean onlyAllowsLocalCalls(Bridge bridge, Object[] args) {
        return callPermissionProvider.getOnlyAllowLocalCallsPermission(bridge, args);
    }

    public boolean acceptsThisTypeOfCall(MsgType callType) {
        return callTypeVerifier.test(callType);
    }

    public interface BridgeCondition {
        boolean isTrue(Bridge bridge);
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

    private interface CostProvider {
        long getCost(Bridge bridge, ActivationConfig.ForBlock config, Object[] args);
    }

    private interface BridgeCostProvider {
        long getCost(Bridge bridge, Object[] args);
    }

    private static CostProvider fixedCost(long cost) {
        return (Bridge bridge, ActivationConfig.ForBlock config, Object[] args) -> cost;
    }

    private static CostProvider fromMethod(BridgeCostProvider bridgeCostProvider) {
        return (Bridge bridge, ActivationConfig.ForBlock config, Object[] args) -> bridgeCostProvider.getCost(bridge, args);
    }

    private interface BridgeCallPermissionProvider {
        boolean getOnlyAllowLocalCallsPermission(Bridge bridge, Object[] args);
    }

    private static BridgeCallPermissionProvider fixedPermission(boolean onlyAllowsLocalCalls) {
        return (Bridge bridge, Object[] args) -> onlyAllowsLocalCalls;
    }

    private static BridgeCallPermissionProvider fromMethod(BridgeCallPermissionProvider bridgeCallPermissionProvider) {
        return (Bridge bridge, Object[] args) -> bridgeCallPermissionProvider.getOnlyAllowLocalCallsPermission(bridge, args);
    }

    private static final Map<ByteArrayWrapper, BridgeMethods> SIGNATURES = Stream.of(BridgeMethods.values())
        .collect(Collectors.toMap(
            m -> new ByteArrayWrapper(m.getFunction().encodeSignature()),
            Function.identity()
        ));
}
