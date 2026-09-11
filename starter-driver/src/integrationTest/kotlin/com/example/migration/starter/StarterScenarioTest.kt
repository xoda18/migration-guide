package com.example.migration.starter

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.junit5.hyphenateWithClass
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.plugins.PluginConfigurator
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.Starter
import kotlin.io.path.Path

/** Shared setup. Every test builds its own context and starts its own IDE. */
abstract class StarterScenarioTest {

  protected val pluginUnderTestId = "com.example.migration.sample"

  private val ideVersion = requiredProperty("ide.version")
  private val sampleProject = Path(requiredProperty("sample.project.dir"))
  private val pluginUnderTest = Path(requiredProperty("path.to.build.plugin"))

  protected fun context(): IDETestContext =
    Starter.newContext(
      CurrentTestMethod.hyphenateWithClass(),
      TestCase(IdeProductProvider.IU, LocalProjectInfo(sampleProject)).useRelease(ideVersion)
    )
      .apply { PluginConfigurator(this).installPluginFromPath(pluginUnderTest) }
      .prepareProjectCleanImport()
      .applyVMOptionsPatch {
        addSystemProperty("ide.show.tips.on.startup.default.value", false)
        addSystemProperty("jb.consents.confirmation.enabled", false)
        addSystemProperty("jb.privacy.policy.text", "<!--999.999-->")
        addSystemProperty("shared.indexes.download.auto.consent", true)
      }
      .addProjectToTrustedLocations()

  private fun requiredProperty(name: String): String =
    checkNotNull(System.getProperty(name)?.takeIf { it.isNotBlank() }) {
      "System property '$name' is not set. Run ./gradlew :starter-driver:integrationTest"
    }
}
