package com.bliss.native_smoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.File;
import org.json.JSONObject;

/** Shared controls for the dedicated local Android demo emulator. */
public abstract class NativeDemoDriver {
    protected static final String APP = "com.bliss.bliss_mobile";
    protected static final long TIMEOUT = 30000;
    protected UiDevice device;

    protected void restartBliss() throws Exception {
        device.executeShellCommand("am force-stop " + APP);
        String stopped = device.executeShellCommand("pidof " + APP).trim();
        assertTrue("Bliss process was not stopped", stopped.isEmpty());
        String launch = device.executeShellCommand(
                "am start -W -f 0x10008000 -n " + APP + "/.MainActivity");
        assertTrue("Bliss did not launch", launch.contains("Status: ok"));
        label("Your account");
    }

    protected void beginSignIn() {
        label("Sign in").click();
        assertTrue("Browser did not open", device.wait(
                Until.hasObject(By.pkg("com.android.chrome")), TIMEOUT));
        UiObject2 welcome = device.wait(Until.findObject(By.res(
                "com.android.chrome:id/signin_fre_dismiss_button")), 3000);
        if (welcome != null) {
            welcome.click();
            assertTrue("Chrome setup did not finish", device.wait(Until.gone(By.res(
                    "com.android.chrome:id/signin_fre_dismiss_button")), TIMEOUT));
        }
        element(By.res("username"), "Local login username field");
        assertEquals("Refuse credentials outside local Keycloak", "localhost:8180",
                element(By.res("com.android.chrome:id/url_bar"), "Local issuer URL").getText());
        assertEquals("Refuse credentials outside Bliss realm", "BLISS",
                element(By.res("kc-header-wrapper"), "Bliss realm header").getText());
    }

    protected void signIn(JSONObject account) throws Exception {
        beginSignIn();
        element(By.res("username"), "Username").setText(account.getString("username"));
        element(By.res("password"), "Password").setText(account.getString("password"));
        device.waitForIdle();
        if (device.executeShellCommand("dumpsys input_method").contains("mInputShown=true")) {
            device.pressBack();
            device.wait(Until.gone(By.pkg("com.google.android.inputmethod.latin")), TIMEOUT);
        }
        element(By.res("kc-login"), "Local login submit").click();
        assertAccount(account);
    }

    protected void assertAccount(JSONObject account) throws Exception {
        label("Hello, " + account.getString("displayName"));
        label("Sign out");
        label("customer");
    }

    protected void logout() {
        label("Sign out").click();
        label("Sign in");
        assertTrue("Private profile remained after logout", findLabel("Sign out") == null);
    }

    protected UiObject2 findLabel(String value) {
        UiObject2 found = device.findObject(By.desc(value));
        return found != null ? found : device.findObject(By.text(value));
    }

    protected UiObject2 label(String value) {
        UiObject2 found = device.wait(Until.findObject(By.desc(value)), TIMEOUT);
        if (found == null) found = device.wait(Until.findObject(By.text(value)), 1000);
        assertNotNull("Missing expected control: " + value, found);
        return found;
    }

    protected UiObject2 element(BySelector selector, String description) {
        UiObject2 found = device.wait(Until.findObject(selector), TIMEOUT);
        assertNotNull("Missing expected control: " + description, found);
        return found;
    }

    protected File report(String name) {
        return new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir(null), name);
    }

    protected void screenshot(String name) {
        assertTrue("Screenshot failed", device.takeScreenshot(report(name)));
    }

    protected void checkpoint(String description) {
        Bundle status = new Bundle();
        status.putString("stream", "Verified: " + description + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(2, status);
    }
}
