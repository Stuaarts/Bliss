package com.bliss.native_smoke;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import org.json.JSONObject;
import org.junit.Test;

/** Separate driver process: stopping Bliss must also kill its Flutter engine. */
public class NativeOidcTest extends NativeDemoDriver {

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

}
