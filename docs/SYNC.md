# Manual Upstream Sync Routine

This document describes how to manually synchronize your fork (`feature/no-repeat`) with upstream `YMusicV4`.

## When to Use Manual Sync
- When `.github/workflows/sync-upstream.yml` fails due to a merge conflict.
- When upstream updates workflow files under `.github/workflows/` (GitHub Actions' `GITHUB_TOKEN` cannot push modified workflow files without special elevated permissions).
- When you want to pull upstream improvements immediately without waiting for the daily scheduled job.

## Step-by-Step Routine

1. **Ensure Working Tree is Clean**:
   ```bash
   git status
   ```

2. **Fetch Latest Upstream Commits**:
   ```bash
   git fetch upstream master
   ```

3. **Fast-Forward Local `master` Mirror**:
   ```bash
   git checkout master
   git merge --ff-only upstream/master
   git push origin master
   ```

4. **Merge `master` into `feature/no-repeat`**:
   ```bash
   git checkout feature/no-repeat
   git merge master
   ```

5. **Resolve Conflicts (if any)**:
   - Git rerere (`git config rerere.enabled true`) will assist with repeating resolutions.
   - For any conflicted file, resolve the conflict markers (`<<<<<<<`, `=======`, `>>>>>>>`).
   - Re-stage resolved files:
     ```bash
     git add <resolved-files>
     git commit -m "Merge branch 'master' into feature/no-repeat"
     ```

6. **Build and Test Verification**:
   ```bash
   ./gradlew :player:allTests :commonUi:allTests
   ```

7. **Push to Your Fork**:
   ```bash
   git push origin feature/no-repeat
   ```
