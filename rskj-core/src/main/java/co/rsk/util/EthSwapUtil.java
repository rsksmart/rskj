/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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
package co.rsk.util;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.ClaimTransactionInfoHolder;
import co.rsk.db.RepositorySnapshot;
import org.ethereum.config.Constants;
import org.ethereum.config.blockchain.upgrades.ActivationConfig;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.solidity.SolidityType;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

public class EthSwapUtil {
    private static final String SWAPS_MAP_POSITION = "0000000000000000000000000000000000000000000000000000000000000000";

    private EthSwapUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static byte[] calculateSwapHash(Transaction newTx, SignatureCache signatureCache) {

        CallTransaction.Invocation invocation = getTxInvocation(newTx);

        // Get arguments from the invocation object
        byte[] preimage = (byte[]) invocation.args[0];
        byte[] preimageHash = HashUtil.sha256(encodePacked(preimage));
        BigInteger amount = (BigInteger) invocation.args[1];
        DataWord refundAddress = (DataWord) invocation.args[2];
        BigInteger timeLock = (BigInteger) invocation.args[3];


        // Remove the leading zeroes from the arguments bytes and merge them
        byte[] argumentsBytes = encodePacked(
                preimageHash,
                amount,
                newTx.getSender(signatureCache),
                new RskAddress(ByteUtil.toHexString(refundAddress.getLast20Bytes())),
                timeLock
        );

        return HashUtil.keccak256(argumentsBytes);
    }

    public static byte[] encodePacked(Object...arguments) {
        byte[] encodedArguments = new byte[]{};

        for(Object arg: arguments) {
            byte[] encodedArg = new byte[]{};
            if(arg instanceof byte[]) {
                SolidityType bytes32Type = SolidityType.getType(SolidityType.BYTES32);
                encodedArg = bytes32Type.encode(arg);
            } else if(arg instanceof RskAddress) {
                SolidityType addressType = SolidityType.getType(SolidityType.ADDRESS);
                byte[] encodedAddress = addressType.encode(((RskAddress) arg).toHexString());
                encodedArg = org.bouncycastle.util.Arrays.copyOfRange(encodedAddress, 12, encodedAddress.length);
            } else if(arg instanceof BigInteger) {
                SolidityType uint256Type = SolidityType.getType(SolidityType.UINT);
                encodedArg = uint256Type.encode(arg);
            }

            encodedArguments = ByteUtil.merge(encodedArguments, encodedArg);
        }

        return encodedArguments;
    }

    public static CallTransaction.Invocation getTxInvocation(Transaction newTx) {
        String abi = "[{\"constant\":false,\"inputs\":[{\"name\":\"preimage\",\"type\":\"bytes32\"}, {\"name\":\"amount\",\"type\":\"uint256\"}, {\"name\":\"address\",\"type\":\"address\"}, {\"name\":\"timelock\",\"type\":\"uint256\"}],\"name\":\"claim\",\"outputs\":[],\"payable\":false,\"type\":\"function\"}]";
        CallTransaction.Contract contract = new CallTransaction.Contract(abi);
        return contract.parseInvocation(newTx.getData());
    }

    public static boolean isClaimTx(Transaction tx, Constants constants) {
        return tx.getReceiveAddress() != null
                && !tx.getReceiveAddress().toHexString().isEmpty()
                && tx.getData() != null
                && tx.getReceiveAddress().toHexString().equalsIgnoreCase(constants.getEtherSwapContractAddress())
                && Arrays.equals(Arrays.copyOfRange(tx.getData(), 0, 4), Constants.CLAIM_FUNCTION_SIGNATURE);
    }

    public static boolean hasLockedFunds(Transaction tx, SignatureCache signatureCache, RepositorySnapshot repositorySnapshot) {
        String swapHash = HexUtils.toUnformattedJsonHex(calculateSwapHash(tx, signatureCache));
        byte[] key = HashUtil.keccak256(HexUtils.decode(HexUtils.stringToByteArray(swapHash + SWAPS_MAP_POSITION)));

        DataWord swapRecord = repositorySnapshot.getStorageValue(tx.getReceiveAddress(), DataWord.valueOf(key));

        return swapRecord != null;
    }

    private static Coin getLockedAmount(Transaction tx) {
        CallTransaction.Invocation invocation = getTxInvocation(tx);
        BigInteger amount = (BigInteger) invocation.args[1];

        return Coin.valueOf(amount.longValue());
    }

    private static Coin getTxCost(Transaction tx,
                                  ActivationConfig.ForBlock activation,
                                  Constants constants,
                                  SignatureCache signatureCache) {
        Coin txCost = tx.getValue();
        if (activation == null || tx.transactionCost(constants, activation, signatureCache) > 0) {
            BigInteger gasLimit = BigInteger.valueOf(GasCost.toGas(tx.getGasLimit()));
            txCost = txCost.add(tx.getGasPrice().multiply(gasLimit));
        }

        return txCost;
    }

    public static boolean isClaimTxAndValid(ClaimTransactionInfoHolder claimTransactionInfoHolder,
                                            Coin txCost) {
        Transaction newTx = claimTransactionInfoHolder.getTx();
        Constants constants = claimTransactionInfoHolder.getConstants();
        SignatureCache signatureCache = claimTransactionInfoHolder.getSignatureCache();
        RepositorySnapshot repositorySnapshot = claimTransactionInfoHolder.getRepositorySnapshot();

        if(!isClaimTx(newTx, constants)) {
            return false;
        }

        if(hasLockedFunds(newTx, signatureCache, repositorySnapshot)){
            Coin lockedAmount = getLockedAmount(newTx);

            Coin balanceWithClaim = repositorySnapshot.getBalance(newTx.getSender(signatureCache))
                    .add(lockedAmount);

            return txCost.compareTo(balanceWithClaim) <= 0;
        }

        return false;
    }

    private static boolean isSameTx(Transaction pendingTx, Transaction newTx, SignatureCache signatureCache) {
        return pendingTx.getSender(signatureCache).toHexString().equals(newTx.getSender(signatureCache).toHexString())
                && pendingTx.getReceiveAddress().toHexString().equals(newTx.getReceiveAddress().toHexString())
                && Arrays.equals(pendingTx.getData(), newTx.getData());
    }

    public static boolean isClaimTxAndSenderCanPayAlongPendingTx(ClaimTransactionInfoHolder claimTransactionInfoHolder, List<Transaction> pendingTransactions) {
        Transaction newTx = claimTransactionInfoHolder.getTx();
        ActivationConfig.ForBlock activation = claimTransactionInfoHolder.getActivation();
        Constants constants = claimTransactionInfoHolder.getConstants();
        SignatureCache signatureCache = claimTransactionInfoHolder.getSignatureCache();
        RepositorySnapshot repositorySnapshot = claimTransactionInfoHolder.getRepositorySnapshot();

        if(!isClaimTx(newTx, constants)
            || !hasLockedFunds(newTx, signatureCache, repositorySnapshot)) {
            return false;
        }

        Coin totalLockedAmount = getLockedAmount(newTx);
        Coin totalClaimTxCost = getTxCost(newTx, activation, constants, signatureCache);
        Coin totalPendingTxCost = Coin.ZERO;

        for (Transaction tx : pendingTransactions) {
            if(isSameTx(tx, newTx, signatureCache)) {
                return false;
            }

            boolean isReplacedTx = Arrays.equals(tx.getNonce(), newTx.getNonce());

            if (isReplacedTx) {
                continue;
            }

            if(isClaimTx(tx, constants)) {
                totalClaimTxCost = totalClaimTxCost.add(getTxCost(tx, activation, constants, signatureCache));
                totalLockedAmount = totalLockedAmount.add(getLockedAmount(tx));
            } else {
                totalPendingTxCost = totalPendingTxCost.add(getTxCost(tx, activation, constants, signatureCache));
            }
        }

        Coin senderBalance = repositorySnapshot.getBalance(newTx.getSender(signatureCache));
        Coin balanceAfterPendingTx = senderBalance.subtract(totalPendingTxCost);


        return balanceAfterPendingTx.add(totalLockedAmount).compareTo(totalClaimTxCost) >= 0;
    }
}
