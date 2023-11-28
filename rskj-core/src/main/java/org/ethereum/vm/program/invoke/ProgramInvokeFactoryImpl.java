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
package org.ethereum.vm.program.invoke;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.SignatureCache;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.Program;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Optional;

import static org.apache.commons.lang3.ArrayUtils.nullToEmpty;

/**
 * @author Roman Mandeleil
 * @since 08.06.2014
 */
public class ProgramInvokeFactoryImpl implements ProgramInvokeFactory {

    private static final Logger logger = LoggerFactory.getLogger("VM");

    // Invocation by the wire tx
    @Override
    public ProgramInvoke createProgramInvoke(Transaction tx, int txindex, Block block, Repository repository,
                                             BlockStore blockStore, SignatureCache signatureCache) {

        /***         ADDRESS op       ***/
        // YP: Get address of currently executing account.
        RskAddress addr = tx.isContractCreation() ? tx.getContractAddress() : tx.getReceiveAddress();

        /***         ORIGIN op       ***/
        // YP: This is the sender of original transaction; it is never a contract.
        byte[] origin = tx.getSender(signatureCache).getBytes();

        /***         CALLER op       ***/
        // YP: This is the address of the account that is directly responsible for this execution.
        byte[] caller = tx.getSender(signatureCache).getBytes();

        /***         BALANCE op       ***/
        Coin getBalanceResult = repository.getBalance(addr);
        Coin balance = getBalanceResult == null ? Coin.ZERO : getBalanceResult;

        /***         GASPRICE op       ***/
        Coin gasPrice = tx.getGasPrice();

        /*** GAS op ***/
        byte[] gas = tx.getGasLimit();

        /***        CALLVALUE op      ***/
        Coin callValue = tx.getValue();

        /***     CALLDATALOAD  op   ***/
        /***     CALLDATACOPY  op   ***/
        /***     CALLDATASIZE  op   ***/
        byte[] data = tx.isContractCreation() ? ByteUtil.EMPTY_BYTE_ARRAY : nullToEmpty(tx.getData());

        /***    PREVHASH  op  ***/
        byte[] lastHash = block.getParentHash().getBytes();

        /***   COINBASE  op ***/
        byte[] coinbase = block.getCoinbase().getBytes();

        /*** TIMESTAMP  op  ***/
        long timestamp = block.getTimestamp();

        /*** NUMBER  op  ***/
        long number = block.getNumber();

        /*** DIFFICULTY  op  ***/
        byte[] difficulty = block.getDifficulty().getBytes();

        /*** GASLIMIT op ***/
        byte[] gaslimit = block.getGasLimit();

        if (logger.isInfoEnabled()) {
            logger.info("Top level call: \n" +
                            "address={}\n" +
                            "origin={}\n" +
                            "caller={}\n" +
                            "balance={}\n" +
                            "gasPrice={}\n" +
                            "gas={}\n" +
                            "callValue={}\n" +
                            "data={}\n" +
                            "lastHash={}\n" +
                            "coinbase={}\n" +
                            "timestamp={}\n" +
                            "blockNumber={}\n" +
                            "transactionIndex={}\n" +
                            "difficulty={}\n" +
                            "gaslimit={}\n",

                    addr,
                    ByteUtil.toHexString(origin),
                    ByteUtil.toHexString(caller),
                    balance,
                    gasPrice,
                    new BigInteger(1, gas).longValue(),
                    callValue,
                    ByteUtil.toHexString(data),
                    ByteUtil.toHexString(lastHash),
                    ByteUtil.toHexString(coinbase),
                    timestamp,
                    number,
                    txindex,
                    ByteUtil.toHexString(difficulty),
                    gaslimit);
        }

        return new ProgramInvokeImpl(addr.getBytes(), origin, caller, balance.getBytes(), gasPrice.getBytes(), gas, callValue.getBytes(), data,
                lastHash, coinbase, timestamp, number, txindex,difficulty, gaslimit,
                repository, blockStore);
    }

    /**
     * This invocation created for contract call contract
     */
    @Override
    public ProgramInvoke createProgramInvoke(Program program, DataWord toAddress, DataWord callerAddress,
                                             DataWord inValue,
                                             long inGas,
                                             Coin balanceInt, byte[] dataIn,
                                             Repository repository, BlockStore blockStore,
                                             boolean isStaticCall, boolean byTestingSuite) {

        DataWord address = toAddress;
        DataWord origin = program.getOriginAddress();
        DataWord caller = callerAddress;

        DataWord balance = DataWord.valueOf(balanceInt.getBytes());
        DataWord gasPrice = program.getGasPrice();
        long agas = inGas;
        DataWord callValue = inValue;

        byte[] data = dataIn;
        DataWord lastHash = program.getPrevHash();
        DataWord coinbase = program.getCoinbase();
        DataWord timestamp = program.getTimestamp();
        DataWord number = program.getNumber();
        DataWord transactionIndex = program.getTransactionIndex();
        DataWord difficulty = program.getDifficulty();
        DataWord gasLimit = program.getGasLimit();

        if (logger.isInfoEnabled()) {
            logger.info("Internal call: \n" +
                            "address={}\n" +
                            "origin={}\n" +
                            "caller={}\n" +
                            "balance={}\n" +
                            "gasPrice={}\n" +
                            "gas={}\n" +
                            "callValue={}\n" +
                            "data={}\n" +
                            "lastHash={}\n" +
                            "coinbase={}\n" +
                            "timestamp={}\n" +
                            "blockNumber={}\n" +
                            "transactionIndex={}\n" +
                            "difficulty={}\n" +
                            "gaslimit={}\n",
                    ByteUtil.toHexString(address.getLast20Bytes()),
                    ByteUtil.toHexString(origin.getLast20Bytes()),
                    ByteUtil.toHexString(caller.getLast20Bytes()),
                    balance.toString(),
                    gasPrice.longValue(),
                    agas,
                    ByteUtil.toHexString(callValue.getNoLeadZeroesData()),
                    data == null ? "" : ByteUtil.toHexString(data),
                    ByteUtil.toHexString(lastHash.getData()),
                    ByteUtil.toHexString(coinbase.getLast20Bytes()),
                    timestamp.longValue(),
                    number.longValue(),
                    transactionIndex.intValue(),
                    ByteUtil.toHexString(difficulty.getNoLeadZeroesData()),
                    gasLimit.bigIntValue());
        }

        return new ProgramInvokeImpl(address, origin, caller, balance, gasPrice, agas, callValue,
                data, lastHash, coinbase, timestamp, number, transactionIndex, difficulty, gasLimit,
                repository, program.getCallDeep() + 1, blockStore,
                isStaticCall, byTestingSuite);
    }
}
