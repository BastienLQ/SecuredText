Building SMSSecure
=====================

Basics
------

SMSSecure uses [Gradle](http://gradle.org) to build the project and to maintain
dependencies.

Building SMSSecure
-------------------

The following steps should help you (re)build SMSSecure from the command line.

1. Checkout the source somewhere on your filesystem with

        git clone https://github.com/SMSSecure/SMSSecure.git

2. Make sure you have the [Android SDK](https://developer.android.com/sdk/index.html) installed somewhere on your system.
3. Ensure that the following packages are installed from the Android SDK manager:
    * Android SDK Build Tools
    * SDK Platform
    * Android Support Repository
    * Google Repository
4. Create a local.properties file at the root of your source checkout and add an sdk.dir entry to it.

        sdk.dir=\<path to your sdk installation\>

5. Execute Gradle:

        ./gradlew build

Visual assets
----------------------

Source assets tend to be large binary blobs, which are best stored outside of git repositories. We host ours in a [Pixelapse repository](https://www.pixelapse.com/opensmssecure/projects/signal-android/). Some source files are SVGs that can be auto-colored and sized using a tool like [android-res-utils](https://github.com/sebkur/android-res-utils).

Sample command for generating our audio placeholder image:

```bash
pngs_from_svg.py ic_audio.svg /path/to/SMSSecure/res/ 150 "#000" 0.54 _light
pngs_from_svg.py ic_audio.svg /path/to/SMSSecure/res/ 150 "#fff" 1.0 _dark
```

Setting up a development environment
------------------------------------

[Android Studio](https://developer.android.com/sdk/installing/studio.html) is the recommended development environment.

1. Install Android Studio.
2. Make sure the "Android Support Repository" is installed in the Android Studio SDK.
3. Make sure the latest "Android SDK build-tools" is installed in the Android Studio SDK.
4. Create a new Android Studio project. from the Quickstart pannel (use File > Close Project to see it), choose "Checkout from Version Control" then "git".
5. Paste the URL for the SMSSecure project when prompted (https://github.com/SMSSecure/SMSSecure.git).
6. Android studio should detect the presence of a project file and ask you whether to open it. Click "yes".
7. Default config options should be good enough.
8. Project initialisation and build should proceed.

Contributing code
-----------------

Code contributions should be sent via github as pull requests, from feature branches [as explained here](https://help.github.com/articles/using-pull-requests).
