include(ExternalProject)
include(Talloc)
include(Unwind)
set(PROOT_SRC ${CMAKE_CURRENT_BINARY_DIR}/proot-prefix/src/proot/src)
set(PROOT_BIN ${CMAKE_CURRENT_BINARY_DIR}/proot-prefix/src/proot-build)
set(PROOT_C_FLAGS "-I${TALLOC_INCLUDE_DIRS} -I${PROOT_SRC} -D_GNU_SOURCE -I${UNWIND_INCLUDE_DIRS}")
set(PROOT_LINKER_FLAGS "-L${TALLOC_BIN}/lib -L${CMAKE_BINARY_DIR} -ltalloc -lunwind_ptrace")
# specific correct clang target to use
if(${CMAKE_ANDROID_ARCH_ABI} STREQUAL "arm64-v8a")
    set(PROOT_C_COMPILER ${ANDROID_TOOLCHAIN_ROOT}/bin/aarch64-linux-android${CMAKE_SYSTEM_VERSION}-clang)
else()
    set(PROOT_C_COMPILER ${ANDROID_TOOLCHAIN_ROOT}/bin/${CMAKE_ANDROID_ARCH_ABI}-linux-android${CMAKE_SYSTEM_VERSION}-clang)
endif()
ExternalProject_Add(
        proot
        GIT_REPOSITORY https://github.com/Ananbox/proot.git
        GIT_TAG 8c60d0287ce9ece3f21df1dc60de55e33f6bdba3
        GIT_SHALLOW 1
        # The pinned revision has no SIGSYS handler for the seccomp filter the
        # Android host installs on app processes: zygote dies on setregid
        # (syscall 143) and init restarts it forever. Apply the local fix,
        # unless the pinned revision already carries it (i.e. after the fix is
        # pushed upstream and GIT_TAG is bumped), so this stays idempotent.
        PATCH_COMMAND sh -c "grep -q handle_seccomp_trap src/tracee/event.c || patch -p1 --forward < ${CMAKE_SOURCE_DIR}/patches/proot-sigsys.patch"
        CONFIGURE_COMMAND cd ${PROOT_SRC} && make clean
        # HAS_SWIG/HAS_PYTHON_CONFIG are cleared on purpose: when a host swig and
        # python3-config are present (e.g. CI runners) proot tries to build its
        # Python extension with host includes and breaks the cross build.
        BUILD_COMMAND cd ${PROOT_SRC} && make V=1 HAS_SWIG= HAS_PYTHON_CONFIG= CC=${PROOT_C_COMPILER} LD=${PROOT_C_COMPILER} STRIP=${CMAKE_STRIP} OBJCOPY=${CMAKE_OBJCOPY} OBJDUMP=${CMAKE_OBJDUMP} CFLAGS=${PROOT_C_FLAGS} LDFLAGS=${PROOT_LINKER_FLAGS}
        # hacked: only lib*.so can be packed into apk
        INSTALL_COMMAND cd ${PROOT_SRC} && cp -f ./proot ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libproot.so
        DEPENDS talloc unwind_ptrace
)
