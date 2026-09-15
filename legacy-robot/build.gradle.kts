import org.gradle.process.CommandLineArgumentProvider

plugins {
  kotlin("jvm")
  id("org.jetbrains.intellij.platform")
}

val ideVersion = "2026.1"
val remoteRobotVersion = "0.11.23"

repositories {
  mavenCentral()
  maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
  intellijPlatform {
    defaultRepositories()
  }
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  testImplementation("com.intellij.remoterobot:remote-robot:$remoteRobotVersion")
  testImplementation("com.intellij.remoterobot:remote-fixtures:$remoteRobotVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
  testImplementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

  intellijPlatform {
    intellijIdea(ideVersion)
  }
}

intellijPlatformTesting.runIde.register("runIdeForUiTests") {
  task {
    jvmArgumentProviders.add(
      CommandLineArgumentProvider {
        listOf(
          "-Drobot-server.port=8082",
          "-Dide.mac.message.dialogs.as.sheets=false",
          "-Djb.privacy.policy.text=<!--999.999-->",
          "-Djb.consents.confirmation.enabled=false",
          "-Dide.mac.file.chooser.native=false",
          "-DjbScreenMenuBar.enabled=false",
          "-Dapple.laf.useScreenMenuBar=false",
          "-Didea.trust.all.projects=true",
          "-Dide.show.tips.on.startup.default.value=false",
          "-Dide.experimental.ui.onboarding=false",
          "-Dide.newUsersOnboarding=false",
        )
      }
    )
  }
  plugins {
    robotServerPlugin(remoteRobotVersion)
  }
}

tasks.test {
  useJUnitPlatform()

  dependsOn(":downloadSampleProject")
  systemProperty("sample.project.dir", rootProject.layout.buildDirectory.dir("sample-project").get().asFile.absolutePath)
}
