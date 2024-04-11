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

import co.rsk.PropertyGetter;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.AccountInformationProvider;
import org.ethereum.config.Constants;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.HashUtil;
import org.ethereum.solidity.SolidityType;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;

import java.math.BigInteger;
import java.util.Arrays;

public class ContractUtil {
    private static final String SWAPS_MAP_POSITION = "0000000000000000000000000000000000000000000000000000000000000000";


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

    public static boolean isClaimTxAndValid(Transaction newTx,
                                            Coin txCost,
                                            Constants constants,
                                            SignatureCache signatureCache,
                                            PropertyGetter propertyGetter) {
        byte[] functionSelector = Arrays.copyOfRange(newTx.getData(), 0, 4);
        if(newTx.getReceiveAddress().toHexString().equalsIgnoreCase(constants.getEtherSwapContractAddress())
                && Arrays.equals(functionSelector, Constants.CLAIM_FUNCTION_SIGNATURE)) {

            String swapHash = HexUtils.toUnformattedJsonHex(calculateSwapHash(newTx, signatureCache));
            byte[] key = HashUtil.keccak256(HexUtils.decode(HexUtils.stringToByteArray(swapHash + SWAPS_MAP_POSITION)));

            AccountInformationProvider accountInformationProvider = propertyGetter.getWeb3InfoRetriever().getInformationProvider("latest");
            DataWord swapRecord = accountInformationProvider.getStorageValue(newTx.getReceiveAddress(), DataWord.valueOf(key));

            if(swapRecord != null){
                CallTransaction.Invocation invocation = getTxInvocation(newTx);
                BigInteger amount = (BigInteger) invocation.args[1];
                Coin balanceWithClaim = accountInformationProvider.getBalance(newTx.getSender(signatureCache))
                        .add(Coin.valueOf(amount.longValue()));

                return txCost.compareTo(balanceWithClaim) <= 0;
            }
        }

        return false;
    }
}
