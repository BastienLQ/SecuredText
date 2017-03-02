# Silence [![Build Status](https://travis-ci.org/SilenceIM/Silence.svg?branch=master)](https://travis-ci.org/SilenceIM/Silence)

[Silence](https://silence.im) (formerly SMSSecure) is an SMS/MMS application that allows you to protect your privacy while communicating with friends.

Using Silence, you can send SMS messages and share media or attachments with complete privacy.

<a href="https://f-droid.org/repository/browse/?fdid=org.smssecure.smssecure" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"/></a>
<a href="https://play.google.com/store/apps/details?id=org.smssecure.smssecure" target="_blank">
<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="80"/></a>

Features:
* Easy. Silence works like any other SMS application. There's nothing to sign up for and no new service your friends need to join.
* Reliable. Silence communicates using encrypted SMS messages. No servers or internet connection required.
* Private. Silence uses the Axolotl encryption protocol to provide privacy for every message, every time.
* Safe. All messages are encrypted locally, so if your phone is lost or stolen, your messages are protected.
* Open Source. Silence is Free and Open Source, enabling anyone to verify its security by auditing the code.


## Project goals

This is a fork of [TextSecure](https://github.com/WhisperSystems/TextSecure) (now Signal) that aims to keep the SMS encryption that TextSecure removed [for a variety of reasons](https://whispersystems.org/blog/goodbye-encrypted-sms/).

Silence focuses on SMS and MMS. This fork aims to:

* Keep SMS/MMS encryption
* Drop Google services dependencies (push messages are not available in Silence)
* Integrate upstream bugfixes and patches from TextSecure

## Migrating from TextSecure to Silence

* In TextSecure, export a plaintext backup. Warning: the backup will **not** be encrypted.
* Install Silence.
* In Silence, import the plaintext backup (this will import the TextSecure backup if no Silence backup is found).
* If TextSecure v2.6.4 or earlier is installed, update or uninstall it so it doesn't conflict (can cause errors with key exchanges).
* Enjoy Silence!

Note: You will have to start new secured sessions with your contacts.

# Contributing

See [CONTRIBUTING.md](https://github.com/SilenceIM/Silence/blob/master/CONTRIBUTING.md) for how to contribute code, translations, or bug reports.

Instructions on how to setup a development environment and build Silence can be found in [BUILDING.md](https://github.com/SilenceIM/Silence/blob/master/BUILDING.md).

# Donate

We accept Bitcoin donations. This will help us to pay various costs (web hosting, domain name, etc.). Our Bitcoin address is:

```
1LoKZXg3bx6kfwAhEFQqS9pgeCE1CFMEJb
```

# Help
## Documentation
Looking for documentation? Check out the wiki of the original project:

https://github.com/WhisperSystems/TextSecure/wiki

## Chat
Have a question? Want to help out? Join our IRC channel: [#Silence on Freenode](https://webchat.freenode.net/?channels=Silence) or follow [@SilenceIM](https://twitter.com/SilenceIM) on Twitter.

# Legal
## Cryptography Notice

This distribution includes cryptographic software. The country in which you currently reside may have restrictions on the import, possession, use, and/or re-export to another country, of encryption software.
BEFORE using any encryption software, please check your country's laws, regulations and policies concerning the import, possession, or use, and re-export of encryption software, to see if this is permitted.
See <http://www.wassenaar.org/> for more information.

## License

Licensed under the GPLv3: http://www.gnu.org/licenses/gpl-3.0.html
