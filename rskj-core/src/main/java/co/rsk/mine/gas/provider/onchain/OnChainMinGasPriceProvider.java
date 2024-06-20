package co.rsk.mine.gas.provider.onchain;

import co.rsk.config.mining.OnChainMinGasPriceSystemConfig;
import co.rsk.config.mining.StableMinGasPriceSystemConfig;
import co.rsk.mine.gas.provider.MinGasPriceProvider;
import co.rsk.mine.gas.provider.MinGasPriceProviderType;
import co.rsk.mine.gas.provider.StableMinGasPriceProvider;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.util.HexUtils;
import org.ethereum.rpc.parameters.BlockIdentifierParam;
import org.ethereum.rpc.parameters.CallArgumentsParam;
import org.ethereum.rpc.parameters.HexAddressParam;
import org.ethereum.rpc.parameters.HexDataParam;
import org.ethereum.rpc.parameters.HexNumberParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class OnChainMinGasPriceProvider extends StableMinGasPriceProvider {
    private static final Logger logger = LoggerFactory.getLogger(OnChainMinGasPriceProvider.class);

    private final String toAddress;
    private final String fromAddress;
    private final String data;

    @FunctionalInterface
    public interface GetContextCallback {
        EthModule getEthModule();
    }

    private final GetContextCallback getContextCallback;

    public OnChainMinGasPriceProvider(MinGasPriceProvider fallBackProvider, StableMinGasPriceSystemConfig config, GetContextCallback getContextCallback) {
        super(fallBackProvider, config.getMinStableGasPrice());
        this.getContextCallback = getContextCallback;
        OnChainMinGasPriceSystemConfig oConfig = config.getOnChainConfig();
        this.toAddress = oConfig.getAddress();
        this.fromAddress = oConfig.getFrom();
        this.data = oConfig.getData();
    }

    @Override
    public MinGasPriceProviderType getType() {
        return MinGasPriceProviderType.ON_CHAIN;
    }

    @Override
    protected Optional<Long> getBtcExchangeRate() {
        EthModule ethModule = this.getContextCallback.getEthModule();
        if (ethModule == null) {
            logger.error("Could not get eth module");
            return Optional.empty();
        }

        CallArgumentsParam callArguments = new CallArgumentsParam(
                new HexAddressParam(fromAddress),
                new HexAddressParam(toAddress),
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

            // Parse the output of the call to get the exchange rate. Will not work with bytes32 values!
            return Optional.of(HexUtils.jsonHexToLong(
                    callOutput));
        } catch (Exception e) {
            logger.error("Error calling eth module", e);
            return Optional.empty();
        }
    }

    public String getToAddress() {
        return toAddress;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getData() {
        return data;
    }
}
