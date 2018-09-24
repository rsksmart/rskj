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

package org.ethereum.core.genesis;

import co.rsk.config.RskSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.TrieImpl;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.core.AccountState;
import org.ethereum.core.Genesis;
import org.ethereum.core.Repository;
import org.ethereum.db.ContractDetails;
import org.ethereum.db.MutableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class GenesisLoader {
    private static final Logger logger = LoggerFactory.getLogger("genesisloader");

    public static Genesis loadGenesis(RskSystemProperties config, String genesisFile, BigInteger initialNonce, boolean isRsk)  {
        InputStream is = GenesisLoader.class.getResourceAsStream("/genesis/" + genesisFile);
        return loadGenesis(config, initialNonce, is, isRsk);
    }

    public static Genesis loadGenesis(RskSystemProperties config, BigInteger initialNonce, InputStream genesisJsonIS, boolean isRsk)  {
        try {

            String json = new String(ByteStreams.toByteArray(genesisJsonIS));

            ObjectMapper mapper = new ObjectMapper();
            JavaType type = mapper.getTypeFactory().constructType(GenesisJson.class);

            GenesisJson genesisJson  = new ObjectMapper().readValue(json, type);

            Genesis genesis = new GenesisMapper().mapFromJson(genesisJson, isRsk);

            Map<RskAddress, InitialAddressState> premine = generatePreMine(config, initialNonce, genesisJson.getAlloc());
            genesis.setPremine(premine);

            byte[] rootHash = generateRootHash(premine);
            genesis.setStateRoot(rootHash);

            genesis.flushRLP();

            return genesis;
        } catch (Exception e) {
            System.err.println("Genesis block configuration is corrupted or not found ./resources/genesis/...");
            logger.error("Genesis block configuration is corrupted or not found ./resources/genesis/...", e);
            System.exit(-1);
            return null;
        }
    }

    private static Map<RskAddress, InitialAddressState> generatePreMine(RskSystemProperties config, BigInteger initialNonce, Map<String, AllocatedAccount> alloc){
        Map<RskAddress, InitialAddressState> premine = new HashMap<>();
        ContractDetailsMapper detailsMapper = new ContractDetailsMapper();

        for (Map.Entry<String, AllocatedAccount> accountEntry : alloc.entrySet()) {
            // Why contracts starting with "00" are excluded ? Are these precompiled ?
            if(!StringUtils.equals("00", accountEntry.getKey())) {
                Coin balance = new Coin(new BigInteger(accountEntry.getValue().getBalance()));
                BigInteger nonce;

                if (accountEntry.getValue().getNonce() != null) {
                    nonce = new BigInteger(accountEntry.getValue().getNonce());
                } else {
                    nonce = initialNonce;
                }

                AccountState acctState = new AccountState(nonce, balance);
                ContractDetails contractDetails = null;
                Contract contract = accountEntry.getValue().getContract();

                if (contract != null) {
                    contractDetails = detailsMapper.mapFromContract(contract);
                }
                premine.put(new RskAddress(accountEntry.getKey()), new InitialAddressState(acctState, contractDetails));
            }
        }

        return premine;
    }

    private static byte[] generateRootHash(Map<RskAddress, InitialAddressState> premine){
        Repository repo = new MutableRepository(new MutableTrieCache(new MutableTrieImpl(new TrieImpl(null, true))));

        for (RskAddress addr : premine.keySet()) {
            InitialAddressState state = premine.get(addr);
            repo.updateAccountState(addr,state.getAccountState());
            ContractDetails cd = state.getContractDetails();
            if (cd!=null)
                repo.updateContractDetails(addr,cd);
        }

        return repo.getRoot();
    }

}
