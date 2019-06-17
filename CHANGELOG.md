# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## Unreleased

### Fixed
- `TransientBean` should be private ([#17](https://github.com/mfikes/cljs-bean/issues/17))
- meta not preserved with `assoc`, `dissoc`, `conj` ([#20](https://github.com/mfikes/cljs-bean/issues/20))

## [0.5.0] - 2019-06-15
### Added
- Support for `count` ([#12](https://github.com/mfikes/cljs-bean/issues/12))

### Changed
- Ship with copies of private fns ([#15](https://github.com/mfikes/cljs-bean/issues/15))

## [0.4.0] - 2019-06-13
### Added
- `bean?` and `object` ([#10](https://github.com/mfikes/cljs-bean/issues/10))

## [0.3.0] - 2019-06-11
### Changed
- Ability to control how keys are mapped
- Make the `Bean` `deftype` be private
- Faster 3-arity `reduce` by delegating to `reduce-kv`
- Faster `seq` when not consuming all

## [0.2.0] - 2019-06-10
### Changed
- Revise project name to “CLJS Bean” ([#1](https://github.com/mfikes/cljs-bean/issues/1))
- Faster `reduce-kv`

## [0.1.0] - 2019-06-09
### Added
- Initial release.

[Unreleased]: https://github.com/mfikes/cljs-bean/compare/0.5.0...HEAD
[0.5.0]: https://github.com/mfikes/cljs-bean/compare/0.4.0...0.5.0
[0.4.0]: https://github.com/mfikes/cljs-bean/compare/0.3.0...0.4.0
[0.3.0]: https://github.com/mfikes/cljs-bean/compare/0.2.0...0.3.0
[0.2.0]: https://github.com/mfikes/cljs-bean/compare/0.1.0...0.2.0
[0.1.0]: https://github.com/mfikes/cljs-bean/compare/e2f9e4e3e960d9f4014609e1885765eb1c199050...0.1.0
