/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
package org.ethereum.core;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.parser.RawTransactionEnvelopeParser;
import org.ethereum.core.transaction.parser.TransactionInput;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;

import java.math.BigInteger;
import java.util.List;

public final class TransactionBuilder {

	private TransactionTypePrefix typePrefix = TransactionTypePrefix.typed(TransactionType.LEGACY);

	private boolean isLocalCall;
	private byte[] nonce;
	private Coin value = Coin.ZERO;
	private RskAddress receiveAddress = RskAddress.nullAddress();
	private Coin gasPrice = Coin.ZERO;
	private byte[] gasLimit;
	private byte[] data;
	private byte chainId;

	private byte[] accessListBytes;
	private Coin maxPriorityFeePerGas;
	private Coin maxFeePerGas;
	private List<SetCodeAuthorization> authorizationList;

	TransactionBuilder() {}

	public TransactionBuilder type(TransactionType type) {
		this.typePrefix = TransactionTypePrefix.typed(type);
		return this;
	}
	public TransactionBuilder type(TransactionType type, Byte rskSubtype) {
		this.typePrefix = TransactionTypePrefix.of(type, rskSubtype);
		return this;
	}
	public TransactionBuilder typePrefix(TransactionTypePrefix typePrefix) {
		this.typePrefix = typePrefix;
		return this;
	}

	public TransactionBuilder isLocalCall(boolean localCall) {
		this.isLocalCall = localCall;
		return this;
	}

	public TransactionBuilder nonce(byte[] nonce) {
		this.nonce = ByteUtil.cloneBytes(nonce);
		return this;
	}

	public TransactionBuilder nonce(BigInteger nonce) {
		return nonce(BigIntegers.asUnsignedByteArray(nonce));
	}

	public TransactionBuilder nonce(Coin nonce) {
		return this.nonce(nonce.getBytes());
	}

	public TransactionBuilder value(Coin value) {
		this.value = value;
		return this;
	}

	public TransactionBuilder value(byte[] value) {
		return value(RLP.parseCoinNullZero(ByteUtil.cloneBytes(value)));
	}

	public TransactionBuilder value(BigInteger value) {
		return value(BigIntegers.asUnsignedByteArray(value));
	}

	public TransactionBuilder receiveAddress(RskAddress receiveAddress) {
		this.receiveAddress = receiveAddress;
		return this;
	}

	public TransactionBuilder receiveAddress(byte[] receiveAddress) {
		return receiveAddress(RLP.parseRskAddress(ByteUtil.cloneBytes(receiveAddress)));
	}

	public TransactionBuilder receiveAddress(String receiveAddress) {
		return receiveAddress(receiveAddress == null ? null : Hex.decode(receiveAddress));
	}


	public TransactionBuilder gasPrice(Coin gasPrice) {
		this.gasPrice = gasPrice;
		return this;
	}

	public TransactionBuilder gasPrice(byte[] gasPrice) {
		return gasPrice(RLP.parseCoinNonNullZero(ByteUtil.cloneBytes(gasPrice)));
	}

	public TransactionBuilder gasPrice(BigInteger gasPrice) {
		return gasPrice(gasPrice.toByteArray());
	}

	public TransactionBuilder gasLimit(byte[] gasLimit) {
		this.gasLimit = ByteUtil.cloneBytes(gasLimit);
		return this;
	}

	public TransactionBuilder gasLimit(BigInteger gasLimit) {
		return gasLimit(BigIntegers.asUnsignedByteArray(gasLimit));
	}

	public TransactionBuilder gasLimit(Coin gasLimit) {
		return this.gasLimit(gasLimit.getBytes());
	}

	public TransactionBuilder data(byte[] data) {
		this.data = ByteUtil.cloneBytes(data);
		return this;
	}

	public TransactionBuilder data(String data) {
		return data(data == null ? null : Hex.decode(data));
	}

	public TransactionBuilder chainId(byte chainId) {
		this.chainId = chainId;
		return this;
	}

	public TransactionBuilder accessList(byte[] accessListBytes) {
		this.accessListBytes = ByteUtil.cloneBytes(accessListBytes);
		return this;
	}

	public TransactionBuilder maxPriorityFeePerGas(Coin maxPriorityFeePerGas) {
		this.maxPriorityFeePerGas = maxPriorityFeePerGas;
		return this;
	}

	public TransactionBuilder maxFeePerGas(Coin maxFeePerGas) {
		this.maxFeePerGas = maxFeePerGas;
		return this;
	}

	public TransactionBuilder authorizationList(List<SetCodeAuthorization> authorizationList) {
		this.authorizationList = authorizationList == null ? null : List.copyOf(authorizationList);
		return this;
	}

	public Transaction build() {
		return Transaction.fromParsed(
				RawTransactionEnvelopeParser.parse(toTransactionInput(), chainId),
				isLocalCall
		);
	}

	TransactionInput toTransactionInput() {
		Byte explicitChainId = chainId == 0 ? null : chainId;
		return TransactionInput.fromBuilderState(
				typePrefix,
				nonce,
				gasPrice,
				maxPriorityFeePerGas,
				maxFeePerGas,
				gasLimit,
				receiveAddress,
				value,
				data,
				explicitChainId,
				accessListBytes,
				authorizationList
		);
	}
}
