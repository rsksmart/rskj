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
import org.ethereum.core.transaction.parser.ParsedRawTransaction;
import org.ethereum.core.transaction.parser.ParsedType0Transaction;
import org.ethereum.core.transaction.parser.ParsedType1Transaction;
import org.ethereum.core.transaction.parser.ParsedType2RSKTransaction;
import org.ethereum.core.transaction.parser.ParsedType2Transaction;
import org.ethereum.core.transaction.parser.ParsedType4Transaction;
import org.ethereum.core.transaction.parser.SignedSignature;
import org.ethereum.core.transaction.parser.UnsignedSignature;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;

import java.math.BigInteger;
import java.util.List;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

public final class TransactionBuilder {

	private TransactionTypePrefix typePrefix = TransactionTypePrefix.typed(TransactionType.LEGACY);
	private static final byte[] EMPTY_ACCESS_LIST_RLP = new byte[]{(byte) 0xc0};

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
		this.authorizationList = authorizationList;
		return this;
	}

	public Transaction build() {
		normalizeTypedFields();

		if (gasLimit == null || gasPrice == null || value == null) {
			throw invalidParamError("Missing parameter, gasPrice, gasLimit or value");
		}

		return new Transaction(
				nonce,
				gasPrice,
				gasLimit,
				receiveAddress,
				value,
				data,
				chainId,
				isLocalCall,
				typePrefix,
				accessListBytes,
				maxPriorityFeePerGas,
				maxFeePerGas,
				authorizationList
		);
	}

	private void normalizeTypedFields() {
		TransactionType type = typePrefix.type();

		if (type == TransactionType.TYPE_1 && accessListBytes == null) {
			accessListBytes = EMPTY_ACCESS_LIST_RLP;
		}

		if (type == TransactionType.TYPE_2 && !typePrefix.isRskNamespace()) {
			if (accessListBytes == null) {
				accessListBytes = EMPTY_ACCESS_LIST_RLP;
			}
			Coin maxP = maxPriorityFeePerGas != null ? maxPriorityFeePerGas : gasPrice;
			Coin maxF = maxFeePerGas != null ? maxFeePerGas : gasPrice;

			if (maxP.compareTo(maxF) > 0) {
				throw new IllegalArgumentException(
						"Type 2 transaction maxPriorityFeePerGas (" + maxP +
								") must not exceed maxFeePerGas (" + maxF + ")"
				);
			}

			maxPriorityFeePerGas = maxP;
			maxFeePerGas = maxF;
			gasPrice = maxP;
		}

		if (type == TransactionType.TYPE_4) {
			if (accessListBytes == null) {
				accessListBytes = EMPTY_ACCESS_LIST_RLP;
			}
			if (authorizationList == null || authorizationList.isEmpty()) {
				throw new IllegalArgumentException("Set-code transaction authorization_list must not be empty");
			}
			Coin maxP = maxPriorityFeePerGas != null ? maxPriorityFeePerGas : gasPrice;
			Coin maxF = maxFeePerGas != null ? maxFeePerGas : gasPrice;

			if (maxP.compareTo(maxF) > 0) {
				throw new IllegalArgumentException(
                        "Type 4 transaction maxPriorityFeePerGas (" + maxP +
                                ") must not exceed maxFeePerGas (" + maxF + ")"
                );
			}

			maxPriorityFeePerGas = maxP;
			maxFeePerGas = maxF;
			gasPrice = maxP;
		}
	}

	//TEMP
	public static TransactionBuilder fromParsed(ParsedRawTransaction parsed) {
		byte chainId = 0;
		byte[] accessListBytes = new byte[0];
		Coin maxPriorityFeePerGas = null;
		Coin maxFeePerGas = null;
		Coin effectiveGasPrice = null;
		List<SetCodeAuthorization> authorizationList = null;

		if (parsed.signatureState() instanceof SignedSignature signed) {
			chainId = signed.chainId();
		}
		if (parsed.signatureState() instanceof UnsignedSignature unsigned) {
			chainId= unsigned.chainId() == null ? 0 : unsigned.chainId();
		}
		if (parsed instanceof ParsedType0Transaction type0Tx) {
			effectiveGasPrice =  type0Tx.gasPrice();
		}
		if (parsed instanceof ParsedType1Transaction type1Tx) {
			accessListBytes = type1Tx.accessListBytes();
			effectiveGasPrice =  type1Tx.gasPrice();
		}
		if (parsed instanceof ParsedType2Transaction type2Tx) {
			accessListBytes =  type2Tx.accessListBytes();
			maxPriorityFeePerGas = type2Tx.maxPriorityFeePerGas();
			maxFeePerGas = type2Tx.maxFeePerGas();
			effectiveGasPrice = type2Tx.maxPriorityFeePerGas().compareTo(type2Tx.maxFeePerGas()) <= 0
					? type2Tx.maxPriorityFeePerGas()
					: type2Tx.maxFeePerGas();
		}
		if (parsed instanceof ParsedType2RSKTransaction type2RskTx) {
			effectiveGasPrice =  type2RskTx.gasPrice();
		}
		if (parsed instanceof ParsedType4Transaction type4Tx) {
			accessListBytes = type4Tx.accessListBytes();
			maxPriorityFeePerGas = type4Tx.maxPriorityFeePerGas();
			maxFeePerGas = type4Tx.maxFeePerGas();
			authorizationList = type4Tx.authorizationList();
			effectiveGasPrice = type4Tx.maxPriorityFeePerGas().compareTo(type4Tx.maxFeePerGas()) <= 0
					? type4Tx.maxPriorityFeePerGas()
					: type4Tx.maxFeePerGas();
		}

		return new TransactionBuilder()
				.nonce(parsed.nonce())
				.gasPrice(effectiveGasPrice)
				.gasLimit(parsed.gasLimit())
				.receiveAddress(parsed.receiveAddress())
				.value(parsed.value())
				.data(parsed.data())
				.chainId(chainId)
				.typePrefix(parsed.typePrefix())
				.accessList(accessListBytes)
				.maxPriorityFeePerGas(maxPriorityFeePerGas)
				.maxFeePerGas(maxFeePerGas)
				.authorizationList(authorizationList);
	}
}
