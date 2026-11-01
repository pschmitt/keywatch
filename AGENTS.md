# Agent instructions

KeyWatch is an Android app that keeps a key-event watcher running on a TV device. Follow the existing Java and Android service patterns and the README's setup notes.

## Working and validation

- Build with the repository's Gradle wrapper and documented Android SDK requirements.
- Keep device interaction out of automated checks; validate changes with available unit, build, and lint tasks.
- Before changing GitHub Actions, check whether [pschmitt/ci-workflows](https://github.com/pschmitt/ci-workflows) provides a reusable workflow or action. Preserve current behavior and check coverage, and use `@main` for shared workflow or action references.
