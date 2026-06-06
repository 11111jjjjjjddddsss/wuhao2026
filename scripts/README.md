# Project Scripts

This directory contains project-owned automation for Nongji Qiancha.

Commit and push scripts that help reproduce project operations, for example:
- deploy, rollback, readiness, health, and capacity checks
- cloud resource usage checks
- build, regression, and project-memory checks
- deterministic asset or compatibility helpers used by the app or backend

Do not commit:
- API keys, AccessKeys, model keys, passwords, signing passwords, or tokens
- local CLI credential files or personal tool configuration
- one-off scratch scripts that only work on one machine and are not part of the project workflow
- generated artifacts, logs, screenshots, or chat attachment caches

Scripts must read secrets from the local environment, local secret files outside the repo, or cloud-side configuration. Never hard-code secrets in this repository.
