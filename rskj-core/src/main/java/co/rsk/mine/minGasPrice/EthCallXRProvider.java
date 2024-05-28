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
    private final String method;
    private final List<String> params;

    Logger logger = LoggerFactory.getLogger(EthCallXRProvider.class);

    public EthCallXRProvider(@Nonnull StableMinGasPriceSourceConfig sourceConfig) {
        this(
                sourceConfig.sourceFrom(),
                sourceConfig.sourceContract(),
                sourceConfig.sourceContractMethod(),
                sourceConfig.sourceContractMethodParams()
        );
    }

    public EthCallXRProvider(
            String fromAddress,
            String address,
            String method,
            List<String> params
    ) {
        super(ETH_CALL);
        this.fromAddress = fromAddress;
        this.address = address;
        this.method = method;
        this.params = params;
    }

    public String getFromAddress() {
        return fromAddress;
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

    // TODO: Not sure we actually need this, but couldn't find anything to deconstruct function signature
    // TODO: if useful, however, we could move it to an override CallTransaction.Function.fromFunctionSignature
    public static CallTransaction.Function makeFunctionFromSignature(String functionSignature) {
        Pattern pattern = Pattern.compile("([a-zA-Z0-9_]+)\\(([^)]*)\\)\\(([^)]*)\\)");
        Matcher matcher = pattern.matcher(functionSignature);

        if (matcher.matches()) { // TODO: this could proabably improve too
            String methodName = matcher.group(1);
            String[] inputTypes = matcher.group(2).trim().isEmpty() ?  new String[0] : matcher.group(2).split(",");
            String[] outputTypes = matcher.group(3).trim().isEmpty() ?  new String[0] : matcher.group(3).split(",");

            return CallTransaction.Function.fromSignature(
                    methodName,
                    inputTypes,
                    outputTypes
            );
        } else {
            throw new IllegalArgumentException("The function signature does not match the expected format."); //TODO: improve message
        }
    }

    @Override
    public long getPrice(MinGasPriceProvider.ProviderContext context) {
        EthModule ethModule = context.ethModule;

        HexAddressParam from = new HexAddressParam(fromAddress);
        HexAddressParam oracleAddress = new HexAddressParam(address);
        HexNumberParam chainId = new HexNumberParam(ethModule.chainId());
        CallTransaction.Function function = makeFunctionFromSignature(method);
        HexDataParam dataHex = new HexDataParam(Hex.toHexString((function.encode(params.toArray()))));

        CallArgumentsParam callArguments = new CallArgumentsParam(
                from,
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
