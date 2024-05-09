package co.rsk.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.*;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class OkHttpClientTestFixture {

    public static final String GET_BEST_BLOCK_CONTENT = "[{\n" +
            "    \"method\": \"eth_getBlockByNumber\",\n" +
            "    \"params\": [\n" +
            "        \"latest\",\n" +
            "        true\n" +
            "    ],\n" +
            "    \"id\": 1,\n" +
            "    \"jsonrpc\": \"2.0\"\n" +
            "}]";

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
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain,
                                                       String authType) throws CertificateException {
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
        return sendJsonRpcMessage(GET_BEST_BLOCK_CONTENT, port);
    }

    public static JsonNode getJsonResponseForGetBestBlockMessage(int port) throws IOException {
        Response response = sendJsonRpcGetBestBlockMessage(port);
        return new ObjectMapper().readTree(response.body().string());
    }
}
