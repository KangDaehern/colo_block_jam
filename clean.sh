#!/usr/bin/env bash
# Remove files that are not part of git from this project directory.
#
# Default = files matched by .gitignore (build/, .gradle/, .kotlin/, app/build/, *.apk, ...). These are
# build outputs and can always be regenerated with ./gradlew.
#
#   ./clean.sh              dry-run: list what would be deleted
#   ./clean.sh -y           delete ignored files (keeps local.properties, see --sdk)
#   ./clean.sh -y --sdk     also delete local.properties (Gradle then needs ANDROID_HOME)
#   ./clean.sh -y --all     ALSO delete untracked files that are NOT ignored (new sources that were never
#                           `git add`-ed!). Asks for an explicit confirmation. Use only when you are sure.
#
# Safety: the script refuses to run outside a git work tree and never touches tracked files.
set -euo pipefail

cd "$(dirname "$0")"
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "error: $(pwd) is not inside a git work tree; refusing to delete anything" >&2
    exit 1
fi

APPLY=0; ALL=0; SDK=0
for arg in "$@"; do
    case "$arg" in
        -y|--yes) APPLY=1 ;;
        --all) ALL=1 ;;
        --sdk) SDK=1 ;;
        -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
        *) echo "unknown option: $arg" >&2; exit 1 ;;
    esac
done

# Ignored paths (directories collapsed to one entry each), relative to this directory.
mapfile -t IGNORED < <(git ls-files --others --ignored --exclude-standard --directory -z . | tr '\0' '\n')
# Untracked but not ignored (never added to git), listed file by file so nothing hides behind a directory entry.
mapfile -t UNTRACKED < <(git ls-files --others --exclude-standard -z . | tr '\0' '\n')

# Never accept the project root itself or anything outside it as a deletion target.
safe_path() { [[ -n "$1" && "$1" != "." && "$1" != "./" && "$1" != ".."* && "$1" != /* ]]; }

TARGETS=()
for p in "${IGNORED[@]}"; do
    safe_path "$p" || continue
    if [[ "$p" == "local.properties" && $SDK -eq 0 ]]; then continue; fi
    TARGETS+=("$p")
done
if [[ $ALL -eq 1 ]]; then
    for p in "${UNTRACKED[@]}"; do safe_path "$p" && [[ "$p" != "clean.sh" ]] && TARGETS+=("$p"); done
fi

if [[ ${#TARGETS[@]} -eq 0 ]]; then
    echo "nothing to delete."
    exit 0
fi

echo "== $( [[ $APPLY -eq 1 ]] && echo 'DELETING' || echo 'dry-run (add -y to delete)' ) in $(pwd):"
for p in "${TARGETS[@]}"; do
    printf '  %8s  %s\n' "$(du -sh -- "$p" 2>/dev/null | cut -f1)" "$p"
done
if [[ $ALL -eq 0 && ${#UNTRACKED[@]} -gt 0 ]]; then
    echo "-- kept (untracked, not ignored; use --all to include): ${#UNTRACKED[@]} path(s)"
fi

[[ $APPLY -eq 1 ]] || exit 0

if [[ $ALL -eq 1 ]]; then
    read -r -p "This also deletes files never added to git. Type 'delete' to continue: " answer
    [[ "$answer" == "delete" ]] || { echo "aborted."; exit 1; }
fi

for p in "${TARGETS[@]}"; do
    rm -rf -- "$p"
done
echo "done."
