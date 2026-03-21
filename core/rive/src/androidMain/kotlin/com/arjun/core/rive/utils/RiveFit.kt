package com.arjun.core.rive.utils

import app.rive.runtime.kotlin.core.Fit

/**
 * Maps common RiveFit to Android-specific Fit
 */
internal fun RiveFit.toAndroidFit(): Fit = when (this) {
    RiveFit.FILL -> Fit.FILL
    RiveFit.CONTAIN -> Fit.CONTAIN
    RiveFit.COVER -> Fit.COVER
    RiveFit.FIT_WIDTH -> Fit.FIT_WIDTH
    RiveFit.FIT_HEIGHT -> Fit.FIT_HEIGHT
    RiveFit.NONE -> Fit.NONE
    RiveFit.SCALE_DOWN -> Fit.SCALE_DOWN
}