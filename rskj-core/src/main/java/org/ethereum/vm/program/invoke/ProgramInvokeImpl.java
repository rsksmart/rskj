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

import org.ethereum.core.Repository;
import org.ethereum.db.BlockStore;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.Program;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;

/**
 * @author Roman Mandeleil
 * @since 03.06.2014
 */
public class ProgramInvokeImpl implements ProgramInvoke {

    private BlockStore blockStore;
    /**
     * TRANSACTION  env **
     */
    private final DataWord address;
    private final DataWord origin;
    private final DataWord caller;
    private final DataWord balance;
    private final DataWord gasPrice;
    private final DataWord callValue;
    private long gas;

    byte[] msgData;

    /**
     * BLOCK  env **
     */
    private final DataWord prevHash;
    private final DataWord coinbase;
    private final DataWord timestamp;
    private final DataWord number;
    private final DataWord difficulty;
    private final DataWord gaslimit;

    private final DataWord transactionIndex;

    private Map<DataWord, DataWord> storage;

    private final Repository repository;
    private boolean byTransaction = true;
    private boolean byTestingSuite = false;
    private int callDeep = 0;
    private boolean isStaticCall = false;

    public ProgramInvokeImpl(DataWord address, DataWord origin, DataWord caller, DataWord balance,
                             DataWord gasPrice,
                             long gas,
                             DataWord callValue, byte[] msgData,
                             DataWord lastHash, DataWord coinbase, DataWord timestamp, DataWord number, DataWord transactionIndex, DataWord
                                     difficulty,
                             DataWord gaslimit, Repository repository, int callDeep, BlockStore blockStore,
                             boolean isStaticCall, boolean byTestingSuite) {

        // Transaction env
        this.address = address;
        this.origin = origin;
        this.caller = caller;
        this.balance = balance;
        this.gasPrice = gasPrice;
        this.gas = gas;
        this.callValue = callValue;
        this.msgData = msgData;

        // last Block env
        this.prevHash = lastHash;
        this.coinbase = coinbase;
        this.timestamp = timestamp;
        this.number = number;
        this.transactionIndex = transactionIndex;
        this.difficulty = difficulty;
        this.gaslimit = gaslimit;

        this.repository = repository;
        this.byTransaction = false;
        this.callDeep = callDeep;
        this.blockStore = blockStore;
        this.isStaticCall = isStaticCall;
        this.byTestingSuite = byTestingSuite;
    }

    public ProgramInvokeImpl(byte[] address, byte[] origin, byte[] caller, byte[] balance,
                             byte[] gasPrice, byte[] gas, byte[] callValue, byte[] msgData,
                             byte[] lastHash, byte[] coinbase, long timestamp, long number, int transactionIndex, byte[] difficulty,
                             byte[] gaslimit,
                             Repository repository, BlockStore blockStore,
                             boolean byTestingSuite) {
        this(address, origin, caller, balance, gasPrice, gas, callValue, msgData, lastHash, coinbase,
                timestamp, number, transactionIndex, difficulty, gaslimit, repository, blockStore);

        this.byTestingSuite = byTestingSuite;
    }


    public ProgramInvokeImpl(byte[] address, byte[] origin, byte[] caller, byte[] balance,
                             byte[] gasPrice, byte[] gas, byte[] callValue, byte[] msgData,
                             byte[] lastHash, byte[] coinbase, long timestamp, long number, int transactionIndex, byte[] difficulty,
                             byte[] gaslimit,
                             Repository repository, BlockStore blockStore) {

        // Transaction env
        this.address = new DataWord(address);
        this.origin = new DataWord(origin);
        this.caller = new DataWord(caller);
        this.balance = new DataWord(balance);
        this.gasPrice = new DataWord(gasPrice);
        this.gas = Program.limitToMaxLong(new DataWord(gas));
        this.callValue = new DataWord(callValue);
        this.msgData = msgData;

        // last Block env
        this.prevHash = new DataWord(lastHash);
        this.coinbase = new DataWord(coinbase);
        this.timestamp = new DataWord(timestamp);
        this.number = new DataWord(number);
        this.transactionIndex = new DataWord(transactionIndex);
        this.difficulty = new DataWord(difficulty);
        this.gaslimit = new DataWord(gaslimit);

        this.repository = repository;
        this.blockStore = blockStore;
    }

    /*           ADDRESS op         */
    public DataWord getOwnerAddress() {
        return address;
    }

    /*           BALANCE op         */
    public DataWord getBalance() {
        return balance;
    }

    /*           ORIGIN op         */
    public DataWord getOriginAddress() {
        return origin;
    }

    /*           CALLER op         */
    public DataWord getCallerAddress() {
        return caller;
    }

    /*           GASPRICE op       */
    public DataWord getMinGasPrice() {
        return gasPrice;
    }

    /*           GAS op       */
    public long  getGas() {
        return gas;
    }

    /*          CALLVALUE op    */
    public DataWord getCallValue() {
        return callValue;
    }

    /*****************/
    /***  msg data ***/
    /*****************/
    /* NOTE: In the protocol there is no restriction on the maximum message data,
     * However msgData here is a byte[] and this can't hold more than 2^32-1
     */
    private static BigInteger maxMsgData = BigInteger.valueOf(Integer.MAX_VALUE);

    /*     CALLDATALOAD  op   */
    public DataWord getDataValue(DataWord indexData) {

        BigInteger tempIndex = indexData.value();
        int index = tempIndex.intValue(); // possible overflow is caught below
        int size = 32; // maximum datavalue size

        if (msgData == null || index >= msgData.length
                || tempIndex.compareTo(maxMsgData) == 1) {
            return new DataWord();
        }
        if (index + size > msgData.length) {
            size = msgData.length - index;
        }

        byte[] data = new byte[32];
        System.arraycopy(msgData, index, data, 0, size);
        return new DataWord(data);
    }

    /*  CALLDATASIZE */
    public DataWord getDataSize() {

        if (msgData == null || msgData.length == 0) {
            return DataWord.ZERO;
        }
        int size = msgData.length;
        return new DataWord(size);
    }

    /*  CALLDATACOPY */
    public byte[] getDataCopy(DataWord offsetData, DataWord lengthData) {

        int offset = offsetData.intValueSafe();
        int length = lengthData.intValueSafe();

        byte[] data = new byte[length];

        if (msgData == null) {
            return data;
        }
        if (offset > msgData.length) {
            return data;
        }
        if (offset + length > msgData.length) {
            length = msgData.length - offset;
        }

        System.arraycopy(msgData, offset, data, 0, length);

        return data;
    }


    /*     PREVHASH op    */
    public DataWord getPrevHash() {
        return prevHash;
    }

    /*     COINBASE op    */
    public DataWord getCoinbase() {
        return coinbase;
    }

    /*     TIMESTAMP op    */
    public DataWord getTimestamp() {
        return timestamp;
    }

    /*     NUMBER op    */
    public DataWord getNumber() {
        return number;
    }

    /*     TXINDEX op    */
    public DataWord getTransactionIndex() {
        return transactionIndex;
    }

    /*     DIFFICULTY op    */
    public DataWord getDifficulty() {
        return difficulty;
    }

    /*     GASLIMIT op    */
    public DataWord getGaslimit() {
        return gaslimit;
    }

    /*  Storage */
    public Map<DataWord, DataWord> getStorage() {
        return storage;
    }

    public Repository getRepository() {
        return repository;
    }

    @Override
    public BlockStore getBlockStore() {
        return blockStore;
    }

    @Override
    public boolean byTransaction() {
        return byTransaction;
    }

    @Override
    public boolean isStaticCall() {
        return isStaticCall;
    }

    @Override
    public boolean byTestingSuite() {
        return byTestingSuite;
    }

    @Override
    public int getCallDeep() {
        return this.callDeep;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ProgramInvokeImpl that = (ProgramInvokeImpl) o;

        if (byTestingSuite != that.byTestingSuite) {
            return false;
        }
        if (byTransaction != that.byTransaction) {
            return false;
        }
        if (address != null ? !address.equals(that.address) : that.address != null) {
            return false;
        }
        if (balance != null ? !balance.equals(that.balance) : that.balance != null) {
            return false;
        }
        if (callValue != null ? !callValue.equals(that.callValue) : that.callValue != null) {
            return false;
        }
        if (caller != null ? !caller.equals(that.caller) : that.caller != null) {
            return false;
        }
        if (coinbase != null ? !coinbase.equals(that.coinbase) : that.coinbase != null) {
            return false;
        }
        if (difficulty != null ? !difficulty.equals(that.difficulty) : that.difficulty != null) {
            return false;
        }
        if (gas!=that.gas) {
            return false;
        }
        if (gasPrice != null ? !gasPrice.equals(that.gasPrice) : that.gasPrice != null) {
            return false;
        }
        if (gaslimit != null ? !gaslimit.equals(that.gaslimit) : that.gaslimit != null) {
            return false;
        }
        if (!Arrays.equals(msgData, that.msgData)) {
            return false;
        }
        if (number != null ? !number.equals(that.number) : that.number != null) {
            return false;
        }
        if (origin != null ? !origin.equals(that.origin) : that.origin != null) {
            return false;
        }
        if (prevHash != null ? !prevHash.equals(that.prevHash) : that.prevHash != null) {
            return false;
        }
        if (repository != null ? !repository.equals(that.repository) : that.repository != null) {
            return false;
        }
        if (storage != null ? !storage.equals(that.storage) : that.storage != null) {
            return false;
        }
        if (timestamp != null ? !timestamp.equals(that.timestamp) : that.timestamp != null) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return "ProgramInvokeImpl{" +
                "address=" + address +
                ", origin=" + origin +
                ", caller=" + caller +
                ", balance=" + balance +
                ", gas=" + gas +
                ", gasPrice=" + gasPrice +
                ", callValue=" + callValue +
                ", msgData=" + Arrays.toString(msgData) +
                ", prevHash=" + prevHash +
                ", coinbase=" + coinbase +
                ", timestamp=" + timestamp +
                ", number=" + number +
                ", difficulty=" + difficulty +
                ", gaslimit=" + gaslimit +
                ", storage=" + storage +
                ", repository=" + repository +
                ", byTransaction=" + byTransaction +
                ", byTestingSuite=" + byTestingSuite +
                ", callDeep=" + callDeep +
                '}';
    }
}
