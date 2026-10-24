package com.example.timemanager.ui.home

import com.example.timemanager.data.model.AppUsage
import com.example.timemanager.data.model.InstalledApp
import org.junit.Assert.*
import org.junit.Test

class ManagedHomeUsageTest {
    @Test fun newlyManagedAppWithNoUsageRecordAppearsWithZeroUsage() {
        val app = InstalledApp("managed.new", "New app", null)
        val rows = managedHomeUsage(emptyList(), listOf(app))
        assertEquals(listOf("managed.new"), rows.map { it.packageName })
        assertEquals("New app", rows.single().appName)
        assertEquals(0L, rows.single().usageTimeMillis)
    }

    @Test fun addingToManagedSetUpdatesListEvenWhenUsageSnapshotIsUnchanged() {
        val cachedUsage = emptyList<AppUsage>()
        assertTrue(managedHomeUsage(cachedUsage, emptyList()).isEmpty())
        val added = listOf(InstalledApp("managed.new", "New app", null))
        assertEquals(1, managedHomeUsage(cachedUsage, added).size)
    }

    @Test fun existingUsageIsPreservedAndUnmanagedAppsAreExcluded() {
        val rows = managedHomeUsage(
            listOf(AppUsage("Used", "managed.used", 42_000L, null),
                AppUsage("Other", "unmanaged", 100_000L, null)),
            listOf(InstalledApp("managed.zero", "Zero", null), InstalledApp("managed.used", "Used", null)),
        )
        assertEquals(listOf("managed.used", "managed.zero"), rows.map { it.packageName })
        assertEquals(42_000L, rows.first().usageTimeMillis)
    }

    @Test fun removalChangesMembershipWithoutDeletingHistoricalUsage() {
        val history = listOf(AppUsage("Used", "managed.used", 42_000L, null))
        assertTrue(managedHomeUsage(history, emptyList()).isEmpty())
        assertEquals(42_000L, history.single().usageTimeMillis)
    }
}
