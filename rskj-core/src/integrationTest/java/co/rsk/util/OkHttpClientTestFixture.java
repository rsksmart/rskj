/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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
package co.rsk.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;

public class OkHttpClientTestFixture {

    // Pre-funded Test Accounts on Regtest
    public static final List<String> PRE_FUNDED_ACCOUNTS = List.of(
            "0xcd2a3d9f938e13cd947ec05abc7fe734df8dd826",
            "0x7986b3df570230288501eea3d890bd66948c9b79",
            "0x0a3aa774752ec2042c46548456c094a76c7f3a79",
            "0xcf7cdbbb5f7ba79d3ffe74a0bba13fc0295f6036",
            "0x39b12c05e8503356e3a7df0b7b33efa4c054c409",
            "0xc354d97642faa06781b76ffb6786f72cd7746c97",
            "0xdebe71e1de41fc77c44df4b6db940026e31b0e71",
            "0x7857288e171c6159c5576d1bd9ac40c0c48a771c",
            "0xa4dea4d5c954f5fd9e87f0e9752911e83a3d18b3",
            "0x09a1eda29f664ac8f68106f6567276df0c65d859",
            "0xec4ddeb4380ad69b3e509baad9f158cdf4e4681d"
    );

    public static final String GET_BLOCK_CONTENT = """
            [{
                "method": "eth_getBlockByNumber",
                "params": [
                    "<BLOCK_NUM_OR_TAG>",
                    true
                ],
                "id": 1,
                "jsonrpc": "2.0"
            }]
            """;

    public static final String ETH_GET_BLOCK_BY_NUMBER = """
            {
                "method": "eth_getBlockByNumber",
                "params": [
                    "<BLOCK_NUM_OR_TAG>",
                    true
                ],
                "id": 1,
                "jsonrpc": "2.0"
            }
            """;

    public static final String ETH_SEND_TRANSACTION = """
            {
                "jsonrpc": "2.0",
                "method": "eth_sendTransaction",
                "id": 1,
                "params": [{
                    "from": "<ADDRESS_FROM>",
                    "to": "<ADDRESS_TO>",
                    "gas": "<GAS>",
                    "gasPrice": "<GAS_PRICE>",
                    "value": "<VALUE>"
                }]
            }
            """;

    private OkHttpClientTestFixture() {
    }


    public static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain,
                                                       String authType) throws CertificateException {
                            // We don't want to verify client certificates in test
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain,
                                                       String authType) throws CertificateException {
                            // We don't want to check server trusted in test
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            return new OkHttpClient()
                    .setSslSocketFactory(sslSocketFactory)
                    .setHostnameVerifier(new HostnameVerifier() {
                        @Override
                        public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Response sendJsonRpcMessage(String content, int port) throws IOException {
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json-rpc"), content);
        URL url = new URL("http", "localhost", port, "/");
        Request request = new Request.Builder().url(url)
                .addHeader("Host", "localhost")
                .addHeader("Accept-Encoding", "identity")
                .post(requestBody).build();
        return getUnsafeOkHttpClient().newCall(request).execute();
    }

    public static Response sendJsonRpcGetBestBlockMessage(int port) throws IOException {
        return sendJsonRpcGetBlockMessage(port, "latest");
    }

    public static Response sendJsonRpcGetBlockMessage(int port, String blockNumOrTag) throws IOException {
        return sendJsonRpcMessage(GET_BLOCK_CONTENT.replace("<BLOCK_NUM_OR_TAG>", blockNumOrTag), port);
    }

    public static JsonNode getJsonResponseForGetBestBlockMessage(int port, String blockNumOrTag) throws IOException {
        Response response = sendJsonRpcGetBlockMessage(port, blockNumOrTag);
        try {
            return new ObjectMapper().readTree(response.body().string());
        } finally {
            if (response.body() != null) {
                response.body().close();
            }
        }
    }

    public static String getEnvelopedMethodCalls(String... methodCall) {
        return "[\n" + String.join(",\n", methodCall) + "]";
    }

    public static Response sendBulkTransactions(int rpcPort, FromToAddressPair... fromToAddresses) throws IOException {
        Objects.requireNonNull(fromToAddresses);

        String gas = "0x9C40";
        String gasPrice = "0x10";
        String value = "0x500";

        String[] placeholders = new String[]{
                "<ADDRESS_FROM>", "<ADDRESS_TO>", "<GAS>",
                "<GAS_PRICE>", "<VALUE>"
        };

        String[] methodCalls = new String[fromToAddresses.length];
        for (int i = 0; i < fromToAddresses.length; i++) {
            FromToAddressPair fromToPair = fromToAddresses[i];
            methodCalls[i] = StringUtils.replaceEach(ETH_SEND_TRANSACTION, placeholders,
                    new String[]{fromToPair.from, fromToPair.to, gas, gasPrice, value});
        }
        String content = getEnvelopedMethodCalls(methodCalls);

        return OkHttpClientTestFixture.sendJsonRpcMessage(content, rpcPort);
    }

    public static class FromToAddressPair {
        private final String from;
        private final String to;

        private FromToAddressPair(String from, String to) {
            this.from = from;
            this.to = to;
        }

        public static FromToAddressPair of(String from, String to) {
            return new FromToAddressPair(from, to);
        }
    }
}
