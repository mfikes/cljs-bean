# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]
### Changed
- Make the `Bean` `deftype` be private ([#4](https://github.com/mfikes/cljs-bean/pull/4))
- Faster 3-arity `reduce` by delegating to `reduce-kv` ([#5](https://github.com/mfikes/cljs-bean/pull/5))
- Faster `seq` when not consuming all ([#6](https://github.com/mfikes/cljs-bean/pull/6))

## [0.2.0] - 2019-06-10
### Changed
- Revise project name to “CLJS Bean” ([#1](https://github.com/mfikes/cljs-bean/issues/1))
- Faster `reduce-kv` ([#2](https://github.com/mfikes/cljs-bean/pull/2))

## [0.1.0] - 2019-06-09
### Added
- Initial release.

[Unreleased]: https://github.com/mfikes/cljs-bean/compare/0.2.0...HEAD
[0.2.0]: https://github.com/mfikes/cljs-bean/compare/0.1.0...0.2.0
[0.1.0]: https://github.com/mfikes/cljs-bean/compare/e2f9e4e3e960d9f4014609e1885765eb1c199050...0.1.0
