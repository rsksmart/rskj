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

package org.ethereum.core;

import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPItem;
import org.ethereum.util.RLPList;
import org.ethereum.vm.LogInfo;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static co.rsk.util.ListArrayUtil.nullToEmpty;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * The transaction receipt is a tuple of three items
 * comprising the transaction, together with the post-transaction state,
 * and the cumulative gas used in the block containing the transaction receipt
 * as of immediately after the transaction has happened,
 */
public class TransactionReceipt {

    private Transaction transaction;

    // status codes
    protected static final byte[] FAILED_STATUS = EMPTY_BYTE_ARRAY;
    protected static final byte[] SUCCESS_STATUS = new byte[]{0x01};
    protected static final byte[] MANUAL_REVERT_RSKIP113_STATUS = new byte[]{-1}; // #mish e.g. doREVERT() opCode in VM.java
    protected static final byte[] RENT_OOG_RSKIP113_STATUS = new byte[]{-2};

    private byte[] postTxState = EMPTY_BYTE_ARRAY;
    // cumulativeGas field (as before) represents execution gas alone (rentgas does not count towards block gas limit) 
    private byte[] cumulativeGas = EMPTY_BYTE_ARRAY;
    // #mish Note: gasLimit field in Transaction.java represents the combined limits for execution and rent gas.
    // likewise, the gasUsed field here includes both execution and rent gas used.
    private byte[] gasUsed = EMPTY_BYTE_ARRAY;
    // To help with testing (encoding, root hashes) and enable separation in future dinstinguish execution and rent gas
    private byte[] execGasUsed = EMPTY_BYTE_ARRAY;
    private byte[] rentGasUsed = EMPTY_BYTE_ARRAY;

    private byte[] status = EMPTY_BYTE_ARRAY;

    private Bloom bloomFilter = new Bloom();
    private List<LogInfo> logInfoList = new ArrayList<>();

    /* Tx Receipt in encoded form */
    private byte[] rlpEncoded;

    public TransactionReceipt() {
    }

    public TransactionReceipt(byte[] rlp) {

        ArrayList<RLPElement> params = RLP.decode2(rlp);
        RLPList receipt = (RLPList) params.get(0);

        RLPItem postTxStateRLP = (RLPItem) receipt.get(0);
        RLPItem cumulativeGasRLP = (RLPItem) receipt.get(1);
        RLPItem bloomRLP = (RLPItem) receipt.get(2);
        RLPList logs = (RLPList) receipt.get(3);
        RLPItem gasUsedRLP = (RLPItem) receipt.get(4);

        postTxState = nullToEmpty(postTxStateRLP.getRLPData());
        cumulativeGas = cumulativeGasRLP.getRLPData() == null ? EMPTY_BYTE_ARRAY : cumulativeGasRLP.getRLPData();
        bloomFilter = new Bloom(bloomRLP.getRLPData());
        gasUsed = gasUsedRLP.getRLPData() == null ? EMPTY_BYTE_ARRAY : gasUsedRLP.getRLPData();

        if (receipt.size() > 5 ) {
            byte[] transactionStatus = nullToEmpty(receipt.get(5).getRLPData());
            this.status = transactionStatus;
        }

        for (int k = 0; k < logs.size(); k++) {
            RLPElement log = logs.get(k);
            LogInfo logInfo = new LogInfo(log.getRLPData());
            logInfoList.add(logInfo);
        }

        rlpEncoded = rlp;
    }

    // original constructor without storage rent
    public TransactionReceipt(byte[] postTxState, byte[] cumulativeGas, byte[] gasUsed,
                              Bloom bloomFilter, List<LogInfo> logInfoList, byte[] status) {
        this.postTxState = postTxState;
        this.cumulativeGas = cumulativeGas;
        this.gasUsed = gasUsed; //exec only!
        this.bloomFilter = bloomFilter;
        this.logInfoList = logInfoList;
        if (Arrays.equals(status, FAILED_STATUS) || Arrays.equals(status, SUCCESS_STATUS) ||
                Arrays.equals(status, MANUAL_REVERT_RSKIP113_STATUS) || Arrays.equals(status, RENT_OOG_RSKIP113_STATUS)) {
            this.status = status;
        }
    }

    // constructor with storage rent implemented
    public TransactionReceipt(byte[] postTxState, byte[] cumulativeGas, byte[] gasUsed,
                              byte[] execGasUsed, byte[] rentGasUsed,
                              Bloom bloomFilter, List<LogInfo> logInfoList, byte[] status) {
        this.postTxState = postTxState;
        this.cumulativeGas = cumulativeGas;
        this.gasUsed = gasUsed; //exec+rent
        this.execGasUsed = execGasUsed; //exe
        this.rentGasUsed = rentGasUsed; //rent
        this.bloomFilter = bloomFilter;
        this.logInfoList = logInfoList;
        if (Arrays.equals(status, FAILED_STATUS) || Arrays.equals(status, SUCCESS_STATUS) ||
                Arrays.equals(status, MANUAL_REVERT_RSKIP113_STATUS) || Arrays.equals(status, RENT_OOG_RSKIP113_STATUS)) {
            this.status = status;
        }
    }

    public byte[] getPostTxState() {
        return postTxState;
    }

    public byte[] getCumulativeGas() {
        return cumulativeGas;
    }

    // TODO: return gas used for this transaction instead of cumulative gas
    //#mish gasUsed field here includes both execution and rent gas used (consistent with single gas limit field in TX)
    public byte[] getGasUsed() {
        return gasUsed;
    }

    public long getCumulativeGasLong() {
        return new BigInteger(1, cumulativeGas).longValue();
    }

    // #mish for testing and future use
    public long getExecGasUsedLong() {
        return new BigInteger(1, execGasUsed).longValue();
    }
    
    public long getRentGasUsedLong() {
        return new BigInteger(1, rentGasUsed).longValue();
    }


    public Bloom getBloomFilter() {
        return bloomFilter;
    }

    public List<LogInfo> getLogInfoList() {
        return logInfoList;
    }

    /* [postTxState, cumulativeGas, bloomFilter, logInfoList] */
    // #mish note: this encoding does not use rentgas in the computation
    public byte[] getEncoded() {

        if (rlpEncoded != null) {
            return rlpEncoded;
        }

        byte[] postTxStateRLP = RLP.encodeElement(this.postTxState);
        byte[] cumulativeGasRLP = RLP.encodeElement(this.cumulativeGas);
        byte[] gasUsedRLP = RLP.encodeElement(this.execGasUsed);
        byte[] bloomRLP = RLP.encodeElement(this.bloomFilter.getData());
        byte[] statusRLP = RLP.encodeElement(this.status);

        final byte[] logInfoListRLP;
        if (logInfoList != null) {
            byte[][] logInfoListE = new byte[logInfoList.size()][];

            int i = 0;
            for (LogInfo logInfo : logInfoList) {
                logInfoListE[i] = logInfo.getEncoded();
                ++i;
            }
            logInfoListRLP = RLP.encodeList(logInfoListE);
        } else {
            logInfoListRLP = RLP.encodeList();
        }

        rlpEncoded = RLP.encodeList(postTxStateRLP, cumulativeGasRLP, bloomRLP, logInfoListRLP, gasUsedRLP, statusRLP);

        return rlpEncoded;
    }

    // #mish for storage rent .. this version has an boolean argument for storage rent.
    // boolean incRent: true indicates encoding should reflect storage rent
    // false should provide the same encoding when storage rent is not implemented
    public byte[] getEncoded(boolean incRent) {

        if (rlpEncoded != null) {
            return rlpEncoded;
        }

        byte[] postTxStateRLP = RLP.encodeElement(this.postTxState);
        byte[] cumulativeGasRLP = RLP.encodeElement(this.cumulativeGas);
        byte[] gasUsedRLP;
        if (incRent) {
            gasUsedRLP = RLP.encodeElement(this.gasUsed); //combined rent and exec gas
        } else {
            gasUsedRLP = RLP.encodeElement(this.execGasUsed);
        }        
        byte[] bloomRLP = RLP.encodeElement(this.bloomFilter.getData());
        byte[] statusRLP = RLP.encodeElement(this.status);

        final byte[] logInfoListRLP;
        if (logInfoList != null) {
            byte[][] logInfoListE = new byte[logInfoList.size()][];

            int i = 0;
            for (LogInfo logInfo : logInfoList) {
                logInfoListE[i] = logInfo.getEncoded();
                ++i;
            }
            logInfoListRLP = RLP.encodeList(logInfoListE);
        } else {
            logInfoListRLP = RLP.encodeList();
        }

        rlpEncoded = RLP.encodeList(postTxStateRLP, cumulativeGasRLP, bloomRLP, logInfoListRLP, gasUsedRLP, statusRLP);

        return rlpEncoded;
    }

    // #mish todo: RSKIP113 going back to single gas field, is this change still needed?
    public void setStatus(byte[] status) {
        if (Arrays.equals(status, FAILED_STATUS)){
            this.status = FAILED_STATUS;
        } else if (Arrays.equals(status, SUCCESS_STATUS)){
            this.status = SUCCESS_STATUS;
        } else if (Arrays.equals(status, MANUAL_REVERT_RSKIP113_STATUS)){
            this.status = MANUAL_REVERT_RSKIP113_STATUS;
        } else if (Arrays.equals(status, RENT_OOG_RSKIP113_STATUS)){
            this.status = RENT_OOG_RSKIP113_STATUS;
        }
    }

    public boolean isSuccessful() {
        return Arrays.equals(this.status, SUCCESS_STATUS);
    }

    public void setTxStatus(boolean success) {
        this.postTxState = success ? new byte[]{1} : new byte[0];
        rlpEncoded = null;
    }

    public boolean hasTxStatus() {
        return postTxState != null && postTxState.length <= 1;
    }

    public boolean isTxStatusOK() {
        return postTxState != null && postTxState.length == 1 && postTxState[0] == 1;
    }

    public void setPostTxState(byte[] postTxState) {
        this.postTxState = postTxState;
    }

    public void setCumulativeGas(long cumulativeGas) {
        this.cumulativeGas = BigIntegers.asUnsignedByteArray(BigInteger.valueOf(cumulativeGas));
    }

    // exec + rent intended, corresponding to single gasLimit field in TX
    public void setGasUsed(long gasUsed) {
        this.gasUsed = BigIntegers.asUnsignedByteArray(BigInteger.valueOf(gasUsed));
    }

    public void setExecGasUsed(long execGasUsed) {
        this.execGasUsed = BigIntegers.asUnsignedByteArray(BigInteger.valueOf(execGasUsed));
    }

    public void setRentGasUsed(long rentGasUsed) {
        this.rentGasUsed = BigIntegers.asUnsignedByteArray(BigInteger.valueOf(rentGasUsed));
    }

    public void setCumulativeGas(byte[] cumulativeGas) {
        this.cumulativeGas = cumulativeGas;
    }
    
    // exec + rent intended, corresponding to single gasLimit field in TX
    public void setGasUsed(byte[] gasUsed) {
        this.gasUsed = gasUsed;
    }

    /*
    public void setRentGasUsed(byte[] rentGasUsed) {
        this.rentGasUsed = rentGasUsed;
    }*/

    public void setLogInfoList(List<LogInfo> logInfoList) {
        if (logInfoList == null) {
            return;
        }
        
        this.rlpEncoded = null;
        this.logInfoList = logInfoList;

        for (LogInfo loginfo : logInfoList) {
            bloomFilter.or(loginfo.getBloom());
        }
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    @Override
    public String toString() {

        // todo: fix that

        return "TransactionReceipt[" +
                "\n  , " + (hasTxStatus() ? ("txStatus=" + (isSuccessful()? "OK" : "FAILED"))
                        : ("postTxState=" + Hex.toHexString(postTxState))) +
                "\n  , cumulativeGas=" + Hex.toHexString(cumulativeGas) +
                "\n  , bloom=" + bloomFilter.toString() +
                "\n  , logs=" + logInfoList +
                ']';
    }

    public byte[] getStatus() {
        return this.status;
    }
}
