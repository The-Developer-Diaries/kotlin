/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.importmodels

import com.google.protobuf.util.JsonFormat
import org.jetbrains.kotlin.importmodels.proto.BaseModel
import org.jetbrains.kotlin.importmodels.proto.CompilationUnitModel
import org.jetbrains.kotlin.importmodels.proto.ProjectModel

public object KotlinImportModelProtoJson {
    private val printer = JsonFormat.printer()
        .preservingProtoFieldNames()
        .alwaysPrintFieldsWithNoPresence()

    private val parser = JsonFormat.parser()

    @JvmStatic
    public fun render(model: BaseModel): String = printer.print(model)

    @JvmStatic
    public fun render(model: ProjectModel): String = printer.print(model)

    @JvmStatic
    public fun render(model: CompilationUnitModel): String = printer.print(model)

    @JvmStatic
    public fun parseBaseModel(json: String): BaseModel = BaseModel.newBuilder()
        .also { parser.merge(json, it) }
        .build()

    @JvmStatic
    public fun parseProjectModel(json: String): ProjectModel = ProjectModel.newBuilder()
        .also { parser.merge(json, it) }
        .build()

    @JvmStatic
    public fun parseCompilationUnitModel(json: String): CompilationUnitModel = CompilationUnitModel.newBuilder()
        .also { parser.merge(json, it) }
        .build()
}
