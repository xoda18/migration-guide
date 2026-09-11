package com.example.migration.starter

import com.intellij.driver.sdk.isPluginLoaded
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.codeEditorForFile
import com.intellij.driver.sdk.ui.components.common.editorTabs
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/** S1. Launch the IDE, open a project, install the plugin, wait until it is ready. */
class S1LaunchAndReadinessTest : StarterScenarioTest() {

  @Test
  fun launchProjectPluginReadiness() {
    // 1. Build the context.
    val context = context()

    // 2. Start the IDE. Leaving the block shuts it down.
    context.runIdeWithDriver().useDriverAndCloseIde {

      // 3. Is the plugin loaded?
      assertTrue(isPluginLoaded(pluginUnderTestId)) { "$pluginUnderTestId was not loaded" }

      // 4. Wait for indexing and every background indicator.
      waitForIndicators(5.minutes)

      ideFrame {
        // 5. A project is open.
        assertNotNull(project) { "No project is open" }

        // 6. Open the Project View.
        leftToolWindowToolbar.projectButton.open()
        projectView {
          projectViewTree.waitFound()
          projectViewTree.waitForNodesLoaded()

          // 7. Expand src. The root node carries two pieces of text, the project name and its
          // location on disk, and they are read back as one label: "sample-project ~/IdeaProjects".
          // So "sample-project" matches only with fullMatch = false.
          projectViewTree.expandPath("sample-project", "src", fullMatch = false)

          // 8. Both files are in the tree. The Project View shows Java files by class name.
          val paths = projectViewTree.collectExpandedPathsAsStrings()
          assertTrue(projectViewTree.findExpandedPath("sample-project", "src", "Main", fullMatch = false) != null) { "Main is missing, the tree shows: $paths" }
          assertTrue(projectViewTree.findExpandedPath("sample-project", "src", "Util", fullMatch = false) != null) { "Util is missing, the tree shows: $paths" }

          // 9. Open the file with a real double click.
          projectViewTree.doubleClickPath("sample-project", "src", "Main", fullMatch = false)
        }

        // 10. The right file opened.
        val editor = codeEditorForFile("Main.java").waitFound()
        assertTrue(editorTabs().isTabOpened("Main.java")) { "There is no Main.java tab" }
        assertTrue(editor.text.contains("class Main")) { "The wrong file is open in the editor" }

        // 11. Collapse and check the tree state really changed.
        projectView {
          val before = projectViewTree.collectExpandedPathsAsStrings()
          projectViewTree.collapsePath("sample-project", "src", fullMatch = false)
          val after = projectViewTree.collectExpandedPathsAsStrings()
          assertTrue(after.size < before.size) { "The tree did not collapse, was ${before.size}, now ${after.size}" }
        }
      }
    }
  }
}
