/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

package co.rsk.jmh.web3;

import co.rsk.jmh.Config;
import org.web3j.protocol.core.DefaultBlockParameter;

public class EthMethodsConfig {
    public static final String ETH_GET_STORAGE_AT_ADDRESS = "ethGetStorageAt.address";
    public static final String ETH_GET_STORAGE_AT_POSITION = "ethGetStorageAt.position";
    public static final String ETH_GET_CODE_ADDRESS = "ethGetCode.address";
    public static final String ETH_GET_CODE_BLOCK_NUMBER = "ethGetCode.blockNumber";



    private final String ethGetStorageAtAddress;
    private final Long ethGetStorageAtPosition;
    private final String ethGetCodeAddress;
    private final Long ethGetCodeBlockNumber;


    public EthMethodsConfig(Config config) {
        ethGetStorageAtAddress = config.getString(ETH_GET_STORAGE_AT_ADDRESS);
        ethGetStorageAtPosition = config.getLong(ETH_GET_STORAGE_AT_POSITION);
        ethGetCodeAddress = config.getString(ETH_GET_CODE_ADDRESS);
        ethGetCodeBlockNumber = config.getLong(ETH_GET_CODE_BLOCK_NUMBER);

    }

    public String getEthGetStorageAtAddress() {
        return ethGetStorageAtAddress;
    }

    public Long getEthGetStorageAtPosition() {
        return ethGetStorageAtPosition;
    }

    public String getEthGetCodeAddress() {
        return ethGetCodeAddress;
    }

    public Long getEthGetCodeBlockNumber() {
        return ethGetCodeBlockNumber;
    }

    public DefaultBlockParameter getLatestBlock() {
        return DefaultBlockParameter.valueOf("latest");
    }

}
