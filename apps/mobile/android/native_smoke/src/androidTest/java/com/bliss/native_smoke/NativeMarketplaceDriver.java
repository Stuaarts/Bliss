package com.bliss.native_smoke;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;
import org.json.JSONObject;

/** Shared native controls for marketplace forms and account switching. */
public abstract class NativeMarketplaceDriver extends NativeDemoDriver {
    protected void account(JSONObject wanted) throws Exception {
        restartBliss();
        label("Your account").click();
        device.wait(Until.hasObject(By.desc("Sign in")), 3000);
        if (findLabel("Sign out") != null) logout();
        signIn(wanted);
    }

    protected void openBooking(String title) throws Exception {
        label("Your activity").click();
        item(title).click();
        label("Booking");
        label(title);
    }

    protected UiObject2 containing(String value) {
        UiObject2 found = device.wait(Until.findObject(By.descContains(value)), TIMEOUT);
        if (found == null) found = device.wait(Until.findObject(By.textContains(value)), 1000);
        assertNotNull("Missing expected content: " + value, found);
        return found;
    }

    protected UiObject2 visible(String value) throws Exception {
        if (findLabel(value) == null) {
            new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().description(value));
        }
        return label(value);
    }

    protected UiObject2 item(String title) throws Exception {
        device.wait(Until.hasObject(By.descContains(title)), TIMEOUT);
        if (!device.hasObject(By.descContains(title))) {
            new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().descriptionContains(title));
        }
        return containing(title);
    }

    protected void button(String value) throws Exception {
        visible(value);
        UiObject2 control = device.wait(Until.findObject(By.desc(value).clickable(true).enabled(true)), TIMEOUT);
        assertNotNull("Missing enabled button: " + value, control);
        control.click();
    }

    protected void confirm(String value) throws Exception {
        button(value);
        label("Go back");
        button(value);
    }

    protected void field(String label, String value) throws Exception {
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
