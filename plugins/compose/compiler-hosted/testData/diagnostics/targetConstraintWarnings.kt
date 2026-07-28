// RUN_PIPELINE_TILL: BACKEND

// MODULE: dep2

import androidx.compose.runtime.*

@ComposableTargetMarker(description = "W")
@Target(AnnotationTarget.TYPE)
annotation class WComposable()

@ComposableTargetMarker(description = "X")
@Target(AnnotationTarget.TYPE)
annotation class XComposable()

@Composable
fun WXWrapper(content: @Composable @WComposable @XComposable () -> Unit) {
    content()
}

// MODULE: dep1

import androidx.compose.runtime.*

@ComposableTargetMarker(description = "Y")
@Target(AnnotationTarget.TYPE)
annotation class YComposable()

@ComposableTargetMarker(description = "Z")
@Target(AnnotationTarget.TYPE)
annotation class ZComposable()

@Composable
fun YZWrapper(content: @Composable @YComposable @ZComposable () -> Unit) {
    content()
}

// MODULE: main(dep1, dep2)

import androidx.compose.runtime.Composable

@Composable fun YZInWX() {
    WXWrapper {
        <!COMPOSE_APPLIER_CALL_MISMATCH!>YZWrapper<!> {}
    }
}

@Composable fun WXInYZ() {
    YZWrapper {
        <!COMPOSE_APPLIER_CALL_MISMATCH!>WXWrapper<!> {}
    }
}

/* GENERATED_FIR_TAGS: annotationDeclaration, functionDeclaration, functionalType, lambdaLiteral, stringLiteral */
