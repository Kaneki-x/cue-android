package io.cueapp.android.otp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import io.cueapp.android.encoding.Base32;
import io.cueapp.android.encoding.EncodingException;
import io.cueapp.android.encoding.Hex;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The text export (one otpauth link per line) of Cue for iOS and this app read each other's
 * mOTP and Yandex lines. The literals are the ones CueVault's tests check on the iOS side
 * (apple/Packages/CueVault/Tests/CueVaultTests/ValidationTests.swift): if either side changes
 * its format, one of the two test suites fails.
 */
@RunWith(RobolectricTestRunner.class)
public class CrossPlatformUriTest {
    private static final String MOTP = "motp:/Example%3Aalice%40example.com?secret=e3152afee62599c8&issuer=Example";
    private static final String YANDEX = "otpauth://yaotp/Work%3Aalice?period=30&digits=8&algorithm=SHA256&secret=LA2V6KMCGYMWWVEW64RNP3JA3I&pin=G42TQNQ&issuer=Work";

    @Test
    public void testMotpIsWrittenAsIosReadsIt() throws OtpInfoException, EncodingException, GoogleAuthInfoException {
        MotpInfo info = new MotpInfo(Hex.decode("e3152afee62599c8"), "1234");
        assertEquals(MOTP, new GoogleAuthInfo(info, "alice@example.com", "Example").getUri().toString());

        GoogleAuthInfo parsed = GoogleAuthInfo.parseUri(MOTP);
        assertEquals("Example", parsed.getIssuer());
        assertEquals("alice@example.com", parsed.getAccountName());
        assertArrayEquals(info.getSecret(), parsed.getOtpInfo().getSecret());
    }

    @Test
    public void testYandexIsWrittenAsIosReadsIt() throws OtpInfoException, EncodingException, GoogleAuthInfoException {
        YandexInfo info = new YandexInfo(Base32.decode("LA2V6KMCGYMWWVEW64RNP3JA3I"), "7586");
        assertEquals(YANDEX, new GoogleAuthInfo(info, "alice", "Work").getUri().toString());

        GoogleAuthInfo parsed = GoogleAuthInfo.parseUri(YANDEX);
        assertEquals(info, parsed.getOtpInfo());
        assertEquals("Work", parsed.getIssuer());
    }
}
