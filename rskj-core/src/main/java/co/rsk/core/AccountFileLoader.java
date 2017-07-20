/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

package co.rsk.core;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.ethereum.rpc.TypeConverter.stringHexToByteArray;

/**
 * Created by mario on 23/11/16.
 */
public class AccountFileLoader {

    private static final Logger logger = LoggerFactory.getLogger(AccountFileLoader.class);

    private final Path filePath;

    public  AccountFileLoader(final Path filePath) {
        this.filePath = filePath;
    }

    public ConcurrentMap<String, AccountData> load() {
        ConcurrentHashMap<String, AccountData> accounts = new ConcurrentHashMap<>();
        if(Files.exists(filePath)) {
            try (FileReader fileReader = new FileReader(this.filePath.toString())){
                JSONParser parser = new JSONParser();
                Object json = parser.parse(fileReader);
                JSONObject jsonObject = (JSONObject) json;
                JSONArray jsonAccounts = (JSONArray) jsonObject.get("accounts");

                for(Object obj : jsonAccounts) {
                    JSONObject jobj = (JSONObject) obj;
                    String acc  = (String) jobj.get("account");
                    String add  = (String) jobj.get("address");
                    accounts.put((String) jobj.get("ip"), new AccountData(stringHexToByteArray(add), stringHexToByteArray(acc)));
                }
            } catch (ParseException | IOException e) {
                logger.error("Error reading accounts file", e);
            }
        }
        return accounts;
    }


}
