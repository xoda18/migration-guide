package com.example.migration.starter

import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.PopupItemUiComponent
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.driver.sdk.waitForOne
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * S3. Pick an entry that only a predicate can identify.
 *
 * The editor context menu holds two entries with the word Paste, the leaf Paste and the submenu
 * Copy / Paste Special, and only the component class separates them. Exact labels are no help
 * either: Rename… ends in a real ellipsis character and Run 'Main.main()' is built at runtime.
 */
class S3PredicateOnlyTest : StarterScenarioTest() {

  @Test
  fun pickEntryOnlyAPredicateCanIdentify() {
    context().runIdeWithDriver().useDriverAndCloseIde {
      waitForIndicators(5.minutes)
      openFile("src/Main.java")

      // 1. Open the context menu with a real right click.
      ui.ideFrame { codeEditor().rightClick() }

      // 2. Take the popup.
      val menu = ui.popupMenu()

      // 3. Two collections: every entry, and submenus only. A single query already handles both
      // classes, so the second one is not there for coverage. Step 4 needs exactly one candidate,
      // and only the submenus give it one.
      val entries = menu.xx(PopupItemUiComponent::class.java) { or(byClass("ActionMenuItem"), byClass("ActionMenu")) }
      val submenus = menu.xx(PopupItemUiComponent::class.java) { byClass("ActionMenu") }

      // 4. Poll the submenus with a predicate. list() is a single snapshot, waitForOne re-runs
      // it and returns only when exactly one entry matches.
      val pasteSpecial = waitForOne(
        message = "the Paste submenu",
        timeout = 45.seconds,
        getter = { submenus.list() },
        checker = { it.getText().contains("Paste") }
      )

      val entryCount = entries.list().size

      // 5. Click it. menu.select() is shorter, but it needs one exact label.
      // menu.select("Paste") would find the regular Paste item, not the submenu, and edit the file.
      // menu.select("Copy / Paste Special") works, but only while the IDE uses that exact text.
      pasteSpecial.click()

      // 6. It really opened a submenu, so there are more entries on screen than before.
      waitFor(message = "the submenu to open", timeout = 45.seconds) {
        entries.list().size > entryCount
      }

      // 7. A submenu is open on top of the menu, so two escapes.
      ui.ideFrame { keyboard { escape(); escape() } }
    }
  }
}
