/*
 * This file is part of RskJ
 * Copyright (C) 2021 RSK Labs Ltd.
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

package org.ethereum.rpc;

import org.ethereum.rpc.exception.RskJsonRpcRequestException;
import org.ethereum.rpc.validation.BlockHashValidator;
import org.ethereum.rpc.validation.BnTagOrNumberValidator;
import org.ethereum.rpc.validation.EthAddressValidator;

import java.util.Arrays;
import java.util.Collection;

public class FilterRequest {

    private String fromBlock;
    private String toBlock;
    private Object address;
    private Object[] topics;
    private String blockHash;

    @Override
    public String toString() {
        return "FilterRequest{" +
                "fromBlock='" + fromBlock + '\'' +
                ", toBlock='" + toBlock + '\'' +
                ", address=" + address +
                ", topics=" + Arrays.toString(topics) +
                ", blockHash='" + blockHash + '\'' +
                '}';
    }

    public String getFromBlock() {
        return fromBlock;
    }

    public void setFromBlock(String fromBlock) {
        this.fromBlock = fromBlock;
    }

    public String getToBlock() {
        return toBlock;
    }

    public void setToBlock(String toBlock) {
        this.toBlock = toBlock;
    }

    public Object getAddress() {
        return address;
    }

    public void setAddress(Object address) {
        this.address = address;
    }

    public Object[] getTopics() {
        return topics;
    }

    public void setTopics(Object[] topics) {
        this.topics = topics;
    }

    public String getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(String blockHash) {
        this.blockHash = blockHash;
    }

    public boolean isValid() {
        if (fromBlock != null) {
            BnTagOrNumberValidator.isValid(fromBlock);
        }
        if (toBlock != null) {
            BnTagOrNumberValidator.isValid(toBlock);
        }
        validateAddress();
        validateTopics();
        if (blockHash != null) {
            BlockHashValidator.isValid(blockHash);
        }
        return true;
    }

    private boolean validateTopics() {
        if (topics != null) {
            for (Object topic : topics) {
                if (topic instanceof String) {
                    new Topic((String) topic);
                } else if (topic instanceof Collection) {
                    Collection<?> iterable = (Collection<?>) topic;
                    iterable.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .forEach(Topic::new);
                } else {
                    throw RskJsonRpcRequestException.invalidParamError("Invalid topic parameter.");
                }
            }
        }
        return true;
    }

    private void validateAddress() {
        if (address != null) {
            if (address instanceof String) {
                EthAddressValidator.isValid((String) address);
            } else if (address instanceof Collection) {
                Collection<?> iterable = (Collection<?>) address;
                iterable.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .forEach(EthAddressValidator::isValid);
            } else {
                throw RskJsonRpcRequestException.invalidParamError("Invalid address parameter.");
            }
        }
    }
}
