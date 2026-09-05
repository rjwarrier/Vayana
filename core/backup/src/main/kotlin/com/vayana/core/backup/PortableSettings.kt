package com.vayana.core.backup

import com.vayana.core.datastore.settings.SettingsRegistry

object PortableSettings {
    val allowlist: Set<String> = SettingsRegistry.all.mapTo(mutableSetOf()) { it.key }
}
