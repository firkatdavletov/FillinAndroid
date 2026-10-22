package com.firkat.fillin.feature.launch.domain

fun interface LocalBudgetRestorer {
    /** Selects an already configured device budget, if one exists. */
    suspend fun restoreCreatedBudget(): Boolean
}
