include(ExternalProject)
set(TALLOC_SRC ${CMAKE_CURRENT_BINARY_DIR}/talloc-prefix/src/talloc)
set(TALLOC_BIN ${CMAKE_CURRENT_BINARY_DIR}/talloc-prefix/src/talloc-build)
set(TALLOC_STATIC_LIB ${TALLOC_BIN}/lib/libtalloc.a)
set(TALLOC_INCLUDE_DIRS ${TALLOC_BIN}/include)
# PRoot >= 5.5.0 hard-fails its build when pkg-config cannot find talloc.pc
# (src/GNUmakefile: `$(error talloc development files not found by pkg-config)`).
# The talloc build below deliberately does not run `make install` -- it
# assembles a static libtalloc.a by hand so that no shared libtalloc.so ends
# up next to the host's own copy -- so talloc.pc has to be provided here.
set(TALLOC_PKGCONFIG_DIR ${TALLOC_BIN}/lib/pkgconfig)

set(TALLOC_VERSION 2.4.1)

# It is written at configure time with file(WRITE) rather than from the
# ExternalProject install step: every path in it is already known here, and
# a shell "printf" would have to carry the newlines as "\n" escapes, which
# CMake expands into real newlines inside a quoted argument. That turns the
# command into a multi-line string and breaks the generated build.ninja
# (ninja then fails with "build.ninja:625: expected '=', got ':'").
file(WRITE "${TALLOC_PKGCONFIG_DIR}/talloc.pc"
"prefix=${TALLOC_BIN}
libdir=${TALLOC_BIN}/lib
includedir=${TALLOC_INCLUDE_DIRS}

Name: talloc
Description: A hierarchical pool based memory system with destructors
Version: ${TALLOC_VERSION}
Libs: -L${TALLOC_BIN}/lib -ltalloc
Cflags: -I${TALLOC_INCLUDE_DIRS}
URL: http://talloc.samba.org/
")

set(TALLOC_ARCHIVE talloc-${TALLOC_VERSION}.tar.gz)
# Hash of the official upstream archive. Every candidate URL must serve this
# exact file; URL_HASH makes CMake verify the archive so a stale, repacked or
# truncated download can never silently change the build inputs.
set(TALLOC_URL_HASH SHA256=410a547f08557007be0e88194f218868358edc0ab98c98ba8c167930db3d33f9)
set(TALLOC_CACHE_DIR ${CMAKE_SOURCE_DIR}/.cache/downloads)

# CI prefetches the archive into TALLOC_CACHE_DIR and keeps it in
# actions/cache. Use that single local path when available so a clean build
# needs no network. ExternalProject rejects a URL *list* that contains a local
# path ("At least one entry of URL is a path (invalid in a list)"), so the
# local copy and the remote fallbacks must be mutually exclusive.
if(EXISTS "${TALLOC_CACHE_DIR}/${TALLOC_ARCHIVE}")
    set(TALLOC_URLS "${TALLOC_CACHE_DIR}/${TALLOC_ARCHIVE}")
else()
    set(TALLOC_URLS
            https://download.samba.org/pub/talloc/${TALLOC_ARCHIVE}
            https://www.samba.org/ftp/talloc/${TALLOC_ARCHIVE}
            https://ftp.samba.org/pub/talloc/${TALLOC_ARCHIVE}
    )
endif()

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