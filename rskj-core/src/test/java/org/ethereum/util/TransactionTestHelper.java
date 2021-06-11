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

package org.ethereum.util;

import org.ethereum.core.Account;
import org.ethereum.core.Transaction;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.rpc.Web3;

import co.rsk.core.RskAddress;

public class TransactionTestHelper {

	public static Web3.CallArguments createArguments(RskAddress sender, RskAddress receiver) {

		// Simulation of the args handled in the sendTransaction call
		Web3.CallArguments args = new Web3.CallArguments();
		args.from = sender.toJsonString();
		args.to = receiver.toJsonString();
		args.gasLimit = "0x76c0";
		args.gasPrice = "0x9184e72a000";
		args.value = "0x186A0";
		args.nonce = "0x01";

		return args;
	}

	public static Transaction createTransaction(Web3.CallArguments args, byte chainId, Account senderAccount) {

		// Transaction that is expected to be constructed WITH the gasLimit
		Transaction tx = Transaction.builder()
			.nonce(TypeConverter.stringNumberAsBigInt(args.nonce))
			.gasPrice(TypeConverter.stringNumberAsBigInt(args.gasPrice))
			.gasLimit(TypeConverter.stringNumberAsBigInt(args.gasLimit))
			.destination(TypeConverter.stringHexToByteArray(args.to))
			.chainId(chainId)
			.value(TypeConverter.stringNumberAsBigInt(args.value))
			.build();
		tx.sign(senderAccount.getEcKey().getPrivKeyBytes());

		return tx;
	}

}