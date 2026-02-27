/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.mine.gas.provider;

import co.rsk.config.mining.EthCallMinGasPriceSystemConfig;
import co.rsk.config.mining.StableMinGasPriceSystemConfig;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.util.HexUtils;
import org.ethereum.rpc.parameters.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class EthCallMinGasPriceProvider extends StableMinGasPriceProvider {
    private static final Logger logger = LoggerFactory.getLogger(EthCallMinGasPriceProvider.class);

    private final String toAddress;
    private final String fromAddress;
    private final String data;

    private final Supplier<EthModule> ethModuleSupplier;

    public EthCallMinGasPriceProvider(MinGasPriceProvider fallBackProvider, StableMinGasPriceSystemConfig config, Supplier<EthModule> ethModuleSupplier) {
        super(fallBackProvider, config.getMinStableGasPrice(), config.getRefreshRate(), config.getMinValidPrice(), config.getMaxValidPrice());
        this.ethModuleSupplier = ethModuleSupplier;
        EthCallMinGasPriceSystemConfig ethCallConfig = config.getEthCallConfig();
        this.toAddress = ethCallConfig.getAddress();
        this.fromAddress = ethCallConfig.getFrom();
        this.data = ethCallConfig.getData();
    }

    @Override
    public MinGasPriceProviderType getType() {
        return MinGasPriceProviderType.ETH_CALL;
    }

    @Override
    protected Optional<Long> getBtcExchangeRate() {
        EthModule ethModule = Objects.requireNonNull(ethModuleSupplier.get());

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
                null,
                null,
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
