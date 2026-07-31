/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.importmodel

import org.jetbrains.kotlin.gradle.util.buildProjectWithJvm
import org.jetbrains.kotlin.gradle.util.buildProjectWithMPP
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class KotlinImportModelsDumpTaskTest {
    @Test
    fun `Kotlin JVM plugin registers the diagnostic dump task`() {
        val project = buildProjectWithJvm()
        project.evaluate()

        val task = assertIs<KotlinImportModelsDumpTask>(project.tasks.getByName("dumpKotlinImportModels"))
        assertEquals("ide", task.group)
        assertEquals(
            "Dumps Kotlin import models as binary protobuf and diagnostic ProtoJSON",
            task.description,
        )
    }

    @Test
    fun `Kotlin Multiplatform plugin does not register the dump task`() {
        val project = buildProjectWithMPP()
        project.evaluate()

        assertNull(project.tasks.findByName("dumpKotlinImportModels"))
    }
}
