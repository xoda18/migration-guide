package com.example.migration.legacy

import com.example.migration.legacy.pages.IdeaFrame
import com.example.migration.legacy.pages.idea
import com.example.migration.legacy.utils.RemoteRobotExtension
import com.example.migration.legacy.utils.StepsLogger
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
abstract class LegacyScenarioTest {

  init {
    StepsLogger.init()
  }

  protected val pluginUnderTestId = "com.example.migration.sample"

  protected val sampleProjectPath: String = checkNotNull(System.getProperty("sample.project.dir")) {
    "System property 'sample.project.dir' is not set. Run ./gradlew :legacy-robot:test"
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
   * The IDE outlives the test, so state has to be cleaned up by hand.
   *
   * closeProject() comes back once the action is queued, not once the project is gone. Without
   * the wait the next scenario opens a project that is still on screen, passes its readiness
   * check against the one on its way out, and then fails on the first call into it. CI caught
   * exactly that: the IDE logged AlreadyDisposedException for FileEditorManager 0.7 seconds
   * into the following scenario, with "Last Action: CloseProject" beside it, and the three
   * scenarios after it failed on symptoms that had nothing to do with their own code.
   *
   * The call itself is allowed to fail, because a scenario that left a dialog open will make it
   * throw. The wait is not: if the project will not close, every scenario after this one is
   * running against a broken IDE, and the scenario that made the mess should be the one to say so.
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
   * Re-finds the frame on every attempt. openProject() rebuilds the window, and a fixture looked
   * up before the rebuild silently points at a component that no longer exists.
   */
  protected fun RemoteRobot.awaitProjectOpen() {
    waitForIgnoringError(Duration.ofMinutes(15), description = "the project to open and finish indexing") {
      find<IdeaFrame>(Duration.ofSeconds(30)).run { projectName.isNotEmpty() && isDumbMode().not() }
    }
  }
}
