package co.rsk.rpc.modules.eth.getProof;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.AccountInformationProvider;
import co.rsk.core.bc.PendingState;
import co.rsk.core.bc.TransactionPoolImpl;
import co.rsk.crypto.Keccak256;
import co.rsk.rpc.modules.eth.EthModule;
import co.rsk.trie.Trie;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.AccountState;
import org.ethereum.crypto.HashUtil;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.TrieKeyMapper;
import org.ethereum.vm.DataWord;
import org.junit.Test;

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

    private static final String VALID_RSK_ADDRESS = "0xdd711B4690CfAd50891cADDd34d56f64ceC9d85B".toLowerCase(); // taken from mainnet
    public static final String EOA_STORAGE_HASH = "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421";
    public static final byte[] STORAGE_VALUE = "nothing relevant".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VALID_CONTRACT_BYTECODE = Hex.decode("608060405234801561001057600080fd5b506101db806100206000396000f3fe608060405234801561001057600080fd5b506004361061002b5760003560e01c8063e674f5e814610030575b600080fd5b6100726004803603602081101561004657600080fd5b81019080803573ffffffffffffffffffffffffffffffffffffffff16906020019092919050505061008c565b604051808215151515815260200191505060405180910390f35b6000806000602090506000604090506000604051828185878a5afa9150506001811415610123577fd1c48ee5d8b9dfbcca9046f456364548ef0b27b0a39faf92aa1c253abf81648286604051808273ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200191505060405180910390a160019450505050506101a1565b600081141561019c577faa679a624a231df95e2bd73419c633e47abb959a4d3bbfd245a07c036c38202e86604051808273ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200191505060405180910390a160009450505050506101a1565b505050505b91905056fea265627a7a72315820d67f920cf77eab20097cb39302201d05cd0f2a99e4e53ccb3230b4ca7b46c0f864736f6c63430005100032");

    private TrieKeyMapper trieKeyMapper = new TrieKeyMapper();

    @Test
    public void getProof_EOA() {
        String rskAddress = VALID_RSK_ADDRESS;

        Map<String, Object> mockAccountProvider = new HashMap<>();
        mockAccountProvider.put("balance", new Coin(BigInteger.ONE));
        mockAccountProvider.put("isContract", false);
        mockAccountProvider.put("nonce", BigInteger.ONE);
        mockAccountProvider.put("storageHash", HashUtil.EMPTY_TRIE_HASH);
        mockAccountProvider.put("accountProof", validAccountProof(rskAddress));
        mockAccountProvider.put("storageProofs", Collections.emptyList());

        List<String> storageKeys = Arrays.asList(staticStorageKey(0));
        String blockId = "latest";

        String expectedBalance = "0x1";
        String expectedCodehash = EthModule.NO_CONTRACT_CODE_HASH; // As specified at EIP-1186 for EOA
        String expectedNonce = "0x1";
        String expectedStorageHash = EOA_STORAGE_HASH;
        List<String> expectedAccountProof = validAccountProof(rskAddress)
                .stream()
                .map(e -> toUnformattedJsonHex(e))
                .collect(Collectors.toList());
        List<StorageProofDTO> expectedStorageProof = Arrays.asList(
                emptyStorageProof(staticStorageKey(0))
        );

        ProofDTO expectedProof = new ProofDTO(expectedBalance, expectedCodehash, expectedNonce,
                expectedStorageHash, expectedAccountProof, expectedStorageProof);

        testGetProof(mockAccountProvider, rskAddress, storageKeys, blockId, expectedProof);
    }

    @Test
    public void getProof_EOA_invalidStorageKeys() {
        String rskAddress = VALID_RSK_ADDRESS;

        Map<String, Object> mockAccountProvider = new HashMap<>();
        mockAccountProvider.put("balance", new Coin(BigInteger.ONE));
        mockAccountProvider.put("isContract", false);
        mockAccountProvider.put("nonce", BigInteger.ONE);
        mockAccountProvider.put("storageHash", HashUtil.EMPTY_TRIE_HASH);
        mockAccountProvider.put("accountProof", validAccountProof(rskAddress));
        mockAccountProvider.put("storageProofs", Collections.emptyList());

        List<String> storageKeys = Arrays.asList(staticStorageKey(0), "0x1"); // the last key is invalid because it isn't unformatted data
        String blockId = "latest";

        try {
            testGetProof(mockAccountProvider, rskAddress, storageKeys, blockId, null);
        } catch (IllegalArgumentException e) {
            assertEquals("invalid storage keys", e.getMessage());
            return;
        }
        fail("this shouldn't happen");
    }

    @Test
    public void getProof_EOA_nonexistentAccount() {
        String rskAddress = VALID_RSK_ADDRESS;

        Map<String, Object> mockAccountProvider = new HashMap<>();
        mockAccountProvider.put("balance", Coin.ZERO);
        mockAccountProvider.put("isContract", false);
        mockAccountProvider.put("nonce", BigInteger.ZERO);
        mockAccountProvider.put("storageHash", HashUtil.EMPTY_TRIE_HASH);
        mockAccountProvider.put("accountProof", nonexistentAccountProof(rskAddress));
        mockAccountProvider.put("storageProofs", Collections.emptyList());

        List<String> storageKeys = Collections.emptyList();
        String blockId = "latest";

        String expectedBalance = "0x0";
        String expectedCodehash = EthModule.NO_CONTRACT_CODE_HASH; // As specified at EIP-1186 for EOA
        String expectedNonce = "0x0";
        String expectedStorageHash = EOA_STORAGE_HASH;
        // todo this might change since we need to define what to do with non existent accounts
        List<String> expectedAccountProof = nonexistentAccountProof(rskAddress)
                .stream()
                .map(e -> toUnformattedJsonHex(e))
                .collect(Collectors.toList());
        List<StorageProofDTO> expectedStorageProof = Collections.emptyList();

        ProofDTO expectedProof = new ProofDTO(expectedBalance, expectedCodehash, expectedNonce,
                expectedStorageHash, expectedAccountProof, expectedStorageProof);

        testGetProof(mockAccountProvider, rskAddress, storageKeys, blockId, expectedProof);
    }

    @Test
    public void getProof_Contract_withExistentStorageKeys() {
        String address = VALID_RSK_ADDRESS;
        byte[] aStorageValue = STORAGE_VALUE;

        Trie trie = new Trie();

        Map<String, Object> mockAccountProvider = new HashMap<>();
        mockAccountProvider.put("balance", new Coin(BigInteger.ONE));
        mockAccountProvider.put("isContract", true);
        mockAccountProvider.put("code", VALID_CONTRACT_BYTECODE);
        mockAccountProvider.put("nonce", BigInteger.ONE);
        mockAccountProvider.put("storageHash", validStorageHash(address, trie));
        mockAccountProvider.put("accountProof", validAccountProof(address));
        mockAccountProvider.put("storageProofs", Arrays.asList(
                validStorageProofDTO(address, staticStorageKey(0), trie, aStorageValue),
                validStorageProofDTO(address, staticStorageKey(1), trie, aStorageValue)
                // todo(fedejinich) add one third proof for a non static storage key (such as mappings)
        ));

        List<String> storageKeys = Arrays.asList(staticStorageKey(0), staticStorageKey(1));
        String blockId = "latest";

        String expectedBalance = "0x1";
        String expectedCodehash = toUnformattedJsonHex(
                new Keccak256(Keccak256Helper.keccak256(VALID_CONTRACT_BYTECODE)).getBytes()); // SHA3(account.data)
        String expectedNonce = "0x1";
        String expectedStorageHash = toUnformattedJsonHex(validStorageHash(address, trie));
        List<String> expectedAccountProof = validAccountProof(address)
                .stream()
                .map(e -> toUnformattedJsonHex(e))
                .collect(Collectors.toList());
        List<StorageProofDTO> expectedStorageProof = Arrays.asList(
                validStorageProofDTO(address, staticStorageKey(0), trie, aStorageValue),
                validStorageProofDTO(address, staticStorageKey(1), trie, aStorageValue)
        );

        ProofDTO expectedProof = new ProofDTO(expectedBalance, expectedCodehash, expectedNonce,
                expectedStorageHash, expectedAccountProof, expectedStorageProof);

        testGetProof(mockAccountProvider, address, storageKeys, blockId, expectedProof);
    }

    @Test
    public void getProof_Contract_invalidStorageKeys() {
        String rskAddress = VALID_RSK_ADDRESS;

        Map<String, Object> mockAccountProvider = new HashMap<>();
        mockAccountProvider.put("balance", new Coin(BigInteger.ONE));
        mockAccountProvider.put("isContract", true);
        mockAccountProvider.put("code", VALID_CONTRACT_BYTECODE);
        mockAccountProvider.put("nonce", BigInteger.ONE);
        mockAccountProvider.put("storageHash", HashUtil.EMPTY_TRIE_HASH);
        mockAccountProvider.put("accountProof", validAccountProof(rskAddress));
        mockAccountProvider.put("storageProofs", Collections.emptyList());

        List<String> storageKeys = Arrays.asList(staticStorageKey(0), "0x1"); // the last key is invalid because it isn't unformatted data
        String blockId = "latest";

        try {
            testGetProof(mockAccountProvider, rskAddress, storageKeys, blockId, null);
        } catch (IllegalArgumentException e) {
            assertEquals("invalid storage keys", e.getMessage());
            return;
        }
        fail("this shouldn't happen");
    }

    @Test
    public void getProof_Contract_nonexistentStorageKeys() {
        String address = VALID_RSK_ADDRESS;
        String existentStorageKey = staticStorageKey(0);
        String nonexistentStorageKey = staticStorageKey(1);
        byte[] aStorageValue = STORAGE_VALUE;
        byte[] validContractBytecode = VALID_CONTRACT_BYTECODE;

        Trie trie = new Trie();

        Map<String, Object> mockAccountProvider = new HashMap<>();
        mockAccountProvider.put("balance", new Coin(BigInteger.ONE));
        mockAccountProvider.put("isContract", true);
        mockAccountProvider.put("code", validContractBytecode);
        mockAccountProvider.put("nonce", BigInteger.ONE);
        mockAccountProvider.put("storageHash", validStorageHash(address, trie));
        mockAccountProvider.put("accountProof", validAccountProof(address));
        mockAccountProvider.put("storageProofs", Arrays.asList(
                validStorageProofDTO(address, existentStorageKey, trie, aStorageValue)
        ));

        List<String> storageKeys = Arrays.asList(existentStorageKey, nonexistentStorageKey);
        String blockId = "latest";

        String expectedBalance = "0x1";
        String expectedCodehash = toUnformattedJsonHex(
                new Keccak256(Keccak256Helper.keccak256(validContractBytecode)).getBytes()); // SHA3(account.data)
        String expectedNonce = "0x1";
        String expectedStorageHash = toUnformattedJsonHex(validStorageHash(address, trie));
        List<String> expectedAccountProof = validAccountProof(address)
                .stream()
                .map(e -> toUnformattedJsonHex(e))
                .collect(Collectors.toList());
        List<StorageProofDTO> expectedStorageProof = Arrays.asList(
                validStorageProofDTO(address, existentStorageKey, trie, aStorageValue),
                new StorageProofDTO(nonexistentStorageKey, null, Collections.emptyList())
        );

        ProofDTO expectedProof = new ProofDTO(expectedBalance, expectedCodehash, expectedNonce,
                expectedStorageHash, expectedAccountProof, expectedStorageProof);

        testGetProof(mockAccountProvider, address, storageKeys, blockId, expectedProof);
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

        List<StorageProofDTO> storageProofs = (List<StorageProofDTO>) mockAccount.get("storageProofs");
        if(!storageProofs.isEmpty()) {
            storageProofs.forEach(proof -> {
                RskAddress rskAddress = new RskAddress(address);
                DataWord storageKey = DataWord.valueFromHex(proof.getKey().substring(2)); // todo (fedejinich) strip correctly
                when(accountInformationProvider.getStorageProof(rskAddress, storageKey))
                        .thenReturn(proof.getProofsAsByteArray());
                when(accountInformationProvider.getStorageValue(rskAddress, storageKey))
                        .thenReturn(DataWord.valueOf(STORAGE_VALUE));
            });
        } else {
            when(accountInformationProvider.getStorageProof(any(), any())).thenReturn(Collections.emptyList());
            when(accountInformationProvider.getStorageValue(any(), any())).thenReturn(null);
        }

        EthModule ethModule = mock(EthModule.class);
        when(ethModule.getProof(any(), any(), any())).thenCallRealMethod();
        when(ethModule.getAccountInformationProvider(any())).thenReturn(accountInformationProvider);

        ProofDTO proof = ethModule.getProof(address, storageKeys, blockId);

        assertEquals(expectedProof, proof);
    }

    private byte[] validStorageHash(String address, Trie baseTrie) {
        byte[] storageKey = trieKeyMapper.getAccountStoragePrefixKey(new RskAddress(address));
        Trie trie = baseTrie.put(storageKey, "non-relevant value".getBytes(StandardCharsets.UTF_8));
        AccountInformationProvider accountInformationProvider = new MutableRepository(null, trie);

        return accountInformationProvider.getStorageHash(new RskAddress(address));
    }

    private List<byte[]> validAccountProof(String address) {
        byte[] value = "non-relevant value".getBytes(StandardCharsets.UTF_8);
        return accountProof(address, value);
    }

    private List<byte[]> nonexistentAccountProof(String address) {
        return accountProof(address, new AccountState().getEncoded());
    }

    private List<byte[]> accountProof(String address, byte[] value) {
        byte[] accountKey = trieKeyMapper.getAccountKey(new RskAddress(address));
        Trie trie = new Trie().put(accountKey, value);
        AccountInformationProvider accountInformationProvider = new MutableRepository(null, trie);

        return accountInformationProvider.getAccountProof(new RskAddress(address));
    }

    private StorageProofDTO validStorageProofDTO(String validRskAddress, String storageKey, Trie baseTrie, byte[] storageValue) {
        RskAddress rskAddress = new RskAddress(validRskAddress);

        // todo(fedejinich) analyse all 's.substring(2)' occurrences, it seems that we are working with JSON RPC formatted data
        DataWord storageKeyDW = DataWord.valueFromHex(storageKey.substring(2)); // todo (fedejinich) strip correctly
        byte[] fullStorageKey = trieKeyMapper.getAccountStorageKey(rskAddress, storageKeyDW);

        Trie trie = baseTrie.put(fullStorageKey, storageValue);

        AccountInformationProvider accountInformationProvider = new MutableRepository(null, trie);

        List<String> proofs = accountInformationProvider.getStorageProof(rskAddress, storageKeyDW)
                .stream()
                .map(proof -> toUnformattedJsonHex(proof))
                .collect(Collectors.toList());
        byte[] trieStoredValue = accountInformationProvider.getStorageValue(rskAddress, storageKeyDW).getData();

        return new StorageProofDTO(storageKey, toUnformattedJsonHex(trieStoredValue), proofs);
    }

    private String staticStorageKey(int storageKey) {
        // a storageKey corresponds to UNFORMATED DATA (check https://eth.wiki/json-rpc/API)
        return "0x" + DataWord.valueOf(storageKey);
    }

    private StorageProofDTO emptyStorageProof(String storageKey) {
        return new StorageProofDTO(storageKey, null, Collections.emptyList());
    }
}
