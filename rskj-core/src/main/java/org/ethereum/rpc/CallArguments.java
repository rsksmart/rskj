package org.ethereum.rpc;

public class CallArguments {
    private String from;
    private String to;
    private String gas;
    private String gasLimit;
    private String gasPrice;
    private String value;
    private String data; // compiledCode
    private String nonce;
    private String chainId; //NOSONAR

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getGas() {
        return gas;
    }

    public void setGas(String gas) {
        this.gas = gas;
    }

    public String getGasLimit() {
        return gasLimit;
    }

    public void setGasLimit(String gasLimit) {
        this.gasLimit = gasLimit;
    }

    public String getGasPrice() {
        return gasPrice;
    }

    public void setGasPrice(String gasPrice) {
        this.gasPrice = gasPrice;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getChainId() {
        return chainId;
    }

    public void setChainId(String chainId) {
        this.chainId = chainId;
    }



    @Override
    public String toString() {
        return "CallArguments{" +
                "from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", gas='" + gas + '\'' +
                ", gasLimit='" + gasLimit + '\'' +
                ", gasPrice='" + gasPrice + '\'' +
                ", value='" + value + '\'' +
                ", data='" + data + '\'' +
                ", nonce='" + nonce + '\'' +
                ", chainId='" + chainId + '\'' +
                '}';
    }
}
