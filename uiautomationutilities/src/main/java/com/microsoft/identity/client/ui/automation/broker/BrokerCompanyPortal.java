package com.microsoft.identity.client.ui.automation.broker;

import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import com.microsoft.identity.client.ui.automation.app.App;

import org.junit.Assert;

import lombok.Getter;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.microsoft.identity.client.ui.automation.utils.CommonUtils.TIMEOUT;
import static com.microsoft.identity.client.ui.automation.utils.CommonUtils.getResourceId;

@Getter
public class BrokerCompanyPortal extends App implements ITestBroker {

    public final static String COMPANY_PORTAL_APP_PACKAGE_NAME = "com.microsoft.windowsintune.companyportal";
    public final static String COMPANY_PORTAL_APP_NAME = "Intune Company Portal";

    public BrokerCompanyPortal() {
        super(COMPANY_PORTAL_APP_PACKAGE_NAME, COMPANY_PORTAL_APP_NAME);
    }

    @Override
    public void performDeviceRegistration(String username, String password) {
        //TODO implement device registration for CP
    }

    @Override
    public void performSharedDeviceRegistration(String username, String password) {
        //TODO implement shared device registration for CP
    }

    @Override
    public void handleFirstRun() {
        //TODO handle first run for CP
    }

    @Override
    public void handleAccountPicker(final String username) {
        UiDevice device = UiDevice.getInstance(getInstrumentation());

        // find the object associated to this username in account picker
        UiObject accountSelected = device.findObject(new UiSelector().resourceId(
                getResourceId(COMPANY_PORTAL_APP_PACKAGE_NAME, "account_chooser_listView")
        ).childSelector(new UiSelector().textContains(
                username
        )));

        try {
            accountSelected.waitForExists(TIMEOUT);
            accountSelected.click();
        } catch (UiObjectNotFoundException e) {
            Assert.fail(e.getMessage());
        }
    }

}
