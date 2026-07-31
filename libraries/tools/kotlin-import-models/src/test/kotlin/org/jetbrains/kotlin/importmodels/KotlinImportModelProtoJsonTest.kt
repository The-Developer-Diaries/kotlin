/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.importmodels

import com.google.gson.JsonParser
import org.jetbrains.kotlin.importmodels.proto.CompilationUnitId
import org.jetbrains.kotlin.importmodels.proto.CompilationUnitModel
import org.jetbrains.kotlin.importmodels.proto.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class KotlinImportModelProtoJsonTest {
    @Test
    fun `renders default fields`() {
        val model = CompilationUnitModel.newBuilder()
            .setModelId(KotlinImportModelIds.COMPILATION_UNIT)
            .setCompilationUnitId(CompilationUnitId.newBuilder().setValue(":|:|jvm|main"))
            .setCompilationName("main")
            .setPlatform(Platform.JVM)
            .setIsTest(false)
            .setCompileTaskPath(":compileKotlin")
            .build()

        val json = KotlinImportModelProtoJson.render(model)

        assertEquals(model, KotlinImportModelProtoJson.parseCompilationUnitModel(json))
        assertFalse(JsonParser.parseString(json).asJsonObject.get("is_test").asBoolean)
    }
}
