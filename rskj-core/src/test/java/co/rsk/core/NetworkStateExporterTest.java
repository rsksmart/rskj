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

import co.rsk.db.MutableTrieImpl;
import co.rsk.db.RepositoryLocator;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieStore;
import co.rsk.trie.TrieStoreImpl;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import org.apache.commons.io.FileUtils;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.MutableRepository;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by oscar on 13/01/2017.
 */
public class NetworkStateExporterTest {
    static String jsonFileName = "networkStateExporterTest.json";
    private MutableRepository repository;
    private NetworkStateExporter nse;
    private Blockchain blockchain;
    private Block block;

    @Before
    public void setup() {
        TrieStore trieStore = new TrieStoreImpl(new HashMapDB());
        MutableTrieImpl mutableTrie = new MutableTrieImpl(trieStore, new Trie(trieStore));
        repository = new MutableRepository(mutableTrie);
        blockchain = mock(Blockchain.class);

        block = mock(Block.class);
        when(blockchain.getBestBlock()).thenReturn(block);
        BlockHeader blockHeader = mock(BlockHeader.class);
        when(block.getHeader()).thenReturn(blockHeader);

        RepositoryLocator repositoryLocator = mock(RepositoryLocator.class);

        when(repositoryLocator.snapshotAt(block.getHeader()))
                .thenReturn(new MutableRepository(mutableTrie));

        this.nse = new NetworkStateExporter(repositoryLocator, blockchain);
    }

    @AfterClass
    public static void cleanup(){
        FileUtils.deleteQuietly(new File(jsonFileName));
    }

    @Test
    public void testEmptyRepo() throws Exception {
        Map result = writeAndReadJson("",true,true);

        Assert.assertEquals(0, result.keySet().size());
    }

    @Test
    public void testNoContracts() throws Exception {
        String address1String = "1000000000000000000000000000000000000000";
        RskAddress addr1 = new RskAddress(address1String);
        repository.createAccount(addr1);
        Set<RskAddress> set;
        set = repository.getAccountsKeys();
        Assert.assertEquals(1,set.size());

        repository.addBalance(addr1, Coin.valueOf(1L));
        repository.increaseNonce(addr1);

        set = repository.getAccountsKeys();
        Assert.assertEquals(1,set.size());

        String address2String = "2000000000000000000000000000000000000000";
        RskAddress addr2 = new RskAddress(address2String);
        repository.createAccount(addr2);
        set = repository.getAccountsKeys();
        Assert.assertEquals(2,set.size());

        repository.addBalance(addr2, Coin.valueOf(10L));
        repository.increaseNonce(addr2);
        repository.increaseNonce(addr2);

        RskAddress remascSender = RskAddress.nullAddress();
        repository.createAccount(remascSender);
        repository.increaseNonce(remascSender);

        repository.createAccount(PrecompiledContracts.REMASC_ADDR);
        repository.addBalance(PrecompiledContracts.REMASC_ADDR, Coin.valueOf(10L));
        repository.increaseNonce(PrecompiledContracts.REMASC_ADDR);

        Map result = writeAndReadJson("",true,true);
        Assert.assertEquals(3, result.keySet().size());

        Map address1Value = (Map) result.get(address1String);
        Assert.assertEquals(2, address1Value.keySet().size());
        Assert.assertEquals("1",address1Value.get("balance"));
        Assert.assertEquals("1",address1Value.get("nonce"));

        Map address2Value = (Map) result.get(address2String);
        Assert.assertEquals(2, address2Value.keySet().size());
        Assert.assertEquals("10",address2Value.get("balance"));
        Assert.assertEquals("2",address2Value.get("nonce"));

        Map remascValue = (Map) result.get(PrecompiledContracts.REMASC_ADDR_STR);
        Assert.assertEquals(2, remascValue.keySet().size());
        Assert.assertEquals("10",remascValue.get("balance"));
        Assert.assertEquals("1",remascValue.get("nonce"));
    }

    @Test
    public void testContracts() throws Exception {
        String address1String = "1000000000000000000000000000000000000000";
        RskAddress addr1 = new RskAddress(address1String);
        repository.createAccount(addr1);
        repository.addBalance(addr1, Coin.valueOf(1L));
        repository.increaseNonce(addr1);

        repository.setupContract(addr1); // necessary for isContract() to return true.
        repository.saveCode(addr1, new byte[]{1, 2, 3, 4});
        repository.addStorageRow(addr1, DataWord.ZERO, DataWord.ONE);
        repository.addStorageBytes(addr1, DataWord.ONE, new byte[]{5, 6, 7, 8});

        AccountState accountState = repository.getAccountState(addr1);
        repository.updateAccountState(addr1, accountState);

        Map result = writeAndReadJson("",true,true);

        Assert.assertEquals(1, result.keySet().size());

        // Getting address1String only works if the Trie is not secure.
        Map address1Value = (Map) result.get(address1String);
        Assert.assertEquals(3, address1Value.keySet().size());
        Assert.assertEquals("1",address1Value.get("balance"));
        Assert.assertEquals("1",address1Value.get("nonce"));
        Map contract = (Map) address1Value.get("contract");
        Assert.assertEquals(3, contract.keySet().size());
        String codeHash =(String) contract.get("codeHash");
        Assert.assertEquals("a6885b3731702da62e8e4a8f584ac46a7f6822f4e2ba50fba902f67b1588d23b", codeHash);
        Assert.assertEquals("01020304",contract.get("code"));
        Map data = (Map) contract.get("data");
        Assert.assertEquals(2, data.keySet().size());

        String addrStr = ByteUtil.toHexString(DataWord.ZERO.getData());

        // A value expanded with leading zeros requires testing in expanded form.
        Assert.assertEquals("01",data.get(addrStr));
        Assert.assertEquals("05060708", data.get(ByteUtil.toHexString(DataWord.ONE.getData())));
    }

    @Test
    public void testSingleAccount() throws Exception {
        String address1String = "1000000000000000000000000000000000000000";
        RskAddress addr1 = new RskAddress(address1String);
        repository.createAccount(addr1);
        repository.addBalance(addr1, Coin.valueOf(1L));
        repository.increaseNonce(addr1);

        repository.setupContract(addr1); // necessary for isContract() to return true.
        repository.saveCode(addr1, new byte[]{1, 2, 3, 4});
        repository.addStorageRow(addr1, DataWord.ZERO, DataWord.ONE);
        repository.addStorageBytes(addr1, DataWord.ONE, new byte[]{5, 6, 7, 8});

        AccountState accountState = repository.getAccountState(addr1);
        repository.updateAccountState(addr1, accountState);

        String address2String = "2000000000000000000000000000000000000000";
        RskAddress addr2 = new RskAddress(address2String);
        repository.createAccount(addr2);

        Map result = writeAndReadJson(addr1.toHexString(),false,false);

        Assert.assertEquals(1, result.keySet().size());

        // Getting address1String only works if the Trie is not secure.
        Map address1Value = (Map) result.get(address1String);
        Assert.assertEquals(3, address1Value.keySet().size());
        Assert.assertEquals("1",address1Value.get("balance"));
        Assert.assertEquals("1",address1Value.get("nonce"));
        Map contract = (Map) address1Value.get("contract");
        // "data" section and "code" must not be present (only "codeHash")
        Assert.assertEquals(1, contract.keySet().size());
        String codeHash =(String) contract.get("codeHash");
        Assert.assertEquals("a6885b3731702da62e8e4a8f584ac46a7f6822f4e2ba50fba902f67b1588d23b", codeHash);
    }

    private Map writeAndReadJson(String singleAccount,boolean exportStorageKeys,boolean exportCode) throws Exception {
        Assert.assertTrue(nse.exportStatus(jsonFileName,singleAccount,exportStorageKeys,exportCode));

        InputStream inputStream = new FileInputStream(jsonFileName);
        String json = new String(ByteStreams.toByteArray(inputStream));

        ObjectMapper mapper = new ObjectMapper();
        JavaType type = mapper.getTypeFactory().constructType(HashMap.class);
        Map result = new ObjectMapper().readValue(json, type);
        return result;
    }
}
