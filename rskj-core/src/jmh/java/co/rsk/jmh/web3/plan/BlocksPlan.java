package co.rsk.jmh.web3.plan;

import java.math.BigInteger;

public class BlocksPlan extends BasePlan {
    private String blockHash = "0x62c7cf2438a8802475120c7f10d525bc58ba3a41d5072c55236e55d939a7968a";
    private BigInteger blockNumber = BigInteger.valueOf(3590005);//"0x36c775"
    private String txHash = "0xb4d65fea7cb717ef609326d8cfcfbfda91d110550038042a92d297839dfd2d25";
    private int txIndex = 0;
    private String address = "0xcb46c0ddc60d18efeb0e586c17af6ea36452dae0";
    private int uncleIndex = 0;

    public String getBlockHash() {
        return blockHash;
    }

    public BigInteger getBlockNumber() {
        return blockNumber;
    }

    public String getTxHash() {
        return txHash;
    }

    public int getTxIndex() {
        return txIndex;
    }

    public String getAddress() {
        return address;
    }

    public BigInteger getUncleIndex() {
        return BigInteger.valueOf(uncleIndex);
    }
}
