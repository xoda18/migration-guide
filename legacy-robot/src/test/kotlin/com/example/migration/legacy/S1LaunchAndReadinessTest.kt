package com.example.migration.legacy

import com.example.migration.legacy.pages.idea
import com.example.migration.legacy.pages.isPluginEnabled
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.steps.CommonSteps
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

    // 4. Wait for the project and indexing. Dumb mode is all the library knows, and it has to
    // happen before any fixture lookup. See awaitProjectOpen().
    awaitProjectOpen()

    idea(Duration.ofMinutes(6)) {
      // 5. A project is open.
      assertTrue(projectName.isNotEmpty()) { "No project is open" }
      bringToFront()

      // 6. Open the Project View by clicking the Project button on the left toolbar. The action
      // id would be shorter, but CommonSteps.invokeAction() gives the action no component to read
      // the project from, so it does nothing and reports no error. The button is a switch, hence
      // the check before the click. Driver calls projectButton.open(), which only opens.
      if (isProjectToolWindowVisible().not()) {
        projectStripeButton.click()
      }

      // The tree cannot be asked whether its nodes are loaded, so poll for rows.
      // waitForIgnoringError(), not waitFor(): a fixture lookup throws while the component is
      // missing, and plain waitFor() does not catch.
      waitForIgnoringError(
        Duration.ofSeconds(90),
        description = "the project tree to have rows",
        errorMessage = "the project tree never got any rows"
      ) {
        projectViewTree.collectRows().isNotEmpty()
      }

      // 7. Expand src. The root label reads back as one string, "sample-project ~/IdeaProjects",
      // and expand(vararg path) compares labels exactly, so it cannot address the root. Only
      // collapsePath() and the click methods take fullMatch, which leaves a double click.
      waitForIgnoringError(Duration.ofSeconds(90), description = "the src folder to appear") {
        projectViewTree.isPathExists("sample-project", "src", fullMatch = false)
      }
      if (projectViewTree.isPathExists("sample-project", "src", "Main", fullMatch = false).not()) {
        projectViewTree.doubleClickPath("sample-project", "src", fullMatch = false)
      }
      waitForIgnoringError(Duration.ofSeconds(45), description = "the src folder to expand") {
        projectViewTree.isPathExists("sample-project", "src", "Main", fullMatch = false)
      }

      // 8. Both files are in the tree.
      val rows = projectViewTree.collectExpandedPaths().map { it.path.joinToString("/") }
      assertTrue(projectViewTree.isPathExists("sample-project", "src", "Main", fullMatch = false)) { "Main is missing, the tree shows: $rows" }
      assertTrue(projectViewTree.isPathExists("sample-project", "src", "Util", fullMatch = false)) { "Util is missing, the tree shows: $rows" }

      // 9. Open the file with a real double click.
      projectViewTree.doubleClickPath("sample-project", "src", "Main", fullMatch = false)

      // 10. The right file opened. There is no editor tabs fixture, so this asks the editor for
      // its file name through another JS call. textEditor() carries its own five second search,
      // so the wait before it looks for the same component.
      waitForIgnoringError(Duration.ofSeconds(90), description = "the editor to open") {
        textEditors().isNotEmpty()
      }
      val editor = textEditor(Duration.ofSeconds(90)).editor
      assertTrue(editor.fileName == "Main.java") { "The wrong file is open, ${editor.fileName}" }
      assertTrue(editor.text.contains("class Main")) { "The editor does not contain class Main" }

      // 11. Collapse and check the tree state really changed.
      val before = projectViewTree.collectExpandedPaths().size
      projectViewTree.collapsePath("sample-project", "src", fullMatch = false)
      waitForIgnoringError(Duration.ofSeconds(45), description = "the tree to collapse") {
        projectViewTree.collectExpandedPaths().size < before
      }
    }
  }
}
