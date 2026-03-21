# Release Process

## Overview

This project uses a GitHub Actions workflow to automatically build and publish a WAR file as a GitHub Release whenever a version tag is pushed.

## How to Create a Release

1. Ensure your changes are committed and pushed to the repository.
2. Create and push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Tags must follow the `v*` pattern (e.g., `v1.0.0`, `v2.1.0-beta`).

## What Happens

When a version tag is pushed, the workflow (`.github/workflows/release.yml`) will:

1. Check out the tagged commit.
2. Set up JDK 21 with Maven dependency caching.
3. Build the WAR file using `mvn -B clean package -DskipTests`.
4. Create a GitHub Release with the WAR artifact attached and auto-generated release notes.

## Viewing Releases

Published releases and their WAR artifacts are available on the repository's [Releases](../../releases) page.
