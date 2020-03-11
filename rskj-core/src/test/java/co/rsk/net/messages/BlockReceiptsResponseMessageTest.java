package co.rsk.net.messages;

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
import static org.mockito.Mockito.*;

public class BlockReceiptsResponseMessageTest {
//    @Test
//    public void createMessage() {
//        List<TransactionReceipt> receipts = new LinkedList<>();
//        receipts.add(createReceipt());
//        receipts.add(createReceipt());
//
//        BlockReceiptsResponseMessage message = new BlockReceiptsResponseMessage(1, receipts);
//        assertEquals(1, message.getId());
//        assertEquals(receipts, message.getBlockReceipts());
//        assertEquals(MessageType.BLOCK_RECEIPTS_RESPONSE_MESSAGE, message.getMessageType());
//    }
//
//    @Test
//    public void accept() {
//
//        List<TransactionReceipt> receipts = new LinkedList<>();
//        BlockReceiptsResponseMessage message = new BlockReceiptsResponseMessage(1, receipts);
//
//        MessageVisitor visitor = mock(MessageVisitor.class);
//
//        message.accept(visitor);
//
//        verify(visitor, times(1)).apply(message);
//    }

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

        receipt.setTransaction(new Transaction((byte[]) null, null, null, null, null, null));

        return receipt;
    }

}
