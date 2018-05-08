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

package co.rsk.net.eth;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.net.NodeID;
import co.rsk.net.messages.GetBlockMessage;
import co.rsk.test.builders.AccountBuilder;
import org.ethereum.core.Account;
import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.net.eth.message.TransactionsMessage;
import org.ethereum.net.message.Message;
import org.junit.Assert;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by ajlopez on 26/04/2017.
 */
public class WriterMessageRecorderTest {

    private static final TestSystemProperties config = new TestSystemProperties();

    @Test
    public void recordRskMessage() throws IOException {
        Message message = createRskMessage();
        StringWriter writer = new StringWriter();
        BufferedWriter bwriter = new BufferedWriter(writer);

        WriterMessageRecorder recorder = new WriterMessageRecorder(bwriter, null);

        recorder.recordMessage(null, message);

        bwriter.close();

        String result = writer.toString();

        String encoded = Hex.toHexString(message.getEncoded());

        Assert.assertTrue(result.contains("," + encoded + ","));
        Assert.assertTrue(result.contains(",0,RSK_MESSAGE,GET_BLOCK_MESSAGE,"));
    }

    @Test
    public void recordRskMessageWithSender() throws IOException {
        Message message = createRskMessage();
        StringWriter writer = new StringWriter();
        BufferedWriter bwriter = new BufferedWriter(writer);

        Random random = new Random();
        byte[] nodeId = new byte[10];
        random.nextBytes(nodeId);
        NodeID sender = new NodeID(nodeId);

        WriterMessageRecorder recorder = new WriterMessageRecorder(bwriter, null);

        recorder.recordMessage(sender, message);

        bwriter.close();

        String result = writer.toString();

        String encoded = Hex.toHexString(message.getEncoded());

        Assert.assertTrue(result.contains("," + encoded + "," + Hex.toHexString(nodeId)));
        Assert.assertTrue(result.contains(",0,RSK_MESSAGE,GET_BLOCK_MESSAGE,"));
    }

    @Test
    public void filterMessage() throws IOException {
        Message message = createRskMessage();
        StringWriter writer = new StringWriter();
        BufferedWriter bwriter = new BufferedWriter(writer);

        List<String> commands = new ArrayList<>();
        commands.add("TRANSACTIONS");
        MessageFilter filter = new MessageFilter(commands);

        Random random = new Random();
        byte[] nodeId = new byte[10];
        random.nextBytes(nodeId);
        NodeID sender = new NodeID(nodeId);

        WriterMessageRecorder recorder = new WriterMessageRecorder(bwriter, filter);

        recorder.recordMessage(sender, message);

        bwriter.close();

        String result = writer.toString();

        Assert.assertEquals(0, result.length());
    }

    @Test
    public void recordEthMessage() throws IOException {
        Message message = createEthMessage();
        StringWriter writer = new StringWriter();
        BufferedWriter bwriter = new BufferedWriter(writer);

        WriterMessageRecorder recorder = new WriterMessageRecorder(bwriter, null);

        recorder.recordMessage(null, message);

        bwriter.close();

        String result = writer.toString();

        String encoded = Hex.toHexString(message.getEncoded());

        Assert.assertTrue(result.contains("," + encoded));
        Assert.assertTrue(result.contains(",0,TRANSACTIONS,,"));
    }

    public static Message createRskMessage() {
        Block block = new BlockGenerator().getBlock(1);
        GetBlockMessage message = new GetBlockMessage(block.getHash().getBytes());
        return new RskMessage(config, message);
    }

    public static Message createEthMessage() {
        Account acc1 = new AccountBuilder().name("acc1").build();
        Account acc2 = new AccountBuilder().name("acc2").build();
        Transaction tx = new co.rsk.test.builders.TransactionBuilder().sender(acc1).receiver(acc2).value(BigInteger.valueOf(1000000)).build();
        return new TransactionsMessage(config, tx);
    }
}
