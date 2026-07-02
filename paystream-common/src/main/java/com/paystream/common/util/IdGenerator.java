package com.paystream.common.util;

import de.huxhorn.sulky.ulid.ULID;

/**
 * Generates Universally Unique Lexicographically Sortable Identifiers (ULIDs).
 * All entity primary keys across PayStream services are VARCHAR(26) ULIDs —
 * they are time-sortable, URL-safe, and collision-resistant.
 *
 * This class is a thin, thread-safe wrapper around {@link ULID}.
 */
public final class IdGenerator {

    // ULID generator is thread-safe
    private static final ULID ULID_GENERATOR = new ULID();

    private IdGenerator() {}

    /**
     * Generates a new ULID string of exactly 26 characters in Crockford Base32 encoding.
     */
    public static String generate() {
        return ULID_GENERATOR.nextULID();
    }
}
