/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package org.ethereum.jsontestsuite;

import co.rsk.config.TestSystemProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class JSONReader {

    private static final TestSystemProperties config = new TestSystemProperties();
    private static Logger logger = LoggerFactory.getLogger("TCK-Test");

    public static String loadJSON(String filename) {
        String json = "";
        if (!config.vmTestLoadLocal())
            json = getFromUrl("https://raw.githubusercontent.com/ethereum/tests/develop/" + filename);
        return json.isEmpty() ? getFromLocal(filename) : json;
    }

    public static String loadJSONFromResource(String resname, ClassLoader loader) {
        System.out.println("Loading local resource: " + resname);

        try (InputStream reader = loader.getResourceAsStream(resname)) {
            return IOUtils.toString(reader, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String loadJSONFromCommit(String filename, String shacommit) {
        String json = "";
        if (!config.vmTestLoadLocal())
            json = getFromUrl("https://raw.githubusercontent.com/ethereum/tests/" + shacommit + "/" + filename);
        if (!json.isEmpty()) json = json.replaceAll("//", "data");
        return json.isEmpty() ? getFromLocal(filename) : json;
    }

    public static String getFromLocal(String filename) {
        System.out.println("Loading local file: " + filename);
        try {
            File vmTestFile = new File(filename);
            if (!vmTestFile.exists()){
                System.out.println(" Error: no file: " +filename);
                System.exit(1);
            }
            return new String(Files.readAllBytes(vmTestFile.toPath()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String getFromUrl(String urlToRead) {
        URL url;
        HttpURLConnection conn;
        BufferedReader rd;
        StringBuilder result = new StringBuilder();
        String line;
        try {
            url = new URL(urlToRead);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setDoOutput(true);
            conn.connect();
            InputStream in = conn.getInputStream();
            rd = new BufferedReader(new InputStreamReader(in), 819200);

            logger.info("Loading remote file: " + urlToRead);
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            rd.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return result.toString();
    }

    public static String getTestBlobForTreeSha(String shacommit, String testcase){

        String result = getFromUrl("https://api.github.com/repos/ethereum/tests/git/trees/" + shacommit);

        ObjectMapper parser = new ObjectMapper();
        JsonNode testSuiteObj;

        try {
            testSuiteObj = parser.readTree(result);
            ArrayNode tree = (ArrayNode)testSuiteObj.get("tree");

            for (Object oEntry : tree) {
                JsonNode entry = (JsonNode) oEntry;
                String testName = entry.get("path").asText();
                if ( testName.equals(testcase) ) {
                    String blobresult = getFromUrl(entry.get("url").asText());

                    testSuiteObj = parser.readTree(blobresult);
                    String blob  = testSuiteObj.get("content").asText();
                    byte[] valueDecoded= Base64.decodeBase64(blob.getBytes() );
                    //System.out.println("Decoded value is " + new String(valueDecoded));
                    return new String(valueDecoded);
                }
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return "";
    }

    public static List<String> getFileNamesForTreeSha(String sha){

        String result = getFromUrl("https://api.github.com/repos/ethereum/tests/git/trees/" + sha);

        ObjectMapper parser = new ObjectMapper();
        JsonNode testSuiteObj = null;

        List<String> fileNames = new ArrayList<String>();
        try {
            testSuiteObj = parser.readTree(result);
            ArrayNode tree = (ArrayNode)testSuiteObj.get("tree");

            for (JsonNode oEntry : tree) {
                String testName = oEntry.get("path").asText();
                fileNames.add(testName);
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return fileNames;
    }
}
