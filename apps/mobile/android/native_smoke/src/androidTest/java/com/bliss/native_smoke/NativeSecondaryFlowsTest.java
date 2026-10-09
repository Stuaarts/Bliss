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
import org.json.JSONObject;
import org.junit.Test;

/** Direct booking and manual dispute verification, using only native UI mutations. */
public class NativeSecondaryFlowsTest extends NativeMarketplaceDriver {
    @Test
    public void directCancellationAndManualDispute() throws Exception {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Explicit dedicated-emulator opt-in required", "secondary".equals(args.getString("blissRun")));
        assumeTrue("Use a dedicated emulator", Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"));
        String runId = args.getString("runId");
        assertTrue("Supply a unique run ID", runId != null && runId.matches("[a-z0-9-]{1,40}"));
        String phase = args.getString("secondaryPhase", "all");
        String cancelledCity = "Demo " + runId + " cancel";
        String disputeCity = "Demo " + runId + " dispute";
        String reason = "Local demo: unfinished garden cleanup for " + runId;
        String evidence = "Provider demo response: cleanup details for " + runId;
        String rationale = "Local demo decision: customer favored after reviewing both accounts for " + runId;
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        JSONObject fixture = new JSONObject(device.executeShellCommand("cat /data/local/tmp/bliss-native-auth.json"));
        JSONObject customer = fixture.getJSONObject("customer"), provider = fixture.getJSONObject("provider");
        JSONObject offering = fixture.getJSONObject("offering");
        String title = offering.getString("title");
        String amount = offering.getString("currency") + " " + offering.getString("price");
        device.executeShellCommand("am force-stop com.android.chrome");
        try {
            if (phase.equals("all") || phase.equals("cancel")) {
                account(customer);
                directBooking(offering, cancelledCity);
                label("Waiting for provider");
                confirm("Cancel booking");
                label("Cancelled");
                visible("Voided · " + amount);
                screenshot("android-direct-cancelled.png");
                restartBliss();
                label("Your account").click();
                assertAccount(customer);
                booking(title, cancelledCity);
                visible("Voided · " + amount);
                checkpoint("Native direct booking cancellation persists with simulated void");
            }
            if (phase.equals("all") || phase.equals("create")) {
                account(customer);
                directBooking(offering, disputeCity);
                label("Waiting for provider");
                checkpoint("Native direct booking created for dispute lifecycle");
            }
            if (phase.equals("all") || phase.equals("fulfil")) {
                account(provider);
                booking(title, disputeCity);
                button("Accept and schedule");
                label("Schedule service");
                button("Accept and schedule");
                checkpoint("Native direct service scheduled; waiting for actual start time");
                long deadline = SystemClock.uptimeMillis() + 150000;
                UiObject2 start = null;
                while (SystemClock.uptimeMillis() < deadline) {
                    start = device.findObject(By.desc("Start service").clickable(true).enabled(true));
                    if (start != null) break;
                    SystemClock.sleep(1000);
                }
                assertNotNull("Scheduled service never became available", start);
                confirm("Start service");
                confirm("Mark work complete");
                label("Waiting for customer");
                checkpoint("Native direct service marked complete");
            }
            if (phase.equals("all") || phase.equals("file")) {
                account(customer);
                booking(title, disputeCity);
                button("Dispute / support");
                field("What happened?", reason);
                button("Open dispute");
                label("Collecting evidence");
                containing(reason);
                assertTrue("Customer must not decide the dispute", findLabel("Resolve dispute") == null);
                screenshot("android-dispute-opened.png");
                checkpoint("Native customer filed dispute and settlement is paused");
            }
            if (phase.equals("all") || phase.equals("evidence")) {
                account(provider);
                booking(title, disputeCity);
                button("Dispute / support");
                containing(reason);
                button("Add evidence");
                field("Evidence", evidence);
                button("Add evidence");
                label("Waiting for admin review");
                assertTrue("Provider must not decide the dispute", findLabel("Resolve dispute") == null);
                screenshot("android-dispute-provider.png");
                checkpoint("Native provider evidence advances case to independent admin review");
            }
            if (phase.equals("all") || phase.equals("resolve")) {
                account(fixture.getJSONObject("admin"));
                label("admin");
                label("Your activity").click();
                label("Disputes").click();
                item(title).click();
                // The admin list is newest first. Verify this exact case before deciding.
                containing(reason);
                item(evidence);
                button("Resolve dispute");
                label("Outcome").click();
                label("Customer favored").click();
                field("Decision rationale", rationale);
                button("Resolve dispute");
                label("Decision applied.");
                containing("Decision: customer favored");
                screenshot("android-dispute-resolved.png");
                checkpoint("Native independent admin decision applied");
            }
            if (phase.equals("all") || phase.equals("check")) {
                account(customer);
                booking(title, disputeCity);
                visible("Voided · " + amount);
                screenshot("android-dispute-voided.png");
                button("Dispute / support");
                containing(reason);
                label("Decision applied.");
                containing(rationale);
                device.pressHome();
                device.executeShellCommand("am start -W -n " + APP + "/.MainActivity");
                label("Decision applied.");
                restartBliss();
                label("Your account").click();
                assertAccount(customer);
                booking(title, disputeCity);
                button("Dispute / support");
                label("Decision applied.");
                restartBliss();
                label("Your account").click();
                assertAccount(customer);
                logout();
                checkpoint("Native secondary flows finished; decision persisted and signed out");
            }
        } catch (Throwable failure) {
            if (APP.equals(device.getCurrentPackageName())) {
                device.dumpWindowHierarchy(report("secondary-failure.xml"));
                screenshot("secondary-failure.png");
            }
            throw failure;
        }
    }

    private void directBooking(JSONObject offering, String city) throws Exception {
        device.pressBack(); // Account -> public catalog.
        label("All services");
        label(offering.getString("category")).click();
        item(offering.getString("title")).click();
        button("Request booking");
        field("Service city", city);
        button("Request booking");
        label("Booking");
        label(offering.getString("title"));
    }

    private void booking(String title, String city) throws Exception {
        label("Your activity").click();
        item(city).click();
        label("Booking");
        label(title);
        containing(city);
    }
}
