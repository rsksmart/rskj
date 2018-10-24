package co.rsk.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StateChangeTrackerTest {
    @Mock
    private StateSnapshotImpl baseSnapshot;

    @Mock(answer=Answers.RETURNS_MOCKS)
    private TrieMapper trieMapper;

    @InjectMocks
    private StateChangeTracker tracker;

    private Address nonExistingAddress = new Address() { };

    private Address existingAddress = new Address() { };

    private Account existingAccount = new TestAccount(Coin.valueOf(300), Nonce.ZERO);

    @Test
    public void hasExistingAccountData() {
        willReturn(Optional.of(existingAccount))
                .given(baseSnapshot).findAccount(existingAddress);
        assertTrue(tracker.hasAccountData(existingAddress));
    }

    @Test
    public void doesntHaveAccountDataIfNotInBaseSnapshot() {
        assertFalse(tracker.hasAccountData(nonExistingAddress));
    }

    @Test
    public void doesntHaveChangesAtStart() {
        assertTrue(tracker.getChanges().isEmpty());
    }

    @Test
    public void hasAccountDataAfterAddingBalance() {
        Address address = mock(Address.class);
        TrieKey addressKey = mock(TrieKey.class);
        AccountChangeTracker otherAccount = spy(AccountChangeTracker.class);

        when(trieMapper.addressToAccountKey(address)).thenReturn(addressKey);
        when(otherAccount.getBalance()).thenReturn(Coin.valueOf(10));

        AccountChangeTracker account = tracker.getOrCreateAccount(address);
        otherAccount.transferTo(account, Coin.valueOf(6));

        assertTrue(tracker.hasAccountData(address));
    }

    @Test
    public void newAccountDefaultValues() {
        Address address = mock(Address.class);

        AccountChangeTracker account = tracker.getOrCreateAccount(address);

        assertEquals(Coin.ZERO, account.getBalance());
        assertEquals(Nonce.ZERO, account.getNonce());
        assertEquals(Optional.empty(), account.getCode());
    }

    @Test
    public void accountTrackerHasDataFromBaseSnapshot() {
        Address address = mock(Address.class);
        Account account = new TestAccount(Coin.valueOf(7), Nonce.ZERO.next());
        EvmBytecode code = mock(EvmBytecode.class);
        when(baseSnapshot.findAccount(address)).thenReturn(Optional.of(account));
        when(baseSnapshot.findCode(account)).thenReturn(Optional.of(code));

        AccountChangeTracker accountTracker = tracker.getOrCreateAccount(address);

        assertEquals(Coin.valueOf(7), accountTracker.getBalance());
        assertEquals(Nonce.ZERO.next(), accountTracker.getNonce());
        assertEquals(code, accountTracker.getCode().get());
    }

    @Test
    public void changesPropagateAcrossAccountTrackers() {
        Address address = mock(Address.class);
        TrieKey addressKey = mock(TrieKey.class);
        Account updatedAccount = new TestAccount(Coin.valueOf(6), Nonce.ZERO);
        TrieValue trieValue = mock(TrieValue.class);
        AccountChangeTracker otherAccount = spy(AccountChangeTracker.class);

        when(otherAccount.getBalance()).thenReturn(Coin.valueOf(10));
        when(trieMapper.addressToAccountKey(address)).thenReturn(addressKey);
        when(trieMapper.accountToValue(Coin.valueOf(6), Nonce.ZERO)).thenReturn(trieValue);
        when(trieMapper.valueToAccount(trieValue)).thenReturn(updatedAccount);

        AccountChangeTracker account = tracker.getOrCreateAccount(address);
        AccountChangeTracker sameAccount = tracker.getOrCreateAccount(address);

        otherAccount.transferTo(account, Coin.valueOf(6));

        assertEquals(Coin.valueOf(6), account.getBalance());
        assertEquals(Coin.valueOf(6), sameAccount.getBalance());
    }

    @Test
    public void canTransferToAccountWithBalance() {
        Address address = mock(Address.class);
        TrieKey addressKey = mock(TrieKey.class);
        Account account = new TestAccount(Coin.valueOf(7), Nonce.ZERO);
        Account updatedAccount = new TestAccount(Coin.valueOf(13), Nonce.ZERO);
        AccountChangeTracker otherAccount = spy(AccountChangeTracker.class);
        TrieValue trieValue = mock(TrieValue.class);

        when(baseSnapshot.findAccount(address)).thenReturn(Optional.of(account));
        when(otherAccount.getBalance()).thenReturn(Coin.valueOf(10));
        when(trieMapper.addressToAccountKey(address)).thenReturn(addressKey);
        when(trieMapper.accountToValue(Coin.valueOf(13), Nonce.ZERO)).thenReturn(trieValue);
        when(trieMapper.valueToAccount(trieValue)).thenReturn(updatedAccount);

        AccountChangeTracker accountTracker = tracker.getOrCreateAccount(address);
        otherAccount.transferTo(accountTracker, Coin.valueOf(6));

        assertEquals(Coin.valueOf(13), accountTracker.getBalance());
        AccountChangeTracker sameAccount = tracker.getOrCreateAccount(address);
        assertEquals(Coin.valueOf(13), sameAccount.getBalance());
    }

    @Test
    public void canIncreaseNonce() {
        Address address = mock(Address.class);
        Account updatedAccount = new TestAccount(Coin.ZERO, Nonce.ZERO.next());
        TrieValue trieValue = mock(TrieValue.class);

        when(trieMapper.accountToValue(Coin.ZERO, Nonce.ZERO.next())).thenReturn(trieValue);
        when(trieMapper.valueToAccount(trieValue)).thenReturn(updatedAccount);

        AccountChangeTracker account = tracker.getOrCreateAccount(address);
        account.increaseNonce();

        assertEquals(Nonce.ZERO.next(), account.getNonce());
    }

    @Test
    public void canSaveCode() {
        Address address = mock(Address.class);
        TrieValue trieValue = mock(TrieValue.class);
        EvmBytecode code = mock(EvmBytecode.class);

        when(trieMapper.codeToValue(code)).thenReturn(trieValue);
        when(trieMapper.valueToCode(trieValue)).thenReturn(code);

        AccountChangeTracker account = tracker.getOrCreateAccount(address);
        account.saveCode(code);

        assertEquals(code, account.getCode().get());
    }

    @Test
    public void canGetAccountChanges() {
        Address address = mock(Address.class);
        TrieValue trieValue = mock(TrieValue.class);
        TrieKey trieKey = mock(TrieKey.class);
        AccountChangeTracker otherAccount = spy(AccountChangeTracker.class);

        when(otherAccount.getBalance()).thenReturn(Coin.valueOf(10));
        when(trieMapper.addressToAccountKey(address)).thenReturn(trieKey);
        when(trieMapper.accountToValue(Coin.valueOf(7), Nonce.ZERO)).thenReturn(trieValue);

        AccountChangeTracker account = tracker.getOrCreateAccount(address);
        otherAccount.transferTo(account, Coin.valueOf(7));

        Map<TrieKey, TrieValue> changes = tracker.getChanges();
        assertEquals(1, changes.size());
        assertEquals(trieValue, changes.get(trieKey));
    }
}
