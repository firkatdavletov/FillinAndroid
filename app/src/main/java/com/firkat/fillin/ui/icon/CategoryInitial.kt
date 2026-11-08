package com.firkat.fillin.ui.icon

import java.util.Locale

internal fun categoryInitialName(name: String, placeholder: String): String =
    name.trim().ifEmpty { placeholder }

internal fun categoryInitialLetter(name: String): String =
    String(Character.toChars(Character.toUpperCase(name.codePointAt(0))))

internal fun categoryInitialColorIndex(name: String, colorCount: Int): Int =
    Math.floorMod(name.lowercase(Locale.ROOT).hashCode(), colorCount)
