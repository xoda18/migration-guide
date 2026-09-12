package com.example.migration.legacy

import com.example.migration.legacy.pages.idea
import com.example.migration.legacy.pages.isPluginEnabled
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.steps.CommonSteps
import com.intellij.remoterobot.utils.waitFor
import com.intellij.remoterobot.utils.waitForIgnoringError
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * S1. Launch, project, plugin, readiness.
 *
 * Only part of this scenario can live in the test. Launching the IDE and installing the plugin
 * happen in Gradle, before this process starts.
 */
class S1LaunchAndReadinessTest : LegacyScenarioTest() {

  @Test
  fun launchProjectPluginReadiness(remoteRobot: RemoteRobot) = with(remoteRobot) {
    // 1. Missing on purpose. There is no context to build.

    // 2. Open the project. No clean import step either, whatever .idea is on disk gets used.
    CommonSteps(this).openProject(sampleProjectPath)

    // 3. Is the plugin loaded? isPluginEnabled() is a hand-written JS string in pages/IdeaFrame.kt.
    assertTrue(isPluginEnabled(pluginUnderTestId)) { "$pluginUnderTestId was not loaded" }

    // 4. Wait for the project and indexing. Dumb mode is the only readiness flag there is.
    // Must happen before any fixture lookup, see awaitProjectOpen().
    awaitProjectOpen()

    idea(Duration.ofMinutes(2)) {
      // 5. A project is open.
      assertTrue(projectName.isNotEmpty()) { "No project is open" }
      bringToFront()

      // 6. Open the Project View by clicking the stripe button.
      // Not by action id: CommonSteps.invokeAction() passes a null context component, so the
      // action is resolved against whatever holds the focus, and ActivateProjectToolWindow does
      // nothing. It reports nothing either, because the ActionCallback is discarded. See
      // pages/IdeaFrame.kt for the version that works and what had to change in it.
      // The click is a toggle, and the IDE both outlives the test and is still restoring its own
      // layout while the project opens. Reading the state once and clicking once can therefore
      // land on the wrong side: a run on CI left the panel closed and never built the tree. So
      // click again for as long as it is still closed. Driver has nothing to guard here, because
      // open() opens and does not toggle.
      waitForIgnoringError(
        Duration.ofSeconds(60),
        description = "the Project tool window to open",
        errorMessage = "the Project tool window stayed closed"
      ) {
        if (isProjectToolWindowVisible().not()) projectStripeButton.click()
        isProjectToolWindowVisible()
      }

      // The tree cannot be asked whether its nodes are loaded, so poll for rows.
      // waitForIgnoringError(), not waitFor(): a fixture lookup throws when the component is not
      // there yet, and plain waitFor() does not catch.
      waitForIgnoringError(
        Duration.ofSeconds(30),
        description = "the project tree to have rows",
        errorMessage = "the project tree never got any rows"
      ) {
        projectViewTree.collectRows().isNotEmpty()
      }

      // 7. Expand src.
      // The root node carries two pieces of text, the project name and its location on disk,
      // and they are read back as one label: "sample-project ~/IdeaProjects".
      // expand(vararg path) compares labels exactly, so it cannot address the root. Only
      // collapsePath() and the click methods take fullMatch, which leaves a double click.
      waitForIgnoringError(Duration.ofSeconds(30), description = "the src folder to appear") {
        projectViewTree.isPathExists("sample-project", "src", fullMatch = false)
      }
      if (projectViewTree.isPathExists("sample-project", "src", "Main", fullMatch = false).not()) {
        projectViewTree.doubleClickPath("sample-project", "src", fullMatch = false)
      }
      waitForIgnoringError(Duration.ofSeconds(15), description = "the src folder to expand") {
        projectViewTree.isPathExists("sample-project", "src", "Main", fullMatch = false)
      }

      // 8. Both files are in the tree.
      val rows = projectViewTree.collectExpandedPaths().map { it.path.joinToString("/") }
      assertTrue(projectViewTree.isPathExists("sample-project", "src", "Main", fullMatch = false)) { "Main is missing, the tree shows: $rows" }
      assertTrue(projectViewTree.isPathExists("sample-project", "src", "Util", fullMatch = false)) { "Util is missing, the tree shows: $rows" }

      // 9. Open the file with a real double click.
      projectViewTree.doubleClickPath("sample-project", "src", "Main", fullMatch = false)

      // 10. The right file opened. There is no editor tabs fixture, so this goes through the
      // editor, and the file name behind it is another JS call.
      waitFor(Duration.ofSeconds(30), description = "the editor to open") {
        findAll<ComponentFixture>(byXpath("//div[@class='EditorComponentImpl']")).isNotEmpty()
      }
      val editor = textEditor().editor
      assertTrue(editor.fileName == "Main.java") { "The wrong file is open, ${editor.fileName}" }
      assertTrue(editor.text.contains("class Main")) { "The editor does not contain class Main" }

      // 11. Collapse and check the tree state really changed.
      val before = projectViewTree.collectExpandedPaths().size
      projectViewTree.collapsePath("sample-project", "src", fullMatch = false)
      waitForIgnoringError(Duration.ofSeconds(15), description = "the tree to collapse") {
        projectViewTree.collectExpandedPaths().size < before
      }
    }
  }
}
