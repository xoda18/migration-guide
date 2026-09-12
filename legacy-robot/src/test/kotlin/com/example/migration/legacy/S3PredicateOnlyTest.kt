package com.example.migration.legacy

import com.example.migration.legacy.pages.allMenuItems
import com.example.migration.legacy.pages.allSubmenus
import com.example.migration.legacy.pages.idea
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitForIgnoringError
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * S3. Pick an entry that only a predicate can identify.
 *
 * The editor context menu holds two entries with the word Paste, the leaf Paste and the submenu
 * Copy / Paste Special, and only the component class separates them. Exact labels are no help
 * either: Rename… ends in a real ellipsis character and Run 'Main.main()' is built at runtime.
 */
class S3PredicateOnlyTest : LegacyScenarioTest() {

  @Test
  fun pickEntryOnlyAPredicateCanIdentify(remoteRobot: RemoteRobot) = with(remoteRobot) {
    openSampleProject(this)

    idea {
      openFile("src/Main.java")
      // Wait for the component the right click needs, not for a different one. textEditor()
      // defaults to a five second search, and that is short for a machine under load: on CI it
      // ran out while the editor was still coming up, and the screenshot taken at that moment
      // already showed the file open.
      waitForIgnoringError(
        Duration.ofSeconds(60),
        description = "the editor to open",
        errorMessage = "no editor ever appeared for src/Main.java"
      ) {
        textEditors().isNotEmpty()
      }
      bringToFront()

      // 1. Open the context menu with a real right click.
      textEditor(Duration.ofSeconds(30)).editor.rightClick()
    }

    // 2 and 3. In Legacy the popup is not available as a separate object. After the right click,
    // its items appear as top-level UI components, so the helpers search from remoteRobot across
    // all open UI windows. allMenuItems() and allSubmenus() are defined in
    // pages/ActionMenuFixture.kt together with their XPath locators. Both helpers are first used
    // in step 4 below.

    // 4. Poll the submenus with a predicate.
    waitForIgnoringError(Duration.ofSeconds(30), description = "the Paste submenu") {
      allSubmenus().count { it.text.contains("Paste") } == 1
    }
    val pasteSpecial = allSubmenus().single { it.text.contains("Paste") }

    // Taken before the click, so step 6 has something to compare against.
    val entryCount = allMenuItems().size

    // 5. Click it.
    pasteSpecial.click()

    // 6. It opened a submenu, so there are more entries on screen than before.
    waitForIgnoringError(Duration.ofSeconds(15), description = "the submenu to open") {
      allMenuItems().size > entryCount
    }

    // 7. A submenu is open on top of the menu, so two escapes.
    keyboard { escape(); escape() }
  }
}
