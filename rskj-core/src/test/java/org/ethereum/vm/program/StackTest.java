package org.ethereum.vm.program;

import org.ethereum.vm.DataWord;
import org.ethereum.vm.program.listener.ProgramListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class StackTest {

    @Mock
    private ProgramListener programListener;
    private Stack stack;

    @BeforeEach
    void init(){
        stack = new Stack();
        stack.setTraceListener(programListener);
    }
    @Test
    void onPushTraceListenerIsCalled(){
        DataWord dataWord = DataWord.ZERO;
        stack.push(dataWord);
        verify(programListener).onStackPush(eq(dataWord));
    }

    @Test
    void onPopTraceListenerIsCalled(){
        DataWord dataWord = DataWord.ZERO;
        stack.push(dataWord);
        stack.pop();
        verify(programListener).onStackPop();
    }

    @Test
    void onSwapTraceListenerIsCalled(){
        DataWord dataWord1 = DataWord.ZERO;
        DataWord dataWord2 = DataWord.valueOf(1);

        stack.push(dataWord1);
        stack.push(dataWord2);
        stack.swap(1,0);
        verify(programListener).onStackSwap(eq(1),eq(0));
    }

    @Test
    void testClone(){
        Stack clone = (Stack) stack.clone();
        assertNotNull(clone);
    }

}