# Gradle QT Plugin

Gradle plugin for QT build process integration with
 [native plugins](https://docs.gradle.org/current/userguide/native_software.html).

## Supported platforms
- Linux
- MacOS

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
    if (name.toLowerCase().contains('debug')) {
      compileTask.macros.put('_DEBUG', null)
      compileTask.optimized = false
      compileTask.debuggable = true
      linkTask.debuggable = true
    } else {
      compileTask.macros.put('NDEBUG', null)
      compileTask.optimized = true
      compileTask.debuggable = false
      linkTask.debuggable = false
    }
  }
}
```
