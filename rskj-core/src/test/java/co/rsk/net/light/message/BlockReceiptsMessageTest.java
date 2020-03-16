package co.rsk.net.light.message;

import co.rsk.net.rlpx.LCMessageFactory;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.Bloom;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionReceipt;
import org.ethereum.vm.LogInfo;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

public class BlockReceiptsMessageTest {

    @Test
    public void createMessage() {
        List<TransactionReceipt> receipts = new LinkedList<>();
        receipts.add(createReceipt());
        receipts.add(createReceipt());

        BlockReceiptsMessage message = new BlockReceiptsMessage(1, receipts);
        assertEquals(1, message.getId());
        assertEquals(receipts, message.getBlockReceipts());
        assertNull(message.getAnswerMessage());
    }

    @Test
    public void messageEncodeDecodeShouldBeCorrect() {
        List<TransactionReceipt> receipts = new LinkedList<>();
        receipts.add(createReceipt());
        receipts.add(createReceipt());
        int requestId = 1;

        BlockReceiptsMessage testMessage = new BlockReceiptsMessage(requestId, receipts);
        byte[] encoded = testMessage.getEncoded();

        LCMessageFactory lcMessageFactory = new LCMessageFactory();
        BlockReceiptsMessage message = (BlockReceiptsMessage) lcMessageFactory.create((byte) 2, encoded);

        List<TransactionReceipt> testBlockReceipts = testMessage.getBlockReceipts();
        List<TransactionReceipt> blockReceipts = message.getBlockReceipts();
        assertEquals(testBlockReceipts.size(), blockReceipts.size());

        for (int i = 0; i < testBlockReceipts.size(); i++) {
            assertArrayEquals(testBlockReceipts.get(i).getEncoded(), blockReceipts.get(i).getEncoded());
        }

        assertEquals(testMessage.getId(), message.getId());
        assertEquals(testMessage.getCommand(), message.getCommand());
        assertEquals(testMessage.getAnswerMessage(), message.getAnswerMessage());
        assertArrayEquals(encoded, message.getEncoded());
    }


    private static TransactionReceipt createReceipt() {
        byte[] stateRoot = Hex.decode("f5ff3fbd159773816a7c707a9b8cb6bb778b934a8f6466c7830ed970498f4b68");
        byte[] gasUsed = Hex.decode("01E848");
        Bloom bloom = new Bloom(Hex.decode("0000000000000000800000000000000004000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));

        LogInfo logInfo1 = new LogInfo(
                Hex.decode("cd2a3d9f938e13cd947ec05abc7fe734df8dd826"),
                null,
                Hex.decode("a1a1a1")
        );

        List<LogInfo> logs = new ArrayList<>();
        logs.add(logInfo1);

        // TODO calculate cumulative gas
        TransactionReceipt receipt = new TransactionReceipt(stateRoot, gasUsed, gasUsed, bloom, logs, new byte[]{0x01});

        receipt.setTransaction(new Transaction( null, null, null, null, null, null));

        return receipt;
    }
}
