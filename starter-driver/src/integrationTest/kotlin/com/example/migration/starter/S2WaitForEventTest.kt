package com.example.migration.starter

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.popups.SearchEverywherePopupUI.SearchEverywhereTab
import com.intellij.driver.sdk.ui.components.common.popups.searchEverywherePopup
import com.intellij.driver.sdk.ui.components.settings.settingsDialog
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/** S2. Wait for an event instead of sleeping. */
class S2WaitForEventTest : StarterScenarioTest() {

  @Test
  fun waitForEventInsteadOfSleep() {
    context().runIdeWithDriver().useDriverAndCloseIde {
      waitForIndicators(15.minutes)

      // 1. Invoke by action id, not by keyboard shortcut.
      invokeAction("SearchEverywhere", now = false)

      val query = "Plugins"
      ui.searchEverywherePopup {
        // 2. Switch to the Actions tab. On the All tab this query also matches the contents of
        // files, and such a result is clickable exactly like the action, so the ambiguity is
        // removed here instead of being worked around in step 5.
        selectTab("Actions")
        shouldBe("the Actions tab is selected") {
          searchEverywhereUi.getSelectedTabID() == SearchEverywhereTab.Actions.id
        }

        // 3. Type the query.
        searchField.text = query

        // 4. Wait for the entry to appear. No sleep, no assertion on result count or order.
        resultsList.shouldBe("'$query' is among the Search Everywhere results") {
          items.any { it.contains(query) }
        }

        // 5. Click the result by text, not by position.
        resultsList.clickItem(query, fullMatch = false)
      }

      // 6. Close the dialog with a button.
      ideFrame {
        settingsDialog {
          waitFound()
          cancelButton.click()

          // 7. It really closed.
          shouldBe("The Settings dialog is closed") { notPresent() }
        }
      }
    }
  }
}
