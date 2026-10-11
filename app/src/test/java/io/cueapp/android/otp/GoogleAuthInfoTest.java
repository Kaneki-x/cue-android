package io.cueapp.android.otp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import io.cueapp.android.encoding.Base32;
import io.cueapp.android.encoding.EncodingException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class GoogleAuthInfoTest {
    @Test
    public void testGoogleAuthInfoEmptySecret() throws GoogleAuthInfoException {
        String uri = "otpauth://totp/test:test?secret=%s&algo=SHA1&digits=6&period=30";
        GoogleAuthInfo.parseUri(String.format(uri, "AA"));
        assertThrows(GoogleAuthInfoException.class, () -> GoogleAuthInfo.parseUri(String.format(uri, "")));
    }

    @Test
    public void testYandexHostAlias() throws GoogleAuthInfoException, OtpInfoException, EncodingException {
        YandexInfo info = new YandexInfo(Base32.decode("LA2V6KMCGYMWWVEW64RNP3JA3IAAAAAAHTSG4HRZPI"), "5239");
        String uri = new GoogleAuthInfo(info, "user", "Yandex").getUri().toString();
        // Android writes yaotp, as before
        assertTrue(uri, uri.startsWith("otpauth://yaotp/"));
        assertEquals(info, GoogleAuthInfo.parseUri(uri).getOtpInfo());

        // Cue for iOS 0.1.0 wrote otpauth://yandex/, which reads as the same entry
        String iosUri = uri.replace("otpauth://yaotp/", "otpauth://yandex/");
        assertEquals(info, GoogleAuthInfo.parseUri(iosUri).getOtpInfo());
    }

    @Test
    public void testOtpInfoEmptySecret() throws OtpInfoException {
        OtpInfo info = new TotpInfo(new byte[0]);
        assertThrows(OtpInfoException.class, info::getOtp);
        info = new HotpInfo(new byte[0]);
        assertThrows(OtpInfoException.class, info::getOtp);
        info = new SteamInfo(new byte[0]);
        assertThrows(OtpInfoException.class, info::getOtp);
    }
}
