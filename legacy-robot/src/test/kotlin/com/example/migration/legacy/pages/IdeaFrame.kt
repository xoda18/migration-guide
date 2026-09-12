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
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

fun RemoteRobot.idea(timeout: Duration = Duration.ofMinutes(1), function: IdeaFrame.() -> Unit) {
  find<IdeaFrame>(timeout = timeout).apply(function)
}

/**
 * The equivalent of the Driver call isPluginLoaded(). On the robot rather than on the frame,
 * because PluginManagerCore is static and the answer has nothing to do with any window.
 */
fun RemoteRobot.isPluginEnabled(pluginId: String): Boolean = callJs(
  """
    const id = com.intellij.openapi.extensions.PluginId.getId("$pluginId")
    const descriptor = com.intellij.ide.plugins.PluginManagerCore.getPlugin(id)
    descriptor != null && descriptor.isEnabled()
  """, true
)

/**
 * The main IDE window. Every method below that reaches into IDE state is a JavaScript string
 * evaluated inside the IDE process, so a rename in the IntelliJ Platform shows up at
 * runtime as a stack trace from the script engine.
 */
@FixtureName("Idea frame")
@DefaultXpath("IdeFrameImpl type", "//div[@class='IdeFrameImpl']")
class IdeaFrame(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
  CommonContainerFixture(remoteRobot, remoteComponent) {

  /**
   * In the robot hierarchy @class is the concrete runtime class, which here is
   * ProjectViewPane$MyProjectViewTree. The plain '//div[@class="ProjectViewTree"]' from the
   * JetBrains ui-test-example matches nothing. @classhierarchy holds the whole chain, so
   * contains() on it is the closest thing to the Driver query byType(...).
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
      Duration.ofSeconds(20)
    )

  /**
   * Nothing raises the window on its own: the IDE was started by another Gradle task, so it is one
   * window among many and a real click goes nowhere while it is behind.
   */
  fun bringToFront() {
    runJs("component.toFront(); component.requestFocus()", true)
    // Raising a window is a request to the window manager, and it answers when it likes. Where it
    // answers at all, wait for the answer. On macOS it never comes, because the system does not
    // hand activation to a background application and isActive() stays false, so the wait is short
    // and its result is not asserted. A fixed sleep was here before and it was too short on CI:
    // the click in S1 and the action in S2 both went out while the window was still coming up.
    runCatching {
      waitFor(
        Duration.ofSeconds(10),
        Duration.ofMillis(200),
        description = "the IDE window to become active"
      ) { callJs<Boolean>("component.isActive()", true) }
    }
  }

  /**
   * The equivalent of Driver waitForIndicators(), written by hand.
   *
   * The isDisposed guard is not padding. Tests share one IDE, so a project on its way out is
   * still reachable for a moment, and without the guard the call fails with
   * AlreadyDisposedException, which reads like an IntelliJ Platform bug.
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
   * The replacement for CommonSteps.invokeAction, which cannot open a popup. Actions that only
   * change state, CloseProject in the @AfterEach for instance, work fine through CommonSteps.
   *
   * The difference that was shown to decide it is the last argument, 'now'. CommonSteps
   * hardcodes true, which runs the action synchronously inside this call while the focus
   * machinery is still in flight, and a popup opened that way is built and then dropped: the
   * window object exists and isShowing() never turns true. With false the action is queued the
   * way a real keystroke is, and the popup comes up and stays.
   *
   * The context component is passed explicitly as well, rather than left null. That one was not
   * isolated, but a null component is what makes an action resolve against whatever holds the
   * focus, and that is already what stops the stripe button action in S1.
   *
   * Neither case is a refusal. The ActionCallback comes back with isRejected() false and a null
   * error, so there is nothing to catch. And CommonSteps discards that callback anyway, which is
   * why the whole thing fails without an exception, without a log line and without a popup.
   *
   * Driver takes both of these as typed parameters on invokeAction(), and it
   * checks the callback.
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
