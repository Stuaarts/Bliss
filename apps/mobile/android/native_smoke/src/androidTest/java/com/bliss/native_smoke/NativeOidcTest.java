package com.bliss.native_smoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.Build;
import android.os.Bundle;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.File;
import org.json.JSONObject;
import org.junit.Test;

/** Separate driver process: stopping Bliss must also kill its Flutter engine. */
public class NativeOidcTest {
    private static final String APP = "com.bliss.bliss_mobile";
    private static final long TIMEOUT = 30000;
    private UiDevice device;

    @Test
    public void nativeOidcLifecycle() throws Exception {
        String mode = InstrumentationRegistry.getArguments().getString("blissRun", "disabled");
        assumeTrue("Explicit emulator-only opt-in required",
                mode.equals("inspect") || mode.equals("local-demo"));
        assumeTrue("Use a dedicated emulator", Build.FINGERPRINT.contains("generic")
                || Build.MODEL.contains("sdk_gphone"));
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        device.executeShellCommand("am force-stop com.android.chrome");
        restartBliss();
        label("Your account").click();

        if (mode.equals("inspect")) {
            beginSignIn();
            device.dumpWindowHierarchy(report("before-login.xml"));
            return;
        }

        // The host runner supplies only generated local demo identities, removes
        // the file in finally, and never passes credentials in shell arguments.
        JSONObject accounts = new JSONObject(device.executeShellCommand(
                "cat /data/local/tmp/bliss-native-auth.json"));
        JSONObject customer = accounts.getJSONObject("customer");
        JSONObject provider = accounts.getJSONObject("provider");
        if (findLabel("Sign out") != null) logout();

        signIn(customer);
        assertTrue("Customer must not have provider management",
                findLabel("Manage your business") == null);
        screenshot("android-customer-signed-in.png");
        checkpoint("Customer browser PKCE callback and authenticated profile");

        restartBliss();
        label("Your account").click();
        assertAccount(customer);
        screenshot("android-customer-restored.png");
        checkpoint("Customer session restored after force-stop without browser login");

        logout();
        restartBliss();
        label("Your account").click();
        label("Sign in");
        screenshot("android-signed-out.png");
        checkpoint("Local logout survives process restart");

        // Requiring the credential form also proves the previous browser SSO
        // session ended; silently signing back in as the customer must fail.
        signIn(provider);
        label("Manage your business");
        screenshot("android-provider-signed-in.png");
        checkpoint("Provider login requires fresh credentials and correct capabilities");

        restartBliss();
        label("Your account").click();
        assertAccount(provider);
        label("Manage your business");
        checkpoint("Provider refresh-token restoration");
        logout();

        signIn(customer);
        assertTrue("Provider controls leaked after account switch",
                findLabel("Manage your business") == null);
        checkpoint("Switch back to customer after identity-provider logout");
        logout();
        restartBliss();
        label("Your account").click();
        label("Sign in");
    }

    private void restartBliss() throws Exception {
        device.executeShellCommand("am force-stop " + APP);
        String stopped = device.executeShellCommand("pidof " + APP).trim();
        assertTrue("Bliss process was not stopped", stopped.isEmpty());
        String launch = device.executeShellCommand(
                "am start -W -f 0x10008000 -n " + APP + "/.MainActivity");
        assertTrue("Bliss did not launch", launch.contains("Status: ok"));
        label("Your account");
    }

    private void beginSignIn() {
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

    private void signIn(JSONObject account) throws Exception {
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

    private void assertAccount(JSONObject account) throws Exception {
        label("Hello, " + account.getString("displayName"));
        label("Sign out");
        label("customer");
    }

    private void logout() {
        label("Sign out").click();
        label("Sign in");
        assertTrue("Private profile remained after logout", findLabel("Sign out") == null);
    }

    private UiObject2 findLabel(String value) {
        UiObject2 found = device.findObject(By.desc(value));
        return found != null ? found : device.findObject(By.text(value));
    }

    private UiObject2 label(String value) {
        UiObject2 found = device.wait(Until.findObject(By.desc(value)), TIMEOUT);
        if (found == null) found = device.wait(Until.findObject(By.text(value)), 1000);
        assertNotNull("Missing expected control: " + value, found);
        return found;
    }

    private UiObject2 element(BySelector selector, String description) {
        UiObject2 found = device.wait(Until.findObject(selector), TIMEOUT);
        assertNotNull("Missing expected control: " + description, found);
        return found;
    }

    private File report(String name) {
        return new File(InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir(null), name);
    }

    private void screenshot(String name) {
        assertTrue("Screenshot failed", device.takeScreenshot(report(name)));
    }

    private void checkpoint(String description) {
        Bundle status = new Bundle();
        status.putString("stream", "Verified: " + description + "\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(2, status);
    }
}
