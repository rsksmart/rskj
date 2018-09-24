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

package org.ethereum.jsontestsuite.validators;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.crypto.HashUtil;
import org.ethereum.vm.DataWord;

import java.math.BigInteger;
import java.util.*;

import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

public class AccountValidator {
    // This class must be used with GlobalKeyMap.enabled == true. If not, a NullPointerException
    // will be thrown in getStorage()

    private static final byte[] EMPTY_DATA_HASH = HashUtil.keccak256(EMPTY_BYTE_ARRAY);

    public static List<String> valid(RskAddress addr, Repository currentRepository, Repository expectedRepository) {
        AccountState currentState = currentRepository.getAccountState(addr);
        AccountState expectedState = expectedRepository.getAccountState(addr);

        List<String> results = new ArrayList<>();

        if (currentState == null || !currentRepository.isContract(addr)) {
            String formattedString = String.format("Account: %s: expected but doesn't exist",
                    addr);
            results.add(formattedString);
            return results;
        }

        if (expectedState == null || !expectedRepository.isContract(addr)) {
            String formattedString = String.format("Account: %s: unexpected account in the repository",
                    addr);
            results.add(formattedString);
            return results;
        }


        Coin expectedBalance = expectedState.getBalance();
        if (!currentState.getBalance().equals(expectedBalance)) {
            String formattedString = String.format("Account: %s: has unexpected balance, expected balance: %s found balance: %s",
                    addr, expectedBalance.toString(), currentState.getBalance().toString());
            results.add(formattedString);
        }

        BigInteger expectedNonce = expectedState.getNonce();
        if (currentState.getNonce().compareTo(expectedNonce) != 0) {
            String formattedString = String.format("Account: %s: has unexpected nonce, expected nonce: %s found nonce: %s",
                    addr, expectedNonce.toString(), currentState.getNonce().toString());
            results.add(formattedString);
        }

        if (!Arrays.equals(expectedRepository.getCode(addr), currentRepository.getCode(addr))) {
            String formattedString = String.format("Account: %s: has unexpected code, expected code: %s found code: %s",
                    addr, Hex.toHexString(expectedRepository.getCode(addr)), Hex.toHexString(currentRepository.getCode(addr)));
            results.add(formattedString);
        }


        // compare storage
        Iterator<DataWord> currentKeys = currentRepository.getStorageKeys(addr);
        Iterator<DataWord> expectedKeys = expectedRepository.getStorageKeys(addr);
        Set<DataWord> checked = new HashSet<>();

        while (currentKeys.hasNext()) {
            DataWord key = currentKeys.next();

            byte[] currentValue = currentRepository.getStorageBytes(addr, key);
            byte[] expectedValue = expectedRepository.getStorageBytes(addr, key);
            if (expectedValue == null) {

                String formattedString = String.format("Account: %s: has unexpected storage data: %s = %s",
                        addr,
                        key,
                        Hex.toHexString(currentValue));

                results.add(formattedString);
                continue;
            }

            if (!Arrays.equals(expectedValue, currentValue)) {

                String formattedString = String.format("Account: %s: has unexpected value, for key: %s , "+
                                "expectedValue: %s real value: %s",
                        addr,
                        key.toString(),
                        Hex.toHexString(expectedValue),
                        Hex.toHexString(currentValue));
                results.add(formattedString);
                continue;
            }

            checked.add(key);
        }

        while (expectedKeys.hasNext()) {
            DataWord key = expectedKeys.next();

            if (!checked.contains(key)) {
                String formattedString = String.format("Account: %s: doesn't exist expected storage key: %s",
                        addr, key.toString());
                results.add(formattedString);
            }
        }

        return results;
    }
}
