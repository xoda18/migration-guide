package com.example.migration.legacy

import com.example.migration.legacy.pages.idea
import com.example.migration.legacy.pages.projectPanel
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.waitForIgnoringError
import org.junit.jupiter.api.Test
import java.time.Duration

/** S5. Use the page object from pages/ProjectPanelFixture.kt. */
class S5PageObjectTest : LegacyScenarioTest() {

  @Test
  fun ownPageObject(remoteRobot: RemoteRobot) = with(remoteRobot) {
    openSampleProject(this)

    idea {
      bringToFront()
      if (isProjectToolWindowVisible().not()) {
        projectStripeButton.click()
      }
      waitForIgnoringError(Duration.ofSeconds(30), description = "the Project tool window to open") {
        isProjectToolWindowVisible()
      }
    }

    // 5. Use the wrapper.
    val panel = projectPanel()

    // 6. Assert through it. No shouldBe() and no hasSubtext(), so each assertion is a retry loop
    // written out. Both reach
    // into the IDE and can throw while the panel fills in, hence waitForIgnoringError().
    waitForIgnoringError(Duration.ofSeconds(30), description = "the project root to be in the tree") {
      panel.tree.collectRows().any { it.contains("sample-project") }
    }
    waitForIgnoringError(Duration.ofSeconds(30), description = "the tool window header to have a content label") {
      panel.contentLabels().isNotEmpty()
    }
  }
}
