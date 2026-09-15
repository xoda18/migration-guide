import java.net.URI

plugins {
  kotlin("jvm") version "2.3.0" apply false
  id("org.jetbrains.intellij.platform") version "2.18.1" apply false
}

val sampleProjectUrl = "https://github.com/xoda18/sample-project/archive/refs/heads/main.tar.gz"

tasks.register("downloadSampleProject") {
  description = "Downloads the project the Remote Robot suite opens; Starter clones it itself"
  val archive = layout.buildDirectory.file("sample-project.tar.gz")
  val target = layout.buildDirectory.dir("sample-project")
  outputs.dir(target)
  doLast {
    val file = archive.get().asFile
    file.parentFile.mkdirs()
    URI(sampleProjectUrl).toURL().openStream().use { input -> file.outputStream().use(input::copyTo) }
    val dir = target.get().asFile
    dir.deleteRecursively()
    copy {
      from(tarTree(resources.gzip(file))) {
        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
        includeEmptyDirs = false
      }
      into(dir)
    }
  }
}
