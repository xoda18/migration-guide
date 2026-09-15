import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
  kotlin("jvm")
  id("org.jetbrains.intellij.platform")
}

val ideVersion = "2026.1"

sourceSets {
  create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
  }
}

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

kotlin {
  jvmToolchain(25)
}

val integrationTestImplementation by configurations.getting {
  extendsFrom(configurations.testImplementation.get())
}

dependencies {
  intellijPlatform {
    intellijIdea(ideVersion)
    testFramework(TestFrameworkType.Starter, configurationName = "integrationTestImplementation")
  }

  integrationTestImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
  integrationTestImplementation("org.kodein.di:kodein-di-jvm:7.20.2")
  integrationTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.1")
  "integrationTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

configurations.named("integrationTestRuntimeClasspath") {
  resolutionStrategy.force("org.jetbrains.kotlin:kotlin-reflect:${kotlin.coreLibrariesVersion}")
}

val integrationTest by intellijPlatformTesting.testIdeUi.registering {
  task {
    val integrationTestSourceSet = sourceSets.getByName("integrationTest")
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath

    systemProperty("ide.version", ideVersion)

    useJUnitPlatform()
  }
}
