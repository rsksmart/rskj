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

        assertTrue(!snapshot.findAccountState(address).isPresent());
    }

    @Test
    public void canFindKnownAccount() {
        TrieKey trieKey = mock(TrieKey.class);
        TrieValue trieValue = mock(TrieValue.class);
        Address address = mock(Address.class);
        AccountState accountState = mock(AccountState.class);

        when(trieMapper.addressToAccountKey(address)).thenReturn(trieKey);
        when(trie.find(trieKey)).thenReturn(Optional.of(trieValue));
        when(trieMapper.valueToAccount(trieValue)).thenReturn(accountState);

        assertEquals(accountState, snapshot.findAccountState(address).get());
    }

    @Test
    public void codeIsNotPresentIfNotInTrie() {
        Address address = mock(Address.class);

        assertTrue(!snapshot.findCode(address).isPresent());
    }

    @Test
    public void canFindKnownCode() {
        TrieKey trieKey = mock(TrieKey.class);
        TrieValue trieValue = mock(TrieValue.class);
        Address address = mock(Address.class);
        EvmBytecode code = mock(EvmBytecode.class);

        when(trieMapper.addressToCodeKey(address)).thenReturn(trieKey);
        when(trie.find(trieKey)).thenReturn(Optional.of(trieValue));
        when(trieMapper.valueToCode(trieValue)).thenReturn(code);

        assertEquals(code, snapshot.findCode(address).get());
    }
}
