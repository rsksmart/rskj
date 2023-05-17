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

package co.rsk.jmh.web3.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.JsonRpc2_0Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;

import java.util.Arrays;
import java.util.Collections;

public class RskModuleWeb3j extends JsonRpc2_0Web3j {

    public RskModuleWeb3j(Web3jService web3jService) {
        super(web3jService);
    }

    public Request<?, GenericJsonResponse> ethGetBlockByNumber(String blockNumber) {
        return new Request<>("eth_getBlockByNumber", Arrays.asList(blockNumber, true), web3jService, RskModuleWeb3j.GenericJsonResponse.class);
    }

    public Request<?, RawBlockHeaderByNumberResponse> rskGetRawBlockHeaderByNumber(String bnOrId) {
        return new Request<>("rsk_getRawBlockHeaderByNumber", Collections.singletonList(bnOrId), web3jService, RawBlockHeaderByNumberResponse.class);
    }

    public Request<?, GenericJsonResponse> rskGetRawTransactionReceiptByHash(String txHash) {
        return new Request<>("rsk_getRawTransactionReceiptByHash", Collections.singletonList(txHash), web3jService, GenericJsonResponse.class);
    }

    public Request<?, GenericJsonResponse> rskGetTransactionReceiptNodesByHash(String blockHash, String txHash){
        return new Request<>("rsk_getTransactionReceiptNodesByHash", Arrays.asList(blockHash, txHash), web3jService, GenericJsonResponse.class);
    }

    public Request<?, GenericJsonResponse> rskGetRawBlockHeaderByHash(String blockHash) {
        return new Request<>("rsk_getRawBlockHeaderByHash", Collections.singletonList(blockHash), web3jService, GenericJsonResponse.class);
    }

    public Request<?, GenericJsonResponse> rskGetRawBlockHeaderByNumber(DefaultBlockParameter blockNumber) {
        return new Request<>("rsk_getRawBlockHeaderByNumber", Collections.singletonList(blockNumber.getValue()), web3jService, GenericJsonResponse.class);
    }

    public static class RawBlockHeaderByNumberResponse extends Response<String> {
        public String getRawHeader() {
            return getResult();
        }
    }

    public static class GenericJsonResponse extends Response<JsonNode> {
        public JsonNode getJson() {
            return getResult();
        }
    }

    public Request<?, GenericJsonResponse> ethCall(RskModuleWeb3j.EthCallArguments args, String bnOrId) {
        return new Request<>("eth_call", Arrays.asList(args, bnOrId), web3jService, RskModuleWeb3j.GenericJsonResponse.class);
    }

    public static class EthCallArguments {
        private String from;
        private String to;
        private String gas;
        private String gasLimit;
        private String gasPrice;
        private String value;
        private String data; // compiledCode
        private String nonce;
        private String chainId;
        private String type; // ignore, see https://github.com/rsksmart/rskj/pull/1601

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public String getGas() {
            return gas;
        }

        public void setGas(String gas) {
            this.gas = gas;
        }

        public String getGasLimit() {
            return gasLimit;
        }

        public void setGasLimit(String gasLimit) {
            this.gasLimit = gasLimit;
        }

        public String getGasPrice() {
            return gasPrice;
        }

        public void setGasPrice(String gasPrice) {
            this.gasPrice = gasPrice;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public String getNonce() {
            return nonce;
        }

        public void setNonce(String nonce) {
            this.nonce = nonce;
        }

        public String getChainId() {
            return chainId;
        }

        public void setChainId(String chainId) {
            this.chainId = chainId;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
