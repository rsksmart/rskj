package co.rsk.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StateSnapshotImplTest {
    @Mock
    private Trie trie;

    @Mock
    private TrieMapper trieMapper;

    @InjectMocks
    private StateSnapshotImpl snapshot;

    @Test
    public void accountIsNotPresentIfNotInTrie() {
        Address address = mock(Address.class);

        assertTrue(!snapshot.findAccount(address).isPresent());
    }

    @Test
    public void canFindKnownAccount() {
        TrieKey trieKey = mock(TrieKey.class);
        TrieValue trieValue = mock(TrieValue.class);
        Address address = mock(Address.class);
        Account account = mock(Account.class);

        when(trieMapper.addressToAccountKey(address)).thenReturn(trieKey);
        when(trie.find(trieKey)).thenReturn(Optional.of(trieValue));
        when(trieMapper.valueToAccount(trieValue)).thenReturn(account);

        assertEquals(account, snapshot.findAccount(address).get());
    }

    @Test
    public void codeIsNotPresentIfNotInTrie() {
        Account account = mock(Account.class);

        assertTrue(!snapshot.findCode(account).isPresent());
    }

    @Test
    public void canFindKnownCode() {
        TrieKey trieKey = mock(TrieKey.class);
        TrieValue trieValue = mock(TrieValue.class);
        Account account = mock(Account.class);
        EvmBytecode code = mock(EvmBytecode.class);

        when(trieMapper.accountToCodeKey(account)).thenReturn(trieKey);
        when(trie.find(trieKey)).thenReturn(Optional.of(trieValue));
        when(trieMapper.valueToCode(trieValue)).thenReturn(code);

        assertEquals(code, snapshot.findCode(account).get());
    }
}
