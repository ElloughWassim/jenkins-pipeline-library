/*
 * Copyright (c) 2026 ElloughWassim
 * SPDX-License-Identifier: MIT
 */
package io.github.elloughwassim.jenkins

/**
 * Small helpers for building safe shell commands.
 */
final class Shell implements Serializable {

    private static final long serialVersionUID = 1L

    private Shell() {
    }

    /**
     * Quotes a value for POSIX {@code sh} using single quotes.
     * {@code it's} becomes {@code 'it'"'"'s'}.
     */
    static String quote(Object value) {
        if (value == null) {
            return "''"
        }
        "'" + value.toString().replace("'", "'\"'\"'") + "'"
    }

    /** Quotes every element and joins them with spaces. */
    static String join(List values) {
        values.collect { quote(it) }.join(' ')
    }

    /**
     * Validates a list of Maven profile ids or goals: letters, digits, dot, dash,
     * underscore, colon and comma only.
     */
    static String requireToken(String field, Object value) {
        String text = value?.toString()?.trim()
        if (!text || !(text ==~ /^[A-Za-z0-9._:,-]+$/)) {
            throw new IllegalArgumentException("Invalid value for '${field}': '${value}'")
        }
        text
    }

    /** Validates a relative or absolute path made of safe characters only. */
    static String requirePath(String field, Object value) {
        String text = value?.toString()?.trim()
        if (!text || !(text ==~ /^[A-Za-z0-9._\/-]+$/) || text.contains('..')) {
            throw new IllegalArgumentException("Invalid value for '${field}': '${value}'")
        }
        text
    }
}
