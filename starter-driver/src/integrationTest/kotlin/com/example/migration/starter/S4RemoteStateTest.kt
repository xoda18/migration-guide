package com.example.migration.starter

import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * S4. Read IDE state without shipping test code in src/main. The @Remote interfaces live in
 * the test source set, so the production plugin carries nothing that exists only for tests.
 */
class S4RemoteStateTest : StarterScenarioTest() {

  // 1. A real IDE class by its fully qualified name, with only the methods the test needs.
  @Remote("com.intellij.openapi.wm.ToolWindowManager")
  interface ToolWindowManagerRef {
    fun getInstance(project: Project): ToolWindowManagerRef
    fun getToolWindow(id: String): ToolWindowRef?
  }

  @Remote("com.intellij.openapi.wm.ToolWindow")
  interface ToolWindowRef {
    fun isVisible(): Boolean
  }

  @Test
  fun readStateThroughRemoteInterface() {
    context().runIdeWithDriver().useDriverAndCloseIde {
      waitForIndicators(15.minutes)

      // 2. The open project.
      val project = singleProject()

      // 3. The service. From here it is an ordinary Kotlin call, checked by the compiler.
      val toolWindowManager = service<ToolWindowManagerRef>(project)

      val projectWindowVisible = { toolWindowManager.getToolWindow("Project")?.isVisible() ?: false }
      val before = projectWindowVisible()

      // 4. A real click
      ideFrame { leftToolWindowToolbar.projectButton.click() }

      // 5. Read the state back. waitFor() throws on timeout, so it is the assertion.
      waitFor("The Project tool window visibility changed from $before", 45.seconds) {
        projectWindowVisible() != before
      }
    }
  }
}
