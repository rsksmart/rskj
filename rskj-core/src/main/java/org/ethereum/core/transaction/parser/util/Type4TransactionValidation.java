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
package org.ethereum.core.transaction.parser.util;

import co.rsk.core.RskAddress;
import org.ethereum.config.Constants;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.core.exception.TransactionException;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.core.transaction.parser.ParsedType4Transaction;
import org.ethereum.core.transaction.parser.SignatureState;
import org.ethereum.core.transaction.parser.SignedSignature;
import org.ethereum.core.transaction.parser.UnsignedSignature;
import org.ethereum.crypto.signature.ECDSASignature;

import java.math.BigInteger;
import java.util.List;

/**
 * RSKIP-545 structural and format validations for type-4 (set-code) transactions.
 *
 * <p>Signature recovery ({@code ecrecover}) is validated separately via {@link Transaction#getSender}
 * so a structurally valid unsigned or not-yet-recoverable payload can still be parsed.
 */
public final class Type4TransactionValidation {

    private static final BigInteger SECP256K1N_HALF = Constants.getSECP256K1N().divide(BigInteger.valueOf(2));

    private Type4TransactionValidation() {}

    /**
     * Validates parsed type-4 fields that do not require chain context or {@code ecrecover}.
     */
    public static void validateParsed(ParsedType4Transaction parsed) {
        byte txChainId = resolveTxChainId(parsed.signatureState());
        validateAuthorizationChainIds(txChainId, parsed.authorizationList());
        if (parsed.signatureState() instanceof SignedSignature signed) {
            validateOuterSignatureFormat(signed.signature());
        }
    }

    /**
     * Tx {@code chain_id} must equal each {@code authority.chain_id} unless the authority chain id is zero.
     */
    public static void validateAuthorizationChainIds(byte txChainId, List<SetCodeAuthorization> authorizations) {
        BigInteger txChainIdValue = BigInteger.valueOf(txChainId & 0xFF);
        for (int i = 0; i < authorizations.size(); i++) {
            BigInteger authChainId = authorizations.get(i).getChainId();
            if (authChainId.signum() != 0 && authChainId.compareTo(txChainIdValue) != 0) {
                throw new IllegalArgumentException(
                        "Authorization chain_id at index " + i + " (" + authChainId
                                + ") must match transaction chain_id (" + txChainIdValue
                                + ") or be zero"
                );
            }
        }
    }

    /**
     * Validates outer transaction signature components (EIP-2 low-{@code s}, valid {@code r}/{@code s}).
     */
    public static void validateOuterSignatureFormat(ECDSASignature signature) {
        if (!signature.validateComponents()) {
            throw new IllegalArgumentException("Type 4 transaction signature components are invalid");
        }
        if (signature.getS().compareTo(SECP256K1N_HALF) >= 0) {
            throw new IllegalArgumentException("Type 4 transaction signature s must be at most secp256k1n/2");
        }
    }

    /**
     * Validates that outer signature recovery succeeds and the sender is not the zero address.
     */
    public static void validateOuterSignatureRecovery(Transaction transaction, SignatureCache signatureCache) {
        if (transaction.getType() != TransactionType.TYPE_4) {
            return;
        }
        if (transaction.getSignature() == null) {
            return;
        }
        RskAddress sender = transaction.getSender(signatureCache);
        if (RskAddress.nullAddress().equals(sender) || RskAddress.ZERO_ADDRESS.equals(sender)) {
            throw new TransactionException(
                    "Type 4 transaction signature recovery failed or sender is the zero address");
        }
    }

    private static byte resolveTxChainId(SignatureState signatureState) {
        if (signatureState instanceof SignedSignature signed) {
            return signed.chainId();
        }
        if (signatureState instanceof UnsignedSignature unsigned) {
            if (unsigned.chainId() == null) {
                throw new IllegalArgumentException("Type 4 transaction chainId must not be absent");
            }
            return unsigned.chainId();
        }
        throw new IllegalArgumentException("Type 4 transaction signature state is missing");
    }
}
