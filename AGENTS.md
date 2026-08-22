# Solaris client agent instructions

- After changing client source, run `ant jars dll_64` and validate that the build succeeds.
- Always refresh the runnable package in `dist/solaris-ui-scale` before reporting a change as complete. Copy the newly built `build/haven.jar` there, and synchronize dependency JARs, Windows 64-bit DLLs, or required configuration files whenever those inputs change.
- Treat `dist/solaris-ui-scale` as the user-facing runnable build, not merely an optional archive.
- Do not copy accounts, credentials, cookies, `haven.conf`, map caches, screenshots, or logs into the distribution.
