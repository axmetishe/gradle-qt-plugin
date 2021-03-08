# Gradle QT Plugin

Gradle plugin for QT build process integration with
 [native plugins](https://docs.gradle.org/current/userguide/native_software.html).

[![Build Status](https://travis-ci.com/axmetishe/gradle-qt-plugin.svg?branch=master)](https://travis-ci.com/axmetishe/gradle-qt-plugin)
## Supported platforms
- Linux
- MacOS
- Windows

## Usage example
```groovy
plugins {
  id 'idea'
  id 'cpp-application'
  id 'org.platops.gradle.plugins.qt.gradle-qt-plugin' version '0.0.1-SNAPSHOT'
}

wrapper {
  distributionType = Wrapper.DistributionType.BIN
  gradleVersion = '6.7.1'
}

group = 'org.platops.gradle.plugins.qt.example'
version = '0.0.1-SNAPSHOT'

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
}

application {
  targetMachines = [
    machines.linux.x86_64,
    machines.macOS.x86_64,
    machines.windows.x86_64,
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
