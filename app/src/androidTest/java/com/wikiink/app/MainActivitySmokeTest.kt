package com.wikiink.app

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Launch, rotate (configuration change) and background/foreground without crashing. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @Test
    fun survivesRecreateAndBackgrounding() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(isRoot()).check(matches(isDisplayed()))
            scenario.recreate() // same path as a rotation
            onView(isRoot()).check(matches(isDisplayed()))
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
