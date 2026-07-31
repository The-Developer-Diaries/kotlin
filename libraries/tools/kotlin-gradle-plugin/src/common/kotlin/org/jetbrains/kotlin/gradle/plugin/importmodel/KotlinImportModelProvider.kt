/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.importmodel

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.kotlinJvmExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.internal.compatAccessor
import org.jetbrains.kotlin.gradle.utils.currentBuildId
import org.jetbrains.kotlin.importmodels.KotlinImportModelIds
import org.jetbrains.kotlin.importmodels.proto.*

internal class KotlinImportModelProvider(
    private val project: Project,
) {
    fun baseInformation(): BaseModel = BaseModel.newBuilder()
        .setModelId(KotlinImportModelIds.BASE)
        .setKotlinGradlePluginVersion(project.getKotlinPluginVersion())
        .addCapabilities(Capability.KOTLIN_JVM)
        .build()

    fun projectInformation(): ProjectModel = ProjectModel.newBuilder()
        .setModelId(KotlinImportModelIds.PROJECT_INFORMATION)
        .setProjectPath(project.path)
        .addAllCompilationUnitIds(supportedCompilations().map { compilationUnitId(it.name) })
        .build()

    fun compilationUnit(id: CompilationUnitId): CompilationUnitModel {
        val compilation = supportedCompilations().singleOrNull { compilationUnitId(it.name) == id }
            ?: error("Unknown Kotlin import compilation unit '${id.value}' for project '${project.path}'")

        return CompilationUnitModel.newBuilder()
            .setModelId(KotlinImportModelIds.COMPILATION_UNIT)
            .setCompilationUnitId(id)
            .setCompilationName(compilation.name)
            .setPlatform(Platform.JVM)
            .setIsTest(compilation.name == KotlinCompilation.TEST_COMPILATION_NAME)
            .setCompileTaskPath(compilation.compileTaskProvider.get().path)
            .build()
    }

    private fun supportedCompilations() = listOf(
        KotlinCompilation.MAIN_COMPILATION_NAME,
        KotlinCompilation.TEST_COMPILATION_NAME,
    ).map { name -> project.kotlinJvmExtension.target.compilations.getByName(name) }

    private fun compilationUnitId(compilationName: String): CompilationUnitId {
        val buildName = project.currentBuildId().compatAccessor(project).buildName
        val value = listOf(buildName, project.path, "jvm", compilationName).joinToString("|")
        return CompilationUnitId.newBuilder().setValue(value).build()
    }
}
