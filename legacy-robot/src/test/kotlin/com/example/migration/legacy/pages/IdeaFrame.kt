package com.example.migration.legacy.pages

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import java.time.Duration

fun RemoteRobot.idea(timeout: Duration = Duration.ofMinutes(3), function: IdeaFrame.() -> Unit) {
  find<IdeaFrame>(timeout = timeout).apply(function)
}

/** The equivalent of the Driver call isPluginLoaded(). */
fun RemoteRobot.isPluginEnabled(pluginId: String): Boolean = callJs(
  """
    const id = com.intellij.openapi.extensions.PluginId.getId("$pluginId")
    const descriptor = com.intellij.ide.plugins.PluginManagerCore.getPlugin(id)
    descriptor != null && descriptor.isEnabled()
  """, true
)

/**
 * The main IDE window. Every method below that reads IDE state is a JavaScript string evaluated
 * inside the IDE, so a rename in the IntelliJ Platform surfaces at runtime as a script engine
 * stack trace. Driver reads the same state through typed @Remote interfaces.
 */
@FixtureName("Idea frame")
@DefaultXpath("IdeFrameImpl type", "//div[@class='IdeFrameImpl']")
class IdeaFrame(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
  CommonContainerFixture(remoteRobot, remoteComponent) {

  /**
   * @class is the concrete runtime class, here ProjectViewPane$MyProjectViewTree, so the
   * '//div[@class="ProjectViewTree"]' from the JetBrains ui-test-example matches nothing.
   * @classhierarchy holds the whole chain and is the closest thing to the Driver query byType().
   */
  val projectViewTree: JTreeFixture
    get() = jTree(byXpath("ProjectViewTree", "//div[contains(@classhierarchy, 'ProjectViewTree')]"))

  val projectName: String
    get() = step("Get project name") { return@step callJs("component.getProject().getName()") }

  /** The class name changed between the classic and the new UI, so both are accepted. */
  val projectStripeButton: ComponentFixture
    get() = find(
      byXpath(
        "Project stripe button",
        "//div[(@class='SquareStripeButton' or @class='StripeButton') and @accessiblename='Project']"
      ),
      Duration.ofSeconds(180)
    )

  /**
   * The IDE is started by another Gradle task, so it is one window among many and a click goes
   * nowhere while it is behind. Nothing reports when the window manager has raised it, and what
   * the library offers instead is CommonSteps.wait(seconds), a Thread.sleep with a @Step
   * annotation on it. Driver never needs this: it owns the IDE process.
   */
  fun bringToFront() {
    runJs("component.toFront(); component.requestFocus()", true)
    Thread.sleep(2000)
  }

  /**
   * The same condition CommonSteps.waitForSmartMode() uses, plus an isDisposed check. Tests share
   * one IDE, and the library version throws AlreadyDisposedException on a project being closed.
   */
  fun isDumbMode(): Boolean = callJs(
    """
      const frameHelper = com.intellij.openapi.wm.impl.ProjectFrameHelper.getFrameHelper(component)
      if (frameHelper) {
        const project = frameHelper.getProject()
        !project || project.isDisposed() ? true : com.intellij.openapi.project.DumbService.isDumb(project)
      } else {
        true
      }
    """, true
  )

  /** Closing a project is asynchronous. This turns false once it is really gone. */
  fun hasLiveProject(): Boolean = callJs(
    """
      const frameHelper = com.intellij.openapi.wm.impl.ProjectFrameHelper.getFrameHelper(component)
      const project = frameHelper ? frameHelper.getProject() : null
      project != null && !project.isDisposed()
    """, true
  )

  /** The equivalent of the Driver ToolWindowManager @Remote interface used in S4. */
  fun isProjectToolWindowVisible(): Boolean = callJs(
    """
      const frameHelper = com.intellij.openapi.wm.impl.ProjectFrameHelper.getFrameHelper(component)
      const project = frameHelper ? frameHelper.getProject() : null
      if (!project || project.isDisposed()) {
        false
      } else {
        const manager = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
        const toolWindow = manager.getToolWindow("Project")
        toolWindow != null && toolWindow.isVisible()
      }
    """, true
  )

  /**
   * The replacement for CommonSteps.invokeAction(), which cannot open a popup. It hardcodes
   * now = true, so the action runs inside the call and the popup is built and dropped, and it
   * passes a null context component, so the action resolves against whatever holds the focus.
   * Neither failure is reported, because the ActionCallback is discarded. Driver takes both as
   * parameters on invokeAction() and checks the callback.
   */
  fun invokeAction(actionId: String) {
    runJs(
      """
        const actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
        const action = actionManager.getAction("$actionId")
        actionManager.tryToExecute(action, null, component, null, false)
      """, true
    )
  }

  /** The equivalent of the one line Driver call openFile("src/Main.java"). */
  fun openFile(path: String) {
    runJs(
      """
        importPackage(com.intellij.openapi.fileEditor)
        importPackage(com.intellij.openapi.vfs)
        importPackage(com.intellij.openapi.wm.impl)
        importClass(com.intellij.openapi.application.ApplicationManager)

        const path = '$path'
        const frameHelper = ProjectFrameHelper.getFrameHelper(component)
        if (frameHelper) {
          const project = frameHelper.getProject()
          const projectPath = project.getBasePath()
          const file = LocalFileSystem.getInstance().findFileByPath(projectPath + '/' + path)
          const openFileFunction = new Runnable({
            run: function() {
              FileEditorManager.getInstance(project).openTextEditor(
                new OpenFileDescriptor(project, file), true
              )
            }
          })
          ApplicationManager.getApplication().invokeLater(openFileFunction)
        }
      """, true
    )
  }
}
