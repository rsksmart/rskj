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

import co.rsk.net.TransactionGateway;
import org.ethereum.config.Constants;
import org.ethereum.core.TransactionPool;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.parameters.CallArgumentsParam;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

/**
 * This module disables sendTransaction because it needs a local wallet, but sendRawTransaction should still work.
 */
public class EthModuleTransactionDisabled extends EthModuleTransactionBase {

    public EthModuleTransactionDisabled(Constants constants, TransactionPool transactionPool, TransactionGateway transactionGateway) {
        // wallet is only used from EthModuleTransactionBase::sendTransaction, which is overrode
        super(constants, null, transactionPool, transactionGateway);
    }

    @Override
    public String sendTransaction(CallArgumentsParam args) { // lgtm [java/non-sync-override]
        LOGGER.debug("eth_sendTransaction({}): {}", args, null);
        throw invalidParamError("Local wallet is disabled in this node");
    }
}