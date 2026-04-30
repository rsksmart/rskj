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

    import co.rsk.core.types.bytes.Bytes;
    import co.rsk.core.types.bytes.BytesSlice;
    import org.bouncycastle.util.BigIntegers;
    import org.ethereum.core.transaction.TransactionType;
    import org.ethereum.util.*;
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
    private TransactionTypePrefix typePrefix = TransactionTypePrefix.legacy();

    protected static final byte[] FAILED_STATUS = EMPTY_BYTE_ARRAY;
    protected static final byte[] SUCCESS_STATUS = new byte[]{0x01};

    private byte[] postTxState = EMPTY_BYTE_ARRAY;
    private byte[] cumulativeGas = EMPTY_BYTE_ARRAY;
    private byte[] gasUsed = EMPTY_BYTE_ARRAY;
    private byte[] status = EMPTY_BYTE_ARRAY;

    private Bloom bloomFilter = new Bloom();
    private List<LogInfo> logInfoList = new ArrayList<>();

    /* Tx Receipt in encoded form */
    private byte[] rlpEncoded;

    public TransactionReceipt() {
    }

    /** Decodes legacy and typed receipts from encoded bytes. */
    public TransactionReceipt(byte[] rlp) {
        if (rlp == null || rlp.length == 0) {
            throw new IllegalArgumentException("Receipt RLP data cannot be null or empty");
        }

        TransactionTypePrefix prefix = TransactionTypePrefix.fromRawData(rlp);
        this.typePrefix = prefix;
        BytesSlice receiptData = TransactionTypePrefix.stripPrefix(rlp, prefix);

        ArrayList<RLPElement> params = RLP.decode2(receiptData);
        RLPList receipt = (RLPList) params.get(0);

        if (isType1Or2ReceiptPrefix(prefix)) {
            if (receipt.size() != 4) {
                throw new IllegalArgumentException(
                        "Type 1 / standard Type 2 receipt body must have 4 RLP elements, got: " + receipt.size());
            }
            decodeType1Or2ReceiptBody(receipt);
        } else {
            decodeLegacyReceiptBody(receipt);
        }

        rlpEncoded = rlp;
    }

    /**
     * RSKIP-546: standard Type 1 and Type 2 receipts use
     * {@code rlp([status, cumulativeGasUsed, logsBloom, logs])} after the single-byte type prefix.
     * RSK-namespace Type 2 and Type 3/4 use the legacy six-field body.
     */
    private static boolean isType1Or2ReceiptPrefix(TransactionTypePrefix prefix) {
        if (prefix instanceof StandardTypedPrefix st) {
            TransactionType t = st.type();
            return t == TransactionType.TYPE_1 || t == TransactionType.TYPE_2;
        }
        return false;
    }

    private void decodeType1Or2ReceiptBody(RLPList receipt) {
        RLPItem statusRLP = (RLPItem) receipt.get(0);
        RLPItem cumulativeGasRLP = (RLPItem) receipt.get(1);
        RLPItem bloomRLP = (RLPItem) receipt.get(2);
        RLPList logs = (RLPList) receipt.get(3);

        status = nullToEmpty(statusRLP.getRLPData());
        cumulativeGas = cumulativeGasRLP.getRLPData() == null ? EMPTY_BYTE_ARRAY : cumulativeGasRLP.getRLPData();
        bloomFilter = new Bloom(bloomRLP.getRLPData());
        gasUsed = EMPTY_BYTE_ARRAY;
        postTxState = Arrays.equals(status, SUCCESS_STATUS) ? new byte[]{1} : EMPTY_BYTE_ARRAY;

        for (int k = 0; k < logs.size(); k++) {
            RLPElement log = logs.get(k);
            LogInfo logInfo = new LogInfo(log.getRLPData());
            logInfoList.add(logInfo);
        }
    }

    private void decodeLegacyReceiptBody(RLPList receipt) {
        RLPItem postTxStateRLP = (RLPItem) receipt.get(0);
        RLPItem cumulativeGasRLP = (RLPItem) receipt.get(1);
        RLPItem bloomRLP = (RLPItem) receipt.get(2);
        RLPList logs = (RLPList) receipt.get(3);
        RLPItem gasUsedRLP = (RLPItem) receipt.get(4);

        postTxState = nullToEmpty(postTxStateRLP.getRLPData());
        cumulativeGas = cumulativeGasRLP.getRLPData() == null ? EMPTY_BYTE_ARRAY : cumulativeGasRLP.getRLPData();
        bloomFilter = new Bloom(bloomRLP.getRLPData());
        gasUsed = gasUsedRLP.getRLPData() == null ? EMPTY_BYTE_ARRAY : gasUsedRLP.getRLPData();

        if (receipt.size() > 5) {
            byte[] transactionStatus = nullToEmpty(receipt.get(5).getRLPData());
            this.status = transactionStatus;
        }

        for (int k = 0; k < logs.size(); k++) {
            RLPElement log = logs.get(k);
            LogInfo logInfo = new LogInfo(log.getRLPData());
            logInfoList.add(logInfo);
        }
    }


    public TransactionReceipt(byte[] postTxState, byte[] cumulativeGas, byte[] gasUsed,
                              Bloom bloomFilter, List<LogInfo> logInfoList, byte[] status) {
        this.postTxState = postTxState;
        this.cumulativeGas = cumulativeGas;
        this.gasUsed = gasUsed;
        this.bloomFilter = bloomFilter;
        this.logInfoList = logInfoList;
        if (Arrays.equals(status, FAILED_STATUS) || Arrays.equals(status, SUCCESS_STATUS)) {
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

    /* Legacy: [postTxState, cumulativeGas, bloomFilter, logs, gasUsed, status].
     * RSKIP-546 Type 1 / standard Type 2: [status, cumulativeGas, bloom, logs]. */
    public byte[] getEncoded() {

        if (rlpEncoded != null) {
            return rlpEncoded;
        }

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

        byte[] bloomRLP = RLP.encodeElement(this.bloomFilter.getData());
        byte[] cumulativeGasRLP = RLP.encodeElement(this.cumulativeGas);
        byte[] statusRLP = RLP.encodeElement(this.status);

        byte[] receiptData;
        if (isType1Or2ReceiptEncodingForTransaction()) {
            receiptData = RLP.encodeList(statusRLP, cumulativeGasRLP, bloomRLP, logInfoListRLP);
        } else {
            byte[] postTxStateRLP = RLP.encodeElement(this.postTxState);
            byte[] gasUsedRLP = RLP.encodeElement(this.gasUsed);
            receiptData = RLP.encodeList(postTxStateRLP, cumulativeGasRLP, bloomRLP,
                    logInfoListRLP, gasUsedRLP, statusRLP);
        }

        byte[] prefix = getReceiptTypePrefix();
        rlpEncoded = prefix.length == 0 ? receiptData : ByteUtil.merge(prefix, receiptData);

        return rlpEncoded;
    }

    private boolean isType1Or2ReceiptEncodingForTransaction() {
        if (transaction == null) {
            return false;
        }
        return isType1Or2ReceiptPrefix(transaction.getTypePrefix());
    }

    private byte[] getReceiptTypePrefix() {
        if (transaction != null) {
            return transaction.getTypePrefix().toBytes();
        }
        return typePrefix.toBytes();
    }

    public void setStatus(byte[] status) {
        if (Arrays.equals(status, FAILED_STATUS)){
            this.status = FAILED_STATUS;
        } else if (Arrays.equals(status, SUCCESS_STATUS)){
            this.status = SUCCESS_STATUS;
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
        this.rlpEncoded = null;
        this.transaction = transaction;
        if (transaction != null) {
            this.typePrefix = transaction.getTypePrefix();
        }
    }

    public Transaction getTransaction() {
        return transaction;
    }

    @Override
    public String toString() {

        // todo: fix that

        return "TransactionReceipt[" +
                "\n  , " + (hasTxStatus() ? ("txStatus=" + (isSuccessful()? "OK" : "FAILED"))
                        : ("postTxState=" + Bytes.of(postTxState))) +
                "\n  , cumulativeGas=" + Bytes.of(cumulativeGas) +
                "\n  , bloom=" + bloomFilter.toString() +
                "\n  , logs=" + logInfoList +
                ']';
    }

    public byte[] getStatus() {
        return this.status;
    }
}
