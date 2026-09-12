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
      // textEditor() carries its own five second search, so wait for the same component.
      waitForIgnoringError(
        Duration.ofSeconds(180),
        description = "the editor to open",
        errorMessage = "no editor ever appeared for src/Main.java"
      ) {
        textEditors().isNotEmpty()
      }
      bringToFront()

      // 1. Open the context menu with a real right click.
      textEditor(Duration.ofSeconds(90)).editor.rightClick()
    }

    // 2 and 3. There is no popup object here. The entries are top level components, so the
    // search starts from remoteRobot and crosses every open window. allMenuItems() and
    // allSubmenus() live in pages/ActionMenuFixture.kt and are first used in step 4.

    // 4. Poll the submenus with a predicate.
    waitForIgnoringError(Duration.ofSeconds(90), description = "the Paste submenu") {
      allSubmenus().count { it.text.contains("Paste") } == 1
    }
    val pasteSpecial = allSubmenus().single { it.text.contains("Paste") }

    // Taken before the click, so step 6 has something to compare against.
    val entryCount = allMenuItems().size

    // 5. Click it.
    pasteSpecial.click()

    // 6. It opened a submenu, so there are more entries on screen than before.
    waitForIgnoringError(Duration.ofSeconds(45), description = "the submenu to open") {
      allMenuItems().size > entryCount
    }

    // 7. A submenu is open on top of the menu, so two escapes.
    keyboard { escape(); escape() }
  }
}
