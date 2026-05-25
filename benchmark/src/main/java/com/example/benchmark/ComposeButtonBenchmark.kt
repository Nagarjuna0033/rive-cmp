package com.example.benchmark

import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.PowerMetric
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
class ComposeButtonBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @RequiresApi(Build.VERSION_CODES.Q)
    @Test
    fun measureComposeButtonFrameTime() = benchmarkRule.measureRepeated(
        packageName = "com.arjun.rivecmptesting",
        metrics = listOf(
            FrameTimingMetric(),
            MemoryUsageMetric(mode = MemoryUsageMetric.Mode.Max),
        ),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait(
                Intent().apply {
                    setClassName(
                        "com.arjun.rivecmptesting",
                        "com.arjun.rivecmptesting.ComposeOnlyActivity"
                    )
                }
            )
        }
    ) {

        repeat(10) { index ->
            val obj = device.wait(
                Until.findObject(By.desc("benchmark_button_$index")),
                5_000
            ) ?: throw AssertionError("benchmark_button_$index not found")

            obj.click()
            device.waitForIdle()
        }
    }
}
