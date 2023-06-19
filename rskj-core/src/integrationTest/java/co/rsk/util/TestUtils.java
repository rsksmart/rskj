/*
 * This file is part of RskJ
 * Copyright (C) 2023 RSK Labs Ltd.
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

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import org.junit.jupiter.api.Assertions;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class TestUtils {

    public static class CustomProcess {
        private final String output;

        public CustomProcess(String output) {
            this.output = output;
        }

        public String getOutput() {
            return output;
        }

    }

    private TestUtils() {
    }

    public static byte[] generateBytesFromRandom(Random random, int size) {
        byte[] byteArray = new byte[size];
        random.nextBytes(byteArray);
        return byteArray;
    }

    public static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
                                                       String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
                                                       String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
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

    public static String readProcStream(InputStream in) throws IOException {
        byte[] bytesAvailable = new byte[in.available()];
        in.read(bytesAvailable, 0, bytesAvailable.length);
        return new String(bytesAvailable);
    }

    public static Response sendJsonRpcMessage(String content, int port) throws IOException {
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json-rpc"), content);
        URL url = new URL("http", "localhost", port, "/");
        Request request = new Request.Builder().url(url)
                .addHeader("Host", "localhost")
                .addHeader("Accept-Encoding", "identity")
                .post(requestBody).build();
        return TestUtils.getUnsafeOkHttpClient().newCall(request).execute();
    }

    public static CustomProcess runCommand(String cmd, int timeout, TimeUnit timeUnit, Consumer<Process> beforeDestroyFn) throws InterruptedException, IOException {
        return runCommand(cmd, timeout, timeUnit, beforeDestroyFn, true);
    }

    public static CustomProcess runCommand(String cmd, int timeout, TimeUnit timeUnit) throws InterruptedException, IOException {
        return runCommand(cmd, timeout, timeUnit, null, true);
    }

    public static CustomProcess runCommand(String cmd, int timeout, TimeUnit timeUnit, boolean assertProcExitCode) throws InterruptedException, IOException {
        return runCommand(cmd, timeout, timeUnit, null, assertProcExitCode);
    }

    public static CustomProcess runCommand(String cmd, int timeout, TimeUnit timeUnit, Consumer<Process> beforeDestroyFn, boolean assertProcExitCode) throws InterruptedException, IOException {
        Process proc = Runtime.getRuntime().exec(cmd);
        String procOutput;

        try {
            proc.waitFor(timeout, timeUnit);

            procOutput = TestUtils.readProcStream(proc.getInputStream());
            String procError = TestUtils.readProcStream(proc.getErrorStream());

            if (assertProcExitCode && !proc.isAlive()) {
                System.out.println("procOutput: " + procOutput);
                System.out.println("procError:" + procError);
                Assertions.assertEquals(0, proc.exitValue(), "Proc exited with value: " + proc.exitValue());
            }

            if (beforeDestroyFn != null) {
                beforeDestroyFn.accept(proc);
            }
        } finally {
            proc.destroy();
        }

        return new CustomProcess(procOutput);
    }
}
