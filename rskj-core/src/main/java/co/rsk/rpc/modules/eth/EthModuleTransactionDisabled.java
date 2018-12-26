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

package co.rsk.rpc.modules.eth;

import co.rsk.config.RskSystemProperties;
import org.ethereum.core.TransactionPool;
import org.ethereum.rpc.Web3;
import org.ethereum.rpc.exception.JsonRpcInvalidParamException;

/**
 * This module disables sendTransaction because it needs a local wallet, but sendRawTransaction should still work.
 */
public class EthModuleTransactionDisabled extends EthModuleTransactionBase {

    public EthModuleTransactionDisabled(RskSystemProperties config, TransactionPool transactionPool) {
        // wallet is only used from EthModuleTransactionBase::sendTransaction, which is overrode
        super(config, null, transactionPool);
    }

    @Override
    public String sendTransaction(Web3.CallArguments args) {
        LOGGER.debug("eth_sendTransaction({}): {}", args, null);
        throw new JsonRpcInvalidParamException("Local wallet is disabled in this node");
    }
}