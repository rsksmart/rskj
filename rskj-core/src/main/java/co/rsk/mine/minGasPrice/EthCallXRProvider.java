package co.rsk.mine.minGasPrice;

import co.rsk.config.StableMinGasPriceSourceConfig;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionBuilder;

import java.util.List;

import static co.rsk.mine.minGasPrice.ExchangeRateProviderFactory.XRSourceType.ETH_CALL;

public class EthCallXRProvider extends ExchangeRateProvider {
    private final String address;
    private final String method;
    private final List<String> params;

    public EthCallXRProvider(StableMinGasPriceSourceConfig sourceConfig) {
        this(
                sourceConfig.sourceContract(),
                sourceConfig.sourceContractMethod(),
                sourceConfig.sourceContractMethodParams()
        );
    }

    public EthCallXRProvider(
            String address,
            String method,
            List<String> params
    ) {
        super(ETH_CALL);
        this.address = address;
        this.method = method;
        this.params = params;
    }

    public String getAddress() {
        return address;
    }

    public String getMethod() {
        return method;
    }

    public List<String> getParams() {
        return params;
    }


    @Override
    public long getPrice() {

        TransactionBuilder tb = Transaction.builder();
        tb.destination(address);

        return 0;
    }
}
