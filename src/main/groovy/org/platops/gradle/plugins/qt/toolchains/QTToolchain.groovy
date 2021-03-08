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

abstract class QTToolchain {
  String sdkPath
  String binaries
  String includes
  String libraries
  String mocTool
  String uicTool
  String rccTool
  Boolean brew = false
  List<String> compilerArgs = []
  List<String> linkerArgs = []

  protected QTPluginExtension qtPluginExtension

  protected static final Logger LOGGER = LoggerFactory.getLogger(this.simpleName) as Logger

  protected static final String SDK_PATH_VARIABLE = 'QT_PATH'
  protected static final Map<String, Pattern> SDK_LAYOUT_PATTERNS = [
    binaries : ~/(moc|rcc|uic)?(-qt\d|\.exe)?/,
    includes : ~/((Qt)(\w)+)/,
    libraries: ~/((lib)?(Qt)(\w)+(\.)(lib|so|a))/,
    versions: ~/((\d)(\.)?)+/,
    toolchains: ~/(\w+_\d+)/
  ]
  protected static final String SDK_TOOLCHAIN_PREFIX = 'gcc_64'
  protected static final List<String> DEFAULT_BINARY_PATH = ['/usr/bin', '/bin']

  QTToolchain(QTPluginExtension qtPluginExtension) {
    this.qtPluginExtension = qtPluginExtension

    configureToolchain()
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

  List<File> processQTModulesLibraries(List<String> modules, Boolean debuggable = false) {
    List<File> librariesList = []
    modules.each { String module ->
      String librarySuffix = OperatingSystem.LINUX.sharedLibrarySuffix
      Pattern libPattern = ~/(lib${module.take(2)})(\d)?(${module.drop(2)})${librarySuffix}/
      List<File> availableLibraries = findFiles(this.libraries, libPattern)
      if (availableLibraries) {
        File library = findFiles(this.libraries, libPattern).sort().first()
        if (library.exists()) {
          LOGGER.info("Adding '${library.name}' as library.")
          librariesList.add(library)
        }
      }
    }

    return librariesList
  }

  protected configureToolchain() {
    File sdkProposedPath = new File(determineAvailableSDKPath())
    if (sdkProposedPath.exists()) {
      Map<String, String> sdkLayout = initializeSDK(sdkProposedPath)

      this.binaries = sdkLayout.binaries
      this.libraries = sdkLayout.libraries
      this.includes = sdkLayout.includes

      platformSpecificArgs()

      String qtToolsSuffix = sdkLayout.containsKey('suffix') ? sdkLayout.suffix : ''
      LOGGER.info("Tools suffix determined as '${qtToolsSuffix}'")

      this.mocTool = sdkLayout.containsKey('moc') ? sdkLayout.moc :
        Paths.get(binaries, "moc${qtToolsSuffix}")
      this.uicTool = sdkLayout.containsKey('uic') ? sdkLayout.uic :
        Paths.get(binaries, "uic${qtToolsSuffix}")
      this.rccTool = sdkLayout.containsKey('rcc') ? sdkLayout.rcc :
        Paths.get(binaries, "rcc${qtToolsSuffix}")

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

  protected Map<String, String> initializeSDK(File sdkProposedPath) {
    LOGGER.info("Initialize QT Toolchain with SDK provided at '${sdkProposedPath}'")
    this.sdkPath = sdkProposedPath
    Map<String, String> sdkLayout = (sdkProposedPath.path in DEFAULT_BINARY_PATH)
      ? osSpecificQTLayout(sdkProposedPath)
      : produceDefaultQTLayout(sdkProposedPath)

    return sdkLayout
  }

  protected void platformSpecificArgs() {}

  protected static String determineAvailableSDKPath() {
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

  protected static String getQtBinariesSuffix(List<File> binaries) {
    //noinspection GroovyAssignabilityCheck
    return ((binaries.first().name =~ SDK_LAYOUT_PATTERNS.binaries)[0][2]) ?: ''
  }

  protected Map<String, String> osSpecificQTLayout(File sdkPath) {
    LOGGER.info("Will try to proceed with default OS layout")
    Map<String, String> layout = [
      binaries: sdkPath.path,
      libraries: '/usr/lib64',
    ]
    layout.putAll(getQTBinaries(sdkPath.path, SDK_LAYOUT_PATTERNS.binaries))
    layout.put('includes', "/usr/include/${layout.suffix.replace('-', '')}")

    validateSDKConfiguration(layout)

    return layout
  }

  protected static Map<String, String> getQTBinaries(String sdkPath, Pattern binariesPattern) {
    List<File> qtBinaries = findFiles(sdkPath, binariesPattern)
    String qtToolsSuffix = getQtBinariesSuffix(qtBinaries)

    return [
      rcc: qtBinaries.find { it.name.contains('rcc') }.path,
      moc: qtBinaries.find { it.name.contains('moc') }.path,
      uic: qtBinaries.find { it.name.contains('uic') }.path,
      suffix: qtToolsSuffix
    ]
  }

  protected static Map<String, String> produceDefaultQTLayout(String sdkPath) {
    return produceDefaultQTLayout(new File(sdkPath))
  }

  protected static Map<String, String> produceDefaultQTLayout(File sdkPath) {
    Map<String, String> layout = [:]
    String sdkToolchainPrefix
    LOGGER.info("Will try to proceed with default QT layout")

    File toolchainDir = sdkPath.parentFile
    String toolchainPrefix = toolchainDir.name.matches(SDK_LAYOUT_PATTERNS.toolchains)
      ? toolchainDir.name
      : SDK_TOOLCHAIN_PREFIX
    LOGGER.info("Referenced toolchain determined as '${toolchainPrefix}'")

    File sdkRootPath = toolchainDir.parentFile.parentFile
    LOGGER.info("QT SDK root path at '${sdkRootPath.path}'")

    LOGGER.info("Attempt to find available toolchain versions")
    List<File> sdkVersionDirs = findDirectories(sdkRootPath, SDK_LAYOUT_PATTERNS.versions).sort()
    if (sdkVersionDirs) {
      sdkVersionDirs.forEach { LOGGER.info("Found '${it.name}' at '${it.path}'") }
      File newestSDKDir = sdkVersionDirs.last()
      LOGGER.info("We will take the newest available: '${newestSDKDir.name}'")
      if (!new File("${newestSDKDir.path}/${toolchainPrefix}").exists()) {
        LOGGER.info("Referenced toolchain '${toolchainPrefix}' is not available for '${newestSDKDir.name}'")
        List<File> sdkToolchains = findDirectories(newestSDKDir, SDK_LAYOUT_PATTERNS.toolchains)
        LOGGER.info("Found toolchains at '${newestSDKDir.name}' - '${sdkToolchains.join(', ')}'")
        LOGGER.info("We will use the first available - '${sdkToolchains.first().name}'")
        toolchainPrefix = sdkToolchains.first().name
      } else {
        LOGGER.info("Referenced toolchain '${toolchainPrefix}' is available for '${newestSDKDir.name}'")
      }

      sdkToolchainPrefix = Paths.get(newestSDKDir.path, toolchainPrefix)
    } else {
      LOGGER.warn("Incorrect SDK layout found at '${sdkRootPath}', we will try to use it anyway.")
      sdkToolchainPrefix = toolchainDir
    }

    layout.putAll([
      binaries: Paths.get(sdkToolchainPrefix, 'bin').toString(),
      libraries: Paths.get(sdkToolchainPrefix, 'lib').toString(),
      includes: Paths.get(sdkToolchainPrefix, 'include').toString(),
      sdkPath: sdkPath.path
    ])
    layout.putAll(getQTBinaries(sdkPath.path, SDK_LAYOUT_PATTERNS.binaries))

    validateSDKConfiguration(layout)

    return layout
  }

  protected static List<File> findFiles(File searchDir, Pattern pattern) {
    return searchDir.listFiles().findAll { it.name.matches(pattern) }
  }

  protected static List<File> findFiles(File searchDir, String pattern) {
    return findFiles(searchDir, Pattern.compile(pattern))
  }

  protected static List<File> findFiles(String searchDir, Pattern pattern) {
    return findFiles(new File(searchDir), pattern)
  }

  protected static List<File> findDirectories(File searchDir, Pattern pattern) {
    return findFiles(searchDir, pattern).findAll { it.directory }
  }

  protected static List<File> findDirectories(String searchDir, Pattern pattern) {
    return findDirectories(new File(searchDir), pattern)
  }

  protected static void validateSDKConfiguration(Map<String, String> sdkLayout) {
    LOGGER.info("Validate configured SDK layout")
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

  private static List<String> getBinaryPathFromEnvVariable(Pattern binaryPattern) {
    List<String> binaryPathString = System.getenv('PATH')
      .split(Pattern.quote(File.pathSeparator))
      .findAll { String searchPath ->
        findFiles(searchPath, binaryPattern)
      }

    return binaryPathString
  }
}
