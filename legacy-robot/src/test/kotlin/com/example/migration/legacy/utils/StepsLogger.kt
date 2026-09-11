package com.example.migration.legacy.utils

import com.intellij.remoterobot.stepsProcessing.StepLogger
import com.intellij.remoterobot.stepsProcessing.StepWorker

/**
 * Turns every step { } block into a log line. Copied from the JetBrains ui-test-example.
 * Driver has no equivalent because it logs its own waits and clicks by default.
 */
object StepsLogger {
  private var initialized = false

  @JvmStatic
  fun init() {
    if (initialized.not()) {
      StepWorker.registerProcessor(StepLogger())
      initialized = true
    }
  }
}
