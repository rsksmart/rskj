package co.rsk.mine.minGasPrice;

import co.rsk.config.StableMinGasPriceSourceConfig;
import co.rsk.core.RskAddress;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.util.HexUtils;
import org.ethereum.core.CallTransaction;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionBuilder;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDataParam;
import org.ethereum.rpc.parameters.HexNumberParam;

import java.util.ArrayList;
import java.util.List;

import static co.rsk.mine.minGasPrice.ExchangeRateProviderFactory.XRSourceType.ETH_CALL;

public class EthCallXRProvider extends ExchangeRateProvider {
    private final String address;
    private final String method;
    private final List<String> params;
    private final List<String> inputTypes;
    private final List<String> outputTypes;

    public EthCallXRProvider(StableMinGasPriceSourceConfig sourceConfig) {
        this(
                sourceConfig.sourceContract(),
                sourceConfig.sourceContractMethod(),
                sourceConfig.sourceContractMethodParams(),
                new ArrayList<String>(),
                new ArrayList<String>()
        );
    }

    public EthCallXRProvider(
            String address,
            String method,
            List<String> params,
            List<String> inputTypes,
            List<String> outputTypes
    ) {
        super(ETH_CALL);
        this.address = address;
        this.method = method;
        this.params = params;
        this.inputTypes = inputTypes;
        this.outputTypes = outputTypes;
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
    public long getPrice(MinGasPriceProvider.ProviderContext context) { return 0; }
}
