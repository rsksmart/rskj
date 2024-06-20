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
package co.rsk.net.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;

public class SimpleHttpClient {
    private static final Logger logger = LoggerFactory.getLogger("simpleHttp");
    private static final String GET_METHOD = "GET";
    private final int timeoutMillis;

    public SimpleHttpClient(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public String doGet(String targetUrl) throws HttpException {
        StringBuilder response = new StringBuilder();
        try {
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(GET_METHOD);
            conn.setConnectTimeout(timeoutMillis);
            conn.setReadTimeout(timeoutMillis);
            int responseCode = conn.getResponseCode();

            if (responseCode >= 300 || responseCode < 200) {
                String responseMessage = conn.getResponseMessage();
                throw new HttpException("Http request failed with code :  " + responseCode + " - " + responseMessage);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
        } catch (HttpException httpException) {
            throw httpException;
        } catch (UnknownHostException unknownHostException) {
            logger.error("Unknown host from url:  {}. {}", targetUrl, unknownHostException.getMessage());
            throw new HttpException("Unknown host from url:  " + targetUrl);
        } catch (Exception e) {
            logger.error("Http request failed.", e);
            throw new HttpException("Http request failed with error : " + e.getMessage());
        }

        return response.toString();
    }


}
