/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package org.ethereum.core.transaction.parser;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;
import org.ethereum.core.TransactionTypePrefix;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.parser.util.AccessListCodec;
import org.ethereum.core.transaction.parser.util.AuthorizationListCodec;
import org.ethereum.core.transaction.parser.util.CommonParsingUtils;
import org.ethereum.rpc.CallArguments;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.GasCost;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static org.ethereum.rpc.exception.RskJsonRpcRequestException.invalidParamError;

/**
 * Structured transaction input for parser ingress from JSON-RPC call arguments or the transaction builder.
 * Raw byte ingress decodes to {@link org.ethereum.util.RLPList} and bypasses this type.
 */
public final class TransactionInput {

    private static final BigInteger DEFAULT_GAS_LIMIT = BigInteger.valueOf(GasCost.TRANSACTION_DEFAULT);
    private static final String ERR_INVALID_CHAIN_ID = "Invalid chainId: ";

    private final TransactionTypePrefix typePrefix;
    @Nullable
    private final byte[] nonce;
    @Nullable
    private final Coin gasPrice;
    @Nullable
    private final Coin maxPriorityFeePerGas;
    @Nullable
    private final Coin maxFeePerGas;
    @Nullable
    private final byte[] gasLimit;
    @Nullable
    private final RskAddress receiveAddress;
    @Nullable
    private final Coin value;
    @Nullable
    private final byte[] data;
    @Nullable
    private final Byte chainId;
    @Nullable
    private final byte[] accessListBytes;
    @Nullable
    private final List<SetCodeAuthorization> authorizationList;

    private TransactionInput(
            TransactionTypePrefix typePrefix,
            @Nullable byte[] nonce,
            @Nullable Coin gasPrice,
            @Nullable Coin maxPriorityFeePerGas,
            @Nullable Coin maxFeePerGas,
            @Nullable byte[] gasLimit,
            @Nullable RskAddress receiveAddress,
            @Nullable Coin value,
            @Nullable byte[] data,
            @Nullable Byte chainId,
            @Nullable byte[] accessListBytes,
            @Nullable List<SetCodeAuthorization> authorizationList
    ) {
        this.typePrefix = Objects.requireNonNull(typePrefix, "typePrefix");
        this.nonce = ByteUtil.cloneBytes(nonce);
        this.gasPrice = gasPrice;
        this.maxPriorityFeePerGas = maxPriorityFeePerGas;
        this.maxFeePerGas = maxFeePerGas;
        this.gasLimit = ByteUtil.cloneBytes(gasLimit);
        this.receiveAddress = receiveAddress;
        this.value = value;
        this.data = ByteUtil.cloneBytes(data);
        this.chainId = chainId;
        this.accessListBytes = ByteUtil.cloneBytes(accessListBytes);
        this.authorizationList = authorizationList == null ? null : List.copyOf(authorizationList);
    }

    public static TransactionInput fromCallArguments(CallArguments args, Supplier<String> nonceSupplier) {
        Objects.requireNonNull(args, "args");
        if (args.getNonce() == null && nonceSupplier != null) {
            args.setNonce(nonceSupplier.get());
        }

        TransactionTypePrefix typePrefix = TransactionTypePrefix.fromHex(args.getType(), args.getRskSubtype());
        BigInteger nonce = Optional.ofNullable(args.getNonce())
                .map(HexUtils::strHexOrStrNumberToBigInteger)
                .orElse(null);
        BigInteger gasLimit = CommonParsingUtils.parseBigInteger(
                args.getGas(),
                () -> CommonParsingUtils.parseBigInteger(args.getGasLimit(), () -> DEFAULT_GAS_LIMIT));
        Coin gasPrice = CommonParsingUtils.parseCoin(args.getGasPrice());
        Coin value = CommonParsingUtils.parseCoin(args.getValue());
        RskAddress receiveAddress = CommonParsingUtils.parseAddress(args.getTo());
        byte[] data = CommonParsingUtils.parseHexData(args.getData());
        Byte chainId = parseOptionalChainId(args.getChainId());
        byte[] accessListBytes = AccessListCodec.encodeAccessList(args.getAccessList());
        List<SetCodeAuthorization> authorizationList = args.getAuthorizationList() == null
                ? null
                : AuthorizationListCodec.parseFromCallArguments(args.getAuthorizationList());

        Coin maxPriorityFeePerGas = parseOptionalCoin(args.getMaxPriorityFeePerGas());
        Coin maxFeePerGas = parseOptionalCoin(args.getMaxFeePerGas());

        return new TransactionInput(
                typePrefix,
                nonce == null ? null : nonce.toByteArray(),
                gasPrice,
                maxPriorityFeePerGas,
                maxFeePerGas,
                gasLimit.toByteArray(),
                receiveAddress,
                value,
                data,
                chainId,
                accessListBytes,
                authorizationList
        );
    }

    public static TransactionInput fromBuilderState(
            TransactionTypePrefix typePrefix,
            @Nullable byte[] nonce,
            @Nullable Coin gasPrice,
            @Nullable Coin maxPriorityFeePerGas,
            @Nullable Coin maxFeePerGas,
            @Nullable byte[] gasLimit,
            @Nullable RskAddress receiveAddress,
            @Nullable Coin value,
            @Nullable byte[] data,
            @Nullable Byte chainId,
            @Nullable byte[] accessListBytes,
            @Nullable List<SetCodeAuthorization> authorizationList
    ) {
        return new TransactionInput(
                typePrefix,
                nonce,
                gasPrice,
                maxPriorityFeePerGas,
                maxFeePerGas,
                gasLimit,
                receiveAddress,
                value,
                data,
                chainId,
                accessListBytes,
                authorizationList
        );
    }

    public TransactionTypePrefix typePrefix() {
        return typePrefix;
    }

    @Nullable
    public byte[] nonce() {
        return ByteUtil.cloneBytes(nonce);
    }

    @Nullable
    public Coin gasPrice() {
        return gasPrice;
    }

    @Nullable
    public Coin maxPriorityFeePerGas() {
        return maxPriorityFeePerGas;
    }

    @Nullable
    public Coin maxFeePerGas() {
        return maxFeePerGas;
    }

    @Nullable
    public byte[] gasLimit() {
        return ByteUtil.cloneBytes(gasLimit);
    }

    @Nullable
    public RskAddress receiveAddress() {
        return receiveAddress;
    }

    @Nullable
    public Coin value() {
        return value;
    }

    @Nullable
    public byte[] data() {
        return ByteUtil.cloneBytes(data);
    }

    @Nullable
    public Byte chainId() {
        return chainId;
    }

    @Nullable
    public byte[] accessListBytes() {
        return ByteUtil.cloneBytes(accessListBytes);
    }

    @Nullable
    public List<SetCodeAuthorization> authorizationList() {
        return authorizationList == null ? null : List.copyOf(authorizationList);
    }

    @Nullable
    private static Coin parseOptionalCoin(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return new Coin(HexUtils.strHexOrStrNumberToBigInteger(value));
    }

    @Nullable
    private static Byte parseOptionalChainId(String hex) {
        if (hex == null) {
            return null;
        }
        try {
            byte[] bytes = HexUtils.strHexOrStrNumberToByteArray(hex);
            if (bytes.length != 1) {
                throw invalidParamError(ERR_INVALID_CHAIN_ID + hex);
            }
            return bytes[0];
        } catch (RskJsonRpcRequestException e) {
            throw e;
        } catch (Exception e) {
            throw invalidParamError(ERR_INVALID_CHAIN_ID + hex, e);
        }
    }

    static byte resolveLegacyChainId(@Nullable Byte chainId, byte defaultChainId) {
        if (chainId == null) {
            return defaultChainId;
        }
        return chainId == 0 ? defaultChainId : chainId;
    }

    static byte resolveTypedChainId(@Nullable Byte chainId) {
        if (chainId == null) {
            throw invalidParamError("Typed transaction requires chainId");
        }
        return chainId;
    }

    static BigInteger resolveGasLimit(@Nullable byte[] gasLimitBytes) {
        if (gasLimitBytes == null) {
            return DEFAULT_GAS_LIMIT;
        }
        return new BigInteger(1, gasLimitBytes);
    }

    static byte[] resolveNonceBytes(@Nullable byte[] nonceBytes, boolean defaultToZero) {
        if (nonceBytes != null) {
            return nonceBytes;
        }
        return defaultToZero ? BigInteger.ZERO.toByteArray() : null;
    }
}
