/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.driver.phases.split

import kotlinx.cinterop.toCValues
import llvm.LLVMAddGlobal
import llvm.LLVMConstBitCast
import llvm.LLVMConstStringInContext
import llvm.LLVMContextRef
import llvm.LLVMInt8TypeInContext
import llvm.LLVMLinkage
import llvm.LLVMModuleCreateWithNameInContext
import llvm.LLVMModuleRef
import llvm.LLVMPointerType
import llvm.LLVMSetAlignment
import llvm.LLVMSetDataLayout
import llvm.LLVMSetGlobalConstant
import llvm.LLVMSetInitializer
import llvm.LLVMSetLinkage
import llvm.LLVMSetSection
import llvm.LLVMTypeOf
import org.jetbrains.kotlin.backend.common.phaser.PhaseEngine
import org.jetbrains.kotlin.backend.konan.DependenciesTrackingResult
import org.jetbrains.kotlin.backend.konan.NativeGenerationState
import org.jetbrains.kotlin.backend.konan.ResolvedCacheBinaries
import org.jetbrains.kotlin.backend.konan.driver.NativeBackendPhaseContext
import org.jetbrains.kotlin.backend.konan.driver.phases.ObjectFilesPhase
import org.jetbrains.kotlin.backend.konan.driver.phases.ObjectFilesPhaseInput
import org.jetbrains.kotlin.backend.konan.driver.phases.WriteBitcodeFileInput
import org.jetbrains.kotlin.backend.konan.driver.phases.WriteBitcodeFilePhase
import org.jetbrains.kotlin.backend.konan.driver.phases.createBitcodeFile
import org.jetbrains.kotlin.backend.konan.driver.phases.runAndMeasurePhase
import org.jetbrains.kotlin.backend.konan.resolveCacheBinaries
import org.jetbrains.kotlin.konan.TempFiles
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Path

private const val MANIFEST_MODULE_NAME: String = "manifest"

private val FORCE_LOADED_CACHES_FQN = setOf(
        "kotlin.native.internal", // This points to the Runtime code
        "skiko",
        "libkotlin",
        "libstdlib-cache"
)

/**
 * Does this library need to be force-loaded into the host executable?
 */
private val String.isForceLoadCache: Boolean
    get() = FORCE_LOADED_CACHES_FQN.any { this@isForceLoadCache.contains(it) }

internal data class BootstrapCompilationMetadata(
        val forceLoadCaches: List<String>,
        val cachesToLoadAtRuntime: List<String>,
        val resolvedCaches: ResolvedCacheBinaries
)

private const val KALDO_START_MANIFEST_DATA_NAME: String = "kaldoStartManifestData"
private const val KALDO_START_MANIFEST_NAME: String = "kaldoStartManifest"

/**
 * Write the bootstrap manifest into the *read-only* data section of the given module.
 *
 * At the time of writing, this function works mainly for Darwin (i.e., macOS, iOS, ...).
 */
private fun LLVMModuleRef.embedBootstrapManifest(context: LLVMContextRef, manifest: BootstrapCompilationMetadata) {
    val byteArrayStream = ByteArrayOutputStream().apply {
        DataOutputStream(this).use { out ->
            out.writeInt(manifest.cachesToLoadAtRuntime.size)
            for (path in manifest.cachesToLoadAtRuntime) {
                val pathBytes = path.toByteArray(Charsets.UTF_8)
                out.writeInt(pathBytes.size)
                out.write(pathBytes)
            }
        }
    }

    val manifestBytes = byteArrayStream.toByteArray()
    val dataInitializer = LLVMConstStringInContext(context, manifestBytes.toCValues(), manifestBytes.size, 1)
    val dataGlobal = LLVMAddGlobal(this, LLVMTypeOf(dataInitializer), KALDO_START_MANIFEST_DATA_NAME)
    LLVMSetInitializer(dataGlobal, dataInitializer)
    LLVMSetGlobalConstant(dataGlobal, 1)
    LLVMSetLinkage(dataGlobal, LLVMLinkage.LLVMInternalLinkage)
    LLVMSetAlignment(dataGlobal, 8)
    // TODO(Gabriele): here we should switch based on the OS
    LLVMSetSection(dataGlobal, "__TEXT,__const")

    val ptrType = LLVMPointerType(LLVMInt8TypeInContext(context), 0)
    val ptrGlobal = LLVMAddGlobal(this, ptrType, KALDO_START_MANIFEST_NAME)
    LLVMSetInitializer(ptrGlobal, LLVMConstBitCast(dataGlobal, ptrType))
    LLVMSetGlobalConstant(ptrGlobal, 1)
    LLVMSetLinkage(ptrGlobal, LLVMLinkage.LLVMExternalLinkage)
}

internal fun <C : NativeBackendPhaseContext> PhaseEngine<C>.resolveBootstrapMetadata(
        dependenciesTrackingResult: DependenciesTrackingResult,
): BootstrapCompilationMetadata {
    // Resolve cache binaries (stdlib, platform libs, etc.) that the host must link against
    val resolvedCaches = resolveCacheBinaries(context.config.cachedLibraries, dependenciesTrackingResult)
    val [forceLoadCaches, jitCaches] = resolvedCaches.static.partition { it.isForceLoadCache }
    return BootstrapCompilationMetadata(forceLoadCaches, jitCaches, resolvedCaches)
}

/**
 * The split-compilation manifest defines with objects from the cache should be loaded
 * at the start of the host.
 */
internal fun PhaseEngine<NativeGenerationState>.generateManifestObject(
        manifest: BootstrapCompilationMetadata,
        temporaryFiles: TempFiles,
): Path {
    val manifestObjectPath = temporaryFiles.create(MANIFEST_MODULE_NAME, ".o")
    val manifestBitcodePath = temporaryFiles.createBitcodeFile(MANIFEST_MODULE_NAME)
    val manifestModule = LLVMModuleCreateWithNameInContext(MANIFEST_MODULE_NAME, context.llvmContext)!!.apply {
        LLVMSetDataLayout(this, context.runtime.dataLayout)
        embedBootstrapManifest(context.llvmContext, manifest)
    }
    runAndMeasurePhase(WriteBitcodeFilePhase, WriteBitcodeFileInput(manifestModule, manifestBitcodePath))
    runAndMeasurePhase(ObjectFilesPhase, ObjectFilesPhaseInput(manifestBitcodePath, manifestObjectPath))
    return manifestObjectPath
}