package org.kashmir.heatguard.data

import org.kashmir.heatguard.core.model.Baseline

interface BaselineRepository {
    fun getBaseline(): Baseline
    fun setBaseline(baseline: Baseline)
}
