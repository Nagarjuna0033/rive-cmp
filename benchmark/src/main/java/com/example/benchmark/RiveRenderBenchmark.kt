package com.example.benchmark

import android.content.Intent
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RiveRenderBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun measureRiveRender() = benchmarkRule.measureRepeated(
        packageName = "com.arjun.rivecmptesting",
        metrics = listOf(
            FrameTimingMetric(),
            MemoryUsageMetric(mode = MemoryUsageMetric.Mode.Max)
        ),
        iterations = 5,
        startupMode = StartupMode.COLD,
    ) {

        startActivityAndWait(
            Intent().setClassName(
                "com.arjun.rivecmptesting",
                "com.arjun.rivecmptesting.RiveRenderActivity"
            )
        )

        device.waitForIdle()
        Thread.sleep(2000)

    }

}