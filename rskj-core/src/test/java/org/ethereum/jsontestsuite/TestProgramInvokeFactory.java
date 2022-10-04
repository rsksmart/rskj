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
package org.ethereum.jsontestsuite;

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
import org.ethereum.vm.program.invoke.ProgramInvoke;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.ethereum.vm.program.invoke.ProgramInvokeImpl;

import java.util.Map;

/**
 * @author Roman Mandeleil
 * @since 19.12.2014
 */
public class TestProgramInvokeFactory implements ProgramInvokeFactory {

    private final Env env;

    public TestProgramInvokeFactory(Env env) {
        this.env = env;
    }


    @Override
    public ProgramInvoke createProgramInvoke(Transaction tx, int txindex, Block block, Repository repository, BlockStore blockStore, SignatureCache signatureCache) {
        return generalInvoke(tx, txindex, repository, blockStore, signatureCache);
    }

    @Override
    public ProgramInvoke createProgramInvoke(Program program, DataWord toAddress, DataWord callerAddress,
                                             DataWord inValue, long inGas,
                                             Coin balanceInt, byte[] dataIn,
                                             Repository repository, BlockStore blockStore,
                                             boolean isStaticCall, boolean byTestingSuite,
                                             Map<Integer, Long> lockedGasByDepth) {
        return null;
    }

    private ProgramInvoke generalInvoke(Transaction tx, int txindex, Repository repository, BlockStore blockStore, SignatureCache signatureCache) {

        /***         ADDRESS op       ***/
        // YP: Get address of currently executing account.
        RskAddress addr = tx.isContractCreation() ? tx.getContractAddress() : tx.getReceiveAddress();

        /***         ORIGIN op       ***/
        // YP: This is the sender of original transaction; it is never a contract.
        RskAddress origin = tx.getSender(signatureCache);

        /***         CALLER op       ***/
        // YP: This is the address of the account that is directly responsible for this execution.
        RskAddress caller = tx.getSender(signatureCache);

        /***         BALANCE op       ***/
        Coin balance = repository.getBalance(addr);

        /***         GASPRICE op       ***/
        Coin gasPrice = tx.getGasPrice();

        /*** GAS op ***/
        byte[] gas = tx.getGasLimit();

        /***        CALLVALUE op      ***/
        Coin callValue = tx.getValue() == null ? Coin.ZERO : tx.getValue();

        /***     CALLDATALOAD  op   ***/
        /***     CALLDATACOPY  op   ***/
        /***     CALLDATASIZE  op   ***/
        byte[] data = tx.isContractCreation() ? ByteUtil.EMPTY_BYTE_ARRAY :( tx.getData() == null ? ByteUtil.EMPTY_BYTE_ARRAY : tx.getData() );
//        byte[] data =  tx.getData() == null ? ByteUtil.EMPTY_BYTE_ARRAY : tx.getData() ;

        /***    PREVHASH  op  ***/
        byte[] lastHash = env.getPreviousHash();

        /***   COINBASE  op ***/
        byte[] coinbase = env.getCurrentCoinbase();

        /*** TIMESTAMP  op  ***/
        long timestamp = ByteUtil.byteArrayToLong(env.getCurrentTimestamp());

        /*** NUMBER  op  ***/
        long number = ByteUtil.byteArrayToLong(env.getCurrentNumber());

        /*** DIFFICULTY  op  ***/
        byte[] difficulty = env.getCurrentDifficulty();

        /*** GASLIMIT op ***/
        byte[] gaslimit = env.getCurrentGasLimit();

        return new ProgramInvokeImpl(addr.getBytes(), origin.getBytes(), caller.getBytes(), balance.getBytes(),
                gasPrice.getBytes(), gas, callValue.getBytes(), data, lastHash, coinbase,
                timestamp, number, txindex, difficulty, gaslimit, repository, blockStore, null);
    }

}
