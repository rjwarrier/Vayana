package com.vayana.core.database.repository

import javax.inject.Inject
import javax.inject.Singleton

/** This device's sync name, stamped on the reading sessions it records; the app keeps it current from settings. */
@Singleton
class LocalDeviceName @Inject constructor() {
    @Volatile var value: String? = null
}
