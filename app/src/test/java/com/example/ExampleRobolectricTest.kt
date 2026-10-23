package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ActionType
import com.example.data.model.AutomatedActionStep
import com.example.data.repository.AppSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AgentFlow", appName)
    }

    @Test
    fun `verify default voice routines`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AppSettingsRepository(context)
        val defaultRoutines = repo.getDefaultPresetRoutines()
        assertTrue("Preset routines should not be empty", defaultRoutines.isNotEmpty())
        assertTrue("Should include morning routine", defaultRoutines.any { it.id == "preset-morning" })
    }

    @Test
    fun `verify automated action step data model`() {
        val step = AutomatedActionStep(
            actionType = ActionType.OPEN_APP,
            description = "Launch YouTube",
            packageName = "com.google.android.youtube",
            appName = "YouTube"
        )
        assertEquals(ActionType.OPEN_APP, step.actionType)
        assertEquals("com.google.android.youtube", step.packageName)
        assertNotNull(step.id)
    }
}
