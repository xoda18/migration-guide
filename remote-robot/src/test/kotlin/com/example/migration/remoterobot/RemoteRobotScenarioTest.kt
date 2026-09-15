package com.example.migration.remoterobot

import com.example.migration.remoterobot.pages.IdeaFrame
import com.example.migration.remoterobot.pages.idea
import com.example.migration.remoterobot.utils.RemoteRobotExtension
import com.example.migration.remoterobot.utils.StepsLogger
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.steps.CommonSteps
import com.intellij.remoterobot.utils.waitForIgnoringError
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

/**
 * Shared setup. The IDE is started by runIdeForUiTests, not from here, so there is nothing
 * about the product, the version or the plugin under test in this file.
 */
@ExtendWith(RemoteRobotExtension::class)
abstract class RemoteRobotScenarioTest {

  init {
    StepsLogger.init()
  }

  protected val pluginUnderTestId = "com.example.migration.sample"

  protected val sampleProjectPath: String = checkNotNull(System.getProperty("sample.project.dir")) {
    "System property 'sample.project.dir' is not set. Run ./gradlew :Remote Robot-robot:test"
  }

  /**
   * Settings can only be changed once the IDE is up. Without the second call the first
   * openProject() raises a modal dialog that blocks every later test.
   */
  @BeforeEach
  fun waitForIde(remoteRobot: RemoteRobot) {
    waitForIgnoringError(Duration.ofMinutes(9)) { remoteRobot.callJs("true") }
    remoteRobot.runJs(
      """
        const settings = com.intellij.ide.GeneralSettings.getInstance()
        settings.setConfirmOpenNewProject(com.intellij.ide.GeneralSettings.OPEN_PROJECT_SAME_WINDOW)
        settings.setReopenLastProject(false)
        const path = java.nio.file.Paths.get("$sampleProjectPath")
        com.intellij.ide.impl.TrustedPaths.getInstance().setProjectPathTrusted(path, true)
      """, true
    )
  }

  /**
   * The IDE outlives the test, so state has to be cleaned up by hand. closeProject() comes back
   * once the action is queued, not once the project is gone, and the next scenario would then
   * run against a project being disposed. Starter needs none of this: it throws the IDE away.
   */
  @AfterEach
  fun closeProject(remoteRobot: RemoteRobot) {
    runCatching { CommonSteps(remoteRobot).closeProject() }
    waitForIgnoringError(
      Duration.ofSeconds(60),
      description = "the project to finish closing",
      errorMessage = "a project was still open after closeProject()"
    ) {
      remoteRobot.findAll<IdeaFrame>().none { it.hasLiveProject() }
    }
  }

  protected fun openSampleProject(remoteRobot: RemoteRobot) {
    CommonSteps(remoteRobot).openProject(sampleProjectPath)
    remoteRobot.awaitProjectOpen()
    remoteRobot.idea { bringToFront() }
  }

  /**
   * All the readiness the library has: dumb mode is off, the same condition as
   * CommonSteps.waitForSmartMode(). Anything else still running is invisible to it, so every
   * scenario below waits for its own component by hand. Driver's waitForIndicators() also
   * watches the status bar and requires ten quiet seconds in a row.
   *
   * The frame is re-found on every attempt, because openProject() rebuilds the window.
   */
  protected fun RemoteRobot.awaitProjectOpen() {
    waitForIgnoringError(Duration.ofMinutes(15), description = "the project to open") {
      find<IdeaFrame>(Duration.ofSeconds(30)).run { projectName.isNotEmpty() && isDumbMode().not() }
    }
  }
}
