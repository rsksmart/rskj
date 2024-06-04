package co.rsk.mine.minGasPrice;

import co.rsk.config.StableMinGasPriceSourceConfig;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.util.HexUtils;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDataParam;
import org.ethereum.rpc.parameters.HexNumberParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;

import static co.rsk.mine.minGasPrice.ExchangeRateProvider.XRSourceType.ETH_CALL;

public class EthCallXRProvider extends ExchangeRateProvider {
    private final String address;
    private final String method;
    private final List<String> params;

    Logger logger = LoggerFactory.getLogger(EthCallXRProvider.class);

    public EthCallXRProvider(@Nonnull StableMinGasPriceSourceConfig sourceConfig) {
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
    public long getPrice(MinGasPriceProvider.ProviderContext context) {
        EthModule ethModule = context.ethModule;

        HexDataParam dataHex = new HexDataParam(String.join("", params));
        HexAddressParam oracleAddress = new HexAddressParam(address);
        HexNumberParam chainId = new HexNumberParam(ethModule.chainId());

        CallArgumentsParam callArguments = new CallArgumentsParam(
                null,
                oracleAddress,
                null,
                null,
                null,
                null,
                chainId,
                null,
                dataHex,
                null
        );

        try {
            return HexUtils.jsonHexToLong(
                    ethModule.call(callArguments, new BlockIdentifierParam("latest"))
            );
        } catch (Exception e) {
            logger.error("Error calling eth module", e);
            return 0;
        }
    }
}
