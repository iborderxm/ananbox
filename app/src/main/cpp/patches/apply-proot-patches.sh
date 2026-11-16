#!/bin/sh
# Apply the local patches to the PRoot source tree.
#
# CMake's ExternalProject accepts only ONE PATCH_COMMAND: a second one is
# merged into the first as an extra argument of "sh -c" and is therefore
# silently dropped.  Both patches have to be applied from a single command,
# hence this script.
#
# Each patch is guarded by a grep for a token that only exists once that
# patch has been applied, so re-running the build is a no-op.  The token
# must not be a word that already occurs in the unpatched upstream file
# (e.g. "HAS_CAP" does, inside a comment).

set -e

patch_dir="$1"

if ! grep -q handle_seccomp_trap src/tracee/event.c; then
    echo "=== applying proot-sigsys.patch ==="
    patch -p1 --forward < "$patch_dir/proot-sigsys.patch"
fi

if ! grep -q keepCaps src/extension/fake_id0/fake_id0.c; then
    echo "=== applying proot-android-adapt.patch ==="
    patch -p1 --forward < "$patch_dir/proot-android-adapt.patch"
fi