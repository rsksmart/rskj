package co.rsk.mine.minGasPrice;

import co.rsk.config.StableMinGasPriceSourceConfig;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.util.HexUtils;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.CallTransaction;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDataParam;
import org.ethereum.rpc.parameters.HexNumberParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static co.rsk.mine.minGasPrice.ExchangeRateProvider.XRSourceType.ETH_CALL;

public class EthCallXRProvider extends ExchangeRateProvider {
    private final String fromAddress;
    private final String address;
    private final String data;

    Logger logger = LoggerFactory.getLogger(EthCallXRProvider.class);

    public EthCallXRProvider(@Nonnull StableMinGasPriceSourceConfig sourceConfig) {
        this(
                sourceConfig.sourceFrom(),
                sourceConfig.sourceContract(),
                sourceConfig.sourceContractData()
        );
    }

    public EthCallXRProvider(
            String fromAddress,
            String address,
            String data
    ) {
        super(ETH_CALL);
        this.fromAddress = fromAddress;
        this.address = address;
        this.data = data;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getAddress() {
        return address;
    }

    public String getData() {
        return data;
    }

    @Override
    public long getPrice(MinGasPriceProvider.ProviderContext context) {
        EthModule ethModule = context.ethModule;

        CallArgumentsParam callArguments = new CallArgumentsParam(
                new HexAddressParam(fromAddress),
                new HexAddressParam(address),
                null,
                null,
                null,
                null,
                new HexNumberParam(ethModule.chainId()),
                null,
                new HexDataParam(data),
                null
        );

        try {
            String callOutput = ethModule.call(callArguments, new BlockIdentifierParam("latest"));

            // TODO: how should we handle the output based on the return types of the function signature?
            return HexUtils.jsonHexToLong(
                   callOutput
            );
        } catch (Exception e) {
            logger.error("Error calling eth module", e);
            return 0;
        }
    }
}
