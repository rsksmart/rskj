package co.rsk;

import co.rsk.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

public class StartLogback {
    private static Logger logger = LoggerFactory.getLogger("start");

    public static void main(String[] args) {
        Coin coin = new Coin(BigInteger.ONE);
        TestObject obj = new TestObject();
        logger.info("start {} {}", coin, obj);
        logger.info("coin {}", coin);
        logger.info("obj {}", obj);
        System.out.println("OK");
    }

    private static class TestObject {
        String msg;

        @Override
        public String toString() {
            return msg.toUpperCase();
        }
    }
}
