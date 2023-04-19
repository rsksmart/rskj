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

import co.rsk.jmh.web3.Web3Connector;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.math.BigInteger;

public class Web3ConnectorE2E implements Web3Connector {

    private static Web3ConnectorE2E connector;

    private final Web3j web3j;

    private Web3ConnectorE2E(String host) {
        this.web3j = Web3j.build(new HttpService(host));
    }

    public static Web3ConnectorE2E create(String host) {
        if (connector == null) {
            connector = new Web3ConnectorE2E(host);
        }
        return connector;
    }

    @Override
    public BigInteger ethGetBalance(String address, String block) throws HttpRpcException {
        try {
            Request<?, EthGetBalance> request = web3j.ethGetBalance(address, DefaultBlockParameter.valueOf(block));
            EthGetBalance response = request.send();
            return response.getBalance();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

    @Override
    public String ethBlockNumber() throws HttpRpcException {
        try {
            Request<?, EthBlockNumber> request = web3j.ethBlockNumber();
            EthBlockNumber response = request.send();
            return response.getBlockNumber().toString();
        } catch (IOException e) {
            e.printStackTrace();
            throw new HttpRpcException(e.getMessage());
        }
    }

//    @Override
//    public String ethSendRawTransaction() throws HttpRpcException {
//        String tx = "";
//        try {
//            Request<?, EthSendTransaction> request = web3j.ethSendRawTransaction(tx);
//            EthSendTransaction response = request.send();
//            return response.getTransactionHash();
//        } catch (IOException e) {
//            e.printStackTrace();
//            throw new HttpRpcException(e.getMessage());
//        }
//    }

}
