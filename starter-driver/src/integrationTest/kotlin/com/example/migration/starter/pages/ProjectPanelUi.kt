package com.example.migration.starter.pages

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

/** A wrapper is an ordinary class plus one function that knows how to find it. */
// 1. The class.
class ProjectPanelUi(data: ComponentData) : UiComponent(data) {

  // 2. Single children are fields. x() is lazy: nothing is searched here, and the lookup
// retries when the field is used.
  val tree: UiComponent = x { byType("com.intellij.ide.projectView.impl.ProjectViewTree") }

  // 3. Collections do not wait. xx().list() is a single findAll() with no retry, so the caller
  // decides how long to wait.
  private val contentLabels = xx { byClass("ContentComboLabel") }

  fun contentLabels(): List<UiComponent> = contentLabels.list()
}

// 4. The function that finds it. By type, not by an xpath string.
fun Finder.projectPanel(): ProjectPanelUi =
  x(ProjectPanelUi::class.java) {
    componentWithChild(
      byType("com.intellij.toolWindow.InternalDecoratorImpl"),
      byType("com.intellij.ide.projectView.impl.ProjectViewTree")
    )
  }
