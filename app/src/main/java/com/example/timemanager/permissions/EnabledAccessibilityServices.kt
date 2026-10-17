package com.example.timemanager.permissions

/** Matches our component only; Dev and release share a service class, not an app ID. */
internal fun isAccessibilityComponentEnabled(
    enabledServices: String?,
    packageName: String,
    serviceClassName: String,
): Boolean = enabledServices.orEmpty().split(':').any { component ->
    val parts = component.split('/', limit = 2)
    if (parts.size != 2 || parts[0] != packageName) return@any false
    val className = if (parts[1].startsWith('.')) parts[0] + parts[1] else parts[1]
    className == serviceClassName
}
