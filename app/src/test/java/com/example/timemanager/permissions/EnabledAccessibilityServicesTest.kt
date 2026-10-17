package com.example.timemanager.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnabledAccessibilityServicesTest {
    private val service = "com.example.timemanager.service.TimeManagerAccessibilityService"
    private val dev = "com.example.timemanager.debug"
    private val stable = "com.example.timemanager"

    @Test fun enabledDevUsesItsOwnPackageAndTheSharedClassName() {
        assertTrue(isAccessibilityComponentEnabled("$dev/$service", dev, service))
        assertFalse(isAccessibilityComponentEnabled("$stable/$service", dev, service))
    }

    @Test fun disabledOrDifferentServicesDoNotGrantAccess() {
        assertFalse(isAccessibilityComponentEnabled(null, dev, service))
        assertFalse(isAccessibilityComponentEnabled("", dev, service))
        assertFalse(isAccessibilityComponentEnabled("$dev/other.Service", dev, service))
    }

    @Test fun listAndShortComponentNamesAreSupported() {
        assertTrue(isAccessibilityComponentEnabled("other/other.Service:$dev/$service", dev, service))
        assertTrue(isAccessibilityComponentEnabled("$stable/.service.TimeManagerAccessibilityService", stable, service))
    }
}
