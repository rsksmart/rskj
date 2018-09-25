package co.rsk.db;

import org.ethereum.vm.DataWord;

import java.util.*;

/**
 * Created by SerAdmin on 9/25/2018.
 * This class is ONLY for unit testing. It should NOT be active in production because
 * it grows in size without bounds.
 */
public class GlobalKeyMap {
    static public Set<DataWord> globalKeyMap = Collections.synchronizedSet(new HashSet<DataWord>());
}

