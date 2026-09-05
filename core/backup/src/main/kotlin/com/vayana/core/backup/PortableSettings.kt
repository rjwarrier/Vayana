package com.vayana.core.backup

import com.vayana.core.datastore.settings.SettingsRegistry
import com.vayana.core.datastore.settings.StringSetting

object PortableSettings {
    val allowlist: Set<String> = SettingsRegistry.all
        .filterNot { setting -> setting is StringSetting && !setting.exportable }
        .mapTo(mutableSetOf()) { it.key }
}
