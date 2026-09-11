package com.example.migration.legacy.utils

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Supplies the RemoteRobot instance and dumps artifacts on failure.
 *
 * This whole file is infrastructure the test author owns. The Starter equivalent is one flag,
 * useDriverAndCloseIde(takeScreenshot = true), because Starter owns the IDE process.
 */
class RemoteRobotExtension : AfterTestExecutionCallback, ParameterResolver {

  private val url: String = System.getProperty("remote-robot-url") ?: "http://127.0.0.1:8082"
  private val remoteRobot: RemoteRobot = RemoteRobot(url)
  private val client = OkHttpClient()

  override fun supportsParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Boolean =
    parameterContext?.parameter?.type?.equals(RemoteRobot::class.java) ?: false

  override fun resolveParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Any =
    remoteRobot

  override fun afterTestExecution(context: ExtensionContext?) {
    val testName = context?.requiredTestMethod?.name ?: return
    val failed = context.executionException?.isPresent ?: false
    if (failed.not()) return
    runCatching { saveIdeaFrames(testName) }
    runCatching { saveHierarchy(testName) }
  }

  /** The component tree of the running IDE as HTML. There is no offline way to get it. */
  private fun saveHierarchy(testName: String) {
    val snapshot = saveFile(url, "build/reports", "hierarchy-$testName.html")
    if (File("build/reports/styles.css").exists().not()) {
      saveFile("$url/styles.css", "build/reports", "styles.css")
    }
    println("Hierarchy snapshot: ${snapshot.absolutePath}")
  }

  private fun saveFile(url: String, folder: String, name: String): File {
    val response = client.newCall(Request.Builder().url(url).build()).execute()
    return File(folder).apply { mkdirs() }.resolve(name).apply { writeText(response.body?.string() ?: "") }
  }

  /** A screenshot has to be painted inside the IDE and shipped back as a byte array. */
  private fun saveIdeaFrames(testName: String) {
    remoteRobot.findAll<ContainerFixture>(byXpath("//div[@class='IdeFrameImpl']")).forEachIndexed { n, frame ->
      val bytes = frame.callJs<ByteArray>(
        """
          importPackage(java.io)
          importPackage(javax.imageio)
          importPackage(java.awt.image)
          const screenShot = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
          component.paint(screenShot.getGraphics())
          let pictureBytes;
          const baos = new ByteArrayOutputStream();
          try {
            ImageIO.write(screenShot, "png", baos);
            pictureBytes = baos.toByteArray();
          } finally {
            baos.close();
          }
          pictureBytes;
        """, true
      )
      bytes.inputStream().use { ImageIO.read(it) }.save(testName + "_" + n)
    }
  }

  private fun BufferedImage.save(name: String) {
    val bytes = ByteArrayOutputStream().use { b ->
      ImageIO.write(this, "png", b)
      b.toByteArray()
    }
    File("build/reports").apply { mkdirs() }.resolve("$name.png").writeBytes(bytes)
  }
}
