package com.bliss.native_smoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import java.util.regex.Pattern;
import org.junit.Test;

/** Host removes/restores only this emulator's API reverse mapping between methods. */
public class NativeConnectivityTest extends NativeDemoDriver {
    private void initialize() {
        assumeTrue("Explicit dedicated-emulator opt-in required", "connectivity".equals(
                InstrumentationRegistry.getArguments().getString("blissRun")));
        assumeTrue("Use a dedicated emulator", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"));
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @Test
    public void unavailable() throws Exception {
        initialize();
        restartBliss();
        element(By.descContains("Let’s try that again"), "Catalog connection error");
        label("Try again");
        screenshot("android-api-unavailable.png");
        checkpoint("Native catalog reports connection failure");
    }

    @Test
    public void recover() {
        initialize();
        assertEquals("Keep the same failed app screen", APP, device.getCurrentPackageName());
        element(By.descContains("Let’s try that again"), "Catalog connection error");
        label("Try again").click();
        assertTrue("Catalog did not recover real offering data", device.wait(Until.hasObject(
                By.desc(Pattern.compile("(?s).*(CAD|USD|EUR|GBP) [0-9]+\\.[0-9]{2}.*"))), TIMEOUT));
        assertTrue("Failure remained after successful retry", !device.hasObject(By.descContains("Let’s try that again")));
        screenshot("android-api-recovered.png");
        checkpoint("Native catalog recovered through Try again without app restart");
    }
}
