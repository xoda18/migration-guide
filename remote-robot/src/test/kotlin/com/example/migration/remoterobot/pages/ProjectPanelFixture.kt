package com.example.migration.remoterobot.pages

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import java.time.Duration

/**
 * The Remote Robot shape of a page object. Compare with starter-driver pages/ProjectPanelUi.kt:
 * CommonContainerFixture instead of UiComponent, a RemoteComponent instead of ComponentData,
 * and a raw xpath in an annotation instead of a typed query. Nothing checks that xpath until
 * the test runs.
 */
// 1. The class. Extend CommonContainerFixture, take a RemoteComponent.
@FixtureName("Project panel")
@DefaultXpath(
  "InternalDecoratorImpl with a ProjectViewTree inside",
  "//div[@class='InternalDecoratorImpl'][.//div[contains(@classhierarchy, 'ProjectViewTree')]]"
)
class ProjectPanelFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
  CommonContainerFixture(remoteRobot, remoteComponent) {

  // 2. Single children are getters. find waits on its own, same as Driver x().
  val tree: JTreeFixture
    get() = jTree(byXpath("ProjectViewTree", "//div[contains(@classhierarchy, 'ProjectViewTree')]"))

  // 3. Collections do not wait, exactly like Driver xx().list(). The caller decides how long.
  fun contentLabels(): List<ComponentFixture> =
    findAll(byXpath("ContentComboLabel", "//div[@class='ContentComboLabel']"))
}

// 4. The locator is the @DefaultXpath annotation above, not a query.
fun RemoteRobot.projectPanel(function: ProjectPanelFixture.() -> Unit = {}): ProjectPanelFixture =
  find<ProjectPanelFixture>(timeout = Duration.ofSeconds(180)).apply(function)
