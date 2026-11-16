---
status: accepted
---

# Releases are git tags

A release of Board Keeper is a git tag `X.Y.Z`, without a `v`, on a commit on `main`. Its section in `CHANGELOG.md` becomes the text of its GitHub Release, and CI refuses to release a tag that has none. The version is read from git when the application is built, so no file carries it: `pom.xml` has a fixed `0-SNAPSHOT`, because Maven 3 needs a version there, and `package.json` has none. The tag and the files can therefore never disagree.

A release publishes the image `X.Y.Z` and moves `latest`. Every commit on `main` publishes `sha-…` and moves `snapshot`. An image calls itself a version only if the tag was there when it was built, and nothing had been changed since. So the `sha-…` image of a release's commit, built when that commit reached `main`, calls itself a Snapshot, though it holds the same code as the release.

## Considered options

- **Writing the version into `pom.xml` and `package.json` on each release**, as Maven usually does. Rejected: two sources can disagree, it takes a commit to set the number and another to move on to the next `-SNAPSHOT`, in thirteen files, and between releases git is needed anyway for the commit and its date.
- **`latest` for the newest commit on `main`**, as it was before the first release. Rejected because most people expect `latest` to be the newest release; `snapshot` now follows `main`.

## Consequences

- A build from a copy without git, such as a downloaded archive, doesn't know its version and says so.
- Releasing takes one commit, which turns the changelog's Unreleased section into the release's, and the tag on that commit. `docs/development.md` has the steps.
- CI needs the whole git history and the tags, to tell how many changes a Snapshot is past the newest release.
