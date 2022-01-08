# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [1.8.0]
### Fixed
- Fix vector shapshot when adding non-primitives ([#87](https://github.com/mfikes/cljs-bean/issues/87))

## [1.7.0]
### Changed
- Avoid `contains?` in `-contains-key?` ([#79](https://github.com/mfikes/cljs-bean/issues/79))

## [1.6.0]
### Added
- Make JavaScript -> ClojureScript be user-hookable ([#67](https://github.com/mfikes/cljs-bean/issues/67))

## [1.5.0]
### Changed
- Add fast paths for primitive values ([#73](https://github.com/mfikes/cljs-bean/issues/73))

## [1.4.0]
### Fixed
- Writing `->clj` to transit doesn't work ([#70](https://github.com/mfikes/cljs-bean/issues/70))

## [1.3.0]
### Fixed
- Can assoc map or vector while remaining a bean ([#68](https://github.com/mfikes/cljs-bean/issues/68))

## [1.2.1] - 2019-07-02
### Fixed
- No need to refer exclude array ([#64](https://github.com/mfikes/cljs-bean/issues/64))
- Malformed cljdoc edn ([#63](https://github.com/mfikes/cljs-bean/issues/63))

## [1.2.0] - 2019-07-02
### Changed
- Separate out copied code ([#53](https://github.com/mfikes/cljs-bean/issues/53))
- Simplify unwrapping code ([#59](https://github.com/mfikes/cljs-bean/issues/59))
- Improve perf of `->js` by unwrapping non-recursive beans ([#60](https://github.com/mfikes/cljs-bean/issues/60))

### Fixed
- `TransientArrayVector` should be private ([#51](https://github.com/mfikes/cljs-bean/issues/51))
- `set-empty-colls!` doesn't need extra private meta ([#52](https://github.com/mfikes/cljs-bean/issues/52))

## [1.1.0] - 2019-06-29
### Added
- Recursive beans ([#46](https://github.com/mfikes/cljs-bean/issues/46))
- Support for `IIterable` ([#32](https://github.com/mfikes/cljs-bean/issues/32))

### Changed
- Re-licence under EPL ([#47](https://github.com/mfikes/cljs-bean/issues/47))
- Type hint return type of `object` ([#31](https://github.com/mfikes/cljs-bean/issues/31))
- Cache `js-keys` ([#36](https://github.com/mfikes/cljs-bean/issues/36))
- Remove unnecessary declares for `bean?` and `object` ([#43](https://github.com/mfikes/cljs-bean/issues/43))

### Fixed
- Count incorrect after `dissoc` non-key ([#38](https://github.com/mfikes/cljs-bean/issues/38))
- Typo in docstring for bean: "controls" ([#40](https://github.com/mfikes/cljs-bean/issues/40))

## [1.0.0] - 2019-06-20
### Changed
- Inline `-lookup` calls ([#27](https://github.com/mfikes/cljs-bean/issues/27))

## [0.6.0] - 2019-06-18
### Fixed
- `TransientBean` should be private ([#17](https://github.com/mfikes/cljs-bean/issues/17))
- meta not preserved with `assoc`, `dissoc`, `conj` ([#20](https://github.com/mfikes/cljs-bean/issues/20))
- Need to protect against objects with `fqn` field ([#22](https://github.com/mfikes/cljs-bean/issues/22))

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

[1.8.0]: https://github.com/mfikes/cljs-bean/compare/1.7.0...1.8.0
[1.7.0]: https://github.com/mfikes/cljs-bean/compare/1.6.0...1.7.0
[1.6.0]: https://github.com/mfikes/cljs-bean/compare/1.5.0...1.6.0
[1.5.0]: https://github.com/mfikes/cljs-bean/compare/1.4.0...1.5.0
[1.4.0]: https://github.com/mfikes/cljs-bean/compare/1.3.0...1.4.0
[1.3.0]: https://github.com/mfikes/cljs-bean/compare/1.2.1...1.3.0
[1.2.1]: https://github.com/mfikes/cljs-bean/compare/1.2.0...1.2.1
[1.2.0]: https://github.com/mfikes/cljs-bean/compare/1.1.0...1.2.0
[1.1.0]: https://github.com/mfikes/cljs-bean/compare/1.0.0...1.1.0
[1.0.0]: https://github.com/mfikes/cljs-bean/compare/0.6.0...1.0.0
[0.6.0]: https://github.com/mfikes/cljs-bean/compare/0.5.0...0.6.0
[0.5.0]: https://github.com/mfikes/cljs-bean/compare/0.4.0...0.5.0
[0.4.0]: https://github.com/mfikes/cljs-bean/compare/0.3.0...0.4.0
[0.3.0]: https://github.com/mfikes/cljs-bean/compare/0.2.0...0.3.0
[0.2.0]: https://github.com/mfikes/cljs-bean/compare/0.1.0...0.2.0
[0.1.0]: https://github.com/mfikes/cljs-bean/compare/e2f9e4e3e960d9f4014609e1885765eb1c199050...0.1.0
