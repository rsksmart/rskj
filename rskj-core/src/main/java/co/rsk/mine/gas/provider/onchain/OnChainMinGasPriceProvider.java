package co.rsk.mine.gas.provider.onchain;

import co.rsk.config.mining.OnChainMinGasPriceSystemConfig;
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

public class OnChainMinGasPriceProvider extends StableMinGasPriceProvider {
    private final String toAddress;
    private final String fromAddress;
    private final String data;

    Logger logger = LoggerFactory.getLogger(OnChainMinGasPriceProvider.class);

    protected OnChainMinGasPriceProvider(MinGasPriceProvider fallBackProvider, OnChainMinGasPriceSystemConfig config, GetContextCallback getContextCallback) {
        super(fallBackProvider, getContextCallback);
        this.toAddress = config.address();
        this.fromAddress = config.from();
        this.data = config.data();
    }

    @Override
    public MinGasPriceProviderType getType() {
        return MinGasPriceProviderType.ON_CHAIN;
    }

    @Override
    public Long getStableMinGasPrice() {
        EthModule ethModule = this.getContextCallback.getEthModule();
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

            // TODO: how should we handle the output based on the return types of the function signature?
            // TODO: This will only support uint256 but bytes32 is possible
            return HexUtils.jsonHexToLong(
                    callOutput
            );
        } catch (Exception e) {
            logger.error("Error calling eth module", e);

            return fallBackProvider.getMinGasPrice();
        }
    }

    public String getToAddress() {
        return toAddress;
    }
}
