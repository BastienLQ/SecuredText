package org.smssecure.smssecure.util;

import org.junit.Test;
import java.io.IOException;
import static junit.framework.Assert.assertEquals;

public class HexTest {
    private byte[] testBytes = new byte[16];//Just bytes
    {
        for(int i=0;i<16;i++){
            testBytes[i] = (byte) i;
        }
    }
    @Test public void testBytesToStringCondensed() {
        String hexOfTestBytes = "000102030405060708090a0b0c0d0e0f";
        assertEquals(hexOfTestBytes,Hex.toStringCondensed(testBytes));
    }
    @Test public void testBytesToString() {
        String hexOfTestBytes = "00 01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f ";
        assertEquals(hexOfTestBytes,Hex.toString(testBytes));
    }
    @Test public void testFromStringCondensed() throws IOException {
        assertEquals("000102030405060708090a0b0c0d0e0f",
                Hex.toStringCondensed(
                        Hex.fromStringCondensed("000102030405060708090a0b0c0d0e0f")
                )
        );
    }
    @Test public void testBytesDump() {
        String hexOfTestBytes = "00000000: 0001 0203 0405 0607 0809 0a0b 0c0d 0e0f ................\n";
        assertEquals(hexOfTestBytes,Hex.dump(testBytes));
    }
    @Test public void testBytesDumpWithOffset() {
        String hexOfTestBytes = "00000000: 0304 0506 0708 09                       .......\n";
        assertEquals(hexOfTestBytes,Hex.dump(testBytes,3,7));
    }
}
