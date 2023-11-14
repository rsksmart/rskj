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
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;

import java.math.BigInteger;

public final class TransactionBuilder {

	private boolean isLocalCall = false;
	private byte[] nonce = ByteUtil.cloneBytes(null);
	private Coin value = Coin.ZERO;
	private RskAddress receiveAddress = RskAddress.nullAddress();
	private Coin gasPrice = Coin.ZERO;
	private byte[] gasLimit = ByteUtil.cloneBytes(null);
	private byte[] data = ByteUtil.cloneBytes(null);
	private byte chainId = 0;

	TransactionBuilder() {
	}

	public TransactionBuilder value(BigInteger value) {
		return this.value(BigIntegers.asUnsignedByteArray(value));
	}

	public TransactionBuilder gasLimit(BigInteger limit) {
		return this.gasLimit(BigIntegers.asUnsignedByteArray(limit));
	}

	public TransactionBuilder gasPrice(BigInteger price) {
		return this.gasPrice(price.toByteArray());
	}

	public TransactionBuilder nonce(BigInteger nonce) {
		return this.nonce(BigIntegers.asUnsignedByteArray(nonce));
	}

	public TransactionBuilder isLocalCall(boolean isLocalCall) {
		this.isLocalCall = isLocalCall;
		return this;
	}

	public TransactionBuilder nonce(byte[] nonce) {
		this.nonce = ByteUtil.cloneBytes(nonce);
		return this;
	}

	public TransactionBuilder value(Coin value) {
		this.value = value;
		return this;
	}

	public TransactionBuilder value(byte[] value) {
		this.value(RLP.parseCoin(ByteUtil.cloneBytes(value)));
		return this;
	}

	public TransactionBuilder destination(RskAddress receiveAddress) {
		this.receiveAddress = receiveAddress;
		return this;
	}

	public TransactionBuilder destination(byte[] receiveAddress) {
		return this.destination(RLP.parseRskAddress(ByteUtil.cloneBytes(receiveAddress)));
	}

	public TransactionBuilder gasPrice(Coin gasPrice) {
		this.gasPrice = gasPrice;
		return this;
	}

	public TransactionBuilder gasPrice(byte[] gasPrice) {
		this.gasPrice(RLP.parseCoinNonNullZero(ByteUtil.cloneBytes(gasPrice)));
		return this;
	}

	public TransactionBuilder gasLimit(byte[] gasLimit) {
		this.gasLimit = ByteUtil.cloneBytes(gasLimit);
		return this;
	}

	public TransactionBuilder data(byte[] data) {
		this.data = ByteUtil.cloneBytes(data);
		return this;
	}

	public TransactionBuilder chainId(byte chainId) {
		this.chainId = chainId;
		return this;
	}

	public TransactionBuilder destination(String to) {
		return this.destination(to == null ? null : Hex.decode(to));
	}

	public TransactionBuilder gasLimit(Coin value) {
		return this.gasLimit(value.getBytes());
	}

	public TransactionBuilder nonce(Coin value) {
		return this.nonce(value.getBytes());
	}

	public Transaction build() {
		return new Transaction(this.nonce, this.gasPrice, this.gasLimit, this.receiveAddress, this.value, this.data, this.chainId, this.isLocalCall);
	}

	public TransactionBuilder data(String data) {
		return this.data(data == null ? null : Hex.decode(data));
	}

	public TransactionBuilder withTransactionArguments(TransactionArguments args) {

		nonce(args.getNonce());
		gasPrice(args.getGasPrice());
		gasLimit(args.getGasLimit());
		destination(args.getTo());
		data(args.getData());
		chainId(args.getChainId());
		value(BigIntegers.asUnsignedByteArray(args.getValue()));

		return this;
	}

}
