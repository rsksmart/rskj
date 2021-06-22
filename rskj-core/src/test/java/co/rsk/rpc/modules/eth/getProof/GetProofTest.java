package co.rsk.rpc.modules.eth.getProof;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.trie.Trie;
import org.ethereum.crypto.HashUtil;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;
import org.junit.Test;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.ethereum.rpc.TypeConverter.toUnformattedJsonHex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class GetProofTest {

    private static final String VALID_RSK_ADDRESS = "0xdd711B4690CfAd50891cADDd34d56f64ceC9d85B".toLowerCase();
    public static final String EOA_STORAGE_HASH = "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421";

    @Test
    public void getProof_EOA() {
        String address = VALID_RSK_ADDRESS;
        List<String> storageKeys = Arrays.asList(storageKey(0), storageKey(1));
        String blockId = "latest";

        String expectedBalance = "0x1";
        String expectedCodehash = EthModule.NO_CONTRACT_CODE_HASH; // As specified at EIP-1186 for EOA
        String expectedNonce = "0x1";
        String expectedStorageHash = EOA_STORAGE_HASH;
        List<String> expectedAccountProof = validAccountProof()
                .stream()
                .map(e -> toUnformattedJsonHex(e))
                .collect(Collectors.toList());
        List<StorageProofDTO> expectedStorageProof = Arrays.asList(
                emptyStorageProof(storageKey(0)),
                emptyStorageProof(storageKey(1))
        );

        ProofDTO expectedProof = new ProofDTO(expectedBalance, expectedCodehash, expectedNonce,
                expectedStorageHash, expectedAccountProof, expectedStorageProof);

        Map<String, Object> mockProvider = mockAccountProviderEOA();

        testGetProof(mockProvider, address, storageKeys, blockId, expectedProof);
    }

    @Test
    public void getProof_EOA_invalidStorageKeys() {
        Map<String, Object> mockProvider = mockAccountProviderEOA();

        String address = VALID_RSK_ADDRESS;
        List<String> storageKeys = Arrays.asList(storageKey(0), storageKey(1), "0x2"); // the last key is invalid because it isn't unformatted data
        String blockId = "latest";

        try {
            testGetProof(mockProvider, address, storageKeys, blockId, null);
        } catch (IllegalArgumentException e) {
            assertEquals("invalid storage keys", e.getMessage());
            return;
        }
        fail("this shouldn't happen");
    }

    @Test
    public void getProof_Contract_withExistentStorageKeys() {
        throw new NotImplementedException();
    }

    @Test
    public void getProof_Contract_withNonExistentStorageKeys() {
        throw new NotImplementedException();
    }

    @Test
    public void getProof_Contract_invalidStorageKeys() {
        throw new NotImplementedException();
    }

    private Map<String, Object> mockAccountProviderEOA() {
        Map<String, Object> mockProviderParams = new HashMap<>();
        mockProviderParams.put("balance", new Coin(BigInteger.ONE));
        mockProviderParams.put("isContract", false);
        mockProviderParams.put("code", null);
        mockProviderParams.put("nonce", BigInteger.ONE);
        mockProviderParams.put("storageHash", HashUtil.EMPTY_TRIE_HASH);
        mockProviderParams.put("accountProof", validAccountProof());
        mockProviderParams.put("storageProof", Collections.emptyList());
        return mockProviderParams;
    }

    private List<byte[]> validAccountProof() {
        byte[] accountKey = new TrieKeyMapper().getAccountKey(new RskAddress(VALID_RSK_ADDRESS));
        Trie trie = new Trie().put(accountKey, "non-relevant value".getBytes(StandardCharsets.UTF_8));

        return trie.getNodes(accountKey).stream().map(e -> e.getProof()).collect(Collectors.toList());
    }

    private List<byte[]> storageProof(String storageKey) {
        throw new NotImplementedException();
    }

    private String storageKey(int storageKey) {
        // a storageKey corresponds to UNFORMATED DATA (check https://eth.wiki/json-rpc/API)
        return "0x" + DataWord.valueOf(storageKey);
    }

    private StorageProofDTO emptyStorageProof(String storageKey) {
        return new StorageProofDTO(storageKey, null, Collections.emptyList());
    }

    private void testGetProof(Map<String, Object> mockAccount, String address, List<String> storageKeys,
                              String blockId, ProofDTO expectedProof) {
        AccountInformationProvider accountInformationProvider = mock(AccountInformationProvider.class);
        when(accountInformationProvider.getBalance(any())).thenReturn((Coin) mockAccount.get("balance"));
        when(accountInformationProvider.isContract(any())).thenReturn((Boolean) mockAccount.get("isContract"));
        when(accountInformationProvider.getCode(any())).thenReturn((byte[]) mockAccount.get("code"));
        when(accountInformationProvider.getNonce(any())).thenReturn((BigInteger) mockAccount.get("nonce"));
        when(accountInformationProvider.getStorageHash(any())).thenReturn((byte[]) mockAccount.get("storageHash"));
        when(accountInformationProvider.getAccountProof(any())).thenReturn((List<byte[]>) mockAccount.get("accountProof"));
        when(accountInformationProvider.getStorageProof(any(), any())).thenReturn((List<byte[]>) mockAccount.get("storageProof"));
        when(accountInformationProvider.getStorageValue(any(), any())).thenReturn(null);

        EthModule ethModule = mock(EthModule.class);
        when(ethModule.getProof(any(), any(), any())).thenCallRealMethod();
        when(ethModule.getAccountInformationProvider(any())).thenReturn(accountInformationProvider);

        ProofDTO proof = ethModule.getProof(address, storageKeys, blockId);

        assertEquals(expectedProof, proof);
    }
}
