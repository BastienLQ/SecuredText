## Translations

Please do not submit issues or pull requests for translation fixes. Anyone can update the translations in [Transifex](https://www.transifex.com/silence/silence/).
Please submit your corrections there.


## Submitting bug reports

1. Search our issues first to make sure this is not a duplicate.
2. (Optional) Search [Signal's issues](https://github.com/WhisperSystems/Signal-Android/issues).
3. Open an issue and follow the template carefully. If you can't get a debug log from Settings, you can use ADB to grab it: `adb logcat | grep $(adb shell ps | grep org.smssecure.smssecure | cut -c10-15)`.

## Submitting pull requests

All useful PRs are accepted. Please respect [our template](https://github.com/SilenceIM/Silence/blob/master/.github/PULL_REQUEST_TEMPLATE.md) and ask to merge your commits in `unstable` (PRs in `master` will be closed).
