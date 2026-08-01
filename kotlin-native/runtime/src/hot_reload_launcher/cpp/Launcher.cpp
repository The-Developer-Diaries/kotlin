#include "HotReload.hpp"

#include <arpa/inet.h>

#include <vector>
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <string_view>

extern "C" RUNTIME_WEAK const uint8_t* kaldoStartManifest;

struct KaldoManifestReader {
    /**
    * Parses the content of [kaldoStartManifest] and returns an array of paths
    * of objects to be loaded at runtime. The string views have a valid lifetime
    * since [kaldoStartManifest] is defined statically.
    */
    static std::vector<std::string_view> parse(const uint8_t* ptr) {
        if (!ptr) return {};

        // The Kotlin/Native compiler runs on the JVM, and by default it is big-endian.
        // Thus, to convert to the host's endianness we need to use ntohl.

        uint32_t raw_count = 0;
        std::memcpy(&raw_count, ptr, sizeof(uint32_t));
        uint32_t count = ntohl(raw_count);
        ptr += sizeof(uint32_t);

        std::vector<std::string_view> paths;
        paths.reserve(count);

        for (uint32_t i = 0; i < count; ++i) {
            uint32_t raw_len = 0;
            std::memcpy(&raw_len, ptr, sizeof(uint32_t));
            uint32_t len = ntohl(raw_len);
            ptr += sizeof(uint32_t);

            std::string_view object_path(reinterpret_cast<const char*>(ptr), len);
            paths.push_back(object_path);

            ptr += len;
        }

        return paths;
    }
};

// TODO(Gabriele): This function will be renamed into future. Right now, it does nothing
// TODO(Gabriele): since the hot-reload runtime is not implemented.
extern "C" RUNTIME_EXPORT void* KNHR_LoadObjCStubAddress(void* arg) {
    return nullptr;
}

extern "C" RUNTIME_EXPORT int Konan_main(const int argc, const char** argv) {

    Kotlin_initRuntimeIfNeeded();

    fprintf(stderr,
        "[warning] :: hot-reload runtime is not implemented yet, thus this program does nothing "
        "and will now terminate. Please use the 'closed' compilation scheme instead.\n");

    auto bootstrap_objs = KaldoManifestReader::parse(kaldoStartManifest);
    for (const auto& path : bootstrap_objs) {
        fprintf(stderr, "[debug] :: the runtime will load the object at '%s'\n", path.data());
    }

    Kotlin_shutdownRuntime();

    return 0;
}