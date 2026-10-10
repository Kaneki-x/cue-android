package io.cueapp.android.otp;

import static org.junit.Assert.assertEquals;

import io.cueapp.android.crypto.otp.MOTPTest;
import io.cueapp.android.encoding.EncodingException;
import io.cueapp.android.encoding.Hex;

import org.junit.Test;

public class MotpInfoTest {
    @Test
    public void testMotpInfoOtp() throws OtpInfoException, EncodingException {
        for (MOTPTest.Vector vector : MOTPTest.VECTORS) {
            MotpInfo info = new MotpInfo(Hex.decode(vector.Secret), vector.Pin);
            assertEquals(vector.OTP, info.getOtp(vector.Time));
        }
    }
}
