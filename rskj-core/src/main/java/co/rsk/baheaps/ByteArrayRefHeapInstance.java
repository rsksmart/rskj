package co.rsk.baheaps;

import co.rsk.baheaps.ByteArrayRefHeap;

public class ByteArrayRefHeapInstance {

    static ByteArrayRefHeap objectHeap;

    public static ByteArrayRefHeap get() {
        if (objectHeap == null)
            objectHeap = new ByteArrayRefHeap();

        return objectHeap;
    }


}
