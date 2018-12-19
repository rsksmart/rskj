package co.rsk.metrics.block.builder;

import java.math.BigInteger;

public class GasLimits {

    private BigInteger specialCasesLimit;
    private BigInteger tokenTransferLimit;
    private BigInteger coinTransferLimit;
    private BigInteger blockLimit;
    private BigInteger erc20ContractGenLimit;
    private BigInteger specialCasesContractGenLimit;
    private BigInteger gasPrice;

    public GasLimits(BigInteger specialCasesLimit, BigInteger tokenTransferLimit, BigInteger coinTransferLimit, BigInteger blockLimit, BigInteger gasPrice){
        this.specialCasesLimit = specialCasesLimit;
        this.tokenTransferLimit = tokenTransferLimit;
        this.coinTransferLimit = coinTransferLimit;
        this.blockLimit = blockLimit;
        this.gasPrice = gasPrice;
        this.erc20ContractGenLimit = null;
        this.specialCasesContractGenLimit = null;
    }

    public GasLimits(BigInteger specialCasesLimit, BigInteger tokenTransferLimit, BigInteger coinTransferLimit, BigInteger blockLimit, BigInteger gasPrice, BigInteger erc20GenLimit, BigInteger specialCasesGenLimit){
        this(specialCasesLimit, tokenTransferLimit, coinTransferLimit, blockLimit, gasPrice);
        this.erc20ContractGenLimit = erc20GenLimit;
        this.specialCasesContractGenLimit = specialCasesGenLimit;
    }


    public BigInteger getSpecialCasesLimit() {
        return specialCasesLimit;
    }

    public BigInteger getTokenTransferLimit() {
        return tokenTransferLimit;
    }

    public BigInteger getCoinTransferLimit() {
        return coinTransferLimit;
    }

    public BigInteger getBlockLimit() {
        return blockLimit;
    }

    public BigInteger getGasPrice() {
        return gasPrice;
    }

    public BigInteger getErc20ContractGenLimit() {
        return erc20ContractGenLimit;
    }

    public BigInteger getSpecialCasesContractGenLimit() {
        return specialCasesContractGenLimit;
    }
}
