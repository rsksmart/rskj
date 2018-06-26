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

package co.rsk.net.messages;

import org.ethereum.core.Block;
import org.junit.Assert;
import org.junit.Test;
import org.bouncycastle.util.encoders.Hex;

/**
 * Created by ajlopez on 5/10/2016.
 */
public class BlockMessageTest {
    private static String rlp = "f902b3f902aea0abff92b32e43e9f34eda3fa7fe5359cb06871b172226a829daa9af22d1fac2cea01dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347949efa02278cc63dc612c174976f11037d382f8b67a0c5d6ad68162cb8f04ef7afc8bf74558a19bceaf334b0497bd8d3c86c24de9f9da056e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421a0a8f57ab26ee2c88d15f6bd20f052dbc76a1f4a0b55d214a88f4b8201b8840736b901000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000083020000018407fe000080845730e70e808080b85004000000f08613eff431f121c059541e38594a190d6b0e46d2cb2bb52dc6b692020000003b8cacca5ed46c229a777c7c55ddfefed2b78ccb4ce6df07ecb1e36bde29f4741ee73057ffff7f20005a40aca701000000013b8cacca5ed46c229a777c7c55ddfefed2b78ccb4ce6df07ecb1e36bde29f4740101b86100000000000000801f506f4152ccdb46a5e162488b24647e750c88073c56530d2154475fdc8c4cb4ac00000000000000002b6a524f4f5453544f434b3a1ff8eae11ecb36803b0fd23bd31490bd1054e078852a0f975428871c42bef4090000000080c0c0";

    @Test
    public void getMessageType() {
        BlockMessage message = new BlockMessage(null);
        Assert.assertEquals(MessageType.BLOCK_MESSAGE, message.getMessageType());
    }

    @Test
    public void getBlock() {
        Block block = new Block(Hex.decode(rlp));
        BlockMessage message = new BlockMessage(block);
        Assert.assertSame(block, message.getBlock());
    }
}
