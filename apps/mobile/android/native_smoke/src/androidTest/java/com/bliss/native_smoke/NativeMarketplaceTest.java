package com.bliss.native_smoke;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.Build;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;
import org.json.JSONObject;
import org.junit.Test;

/** Real UI mutations only: no API shortcuts or database state changes. */
public class NativeMarketplaceTest extends NativeDemoDriver {
    @Test
    public void requestQuoteBookingChatAndReview() throws Exception {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Explicit emulator-only opt-in required", "marketplace".equals(args.getString("blissRun")));
        assumeTrue("Use a dedicated emulator", Build.FINGERPRINT.contains("generic")
                || Build.MODEL.contains("sdk_gphone"));
        String runId = args.getString("runId");
        assertTrue("Supply a unique local run ID", runId != null && runId.matches("[a-z0-9-]{1,40}"));
        String title = "Android garden " + runId;
        String phase = args.getString("marketplacePhase", "all");
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        JSONObject accounts = new JSONObject(device.executeShellCommand("cat /data/local/tmp/bliss-native-auth.json"));
        JSONObject customer = accounts.getJSONObject("customer");
        JSONObject provider = accounts.getJSONObject("provider");
        device.executeShellCommand("am force-stop com.android.chrome");
        try {
            if (phase.equals("all") || phase.equals("request")) {
                account(customer);
                label("Your activity").click();
                label("My requests").click();
                button("Create request");
                field("Request title", title);
                field("Describe the work", "Local Android demo: tidy the garden and remove leaves.");
                field("Service city", "Toronto");
                visible("Category").click();
                label("Gardening").click();
                field("Budget (optional)", "95.50");
                button("Create request");
                label("Your request");
                label(title);
                screenshot("android-marketplace-request.png");
                checkpoint("Native customer created request: " + title);
            }
            if (phase.equals("all") || phase.equals("quote")) {
                account(provider);
                label("Your activity").click();
                label("Open opportunities").click();
                item(title).click();
                button("Send quote");
                field("Scope of work", "Garden tidy and leaf removal for " + runId);
                field("Price (CAD)", "85.50");
                button("Send quote");
                label("Quote sent. You can follow it in My quotes.");
                checkpoint("Native provider submitted quote");
            }
            if (phase.equals("all") || phase.equals("accept")) {
                account(customer);
                label("Your activity").click();
                label("My requests").click();
                item(title).click();
                confirm("Accept quote");
                label("Booking");
                label(title);
                button("Messages");
                field("Message", "Customer ready for the garden visit " + runId);
                button("Send");
                containing("Customer ready for the garden visit " + runId);
                checkpoint("Native customer accepted quote and sent booking message");
            }
            if (phase.equals("all") || phase.equals("fulfil")) {
                account(provider);
                openBooking(title);
                button("Messages");
                containing("Customer ready for the garden visit " + runId);
                field("Message", "Provider confirmed the garden visit " + runId);
                button("Send");
                containing("Provider confirmed the garden visit " + runId);
                screenshot("android-marketplace-chat.png");
                device.pressBack();
                button("Accept and schedule");
                label("Schedule service");
                button("Accept and schedule");
                checkpoint("Native provider scheduled service; waiting for real scheduled time");
                // The UI defaults to two minutes ahead. Respect that real schedule.
                long deadline = SystemClock.uptimeMillis() + 150000;
                UiObject2 start = null;
                while (SystemClock.uptimeMillis() < deadline) {
                    start = device.findObject(By.desc("Start service").clickable(true).enabled(true));
                    if (start != null) break;
                    SystemClock.sleep(1000);
                }
                assertNotNull("Start service did not become available at scheduled time", start);
                confirm("Start service");
                confirm("Mark work complete");
                label("Waiting for customer");
                screenshot("android-marketplace-provider-complete.png");
                checkpoint("Native provider started and completed scheduled work");
            }
            if (phase.equals("all") || phase.equals("review")) {
                account(customer);
                openBooking(title);
                confirm("Confirm completion");
                visible("Captured · CAD 85.50");
                screenshot("android-marketplace-completed.png");
                checkpoint("Native customer confirmed completion and simulated capture");
                restartBliss();
                label("Your account").click();
                assertAccount(customer);
                openBooking(title);
                visible("Captured · CAD 85.50");
                button("Messages");
                containing("Customer ready for the garden visit " + runId);
                containing("Provider confirmed the garden visit " + runId);
                device.pressBack();
                button("Booking review");
                visible("Rating").click();
                label("5 stars").click();
                field("Comment (optional)", "Android demo: careful garden work and clear communication.");
                button("Publish review");
                label("Review published");
                device.pressBack();
                button("Booking review");
                label("Review published");
                containing("5 / 5 · Verified customer");
                containing("Android demo: careful garden work and clear communication.");
                screenshot("android-marketplace-review.png");
                device.pressHome();
                device.executeShellCommand("am start -W -n " + APP + "/.MainActivity");
                label("Review published");
                checkpoint("Native completed payment, persisted chat/review and background resume");
                restartBliss();
                label("Your account").click();
                assertAccount(customer);
                logout();
                checkpoint("Native marketplace lifecycle finished; signed out");
            }
        } catch (Throwable failure) {
            // Never capture the browser credential form on failure.
            if (APP.equals(device.getCurrentPackageName())) {
                device.dumpWindowHierarchy(report("marketplace-failure.xml"));
                screenshot("marketplace-failure.png");
            }
            throw failure;
        }
    }

    private void account(JSONObject wanted) throws Exception {
        restartBliss();
        label("Your account").click();
        device.wait(Until.hasObject(By.desc("Sign in")), 3000);
        if (findLabel("Sign out") != null) logout();
        signIn(wanted);
    }

    private void openBooking(String title) throws Exception {
        label("Your activity").click();
        item(title).click();
        label("Booking");
        label(title);
    }

    private UiObject2 containing(String value) {
        UiObject2 found = device.wait(Until.findObject(By.descContains(value)), TIMEOUT);
        if (found == null) found = device.wait(Until.findObject(By.textContains(value)), 1000);
        assertNotNull("Missing expected content: " + value, found);
        return found;
    }

    private UiObject2 visible(String value) throws Exception {
        if (findLabel(value) == null) {
            new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().description(value));
        }
        return label(value);
    }

    private UiObject2 item(String title) throws Exception {
        device.wait(Until.hasObject(By.descContains(title)), TIMEOUT);
        if (!device.hasObject(By.descContains(title))) {
            new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().descriptionContains(title));
        }
        return containing(title);
    }

    private void button(String value) throws Exception {
        visible(value);
        UiObject2 control = device.wait(Until.findObject(By.desc(value).clickable(true).enabled(true)), TIMEOUT);
        assertNotNull("Missing enabled button: " + value, control);
        control.click();
    }

    private void confirm(String value) throws Exception {
        button(value);
        label("Go back");
        button(value);
    }

    private void field(String label, String value) throws Exception {
        UiObject2 input = device.wait(Until.findObject(By.clazz("android.widget.EditText").hint(label)), TIMEOUT);
        for (int attempt = 0; input == null && attempt < 4; attempt++) {
            new UiScrollable(new UiSelector().scrollable(true)).scrollForward();
            input = device.findObject(By.clazz("android.widget.EditText").hint(label));
        }
        assertNotNull("Missing field: " + label, input);
        input.click();
        UiObject2 focused = device.wait(Until.findObject(By.clazz("android.widget.EditText").focused(true)), TIMEOUT);
        assertNotNull("Field did not receive focus: " + label, focused);
        focused.setText(value);
        assertTrue("Field value did not update: " + label,
                device.wait(Until.hasObject(By.clazz("android.widget.EditText").text(value)), TIMEOUT));
        device.waitForIdle();
        if (device.executeShellCommand("dumpsys input_method").contains("mInputShown=true")) device.pressBack();
    }
}
