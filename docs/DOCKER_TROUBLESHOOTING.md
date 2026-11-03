# Docker Desktop startup: inaccessible Secrets Engine socket

Incident verified on 2026-11-02.

Observed on Docker Desktop 4.97.0 for Windows / WSL2:
the CLI was installed but the Linux engine pipe did not exist.
The backend log showed `initializing Secrets Engine` failing to rename
`engine.sock` with `The file cannot be accessed by the system`.

The affected directory contained only a zero-byte `engine.sock` reparse point.
With Docker fully stopped, the directory was renamed to
`docker-secrets-engine.backup-20261102-203559` under the user's local AppData.
Docker Desktop then recreated the runtime directory and started successfully.
No images, volumes, containers or project files were deleted by this repair.

Verification: `docker version` returned Linux Engine 29.9.2; a disposable
PostgreSQL container started, all 140 Java tests passed, and the GeoQuest
Docker image built successfully.

For a recurrence, inspect the current logs and directory contents first. Stop
the application normally, confirm no backend process is running, and preserve
the affected runtime directory before starting Docker again. Do not apply this
workaround to unrelated paths or use a factory reset as the first response.

Upstream report: https://github.com/docker/for-win/issues/15064

The same root Dockerfile is used by the current Vercel deployment. Docker Desktop
is needed for local container work; it does not need to stay open on the developer's
computer for the published application to run. See [DEPLOYMENT.md](DEPLOYMENT.md)
for the current hosting and environment configuration.
