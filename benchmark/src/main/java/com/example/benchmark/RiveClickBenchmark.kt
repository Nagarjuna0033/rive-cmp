package com.example.benchmark

import android.content.Intent
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RiveClickBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun measureRiveClick() = benchmarkRule.measureRepeated(
        packageName = "com.arjun.rivecmptesting",
        metrics = listOf(
            FrameTimingMetric(),
            MemoryUsageMetric(mode = MemoryUsageMetric.Mode.Max)
        ),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait(
                Intent().setClassName(
                    "com.arjun.rivecmptesting",
                    "com.arjun.rivecmptesting.RiveClickActivity"
                )
            )
        }
    ) {

        device.wait(Until.hasObject(By.desc("rive_box_1")), 5_000)

        repeat(10) {
            val box = device.findObject(By.desc("rive_box_1"))
                ?: error("rive_box_1 not found")

            box.click()
            device.waitForIdle()
        }

    }

}