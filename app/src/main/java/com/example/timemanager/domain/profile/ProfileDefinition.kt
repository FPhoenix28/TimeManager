package com.example.timemanager.domain.profile

@JvmInline
value class ProfileId(val value: String) {
    init { require(value.isNotBlank()) }

    companion object {
        /** A migration can reuse this identity; it must not generate a new default on startup. */
        val DEFAULT = ProfileId("default")
    }
}

/** Configuration only. Active profile selection and all usage/session facts live elsewhere. */
class ProfileDefinition(
    val id: ProfileId,
    val name: String,
    val enabled: Boolean,
    appPolicies: Collection<AppPolicy>,
    val configVersion: Int = 1,
) {
    val appPolicies: List<AppPolicy> = buildList { addAll(appPolicies) }

    init {
        require(name.isNotBlank())
        require(configVersion > 0)
        require(this.appPolicies.map(AppPolicy::packageName).distinct().size == this.appPolicies.size)
    }
}
