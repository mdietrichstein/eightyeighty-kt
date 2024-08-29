# Intel 8080 Emulator

eightyeighty-kt is an Intel 8080 emulator, written in Kotlin. This emulator is fully functional and has been tested with the following test roms:

* 8080PRE.COM
* CPUTEST.COM
* TST8080.COM
* 8080EXM.COM

These ROMs are available in the `roms/cpu_tests` directory and were sourced from [https://altairclone.com/downloads/cpu_tests/](https://altairclone.com/downloads/cpu_tests/). The test runner is implemented in `jvmTest/kotlin/CpuTests`. This is a Kotlin multiplatform project, which means it can run on JVM, JavaScript, WASM, Android, and iOS. The emulator is designed to be platform-agnostic and doesn't require any external dependencies.


## Why Intel 8080?

The Intel 8080 is a popular 8-bit microprocessor that was used in many early computers and arcade games. One of its most notable uses was in the original Space Invaders arcade cabinet.

## Why Kotlin?

I wanted to create an emulator that could run on as many devices as possible, and Kotlin is a great language for this because it has mature support for various platforms through the Kotlin Multiplatform project. Also, there aren't that many Kotlin-based emulators out there, so I thought it would be a fun unique project to work on.

## Resources

The following resources were immensely helpful in implementing the emulator:

* [8080 Emulator by superzazu](https://github.com/superzazu/8080)
* [8080PRE Trace by superzazu](https://gist.github.com/superzazu/406b5fbc0b22f523560cf62108d6da54)
* [TST8080 Trace by superzazu](https://gist.github.com/superzazu/d8f792ea0f0faebd9b647c47e6ca604b)
* [e8080 Emulator by amensch](https://github.com/amensch/e8080/blob/master/e8080/)
* [Computer Archeology on Space Invaders Hardware](https://computerarcheology.com/Arcade/SpaceInvaders/Hardware.html)
* [8080A Bugbook Microcomputer Trainer](https://archive.org/details/8080abugbookmicr0000rony)
* [CP/M BIOS Source Listing](https://www.seasip.info/Cpm/bdos.html)
