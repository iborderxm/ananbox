include(ExternalProject)
set(TALLOC_SRC ${CMAKE_CURRENT_BINARY_DIR}/talloc-prefix/src/talloc)
set(TALLOC_BIN ${CMAKE_CURRENT_BINARY_DIR}/talloc-prefix/src/talloc-build)
set(TALLOC_STATIC_LIB ${TALLOC_BIN}/lib/libtalloc.a)
set(TALLOC_INCLUDE_DIRS ${TALLOC_BIN}/include)

set(TALLOC_VERSION 2.4.1)
set(TALLOC_ARCHIVE talloc-${TALLOC_VERSION}.tar.gz)
# Hash of the official upstream archive. Every candidate URL must serve this
# exact file; URL_HASH makes CMake verify the archive so a stale, repacked or
# truncated download can never silently change the build inputs.
set(TALLOC_URL_HASH SHA256=410a547f08557007be0e88194f218868358edc0ab98c98ba8c167930db3d33f9)
set(TALLOC_CACHE_DIR ${CMAKE_SOURCE_DIR}/.cache/downloads)

# CI prefetches the archive into TALLOC_CACHE_DIR and keeps it in
# actions/cache. Prefer that local copy so a clean build needs no network.
# The upstream entry points are fallbacks, tried in order by CMake.
set(TALLOC_URLS)
if(EXISTS "${TALLOC_CACHE_DIR}/${TALLOC_ARCHIVE}")
    list(APPEND TALLOC_URLS "${TALLOC_CACHE_DIR}/${TALLOC_ARCHIVE}")
endif()
list(APPEND TALLOC_URLS
        https://download.samba.org/pub/talloc/${TALLOC_ARCHIVE}
        https://www.samba.org/ftp/talloc/${TALLOC_ARCHIVE}
        https://ftp.samba.org/pub/talloc/${TALLOC_ARCHIVE}
)

# specific correct clang target to use
if(${CMAKE_ANDROID_ARCH_ABI} STREQUAL "arm64-v8a")
    set(TALLOC_C_COMPILER ${ANDROID_TOOLCHAIN_ROOT}/bin/aarch64-linux-android${CMAKE_SYSTEM_VERSION}-clang)
else()
    set(TALLOC_C_COMPILER ${ANDROID_TOOLCHAIN_ROOT}/bin/${CMAKE_ANDROID_ARCH_ABI}-linux-android${CMAKE_SYSTEM_VERSION}-clang)
endif()
ExternalProject_Add(
        talloc
        URL ${TALLOC_URLS}
        URL_HASH ${TALLOC_URL_HASH}
        TIMEOUT 600
        CONFIGURE_COMMAND cd ${TALLOC_SRC} && ./configure CC=${TALLOC_C_COMPILER} LD=${TALLOC_C_COMPILER} --prefix=${TALLOC_BIN} --disable-rpath --disable-python --without-gettext --cross-compile --cross-answers=${CMAKE_CURRENT_SOURCE_DIR}/talloc/cross-answers.txt
        BUILD_COMMAND cd ${TALLOC_SRC} && make
        INSTALL_COMMAND cd ${TALLOC_SRC} && mkdir -p ${TALLOC_INCLUDE_DIRS} ${TALLOC_BIN}/lib && sh -c "${CMAKE_AR} rcs ${TALLOC_STATIC_LIB} bin/default/talloc.c*.o" && cp -f talloc.h ${TALLOC_INCLUDE_DIRS}
)