package co.rsk.rpc.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomJsonNodeFactoryTest {

    @Test
    void createArrayNode_withLimit() {
        CustomJsonNodeFactory factory = new CustomJsonNodeFactory(50);
        assertTrue(factory.arrayNode() instanceof LimitedArrayNode);
    }

    @Test
    void createArrayNode_withoutLimit() {
        CustomJsonNodeFactory factory = new CustomJsonNodeFactory(-1);
        assertFalse(factory.arrayNode() instanceof LimitedArrayNode);

        CustomJsonNodeFactory factory2 = new CustomJsonNodeFactory();
        assertFalse(factory2.arrayNode() instanceof LimitedArrayNode);
    }
}