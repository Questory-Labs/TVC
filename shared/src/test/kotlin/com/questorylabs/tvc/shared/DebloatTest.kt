package com.questorylabs.tvc.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DebloatTest {
    @Test
    fun bundledCatalogParses() {
        val file = File("../debloat/packages.json")
        val list = DebloatCatalog.parse(file.readText())
        assertTrue(list.packages.isNotEmpty())
        assertEquals(list.packages.size, list.packages.map { it.pkg }.toSet().size)
        list.packages.forEach { assertTrue(it.pkg, ShellPolicy.isPackageName(it.pkg)) }
    }

    @Test
    fun parsesPmOutput() {
        val out = "package:com.a\npackage:com.b\r\n\njunk\n"
        assertEquals(setOf("com.a", "com.b"), DebloatCatalog.parseDisabledPackages(out))
    }
}
