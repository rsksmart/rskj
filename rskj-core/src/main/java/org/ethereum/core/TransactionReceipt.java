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

import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPItem;
import org.ethereum.util.RLPList;
import org.ethereum.vm.LogInfo;

import org.spongycastle.util.BigIntegers;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.ArrayUtils.nullToEmpty;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

/**
 * The transaction receipt is a tuple of three items
 * comprising the transaction, together with the post-transaction state,
 * and the cumulative gas used in the block containing the transaction receipt
 * as of immediately after the transaction has happened,
 */
public class TransactionReceipt {

    private Transaction transaction;

    private byte[] postTxState = EMPTY_BYTE_ARRAY;
    private byte[] cumulativeGas = EMPTY_BYTE_ARRAY;
    private byte[] gasUsed = EMPTY_BYTE_ARRAY;
    private byte[] executionResult = EMPTY_BYTE_ARRAY;
    private String error = "";

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
            RLPItem result = (RLPItem) receipt.get(5);
            executionResult = (executionResult = result.getRLPData()) == null ? EMPTY_BYTE_ARRAY : executionResult;
        }


        if (receipt.size() > 6) {
            byte[] errBytes = receipt.get(6).getRLPData();
            error = errBytes != null ? new String(errBytes, StandardCharsets.UTF_8) : "";
        }


        for (RLPElement log : logs) {
            LogInfo logInfo = new LogInfo(log.getRLPData());
            logInfoList.add(logInfo);
        }

        rlpEncoded = rlp;
    }


    public TransactionReceipt(byte[] postTxState, byte[] cumulativeGas, byte[] gasUsed,
                              Bloom bloomFilter, List<LogInfo> logInfoList) {
        this.postTxState = postTxState;
        this.cumulativeGas = cumulativeGas;
        this.gasUsed = gasUsed;
        this.bloomFilter = bloomFilter;
        this.logInfoList = logInfoList;
    }

    public byte[] getPostTxState() {
        return postTxState;
    }

    public byte[] getCumulativeGas() {
        return cumulativeGas;
    }

    // TODO: return gas used for this transaction instead of cumulative gas
    public byte[] getGasUsed() {
        return gasUsed;
    }

    public long getCumulativeGasLong() {
        return new BigInteger(1, cumulativeGas).longValue();
    }


    public Bloom getBloomFilter() {
        return bloomFilter;
    }

    public List<LogInfo> getLogInfoList() {
        return logInfoList;
    }

    /* [postTxState, cumulativeGas, bloomFilter, logInfoList] */
    public byte[] getEncoded() {

        if (rlpEncoded != null) {
            return rlpEncoded;
        }

        byte[] postTxStateRLP = RLP.encodeElement(this.postTxState);
        byte[] cumulativeGasRLP = RLP.encodeElement(this.cumulativeGas);
        byte[] gasUsedRLP = RLP.encodeElement(this.gasUsed);
        byte[] bloomRLP = RLP.encodeElement(this.bloomFilter.data);
        byte[] result = RLP.encodeElement(this.executionResult);
        byte[] err = RLP.encodeElement(this.error.getBytes(StandardCharsets.UTF_8));

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

        rlpEncoded = RLP.encodeList(postTxStateRLP, cumulativeGasRLP, bloomRLP, logInfoListRLP, gasUsedRLP, result, err);

        return rlpEncoded;
    }

    public boolean isSuccessful() {
        return error.isEmpty();
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

    public void setGasUsed(long gasUsed) {
        this.gasUsed = BigIntegers.asUnsignedByteArray(BigInteger.valueOf(gasUsed));
    }

    public void setCumulativeGas(byte[] cumulativeGas) {
        this.cumulativeGas = cumulativeGas;
    }

    public void setGasUsed(byte[] gasUsed) {
        this.gasUsed = gasUsed;
    }

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
                "\n  , " + (hasTxStatus() ? ("txStatus=" + (isTxStatusOK() ? "OK" : "FAILED"))
                        : ("postTxState=" + Hex.toHexString(postTxState))) +
                "\n  , cumulativeGas=" + Hex.toHexString(cumulativeGas) +
                "\n  , bloom=" + bloomFilter.toString() +
                "\n  , logs=" + logInfoList +
                ']';
    }

    public void setExecutionResult(byte[] executionResult) {
        this.executionResult = executionResult;
    }

    public void setError(String error) {
        this.error = error == null?"":error;
    }

    public String getError() {
        return this.error;
    }
}
