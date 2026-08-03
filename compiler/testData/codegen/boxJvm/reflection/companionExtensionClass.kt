// LANGUAGE: +CompanionBlocks +CompanionExtensions
// WITH_REFLECT

@file:OptIn(kotlin.reflect.ExperimentalCompanionExtensions::class)

class C {
    companion {
        fun blockFun() {}
        val blockVal = "hi"
        var blockVar = 30
    }
}

companion fun C.compFun() {}
companion val C.compVal = "ok"
companion val C.compLazyVal by lazy { 10 }
companion var C.compVar = 20

fun foo() {}

fun box(): String {
    if ((C::compFun).companionExtensionClass != C::class) return "fail compFun"
    if ((C::compVal).companionExtensionClass != C::class) return "fail compVal"
    if ((C::compLazyVal).companionExtensionClass != C::class) return "fail compLazyVal"
    if ((C::compVar).companionExtensionClass != C::class) return "fail compVar"
    if ((C::blockFun).companionExtensionClass != null) return "fail blockFun"
    if ((C::blockVal).companionExtensionClass != null) return "fail blockVal"
    if ((C::blockVar).companionExtensionClass != null) return "fail blockVar"
    if ((::foo).companionExtensionClass != null) return "fail foo"
    return "OK"
}
