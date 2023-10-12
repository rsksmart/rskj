package co.rsk.bridge.btcLockSender;

import co.rsk.bitcoinj.core.Address;
import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Sha256Hash;
import co.rsk.core.RskAddress;
import org.ethereum.crypto.HashUtil;
import org.ethereum.util.ByteUtil;

public class P2shP2wpkhBtcLockSender implements BtcLockSender {

    private TxSenderAddressType txSenderAddressType;
    private Address btcAddress;
    private RskAddress rskAddress;

    public P2shP2wpkhBtcLockSender() {
        this.txSenderAddressType = TxSenderAddressType.P2SHP2WPKH;
    }

    @Override
    public TxSenderAddressType getTxSenderAddressType() {
        return txSenderAddressType;
    }

    @Override
    public Address getBTCAddress() {
        return this.btcAddress;
    }

    @Override
    public RskAddress getRskAddress() {
        return this.rskAddress;
    }

    @Override
    public boolean tryParse(BtcTransaction btcTx) {
        if (btcTx == null) {
            return false;
        }
        if (!btcTx.hasWitness()) {
            return false;
        }
        if (btcTx.getInput(0).getScriptBytes() == null) {
            return false;
        }
        if (btcTx.getInput(0).getScriptSig().getChunks().size() != 1) {
            return false;
        }
        if (btcTx.getWitness(0).getPushCount() != 2) {
            return false;
        }

        byte[] pubKey = btcTx.getWitness(0).getPush(1);
        // get pubKey from witness
        BtcECKey key = BtcECKey.fromPublicOnly(btcTx.getWitness(0).getPush(1));

        if (!key.isCompressed()) {
            return false;
        }

        try {
            // pubkeyhash = hash160(sha256(pubKey))
            byte[] keyHash = key.getPubKeyHash();

            // witnessVersion = 0x00
            // push20 = 0x14
            // scriptPubKey = hash160(sha256(witnessVersion push20 pubkeyhash))
            byte[] redeemScript = ByteUtil.merge(new byte[]{ 0x00, 0x14}, keyHash);
            byte[] scriptPubKey = HashUtil.ripemd160(Sha256Hash.hash(redeemScript));

            this.btcAddress = new Address(btcTx.getParams(), btcTx.getParams().getP2SHHeader(), scriptPubKey);
            this.rskAddress = new RskAddress(org.ethereum.crypto.ECKey.fromPublicOnly(pubKey).getAddress());
        } catch (Exception e) {
            return false;
        }

        return true;
    }
}
