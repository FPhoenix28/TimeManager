package com.example.timemanager.config

/** Variant-owned timing input, never a writer of usage, episode or allowance state. */
fun interface TimeConfigProvider {
    fun current(): TimeConfig
    fun observe(): kotlinx.coroutines.flow.Flow<TimeConfig> = kotlinx.coroutines.flow.flowOf(current())
}
