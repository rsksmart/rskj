package org.ethereum.core;

import co.rsk.core.RskAddress;
import org.ethereum.config.Constants;
import org.ethereum.core.transaction.SetCodeAuthorization;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.signature.Secp256k1;
import org.ethereum.vm.GasCost;

import java.math.BigInteger;
import java.security.SignatureException;
import java.util.Arrays;


public class SetCodeAuthorizationTransactionExecutor {

    public static final byte[] DELEGATION_PREFIX_IN_BYTES = new byte[] {(byte) 0xef, 0x01, 0x00};

    public long processAuthorizationTuple(Repository repository, BigInteger outerTransactionChainId, SetCodeAuthorization authorizationTuple) {
        verifyChainId(authorizationTuple.getChainId(), outerTransactionChainId);
        authorizationTuple.verifyNonceRange();

        RskAddress authority = checkRecoveredAuthority(authorizationTuple);

        byte[] code = repository.getCode(authority);
        byte[] currentNonce  = repository.getNonce(authority).toByteArray();

        verifyAuthorityCode(code);
        verifyAuthorityNonce(authorizationTuple.getNonce(), currentNonce);

        long refund = calculateRefund(code);

        writeDelegation(authority, authorizationTuple.getAddress(), repository);

        repository.increaseNonce(authority);

        return refund;
    }

    private void verifyChainId(BigInteger chainId, BigInteger outerTransactionChainId) {
        final BigInteger UNIVERSAL_CHAIN_ID = BigInteger.ZERO;
        boolean valid = chainId.equals(UNIVERSAL_CHAIN_ID) || chainId.equals(BigInteger.valueOf(Constants.MAINNET_CHAIN_ID)) || chainId.equals(BigInteger.valueOf(Constants.TESTNET_CHAIN_ID)) || chainId.equals(BigInteger.valueOf(Constants.REGTEST_CHAIN_ID));
        if (!valid) {
            throw new IllegalStateException("Invalid chain ID");
        }

        if (!chainId.equals(UNIVERSAL_CHAIN_ID) && !chainId.equals(outerTransactionChainId)) {
            throw new IllegalStateException("Chain ID mismatch");
        }
    }

    private RskAddress checkRecoveredAuthority(SetCodeAuthorization setCodeAuthorization) {
        setCodeAuthorization.verifyLowS();

        byte[] messageHash =  setCodeAuthorization.getSigningHash();
        ECKey key;

        try {
            key = Secp256k1.getInstance().signatureToKey(messageHash, setCodeAuthorization.getSignature());

        } catch (SignatureException e) {
            throw new IllegalStateException("Signature recovery failed", e);
        }

        if (key == null) {
            throw new IllegalStateException("Signature recovery failed");
        }

        RskAddress authority = new RskAddress(key.getAddress());

        if (authority.equals(RskAddress.nullAddress())) {
            throw new IllegalStateException("Recovered authority is zero address");
        }

        return authority;
    }


    private void verifyAuthorityCode(byte[] code) {
        if (code == null || code.length == 0) {
            return;
        }
        if (isDelegatedCode(code)) {
            return;
        }
        throw new IllegalStateException("Authority contains non-delegated code");
    }

    public static boolean isDelegatedCode(byte[] code) {
        // delegation indicator: 0xef0100 || 20-byte address
        if (code == null || code.length != 23) {
            return false;
        }
        return code[0] == (byte) 0xef  && code[1] == 0x01 && code[2] == 0x00;
    }

    private void verifyAuthorityNonce(byte[] expectedNonce, byte[] currentNonce) {
        if (!Arrays.equals(currentNonce, expectedNonce)) {
            throw new IllegalStateException("Authority nonce mismatch");
        }
    }

    private long calculateRefund(byte[] code) {
        boolean isEmpty = code == null || code.length == 0;
        return isEmpty ? 0L : GasCost.PER_EMPTY_ACCOUNT_COST - GasCost.PER_AUTH_BASE_COST;
    }

    private void writeDelegation(RskAddress authority, RskAddress delegatedAddress, Repository repository) {
        if (delegatedAddress.equals(RskAddress.nullAddress()) || delegatedAddress.equals(RskAddress.ZERO_ADDRESS)) {
            repository.saveCode(authority, HashUtil.keccak256(new byte[0]));
            return;
        }
        byte[] codeToSet = createDelegatedCode(delegatedAddress);
        repository.saveCode(authority, codeToSet);
    }

    public static byte[]  createDelegatedCode(RskAddress delegatedAddress) {
        if (delegatedAddress.equals(RskAddress.nullAddress()) || delegatedAddress.equals(RskAddress.ZERO_ADDRESS)) {
            throw new IllegalStateException("Delegated address can not be empty");
        }
        byte[] delegatedAddressBytes = delegatedAddress.getBytes();
        byte[] codeToSet = new byte[DELEGATION_PREFIX_IN_BYTES.length + delegatedAddressBytes.length];

        System.arraycopy(DELEGATION_PREFIX_IN_BYTES, 0, codeToSet, 0, DELEGATION_PREFIX_IN_BYTES.length);
        System.arraycopy(delegatedAddressBytes, 0, codeToSet, DELEGATION_PREFIX_IN_BYTES.length, delegatedAddressBytes.length);
        return codeToSet;
    }
}
