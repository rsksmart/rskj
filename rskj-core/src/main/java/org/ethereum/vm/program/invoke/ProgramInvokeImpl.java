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
import java.util.Objects;

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
    private final DataWord txGasPrice;
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
    private final DataWord minimumGasPrice;

    private final DataWord transactionIndex;

    private Map<DataWord, DataWord> storage;

    private final Repository repository;
    private boolean byTransaction = true;
    private boolean byTestingSuite = false;
    private int callDeep = 0;
    private boolean isStaticCall = false;

    public ProgramInvokeImpl(DataWord address, DataWord origin, DataWord caller, DataWord balance,
                             DataWord txGasPrice,
                             long gas,
                             DataWord callValue, byte[] msgData,
                             DataWord lastHash, DataWord coinbase, DataWord timestamp, DataWord number, DataWord transactionIndex, DataWord
                                     difficulty,
                             DataWord gaslimit, DataWord minimumGasPrice, Repository repository, int callDeep, BlockStore blockStore,
                             boolean isStaticCall,
                             boolean byTestingSuite) {

        // Transaction env
        this.address = address;
        this.origin = origin;
        this.caller = caller;
        this.balance = balance;
        this.txGasPrice = txGasPrice;
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
        this.minimumGasPrice = minimumGasPrice;

        this.repository = repository;
        this.byTransaction = false;
        this.callDeep = callDeep;
        this.blockStore = blockStore;
        this.isStaticCall = isStaticCall;
        this.byTestingSuite = byTestingSuite;
    }

    public ProgramInvokeImpl(byte[] address, byte[] origin, byte[] caller, byte[] balance,
                             byte[] txGasPrice, byte[] gas, byte[] callValue, byte[] msgData,
                             byte[] lastHash, byte[] coinbase, long timestamp, long number, int transactionIndex, byte[] difficulty,
                             byte[] gaslimit, byte[] minimumGasPrice,
                             Repository repository, BlockStore blockStore,
                             boolean byTestingSuite) {
        this(address, origin, caller, balance, txGasPrice, gas, callValue, msgData, lastHash, coinbase,
                timestamp, number, transactionIndex, difficulty, gaslimit, minimumGasPrice, repository, blockStore);

        this.byTestingSuite = byTestingSuite;
    }


    public ProgramInvokeImpl(byte[] address, byte[] origin, byte[] caller, byte[] balance,
                             byte[] txGasPrice, byte[] gas, byte[] callValue, byte[] msgData,
                             byte[] lastHash, byte[] coinbase, long timestamp, long number, int transactionIndex, byte[] difficulty,
                             byte[] gaslimit, byte[] minimumGasPrice,
                             Repository repository, BlockStore blockStore) {

        // Transaction env
        this.address = DataWord.valueOf(address);
        this.origin = DataWord.valueOf(origin);
        this.caller = DataWord.valueOf(caller);
        this.balance = DataWord.valueOf(balance);
        this.txGasPrice = DataWord.valueOf(txGasPrice);
        this.gas = Program.limitToMaxLong(DataWord.valueOf(gas));
        this.callValue = DataWord.valueOf(callValue);
        this.msgData = msgData;

        // last Block env
        this.prevHash = DataWord.valueOf(lastHash);
        this.coinbase = DataWord.valueOf(coinbase);
        this.timestamp = DataWord.valueOf(timestamp);
        this.number = DataWord.valueOf(number);
        this.transactionIndex = DataWord.valueOf(transactionIndex);
        this.difficulty = DataWord.valueOf(difficulty);
        this.gaslimit = DataWord.valueOf(gaslimit);
        this.minimumGasPrice = DataWord.valueOf(minimumGasPrice);

        this.repository = repository;
        this.blockStore = blockStore;
    }

    /*           ADDRESS op         */
    @Override
    public DataWord getOwnerAddress() {
        return address;
    }

    /*           BALANCE op         */
    @Override
    public DataWord getBalance() {
        return balance;
    }

    /*           ORIGIN op         */
    @Override
    public DataWord getOriginAddress() {
        return origin;
    }

    /*           CALLER op         */
    @Override
    public DataWord getCallerAddress() {
        return caller;
    }

    /*           GASPRICE op       */
    @Override
    public DataWord getTxGasPrice() {
        return txGasPrice;
    }

    /*           GAS op       */
    @Override
    public long  getGas() {
        return gas;
    }

    /*          CALLVALUE op    */
    @Override
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
    @Override
    public DataWord getDataValue(DataWord indexData) {

        BigInteger tempIndex = indexData.value();
        int index = tempIndex.intValue(); // possible overflow is caught below
        int size = 32; // maximum datavalue size

        if (msgData == null || index >= msgData.length
                || tempIndex.compareTo(maxMsgData) == 1) {
            return DataWord.ZERO;
        }
        if (index + size > msgData.length) {
            size = msgData.length - index;
        }

        byte[] data = new byte[32];
        System.arraycopy(msgData, index, data, 0, size);
        return DataWord.valueOf(data);
    }

    /*  CALLDATASIZE */
    @Override
    public DataWord getDataSize() {

        if (msgData == null || msgData.length == 0) {
            return DataWord.ZERO;
        }
        int size = msgData.length;
        return DataWord.valueOf(size);
    }

    /*  CALLDATACOPY */
    @Override
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
    @Override
    public DataWord getPrevHash() {
        return prevHash;
    }

    /*     COINBASE op    */
    @Override
    public DataWord getCoinbase() {
        return coinbase;
    }

    /*     TIMESTAMP op    */
    @Override
    public DataWord getTimestamp() {
        return timestamp;
    }

    /*     NUMBER op    */
    @Override
    public DataWord getNumber() {
        return number;
    }

    /*     TXINDEX op    */
    @Override
    public DataWord getTransactionIndex() {
        return transactionIndex;
    }

    /*     DIFFICULTY op    */
    @Override
    public DataWord getDifficulty() {
        return difficulty;
    }

    /*     GASLIMIT op    */
    @Override
    public DataWord getGaslimit() {
        return gaslimit;
    }

    /*     BASEFEE op    */
    @Override
    public DataWord getMinimumGasPrice() {
        return minimumGasPrice;
    }

    /*  Storage */
    public Map<DataWord, DataWord> getStorage() {
        return storage;
    }

    @Override
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
        if (txGasPrice != null ? !txGasPrice.equals(that.txGasPrice) : that.txGasPrice != null) {
            return false;
        }
        if (gaslimit != null ? !gaslimit.equals(that.gaslimit) : that.gaslimit != null) {
            return false;
        }
        if (minimumGasPrice != null ? !minimumGasPrice.equals(that.minimumGasPrice) : that.minimumGasPrice != null) {
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
    public int hashCode() {
        int result = Objects.hash(address, origin, caller, balance, txGasPrice, callValue, gas, prevHash, coinbase, timestamp, number, difficulty, gaslimit, minimumGasPrice, storage, repository, byTransaction, byTestingSuite);
        result = 31 * result + Arrays.hashCode(msgData);
        return result;
    }

    @Override
    public String toString() {
        return "ProgramInvokeImpl{" +
                "address=" + address +
                ", origin=" + origin +
                ", caller=" + caller +
                ", balance=" + balance +
                ", gas=" + gas +
                ", txGasPrice=" + txGasPrice +
                ", callValue=" + callValue +
                ", msgData=" + Arrays.toString(msgData) +
                ", prevHash=" + prevHash +
                ", coinbase=" + coinbase +
                ", timestamp=" + timestamp +
                ", number=" + number +
                ", difficulty=" + difficulty +
                ", gaslimit=" + gaslimit +
                ", minimumGasPrice=" + minimumGasPrice +
                ", storage=" + storage +
                ", repository=" + repository +
                ", byTransaction=" + byTransaction +
                ", byTestingSuite=" + byTestingSuite +
                ", callDeep=" + callDeep +
                '}';
    }
}
