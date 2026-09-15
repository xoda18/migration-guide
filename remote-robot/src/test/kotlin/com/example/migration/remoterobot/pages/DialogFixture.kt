package com.example.migration.remoterobot.pages

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import java.time.Duration

fun RemoteRobot.dialog(
  title: String,
  timeout: Duration = Duration.ofSeconds(60),
  function: DialogFixture.() -> Unit = {},
): DialogFixture = step("Search for dialog with title $title") {
  find<DialogFixture>(DialogFixture.byTitle(title), timeout).apply(function)
}

fun ContainerFixture.dialog(
  title: String,
  timeout: Duration = Duration.ofSeconds(60),
  function: DialogFixture.() -> Unit = {},
): DialogFixture = step("Search for dialog with title $title") {
  find<DialogFixture>(DialogFixture.byTitle(title), timeout).apply(function)
}

/** Taken from the JetBrains ui-test-example. There is no dialog fixture in the library. */
@FixtureName("Dialog")
class DialogFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
  CommonContainerFixture(remoteRobot, remoteComponent) {

  companion object {
    /**
     * Title only. The ui-test-example also pins @class='MyDialog', the class of a modal
     * DialogWrapper, and Settings is a NonModalWindowWrapper$FloatDialog on newer builds.
     * contains(), because a project dialog is titled "Settings - project-name".
     */
    @JvmStatic
    fun byTitle(title: String) = byXpath("title $title", "//div[contains(@title, '$title')]")
  }

  val title: String
    get() = callJs("component.getTitle();")
}
