# Gradle QT Plugin

Gradle plugin for QT build process integration with
[native plugins](https://docs.gradle.org/current/userguide/native_software.html).

## Supported configurations
| Plugin Version | JVM | Gradle |                                                                                                        Build Status                                                                                                         |
|:--------------:|:---:|:------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|
|     1.1.x      |  8  | 6.9.3  | [![Build Status](https://github.com/axmetishe/gradle-qt-plugin/actions/workflows/build.yml/badge.svg?branch=release/1.1.x)](https://github.com/axmetishe/gradle-qt-plugin/actions/workflows/build.yml?branch=release/1.1.x) |
|     1.2.x      | 11  | 7.6.4  | [![Build Status](https://github.com/axmetishe/gradle-qt-plugin/actions/workflows/build.yml/badge.svg?branch=release/1.2.x)](https://github.com/axmetishe/gradle-qt-plugin/actions/workflows/build.yml?branch=release/1.2.x) |
|     1.3.x      | 17  |  8.6   | [![Build Status](https://github.com/axmetishe/gradle-qt-plugin/actions/workflows/build.yml/badge.svg?branch=release/1.3.x)](https://github.com/axmetishe/gradle-qt-plugin/actions/workflows/build.yml?branch=release/1.3.x) |

## Supported platforms
- Linux
- MacOS
- Windows

## Usage example
Example repository - [Gradle QT Application Example](https://github.com/axmetishe/gradle-qt-application-example)

```groovy
plugins {
  id 'idea'
  id 'cpp-application'
  id 'org.platops.gradle.plugins.qt.gradle-qt-plugin' version '1.1.0'
}

wrapper {
  distributionType = Wrapper.DistributionType.BIN
  gradleVersion = '6.9.3'
}

group = 'org.platops.gradle.plugins.qt.example'
version = '1.0.0-SNAPSHOT'

model {
  buildTypes {
    debug
    release
  }
}

qt {
  modules = [
    'QtWidgets',
    'QtGui',
  ]

  deployParameters = [
    windows: [
      '--no-system-d3d-compiler',
      '--no-webkit2',
    ],
    macos: [
      '-no-strip'
    ]
  ]

  plistFile = 'src/main/resources/Info.plist'
}

application {
  targetMachines = [
    machines.windows.x86_64,
    machines.linux.x86_64,
    machines.macOS.x86_64,
  ]

  source.from file('src/main/cpp')
  privateHeaders.from file('src/main/headers')

  binaries.configureEach { CppBinary cppBinary ->
    CppCompile compileTask = cppBinary.compileTask.get()
    AbstractLinkTask linkTask = cppBinary.linkTask.get()

    compileTask.positionIndependentCode = true

    if (compileTask.targetPlatform.get().operatingSystem.isMacOsX()) {
      compileTask.compilerArgs.add('-std=gnu++11')
    }

    if (compileTask.targetPlatform.get().operatingSystem.isWindows()) {
      compileTask.compilerArgs.add("/MD${compileTask.name.toLowerCase().contains('debug') ? 'd' : ''}")
      linkTask.linkerArgs.add("msvcrt${linkTask.name.toLowerCase().contains('debug') ? 'd' : ''}.lib")
      linkTask.linkerArgs.addAll(['ole32.lib', 'user32.lib'])
      linkTask.linkerArgs.addAll(['/SUBSYSTEM:windows', '/ENTRY:mainCRTStartup'])
    }

    if (compileTask.name.toLowerCase().contains('debug')) {
      compileTask.macros.put('_DEBUG', null)
      compileTask.optimized = false
      compileTask.debuggable = true
    } else {
      compileTask.macros.put('NDEBUG', null)
      compileTask.optimized = true
      compileTask.debuggable = false
    }

    linkTask.debuggable = linkTask.name.toLowerCase().contains('debug')
  }
}
```

### How to use for iPhone OS
Gradle cpp-application plugin doesn't have support for other architectures at the moment, for iPhone library build
you can use toolchain redefinition:
```groovy
static String xcodeCompiler(String compilerCmd) {
  return "xcrun --sdk iphoneos --find ${compilerCmd}".execute().text.trim()
}

allprojects {
  model {
    toolChains {
      switch (OperatingSystem.current()) {
        case OperatingSystem.MAC_OS:

          Closure iosBaseArgs = { List<String> args ->
            List removeArgs = []
            ListIterator<String> iterator = args.listIterator()
            while (iterator.hasNext()) {
              String argument = iterator.next()
              if (argument.startsWith("-isystem")) {
                removeArgs.add(argument)
                removeArgs.add(args[iterator.nextIndex()])
              }
            }
            removeArgs.each { args.remove(it) }
            args.remove('-m32')
            args.addAll(["--sysroot=${"xcrun --sdk iphoneos --show-sdk-path".execute().text.trim()}"])
          }

          Closure iosCompilerArgs = { List<String> args ->
            args.addAll([
              '-std=c++14',
              '-arch', 'arm64',
              '-fmessage-length=0',
              '-fmacro-backtrace-limit=0',
              '-fobjc-arc',
              '-fpascal-strings',
              '-fno-common',
              '-fstrict-aliasing',
              '-miphoneos-version-min=10.0',
            ])
          }

          clang(Clang) {
            // iOS
            eachPlatform {
              if (it.platform.architecture.isI386()) {
                cCompiler.executable xcodeCompiler('clang')
                cppCompiler.executable xcodeCompiler('clang++')
                linker.executable xcodeCompiler('clang++')
                staticLibArchiver.executable xcodeCompiler('ar')
                assembler.executable xcodeCompiler('as')

                cCompiler.withArguments(iosBaseArgs)
                cCompiler.withArguments(iosCompilerArgs)
                cppCompiler.withArguments(iosBaseArgs)
                cppCompiler.withArguments(iosCompilerArgs)
                linker.withArguments(iosBaseArgs)
              } else {
                cCompiler.withArguments { List<String> args ->
                  args.addAll(['-mmacosx-version-min=10.14'])
                }
                cppCompiler.withArguments { List<String> args ->
                  args.addAll(['-mmacosx-version-min=10.14'])
                }
              }
            }
          }
          break

        case OperatingSystem.WINDOWS:
          'windows_x86_64'(VisualCpp) {
            eachPlatform() {
              cppCompiler.withArguments { List<String> args ->
                args.addAll(['/EHsc', '/DWIN32', '/D_WIN32' ])
              }
            }
          }
          break

        default:
          clang(Clang) {}
          break
      }
    }
  }
}
```
