/*
 *  ==============================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *  ==============================================================
 */
package org.platops.gradle.plugins.qt.toolchains

import org.gradle.api.logging.Logger
import org.gradle.internal.os.OperatingSystem
import org.platops.gradle.plugins.qt.QTPluginExtension
import org.slf4j.LoggerFactory

import java.nio.file.Paths
import java.util.regex.Pattern

class QTToolchain {
  String sdkPath
  String binaries
  String includes
  String libraries
  String mocTool
  String uicTool
  String rccTool

  protected QTPluginExtension qtPluginExtension
  protected OperatingSystem operatingSystem

  private static final Logger LOGGER = LoggerFactory.getLogger(this.simpleName) as Logger

  private static final String SDK_PATH_VARIABLE = 'QT_PATH'
  private static final HashMap<String, Pattern> SDK_LAYOUT_PATTERNS = [
    binaries : ~/(moc|rcc|uic)?(-qt\d|\.exe)?/,
    includes : ~/((Qt)(\w)+)/,
    libraries: ~/((lib)?(Qt)(\w)+(\.)(lib|so|a))/,
    versions: ~/((\d)(\.)?)+/
  ]
  private static final String SDK_TOOLCHAIN_PREFIX = 'gcc_64'
  private static final List<String> DEFAULT_BINARY_PATH = ['/usr/bin', '/bin']

  QTToolchain(QTPluginExtension qtPluginExtension) {
    this.qtPluginExtension = qtPluginExtension
    this.operatingSystem = OperatingSystem.current()

    String sdkProposedPath = determineAvailableSDKPath()

    if (sdkProposedPath) {
      LOGGER.info("Initialize QT Toolchain with SDK provided at '${sdkProposedPath}' ")
      this.sdkPath = sdkProposedPath

      HashMap<String, String> sdkLayout = (sdkProposedPath in DEFAULT_BINARY_PATH) ?
        osQTLayout(sdkProposedPath) :
        produceDefaultQTLayout(sdkProposedPath)

      this.binaries = sdkLayout.binaries
      this.libraries = sdkLayout.libraries
      this.includes = sdkLayout.includes

      String qtToolsSuffix = sdkLayout.containsKey('suffix') ? sdkLayout.suffix : ''
      LOGGER.info("Tools suffix determined as '${qtToolsSuffix}'")

      this.mocTool = Paths.get(binaries, "moc${qtToolsSuffix}")
      this.uicTool = Paths.get(binaries, "uic${qtToolsSuffix}")
      this.rccTool = Paths.get(binaries, "rcc${qtToolsSuffix}")

      LOGGER.info("Configured sdkPath: '${sdkPath}'")
      LOGGER.info("Configured binaries: '${binaries}'")
      LOGGER.info("Configured includes: '${includes}'")
      LOGGER.info("Configured libraries: '${libraries}'")
      LOGGER.info("Configured mocTool: '${mocTool}'")
      LOGGER.info("Configured uicTool: '${uicTool}'")
      LOGGER.info("Configured rccTool: '${rccTool}'")
    } else {
      throw new Exception(
        """
          Couldn't find valid QT SDK paths.
          Please configure SDK using one of the following methods available:
           - add SDK 'bin' directory into PATH variable to make 'moc', 'uic', 'rcc' available from it
           - define '${SDK_PATH_VARIABLE}' environment variable
        """
      )
    }
  }

  List<File> processQTModulesIncludes(List<String> modules) {
    List<File> includeList = []
    modules.each { String module ->
      findDirectories(this.includes, ~/(${module}.*)/).each { File moduleInclude ->
        LOGGER.info("Adding '${moduleInclude.path}' as include directory.")
        includeList.add(moduleInclude)
      }
    }

    return includeList
  }

  List<File> processQTModulesLibraries(List<String> modules) {
    List<File> librariesList = []
    modules.each { String module ->
      String librarySuffix = OperatingSystem.LINUX.sharedLibrarySuffix
      Pattern libPattern = ~/(lib${module.take(2)})(\d)?(${module.drop(2)})${librarySuffix}/
      File library = findFiles(this.libraries, libPattern).sort().first()
      LOGGER.info("Adding '${library.name}' as library.")
      librariesList.add(library)
    }

    return librariesList
  }

  private static String determineAvailableSDKPath() {
    String availablePath = ''
    String configuredSDKPath = System.getenv(SDK_PATH_VARIABLE) ?: null

    LOGGER.info("Checking PATH for required binaries")
    List<String> referencePaths = getBinaryPathFromEnvVariable(SDK_LAYOUT_PATTERNS.binaries)
    if (referencePaths) {
      availablePath = referencePaths.first()
      LOGGER.info("Found binaries at ${availablePath}, we can use them as reference")
    }

    if (configuredSDKPath) {
      LOGGER.info("${SDK_PATH_VARIABLE} is defined with value '${configuredSDKPath}'")
      LOGGER.info("Environment variable will takes precedence over PATH")
      try {
        validateSDKConfiguration(produceDefaultQTLayout(configuredSDKPath))
        availablePath = configuredSDKPath
      } catch(Exception exception) {
        LOGGER.warn(
          """
            SDK configured via environment variable is not a valid QT installation.
            Please check '${SDK_PATH_VARIABLE}' variable and installation at '${configuredSDKPath}'.
            Error: '${exception.message}'

            ${!availablePath.isEmpty() ? "WARNING: We will try to use installation at '${availablePath}'" : ''}
          """
        )
      }
    }

    return availablePath
  }

  private static String getQtBinariesSuffix(String binariesPath) {
    LOGGER.info("Determine the actual binary name used by SDK at ${binariesPath}")
    List<File> qtBinaries = findFiles(binariesPath, SDK_LAYOUT_PATTERNS.binaries)

    //noinspection GroovyAssignabilityCheck
    return ((qtBinaries.first().name =~ SDK_LAYOUT_PATTERNS.binaries)[0][2]) ?: ''
  }

  private static HashMap<String, String> osQTLayout(String sdkPath) {
    LOGGER.info("Will try to proceed with default OS layout")
    HashMap<String, String> layout = [
      binaries: sdkPath,
      libraries: '/usr/lib64',
    ]

    String qtToolsSuffix = getQtBinariesSuffix(sdkPath)
    layout.putAll([
      includes: "/usr/include/${qtToolsSuffix.replace('-', '')}",
      suffix: qtToolsSuffix
    ])

    return layout
  }

  private static HashMap<String, String> produceDefaultQTLayout(String sdkPath) {
    HashMap<String, String> layout = [:]
    LOGGER.info("Will try to proceed with QT layout")
    List<File> sdkVersionDirs = findDirectories(new File(sdkPath), SDK_LAYOUT_PATTERNS.versions).sort()
    if (sdkVersionDirs) {
      sdkVersionDirs.forEach { LOGGER.info("Found '${it.name}' at '${it.path}'") }
      File newestSDKDir = sdkVersionDirs.last()
      LOGGER.info("We will take the newest available: '${newestSDKDir.name}'")

      String sdkToolchainPrefix = Paths.get(newestSDKDir.path, SDK_TOOLCHAIN_PREFIX)
      layout.putAll([
        binaries: Paths.get(sdkToolchainPrefix, 'bin').toString(),
        libraries: Paths.get(sdkToolchainPrefix, 'lib').toString(),
        includes: Paths.get(sdkToolchainPrefix, 'include').toString(),
      ])
    } else {
      throw new Exception("Incorrect SDK layout found at '${sdkPath}'")
    }

    return layout
  }

  private static List<String> getBinaryPathFromEnvVariable(Pattern binaryPattern) {
    List<String> binaryPathString = System.getenv('PATH')
      .split(Pattern.quote(File.pathSeparator))
      .findAll { String searchPath ->
        findFiles(searchPath, binaryPattern)
      }

    return binaryPathString
  }

  private static List<File> findFiles(File searchDir, Pattern pattern) {
    return searchDir.listFiles().findAll { it.name.matches(pattern) }
  }

  private static List<File> findFiles(String searchDir, Pattern pattern) {
    return findFiles(new File(searchDir), pattern)
  }

  private static List<File> findDirectories(File searchDir, Pattern pattern) {
    return findFiles(searchDir, pattern).findAll { it.directory }
  }

  private static List<File> findDirectories(String searchDir, Pattern pattern) {
    return findDirectories(new File(searchDir), pattern)
  }

  private static void validateSDKConfiguration(HashMap<String, String> sdkLayout) {
    [
      'binaries',
      'includes',
      'libraries',
    ].each { String pathType ->
      String path = sdkLayout[pathType]
      File directory = new File(path)
      LOGGER.info("Checking '${directory.path}'")
      if (!directory.exists() || findFiles(directory, SDK_LAYOUT_PATTERNS[pathType]).isEmpty()) {
        throw new Exception(
          """
            Configured SDK '${pathType}' path at '${directory.path}' doesn't contains required files.
            Please check QT SDK installation
          """
        )
      }
    }
  }
}
