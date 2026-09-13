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

    // 1. Invoke by action id. Not through CommonSteps, which cannot open a popup and says
    // nothing when it fails. See invokeAction() in pages/IdeaFrame.kt.
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

    // Nothing reports a popup that never opened, so the only way to recover is to ask again.
    // Driver reads the ActionCallback and fails on the spot.
    waitForIgnoringError(
      Duration.ofSeconds(180),
      description = "the Search Everywhere popup",
      errorMessage = "the Search Everywhere popup never opened"
    ) {
      if (findAll<CommonContainerFixture>(popupLocator).isEmpty()) {
        idea { invokeAction("SearchEverywhere") }
      }
      findAll<CommonContainerFixture>(popupLocator).isNotEmpty()
    }
    val popup = find<CommonContainerFixture>(popupLocator, Duration.ofSeconds(30))

    // 2. Switch to the Actions tab.
    popup.findText("Actions").click()
    waitForIgnoringError(
      Duration.ofSeconds(90),
      description = "the Actions tab to be selected",
      errorMessage = "Search Everywhere stayed on another tab"
    ) {
      popup.callJs<String>("component.getSelectedTabID()", true) == "ActionSearchEverywhereContributor"
    }

    // 3. Set the text on the component instead of typing it.
    val searchField = popup.find<ComponentFixture>(byXpath("SearchField", "//div[@class='SearchField']"))
    searchField.click()
    searchField.runJs("component.setText('Plugins')", true)

    // 4. Wait for the result list.
    val results = popup.find<ComponentFixture>(byXpath("results", "//div[@class='JBList']"), Duration.ofSeconds(90))
    waitFor(Duration.ofSeconds(90), description = "Search Everywhere results are shown") {
      results.findAllText().any { it.text.contains("Plugins") }
    }

    // 5. Click the result by text, not by position.
    results.findText("Plugins").click()

    // 6. Close with a button.
    dialog("Settings", Duration.ofSeconds(180)) { button("Cancel").click() }

    // 7. It really closed.
    waitFor(Duration.ofSeconds(60), description = "the Settings dialog to close") {
      findAll<ComponentFixture>(DialogFixture.byTitle("Settings")).isEmpty()
    }
  }
}
