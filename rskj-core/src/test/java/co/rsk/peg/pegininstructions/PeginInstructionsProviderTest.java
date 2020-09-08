package co.rsk.peg.pegininstructions;

import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.core.Coin;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptOpCodes;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.core.RskAddress;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

public class PeginInstructionsProviderTest {
    private final NetworkParameters params = BridgeRegTestConstants.getInstance().getBtcParams();

    @Test(expected = PeginInstructionsException.class)
    public void peginInstructionsProvider_null_op_return_data() throws Exception {
        BtcTransaction btcTransaction = new BtcTransaction(params);

        // Add OP_RETURN output with empty data
        btcTransaction.addOutput(Coin.ZERO, new Script(new byte[] { ScriptOpCodes.OP_RETURN }));

        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        peginInstructionsProvider.buildPeginInstructions(btcTransaction);
    }

    @Test(expected = PeginInstructionsException.class)
    public void peginInstructionsProvider_invalid_protocol_version() throws Exception {
        String rawTx = "0200000001956f33bf742ae944ee92b9f7838c81b27c00d01c51eddf815583d71022f75a50000000006a473044022022" +
                "279ce3060a1595e61a6d04b89d59b6b8917542bc1fe9073ce2e2fd6c8e24c802205348bd58180fa89f995ff80e01e8cb60100d" +
                "f0affbc0312a83d291b073f0a4700121036ba47a8665e02bf265f849f41ce9985626fff1a4290dc87cc63c3ab0399f5765ffff" +
                "ffff0328cdf5050000000017a914603a20f8bdace9fd117feaccd9161f3497662c348700e1f5050000000017a91419d7e0ee9b" +
                "f6bd70d1d046b066d1c2726e1accc1870000000000000000186a1609990e537aad84447a2c2a7590d5f2665ef5cf9b667a0000" +
                "0000";

        BtcTransaction btcTransaction = new BtcTransaction(params, Hex.decode(rawTx));

        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        peginInstructionsProvider.buildPeginInstructions(btcTransaction);
    }

    @Test(expected = PeginInstructionsParseException.class)
    public void peginInstructionsProvider_parse_data_length_smaller_than_expected_pegin_instructions_v1() throws
            Exception {
        String rawTx = "02000000000101df517677f84ebd82a4719f532ef660f10dc6df2963fec596d47a559638f37b2f0000000017160014" +
                "5ab27e716e6277a99df7267292353b626c36bf16ffffffff03b03024180100000017a914c1a47cc7236ba6bf7081f3ec6440b3" +
                "6f5c8a41408700e1f505000000001976a91429d3b0d878547000449bef07e44a387761f1578c88ac0000000000000000166a14" +
                "00010e537aad84447a2c2a7590d5f2665ef5cf9b0247304402207e8e20ae25731ee1a4df4f1ee8b9a0da14f4157bbe7e116408" +
                "3492631aca3443022072312aabc1d8e95b98a48edbce7d9ce2247ba6b76194d4bc797a2ce38e2ee3330121024212698b17ae06e" +
                "5abc6520def8ab960adb7afb3f9d6000a4834bd3f5abc146e00000000";

        BtcTransaction btcTransaction = new BtcTransaction(params, Hex.decode(rawTx));

        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        peginInstructionsProvider.buildPeginInstructions(btcTransaction);
    }

    @Test(expected = PeginInstructionsParseException.class)
    public void peginInstructionsProvider_parse_data_length_different_than_supported_pegin_instructions_v1() throws
            Exception {
        String rawTx = "0200000001d891ab0f1365c7272b4a4932647208886ac42f6a8375a0af2839c89dc58084fb000000006a473044022" +
                "06dffd165bc596eebf09fb9d61faeeb433bcb4cd498fb701ebed6e5c71afc4a8f02201439e21528214876eb3e37b610dc41a" +
                "9daa116560f164d3903da762aecd1506d01210337005aa37799aeaa510ac681102c7fcbdac18aa155476332dd8a866e3caaa3" +
                "35ffffffff0300e1f505000000001976a91429d3b0d878547000449bef07e44a387761f1578c88acc4ccf5050000000017a9" +
                "14a0251d6d4eb50f44da49b8dce5a78fd46e9dc1a88700000000000000001b6a1900010e537aad84447a2c2a7590d5f2665e" +
                "f5cf9b667a98909100000000";

        BtcTransaction btcTransaction = new BtcTransaction(params, Hex.decode(rawTx));

        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        peginInstructionsProvider.buildPeginInstructions(btcTransaction);
    }

    @Test
    public void peginInstructionsProvider_return_pegin_instructions_v1() throws Exception {
        String rawTx = "0200000001bf584649795f54f4578f5351bcb072d7f28d97894d8db9733f385edd37d2c64c000000006a47304402203" +
                "a3103a1d10d814f5cf2143c7408e491648ce5775769b1728e2b51366b641dda02207449de7736549886db203ee49bfd15d59d" +
                "079002255abda9da2410763321662c01210211f2a6a136195c327ec67df3e6e1ec06e0e5e107f7b518d12d13d2f7263e4ce3" +
                "ffffffff0380969800000000001976a9148bc874b7d6cf12e920d227e28c994daa5b5cc4ab88ac148ee400000000001976a91" +
                "44f4c767a2d308eebb3f0f1247f9163c896e0b7d288ac0000000000000000186a1600010e537aad84447a2c2a7590d5f2665e" +
                "f5cf9b667a00000000";

        BtcTransaction btcTransaction = new BtcTransaction(params, Hex.decode(rawTx));

        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        PeginInstructionsVersion1 peginInstructionsVersion1 =
                (PeginInstructionsVersion1) peginInstructionsProvider.buildPeginInstructions(btcTransaction);

        Assert.assertEquals(1, peginInstructionsVersion1.getProtocolVersion());
    }

    @Test
    public void peginInstructionsProvider_return_rsk_destination_address_from_pegin_instructions_v1() throws Exception {
        String rawTx = "0200000001bf584649795f54f4578f5351bcb072d7f28d97894d8db9733f385edd37d2c64c000000006a47304402203" +
                "a3103a1d10d814f5cf2143c7408e491648ce5775769b1728e2b51366b641dda02207449de7736549886db203ee49bfd15d59d" +
                "079002255abda9da2410763321662c01210211f2a6a136195c327ec67df3e6e1ec06e0e5e107f7b518d12d13d2f7263e4ce3" +
                "ffffffff0380969800000000001976a9148bc874b7d6cf12e920d227e28c994daa5b5cc4ab88ac148ee400000000001976a91" +
                "44f4c767a2d308eebb3f0f1247f9163c896e0b7d288ac0000000000000000186a1600010e537aad84447a2c2a7590d5f2665e" +
                "f5cf9b667a00000000";

        BtcTransaction btcTransaction = new BtcTransaction(params, Hex.decode(rawTx));

        PeginInstructionsProvider peginInstructionsProvider = new PeginInstructionsProvider();
        PeginInstructionsVersion1 peginInstructionsVersion1 =
                (PeginInstructionsVersion1) peginInstructionsProvider.buildPeginInstructions(btcTransaction);

        RskAddress expectedRskDestinationAddress = new RskAddress("0x0e537aad84447a2c2a7590d5f2665ef5cf9b667a");
        Assert.assertEquals(expectedRskDestinationAddress, peginInstructionsVersion1.getRskDestinationAddress());
    }
}
