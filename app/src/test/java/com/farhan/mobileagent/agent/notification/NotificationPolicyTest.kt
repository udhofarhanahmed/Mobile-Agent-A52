package com.farhan.mobileagent.agent.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {
    @Test fun ownNotificationsAreIgnored() {
        assertFalse(
            NotificationPolicy.shouldObserve(
                sourcePackage = "com.farhan.mobileagent",
                ownPackage = "com.farhan.mobileagent",
                isOngoing = false
            )
        )
    }

    @Test fun ongoingNotificationsAreIgnoredByDefault() {
        assertFalse(
            NotificationPolicy.shouldObserve(
                sourcePackage = "com.example.other",
                ownPackage = "com.farhan.mobileagent",
                isOngoing = true
            )
        )
    }

    @Test fun normalExternalNotificationIsObserved() {
        assertTrue(
            NotificationPolicy.shouldObserve(
                sourcePackage = "com.whatsapp",
                ownPackage = "com.farhan.mobileagent",
                isOngoing = false
            )
        )
    }
}
