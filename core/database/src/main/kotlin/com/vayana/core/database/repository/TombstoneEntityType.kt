package com.vayana.core.database.repository

/**
 * [value] is the wire/storage form (Room column, portable-snapshot JSON) and must stay stable across app
 * versions and devices; it intentionally does not track the Kotlin enum name.
 */
enum class TombstoneEntityType(val value: String) {
    BOOK("book"),
    SHELF("shelf"),
    VOCABULARY_CARD("vocabulary_card"),
    ANNOTATION("annotation"),
    SHELF_MEMBERSHIP("shelf_membership"),
    ;

    companion object {
        fun fromValue(value: String): TombstoneEntityType? = entries.find { it.value == value }
    }
}
