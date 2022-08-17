package co.rsk.baheaps;
import co.rsk.dbutils.FileMapUtil;
import co.rsk.spaces.Space;
import org.ethereum.util.ByteUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

// This class represents a heap on memory or memory-mapped files where elements
// are referenced by handles, instead of their physical position in memory/file.
// The use of indirection allows the compression of the files without propagating
// the changes to the objects that hold the pointers to the hash elements.

public class ByteArrayRefHeap extends ByteArrayHeap implements AbstractByteArrayRefHeap {
    // The number of maxObjects must be at least 5 times higher than the number of
    // elements that will be inserted in the trie because intermediate nodes
    // consume handles also.
    public static int averageObjectSize  = 64;
    public static int default_maxObjects = 0;// autocompute 40 * 1000 * 1000;

    public boolean rejectDoubleRemaps = true;
    public static int debugHandle = -2; //104612;
    final boolean useListForSorting = false;

    long references[];
    int unusedHandles[];
    int unusedHandlesCount;
    int highestHandle;
    int maxReferences;



    int referenceCount;
    private HandleComparator handleComparator;

    // These are only required for remapping
    public BitSet touchedHandles;
    int[] move; // List of handles
    int moveCount;

    public int getMaxReferences() {
        return maxReferences;
    }

    public void setMaxReferences(int maxReferences) {
        if (references!=null)
            throw new RuntimeException("Cannot reset maxReferences");
        this.maxReferences = maxReferences;
    }

    public ByteArrayRefHeap() {
        super();

        maxReferences = default_maxObjects;
        // Must call initialize
    }


    public void reset() {

        if ((default_maxObjects==0) && (maxReferences==0)) {
            maxReferences = (int) (getMaxMemory()/averageObjectSize);
        }

        if (maxReferences > Integer.MAX_VALUE)
            maxReferences = Integer.MAX_VALUE;

        references = new long[maxReferences];
        unusedHandles = new int[maxReferences];
        for (int i = 0; i < maxReferences; i++)
            unusedHandles[i] = maxReferences - i - 1;

        referenceCount = 0;
        unusedHandlesCount = maxReferences;

        super.reset();
    }

    public void removeObjectByHandle(int handle) {
        // removes are handled with remaps. You can't remove a single element
    }

    public void clearRemapMode() {
        super.clearRemapMode();
        move = null;
        moveCount = 0;
        touchedHandles = null;

    }

    public boolean supportsGarbageCollection() {
        return true;
    }

    public void beginRemap() {
        super.beginRemap();
        touchedHandles = new BitSet();

        // Create a vector to mark all objects that need to be moved
        //move = new ArrayList<Integer>();
        move = new int[highestHandle + 1];
    }




    class HandleComparator implements Comparator<Integer> {
        @Override
        public int compare(Integer a, Integer b) {
            long ofsA = references[a];
            long ofsB = references[b];
            return ofsA < ofsB ? -1 : ofsA == ofsB ? 0 : 1;
        }
    }

    public void checkSorted(int[] move, int moveCount) {

        /*for(int i=0;i<move.size()-1;i++) {
            if (references[move.get(i)]>=references[move.get(i+1)]){
                throw new RuntimeException("bad sort");
            }
        }*/
        for (int i = 0; i < moveCount - 1; i++) {
            if (references[move[i]] >= references[move[i + 1]]) {
                throw new RuntimeException("bad sort");
            }
        }
    }


    public void compress(int[] move, int moveCount) { //List<Integer> move) {
        if (debugLogInfo)
            //System.out.println("Handles to move: "+move.size());
            System.out.println("Handles to move: " + moveCount);

        //handleComparator = new HandleComparator();
        // All objects that are in the move list need to be packed in-place
        // We'll sort the move objects based on the offset
        //QuickSort qs = new QuickSort();
        if (logOperations)
            System.out.println(">> sorting...");
        throw new RuntimeException("This requires adding Primitive package");
        // Removed temporarily until Primitive package is added
        /* IntComparator cmp = ((a, b)->((references[(int) a] < references[(int)b] ? -1 : references[(int)a] == references[(int)b] ? 0 : 1)));
        // Primitive.sort(move,0,moveCount, cmp);
        if (useListForSorting) {
            List<Integer> moveSort = new ArrayList<>(moveCount);
            for (int i = 0; i < moveCount; i++)
                moveSort.add(move[i]);
            handleComparator = new HandleComparator();
            moveSort.sort(handleComparator);
            for (int i = 0; i < moveCount; i++)
                move[i] = moveSort.get(i);
            moveSort = null;
        }
        if (logOperations)
            System.out.println(">> sorted!");
        //move.sort(handleComparator);
        if (debugCheckAll)
            checkSorted(move, moveCount);
        long originalSize = 0;
        // Now we can start to compress
        int prevSpace = -1;

        int baseOfs = 0;
        //for(int i=0;i<move.size();i++) {
        //    int h = move.get(i);
        for (int i = 0; i < moveCount; i++) {
            int h = move[i];

            long ofs = references[h];
            if (ofs < 0) debugHandle = h;
            if (h == debugHandle) {
                dumpHandleContent(h);
                System.out.println("Moving from " + ofs);
            }
            int hspace = getSpaceNumOfPointer(ofs);
            int spaceOfs = getSpaceOfsFromPointer(hspace, ofs);
            if (hspace > prevSpace) {
                if (prevSpace != -1) {
                    originalSize += spaces[prevSpace].memTop;
                    spaces[prevSpace].memTop = baseOfs;
                }
                // close this space
                prevSpace = hspace;
                baseOfs = 0;
            }
            // now compress the object in the space
            Space space = spaces[hspace];
            try {
                checkDebugHeader(space, spaceOfs);
            } catch (RuntimeException e) {
                System.out.print("bad handle: " + h);
                //System.out.print(getHandleInfo(h));
                throw e;
            }
            if (h==2)
                h =2;
            references[h] = buildPointer(hspace, baseOfs); // stores new reference

            int dataLen = getEncodedLength(space,spaceOfs+lastMetadataLen);
            int dataLenLength = getEncodedLengthLength(space,spaceOfs+lastMetadataLen);
            // Now check that either both have indexes or none

            byte[] prevObject = new byte[lastMetadataLen+dataLenLength+dataLen];
            space.getBytes(spaceOfs,prevObject,spaceOfs,prevObject.length);
            // StoreObject receives the offset of the object data, not
            // the object header, therefore we must add F3 to spaceOfs.
            int newTop = storeObject(space, baseOfs,
                    prevObject, lastMetadataLen + dataLenLength, dataLen,
                    prevObject,0,lastMetadataLen);

            if (h == debugHandle) {
                dumpHandleContent(h);
                System.out.println("Moving to " + baseOfs);
            }
            baseOfs = newTop;

        }
        if (prevSpace != -1) {
            originalSize += spaces[prevSpace].memTop;
            spaces[prevSpace].memTop = baseOfs;
        }
       
         */
    }


    public void endRemap() {
        compress(move, moveCount);
        markAllUntouchedHandlesAssUnused();
        super.endRemap();
    }

    public void markAllUntouchedHandlesAssUnused() {
        if (debugLogInfo)
            System.out.println("Disposing all unused handles...");
        int disposedHandles = 0;
        for (int i = 0; i < highestHandle; i++) {
            if ((references[i] != -1) && (!touchedHandles.get(i))) {
                references[i] = -1;
                unusedHandles[unusedHandlesCount] = i;
                disposedHandles++;
                unusedHandlesCount++;
            }
        }
        if (debugLogInfo) {
            System.out.println("Disposed handles: " + disposedHandles);
            System.out.println("unusedHandlesCount: "+ unusedHandlesCount);
        }
    }

    public void checkAll() {
        System.out.println("Checking all...");
        for (int i = 0; i < highestHandle; i++) {
            long ofs = references[i];
            if (ofs == -1) // unassigned
                continue;
            checkObjectByOfs(ofs);

        }
        System.out.println("Checked!");
    }

    public String getHandleInfo(int handle) {
        String r = "";
        r += "handle: " + handle + "\n";
        if (handle >= 0) {
            long ofs = references[handle];
            r += "ofs: " + ofs + "\n";
            if (ofs != -1) {
                int s = getSpaceNumOfPointer(ofs);
                r += "space: " + s + "\n";
                int iofs = getSpaceOfsFromPointer(s, ofs);
                r += "internalOfs: " + iofs + "\n";
                r += "metadataLen: " + lastMetadataLen + "\n";
                r += "dataLen: " + spaces[s].getByte(iofs+lastMetadataLen) + "\n";
                byte[] mbytes = retrieveMetadataByOfs(ofs);
                r += "metadata: " + ByteUtil.toHexString(mbytes) + "\n";
                byte[] bytes = retrieveDataByOfs(ofs);
                r += "data: " + ByteUtil.toHexString(bytes) + "\n";
            }
        }
        return r;
    }


    public void dumpHandleContent(int handle) {
        long ofs = references[handle];
        byte[] bytes = retrieveDataByOfs(ofs);
        System.out.println("dump: " + ByteUtil.toHexString(bytes));
    }

    public void checkHandle(int handle) {
        if (handle == -1) return;
        if (handle == -1)
            return;

        if ((handle < -1) || (handle > highestHandle))
            throw new RuntimeException("Invalid handle=" + handle);
        long ofs = references[handle];
        if (ofs >= MaxPointer) {
            throw new RuntimeException("Invalid(2) ofs=" + ofs);
        }
        if (ofs < 0) {
            throw new RuntimeException("Invalid(3) ofs=" + ofs);
        }
    }

    public void checkDuringRemap(int handle) {
        if (handle == -1) return;
        long ofs = references[handle];
        if (ofs == -1) return;
        //if (getSpaceNumOfPointer(ofs)==oldSpaceNum)
        if (oldSpacesBitmap.get(getSpaceNumOfPointer(ofs)))
            throw new RuntimeException("bad pointer!");
    }


    public void remapByHandle(int handle) {
        // mark if it needs movement
        if (handle != -1) {
            if (touchedHandles.get(handle)) {
                System.out.println("double remap: "+handle);
                if (rejectDoubleRemaps)
                    throw new RuntimeException("double remap rejected");
            } else
              touchedHandles.set(handle);
        }
        if (needsToMoveHandle(handle)) {
            //move.add(handle);
            move[moveCount] = handle;
            moveCount++;
        }
    }

    public boolean needsToMoveHandle(int handle) {
        if (handle == -1) return false;
        return needsToMoveOfs(references[handle]);
    }

    public void checkInRightSpace(int handle) {
        //if (handle==-1) return;
        //long ofs = references[handle];
        //if (ofs==-1)
        //    return;
        //int spaceNum = getSpaceNumOfPointer(ofs);

    }

    public List<String> getStats() {
        List<String>  res = super.getStats();
        res.add("highest Handle: "+ highestHandle);
        return res;
    }


    public int add(byte[] encoded) {
        return addGetHandle(encoded,null);
    }

    public int addAndReturnHandle(byte[] encoded, byte[] metadata) {
        return addGetHandle(encoded,metadata);
    }

    public int addGetHandle(byte[] encoded,byte[] metadata) {

        long ofs = addObjectReturnOfs(encoded,metadata);
        if (unusedHandlesCount==0) {
            throw new RuntimeException("No more handles!");
        }
        int newHandle = unusedHandles[unusedHandlesCount - 1];
        unusedHandlesCount--;
        references[newHandle] = ofs;
        if (newHandle > highestHandle)
            highestHandle = newHandle;
        referenceCount++;
        if (newHandle == debugHandle) {
            newHandle = newHandle;
            checkHandle(newHandle);
            dumpHandleContent(newHandle);
        }
        return newHandle;
    }



    public byte[] retrieveDataByHandle(int handle) {
        if (handle == -1)
            throw new RuntimeException("no data");
        return retrieveDataByOfs(references[handle]);
    }

    public void setMetadataByHandle(int handle, byte[] metadata) {
        if (handle == -1)
            throw new RuntimeException("no data");
        setMetadataByOfs(references[handle],metadata);
    }

    public byte[] retrieveMetadataByHandle(int handle) {
        if (handle == -1)
            throw new RuntimeException("no data");
        return retrieveMetadataByOfs(references[handle]);
    }

    public boolean fileExists() {
        Path path = Paths.get(baseFileName + ".desc");
        File f = path.toFile();
        return (f.exists() && !f.isDirectory());
    }

    public long load() throws IOException {
        long r = super.load();
        readFromFile(baseFileName+".ref",false);
        return r;
    }

    public void save() throws IOException {
        super.save();
        saveToFile(baseFileName+".ref");
    }

    protected void readFromFile(String fileName,boolean map) throws IOException {
        File file = new File(fileName);
        FileInputStream fin = new FileInputStream(file);
        BufferedInputStream bin = new BufferedInputStream(fin);
        DataInputStream din = new DataInputStream(bin);

        unusedHandlesCount = din.readInt();
        highestHandle = din.readInt();
        maxReferences = din.readInt();
        int references_length = din.readInt();
        int unusedHandles_length = din.readInt();
        references = null; // Avoid out-of-memory errors
        unusedHandles = null; // Avoid out-of-memory errors
        references = readLongArray(din, references_length);
        unusedHandles = readIntArray(din, unusedHandles_length);
        din.close();
        super.load();

    }

    int[] readIntArray(DataInputStream din,int count) throws IOException {
        int[] table = new int[count];
        System.out.println("reading int array...");
        for (int i = 0; i < count; i++) {
            table[i] = din.readInt();
        }
        System.out.println("done");
        return table;
    }

    long[] readLongArray(DataInputStream din,int count) throws IOException {
        long[] table = new long[count];
        System.out.println("reading long array...");
        for (int i = 0; i < count; i++) {
            table[i] = din.readLong();
        }
        System.out.println("done");
        return table;
    }

    protected void saveToFile(String fileName ) throws IOException {

        RandomAccessFile sc
                = new RandomAccessFile(fileName, "rw");
        FileChannel file = sc.getChannel();
        ByteBuffer buf = file.map(FileChannel.MapMode.READ_WRITE, 0,
                4L * 5);

        buf.putInt(unusedHandlesCount);
        buf.putInt(highestHandle);
        buf.putInt(maxReferences);
        buf.putInt(references.length);
        buf.putInt(unusedHandles.length);

        FileMapUtil.mapAndCopyLongArray(file,4*5,8L*references.length,references);
        FileMapUtil.mapAndCopyIntArray(file,4*5+8L*references.length,
                4L*unusedHandles.length,unusedHandles);

        file.close();
        /* Slower
        DataOutputStream os = new DataOutputStream(
                new FileOutputStream(fileName));

        os.writeInt(unusedHandlesCount);
        os.writeInt(highestHandle);
        os.writeInt(maxReferences);
        os.writeInt(references.length);
        os.writeInt(unusedHandles.length);
        writeLongArray(os,references);
        writeIntArray(os,unusedHandles);
        os.close();

         */
    }

    void writeIntArray(DataOutputStream os,int[] table) throws IOException {
        for (int i =0 ; i < table.length; ++i){
            os.writeInt(table[i]);
        }
    }

    void writeLongArray(DataOutputStream os,long[] table) throws IOException {
        for (int i =0 ; i < table.length; ++i){
            os.writeLong(table[i]);
        }
    }
}

