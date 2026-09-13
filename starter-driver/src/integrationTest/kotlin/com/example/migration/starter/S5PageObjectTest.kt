package com.example.migration.starter

import com.example.migration.starter.pages.projectPanel
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/** S5. Use the page object from pages/ProjectPanelUi.kt. */
class S5PageObjectTest : StarterScenarioTest() {

  @Test
  fun ownPageObject() {
    context().runIdeWithDriver().useDriverAndCloseIde {
      waitForIndicators(5.minutes)

      ideFrame {
        leftToolWindowToolbar.projectButton.open()

        // 5. Use the wrapper. It reads like the built-in components because it is built the same way.
        val panel = projectPanel()

        // 6. Assert through it. shouldBe() retries both the lookup and the condition.
        panel.tree.shouldBe("The project root is in the tree") { hasSubtext("sample-project") }
        panel.shouldBe("The tool window header has a content label") { contentLabels().isNotEmpty() }
      }
    }
  }
}
