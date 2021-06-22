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

package org.ethereum.core;

import java.math.BigInteger;

import java.util.Arrays;


public class TransactionArguments {

	private String from;
	private byte[] to;
	private BigInteger gas;
	private BigInteger gasLimit;
	private BigInteger gasPrice;
	private BigInteger value;
	private String data; // compiledCode
	private BigInteger nonce;
	private byte chainId; // NOSONAR

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public byte[] getTo() {
		return to;
	}

	public void setTo(byte[] to) {
		this.to = to;
	}

	public BigInteger getGas() {
		return gas;
	}

	public void setGas(BigInteger gas) {
		this.gas = gas;
	}

	public BigInteger getGasLimit() {
		return gasLimit;
	}

	public void setGasLimit(BigInteger gasLimit) {
		this.gasLimit = gasLimit;
	}

	public BigInteger getGasPrice() {
		return gasPrice;
	}

	public void setGasPrice(BigInteger gasPrice) {
		this.gasPrice = gasPrice;
	}

	public BigInteger getValue() {
		return value;
	}

	public void setValue(BigInteger value) {
		this.value = value;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public BigInteger getNonce() {
		return nonce;
	}

	public void setNonce(BigInteger nonce) {
		this.nonce = nonce;
	}

	public byte getChainId() {
		return chainId;
	}

	public void setChainId(byte chainId) {
		this.chainId = chainId;
	}

	@Override
	public String toString() {
		return "TransactionArguments{" +
			"from='" + from + '\'' +
			", to='" + Arrays.toString(to) + '\'' +
			", gasLimit='" + ((gas != null)?gas:gasLimit) + '\'' +
			", gasPrice='" + gasPrice + '\'' +
			", value='" + value + '\'' +
			", data='" + data + '\'' +
			", nonce='" + nonce + '\'' +
			", chainId='" + chainId + '\'' +
			"}";
    }
}