package co.rsk.datasource;

import co.rsk.freeheap.FreeHeapBase;
import org.junit.Assert;
import org.junit.Test;

public class FreeHeapTest {

    @Test
    public void putKeyValue() {
        FreeHeapBase baHeap = new FreeHeapBase();
        long maxCapacity = 50_000;
        int maxObjectSize = 1_000;
        baHeap.setMaxMemory(maxCapacity);
        baHeap.setFileName("test");
        baHeap.setFileSystemPageSize(4096);
        baHeap.setMaxObjectSize(maxObjectSize);
        baHeap.setStoreHeadmap(true);
        baHeap.setFileMapping(false); // if true, then we need to remove files before start
        baHeap.initialize();
        // save the description and the files as soon as possible
        //    baHeap.save();
        //checkPair(baHeap,5599,5707);
        //System.exit(1);
        for (int count=0;count<20;count++) {
            // We'll mark at "random" positions (multiples of 509, which is prime)
            long markPos = count*509;
            baHeap.markObjectAtOffset(markPos,true);
            for(int c=0;c<10;c++) {
                long checkPos = markPos + c*c* 27;
                System.out.print("chk "+markPos+" "+checkPos);
                long foundPos = baHeap.findNearestMark(checkPos);
                boolean shouldFind = (checkPos - markPos) <= maxObjectSize;
                boolean found = (foundPos == markPos);
                System.out.println(": "+shouldFind+" "+found);
                Assert.assertEquals(found, shouldFind);
            }
            baHeap.markObjectAtOffset(markPos,false); // unmark
        }
    }

    public void checkPair( FreeHeapBase baHeap ,long markPos,long checkPos) {
        baHeap.markObjectAtOffset(markPos,true);
        long foundPos = baHeap.findNearestMark(checkPos);
        boolean shouldFind = (checkPos - markPos) <= baHeap.getMaxObjectSize();
        boolean found = (foundPos == markPos);
        System.out.println("chk "+markPos+" "+checkPos+ ": "+shouldFind+" "+found);
        Assert.assertEquals(found, shouldFind);
        baHeap.markObjectAtOffset(markPos,false);
    }
}
