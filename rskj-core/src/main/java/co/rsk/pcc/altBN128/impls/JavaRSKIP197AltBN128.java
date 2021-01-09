package co.rsk.pcc.altBN128.impls;

import co.rsk.crypto.altbn128java.*;
import org.ethereum.util.BIUtil;
import org.ethereum.vm.DataWord;

import static co.rsk.pcc.altBN128.BN128Pairing.PAIR_SIZE;
import static org.ethereum.util.ByteUtil.*;

public class JavaRSKIP197AltBN128 extends JavaAltBN128 {

    @Override
    protected int returnError() {
        return -1;
    }
}
