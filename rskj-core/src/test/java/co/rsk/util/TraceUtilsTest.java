package co.rsk.util;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraceUtilsTest{

    @Test
    void testToId() {
        String test = "asdfñalsdkfjasñdlfkjañsldfjañslfjkasf";
        String resultId = TraceUtils.toId(test);
        String expected = test.substring(0,15);
        assertEquals(15,resultId.length());
        assertEquals(expected,resultId);
    }
}