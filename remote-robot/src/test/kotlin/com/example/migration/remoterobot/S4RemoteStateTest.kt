package com.example.migration.remoterobot

import com.example.migration.remoterobot.pages.idea
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * S4. Read IDE state. The only way in is a JavaScript string evaluated inside the IDE. It is
 * not compiled, not checked against the IntelliJ Platform API, and not reachable by a refactoring.
 */
class S4RemoteStateTest : RemoteRobotScenarioTest() {

  @Test
  fun readStateThroughJavaScript(remoteRobot: RemoteRobot) = with(remoteRobot) {
    openSampleProject(this)

    idea {
      // 1 to 3. No interface and no service lookup. The call is isProjectToolWindowVisible in
      // pages/IdeaFrame.kt, and nothing checks the class names in it until the test runs.
      val before = isProjectToolWindowVisible()

      // 4. A real mouse click, the same as on the Driver side.
      projectStripeButton.click()

      // 5. Read the state back. waitFor() throws on timeout, so it is the assertion.
      waitFor(Duration.ofSeconds(45), description = "the Project tool window visibility to change from $before") {
        isProjectToolWindowVisible() != before
      }
    }
  }
}
