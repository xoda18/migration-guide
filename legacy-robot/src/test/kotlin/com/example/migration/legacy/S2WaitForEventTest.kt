package com.example.migration.legacy

import com.example.migration.legacy.pages.DialogFixture
import com.example.migration.legacy.pages.dialog
import com.example.migration.legacy.pages.idea
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import com.intellij.remoterobot.utils.waitForIgnoringError
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * S2. Wait for an event instead of sleeping. Same idea as the Driver side, except every wait
 * has to be written out: there is no shouldBe(), and no handle on the dialog to check again.
 */
class S2WaitForEventTest : LegacyScenarioTest() {

  @Test
  fun waitForEventInsteadOfSleep(remoteRobot: RemoteRobot) = with(remoteRobot) {
    openSampleProject(this)

    // 1. Invoke by action id. Not through CommonSteps: its invokeAction() passes a null context
    // component and hardcodes now = true, and it drops the ActionCallback, so nothing happens
    // and nothing is reported. The working version is another hand-written JavaScript string,
    // in pages/IdeaFrame.kt.
    idea {
      bringToFront()
      invokeAction("SearchEverywhere")
    }

    // Two class names: Search Everywhere is either the old SearchEverywhereUI or the new
    // SePopupContentPane depending on the build. Driver hides this behind searchEverywherePopup.
    val popupLocator = byXpath(
      "SearchEverywhere popup",
      "//div[@class='SearchEverywhereUI' or @class='SePopupContentPane']"
    )

    // The call above reports nothing when it opens no popup, so the only way to find out is to
    // look, and the only way to recover is to ask again. A run on CI produced no popup at all.
    // Driver reads the ActionCallback that invokeAction() returns and fails on the spot.
    waitForIgnoringError(
      Duration.ofSeconds(60),
      description = "the Search Everywhere popup",
      errorMessage = "the Search Everywhere popup never opened"
    ) {
      if (findAll<CommonContainerFixture>(popupLocator).isEmpty()) {
        idea { invokeAction("SearchEverywhere") }
      }
      findAll<CommonContainerFixture>(popupLocator).isNotEmpty()
    }
    val popup = find<CommonContainerFixture>(popupLocator, Duration.ofSeconds(10))

    // 2. Set the text on the component instead of typing it.
    val searchField = popup.find<ComponentFixture>(byXpath("SearchField", "//div[@class='SearchField']"))
    searchField.click()
    searchField.runJs("component.setText('Plugins')", true)

    // 3. Wait for the result list.
    val results = popup.find<ComponentFixture>(byXpath("results", "//div[@class='JBList']"), Duration.ofSeconds(30))
    waitFor(Duration.ofSeconds(30), description = "Search Everywhere results are shown") {
      results.findAllText().any { it.text.contains("Plugins") }
    }

    // 4. Click the result by text, not by position.
    results.findText("Plugins").click()

    // 5. Close with a button.
    dialog("Settings", Duration.ofSeconds(60)) { button("Cancel").click() }

    // 6. It really closed.
    waitFor(Duration.ofSeconds(20), description = "the Settings dialog to close") {
      findAll<ComponentFixture>(DialogFixture.byTitle("Settings")).isEmpty()
    }
  }
}
