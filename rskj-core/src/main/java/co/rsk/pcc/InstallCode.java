/*
 * This file is part of RskJ
 * Copyright (C) 2020 RSK Labs Ltd.
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
package co.rsk.pcc;

import co.rsk.core.RskAddress;
import org.ethereum.core.AccountState;
import org.ethereum.core.Block;
import org.ethereum.core.Repository;
import org.ethereum.core.Transaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.crypto.signature.ECDSASignature;
import org.ethereum.crypto.signature.Secp256k1;
import org.ethereum.db.BlockStore;
import org.ethereum.db.ReceiptStore;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.LogInfo;
import org.ethereum.vm.PrecompiledContracts;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

public class InstallCode extends PrecompiledContracts.PrecompiledContract {
    private Repository repository;

    // The base cost was chosen to be the difference between CREATE and NEW_ACCT_CALL.
    // It should be higher than 5000 (a storage cell change) and lower than 10K
    // (a storage cell creation)
    public static final long STORAGE_COST = 7000; // this is the costo of creation of code node in trie
    // This is the ECRECOVER cost
    public static final long ECDSA_PKRECOVER_COST = 3000; // This is the cost of signature verification
    public static final long BASE_COST =ECDSA_PKRECOVER_COST+STORAGE_COST;
    public static final int argumentsSize = 32*4;

    @Override
    public long getGasForData(byte[] data) {
        // If that is not enough, we do not charge the user.
        if (data.length<argumentsSize) {
            return 0;
        }

        //first argument is destination account
        byte[] account = new byte[32];

        System.arraycopy(data, 0, account, 0, 32);

        // We validate that first 12 bytes must be zero. We do not allow
        // other kind of addresses now but we let this happen in the future

        for(int i=0;i<12;i++)
            if (account[i]!=0) {
                // if address is invalid, we do not charge the caller
                return 0;
            }
        long cost = BASE_COST;
        RskAddress accountAddress = new RskAddress(Arrays.copyOfRange(account, 12, 32));
        AccountState state = repository.getAccountState(accountAddress);
        if(state==null)
            cost = cost + GasCost.NEW_ACCT_CALL;

        // We subsidize contracts having a delegate call to a library.
        // This is a benefit for the platform because code can be embedded into the node
        // data.
        int codeLength =data.length -  argumentsSize;
        if (codeLength<64)
            return cost;
        cost +=(codeLength-64)*GasCost.CREATE_DATA;
        return cost;
    }

    @Override
    public byte[] execute(byte[] data) {

        byte[] account = new byte[32];
        byte[] v = new byte[32];
        byte[] r = new byte[32];
        byte[] s = new byte[32];
        DataWord out = null;
        int p = 0;
        byte[] retFail = new byte[1]; // 0 = error
        RskAddress accountAddress = null;
        byte[] code;
        AccountState state;

        // fail fast if not enough arguments were passed
        if (data.length < argumentsSize) {
            return retFail;
        }

        try {
            //first argument is destination account
            System.arraycopy(data, 0, account, 0, 32);
            p += 32;
            //now comes the signature, 32-byte padded always
            System.arraycopy(data, p, v, 0, 32);
            p += 32;
            System.arraycopy(data, p, r, 0, 32);
            p += 32;

            System.arraycopy(data, p, s, 0, 32);
            p += 32;
            // And now comes the code to install.
            code = new byte[data.length - p];
            System.arraycopy(data, p, code, 0, code.length);

            if (!isValid(r, s, v))
                return retFail;

            // We validate that first 12 bytes must be zero. We do not allow
            // other kind of addresses now but we let this happen in the future
            for (int i = 0; i < 12; i++)
                if (account[i] != 0)
                    return retFail;

            accountAddress = new RskAddress(Arrays.copyOfRange(account, 12, 32));

            state = repository.getAccountState(accountAddress);
            byte[] nonceFromAccount;

            // Do not create the account yet.
            if (state == null) {
                nonceFromAccount = new byte[32];
            } else
                nonceFromAccount = ByteUtil.copyToArray(state.getNonce());


            byte[] h = getHashToSignFromCode(account, nonceFromAccount, code);
            ECDSASignature signature = ECDSASignature.fromComponents(r, s, v[31]);

            ECKey key = Secp256k1.getInstance().signatureToKey(h, signature);
            // now we verify that the account and address recovered are the same
            // In the future we could verify the signature instead
            // of pubkey recovery. It's faster.
            if (!Arrays.equals(key.getAddress(), accountAddress.getBytes()))
                return retFail;


        } catch (Exception any) {
            // it may  enter here if signatureToKey rises an exception.
            return retFail;
        }

        // if account doesn't exist, it may be the case the user only owns tokens.
        // we must create it.
        if (state == null) {
            state = repository.createAccount(accountAddress);
        }

        // Now let the user replace the code if existent.
        if (!repository.isContract(accountAddress))
            repository.setupContract(accountAddress);

        repository.saveCode(accountAddress, code);

        byte[] retOk = new byte[1];
        retOk[0] = 1;
        return retOk;
    }

    static public byte[] getHashToSignFromCode(byte[] account,byte[] nonce, byte[] code) {
        // first hash the code
        byte[] codeHash = Keccak256Helper.keccak256(code);
        byte[] message = buildMessageToSign(account, nonce, codeHash);
        byte[] h = Keccak256Helper.keccak256(message);
        return h;
    }

    static public byte[] buildMessageToSign(byte[] account,byte[] nonce,byte[] codeHash) {
        assert (account.length==32);
        assert (nonce.length ==32);
        assert (codeHash.length ==32);
        byte[] message = new byte[1+1+32+32+32]; // account + nonce + codehash
        message[0] = 0x19;
        message[1] = 0x10;
        System.arraycopy(account, 0, message, 2, 32);
        System.arraycopy(nonce, 0, message, 2+32, 32);
        System.arraycopy(codeHash, 0, message, 2+64, 32);
        return message;
    }
    private boolean isValid(byte[] rBytes, byte[] sBytes, byte[] vBytes) {

        byte v = vBytes[vBytes.length - 1];
        BigInteger r = new BigInteger(1, rBytes);
        BigInteger s = new BigInteger(1, sBytes);

        return ECDSASignature.validateComponents(r, s, v);
    }

    @Override
    public void init(Transaction rskTx, Block rskExecutionBlock, Repository repository, BlockStore rskBlockStore, ReceiptStore rskReceiptStore, List<LogInfo> logs) {
        this.repository = repository;
    }
}

