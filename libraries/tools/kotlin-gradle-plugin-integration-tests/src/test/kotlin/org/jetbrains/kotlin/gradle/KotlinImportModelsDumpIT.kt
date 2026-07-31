/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.testbase.*
import org.jetbrains.kotlin.importmodels.KotlinImportModelIds
import org.jetbrains.kotlin.importmodels.KotlinImportModelProtoJson
import org.jetbrains.kotlin.importmodels.proto.BaseModel
import org.jetbrains.kotlin.importmodels.proto.Capability
import org.jetbrains.kotlin.importmodels.proto.CompilationUnitId
import org.jetbrains.kotlin.importmodels.proto.CompilationUnitModel
import org.jetbrains.kotlin.importmodels.proto.Platform
import org.jetbrains.kotlin.importmodels.proto.ProjectModel
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@JvmGradlePluginTests
class KotlinImportModelsDumpIT : KGPBaseTest() {
    @GradleTest
    fun `dump task writes valid JVM models without compilation`(gradleVersion: GradleVersion) {
        project(
            projectName = "simpleProject",
            gradleVersion = gradleVersion,
            buildOptions = defaultBuildOptions.copy(
                configurationCache = BuildOptions.ConfigurationCacheValue.DISABLED,
            ),
        ) {
            build("dumpKotlinImportModels") {
                assertTasksExecuted(":dumpKotlinImportModels")
                assertTasksAreNotInTaskGraph(":compileKotlin", ":compileTestKotlin", ":compileDeployKotlin")
            }

            val firstIds = assertKotlinImportModelDump(buildOptions.kotlinVersion)

            val staleFile = projectPath.resolve("build/kotlin-import-models/proto/unexpected.bin").toFile()
            staleFile.writeBytes(byteArrayOf(1, 2, 3))

            build("dumpKotlinImportModels") {
                assertTasksExecuted(":dumpKotlinImportModels")
                assertTasksAreNotInTaskGraph(":compileKotlin", ":compileTestKotlin", ":compileDeployKotlin")
            }

            assertFalse(staleFile.exists())
            assertEquals(firstIds, assertKotlinImportModelDump(buildOptions.kotlinVersion))
        }
    }

    private fun TestProject.assertKotlinImportModelDump(kotlinVersion: String): List<CompilationUnitId> {
        val root = projectPath.resolve("build/kotlin-import-models").toFile()

        val base = BaseModel.parseFrom(root.resolve("proto/base.bin").readBytes())
        assertEquals(base, KotlinImportModelProtoJson.parseBaseModel(root.resolve("json/base.json").readText()))
        assertEquals(KotlinImportModelIds.BASE, base.modelId)
        assertEquals(kotlinVersion, base.kotlinGradlePluginVersion)
        assertEquals(listOf(Capability.KOTLIN_JVM), base.capabilitiesList)

        val project = ProjectModel.parseFrom(root.resolve("proto/project.bin").readBytes())
        assertEquals(project, KotlinImportModelProtoJson.parseProjectModel(root.resolve("json/project.json").readText()))
        assertEquals(KotlinImportModelIds.PROJECT_INFORMATION, project.modelId)
        assertEquals(":", project.projectPath)

        val main = readCompilationUnit(root, "main")
        val test = readCompilationUnit(root, "test")
        assertEquals(listOf(main.compilationUnitId, test.compilationUnitId), project.compilationUnitIdsList)
        assertCompilationUnit(main, "main", false, ":compileKotlin")
        assertCompilationUnit(test, "test", true, ":compileTestKotlin")
        return project.compilationUnitIdsList
    }

    private fun readCompilationUnit(root: File, name: String): CompilationUnitModel {
        val binary = CompilationUnitModel.parseFrom(root.resolve("proto/compilation-units/$name.bin").readBytes())
        val json = KotlinImportModelProtoJson.parseCompilationUnitModel(
            root.resolve("json/compilation-units/$name.json").readText(),
        )
        assertEquals(binary, json)
        return binary
    }

    private fun assertCompilationUnit(
        model: CompilationUnitModel,
        expectedName: String,
        expectedIsTest: Boolean,
        expectedCompileTaskPath: String,
    ) {
        assertEquals(KotlinImportModelIds.COMPILATION_UNIT, model.modelId)
        assertEquals(expectedName, model.compilationName)
        assertEquals(Platform.JVM, model.platform)
        assertEquals(expectedIsTest, model.isTest)
        assertEquals(expectedCompileTaskPath, model.compileTaskPath)
    }
}
