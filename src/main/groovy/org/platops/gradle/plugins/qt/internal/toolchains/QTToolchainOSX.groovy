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
package org.platops.gradle.plugins.qt.internal.toolchains

import org.platops.gradle.plugins.qt.QTPluginExtension

import java.nio.file.Paths
import java.util.regex.Pattern

class QTToolchainOSX extends QTToolchain {
  QTToolchainOSX(QTPluginExtension qtPluginExtension) {
    super(qtPluginExtension)
  }

  static final String SDK_TOOLCHAIN_PREFIX = 'clang_64'

  @Override
  protected void platformSpecificArgs() {
    this.compilerArgs = [
      '-iframework', this.libraries
    ]
    this.linkerArgs = [
      '-rpath', this.libraries
    ]
  }

  @Override
  List<File> processQTModulesIncludes(List<String> modules) {
    List<File> includeList = [new File(this.includes)]
    modules.each { String module ->
      findDirectories(this.libraries, ~/(${module}.*)/).each { File moduleInclude ->
        File include = new File("${moduleInclude.path}", 'Headers')

        LOGGER.info("Adding '${include}' as include directory.")
        includeList.add(include)
      }
    }

    return includeList
  }

  @Override
  List<File> processQTModulesLibraries(List<String> modules, Boolean debuggable = true) {
    List<File> librariesList = []
    modules.each { String module ->
      Pattern libPattern = ~/(${module}\.framework)/
      List<File> moduleLibraries = findDirectories(this.libraries, libPattern)
      if (moduleLibraries) {
        File moduleLibrary = moduleLibraries.first()
        String libraryName = debuggable ? "${module}_debug" : module
        if (this.brew) {
          LOGGER.info("Homebrew QT - linkage over Release variant of '${module}'")
          libraryName = module
        }
        File library = findFiles(moduleLibrary, libraryName).sort().first()
        LOGGER.info("Adding '${library.name}' as library.")
        librariesList.add(library)
      }
    }

    return librariesList
  }

  @Override
  protected Map<String, String> initializeSDK(File sdkProposedPath) {
    LOGGER.info("Initialize QT Toolchain with SDK provided at '${sdkProposedPath}'")
    this.sdkPath = sdkProposedPath
    Map<String, String> sdkLayout = new File("${sdkProposedPath.toPath().toRealPath().parent}/.brew/").exists() ?
      osSpecificQTLayout(sdkProposedPath) :
      produceDefaultQTLayout(sdkProposedPath)

    return sdkLayout
  }

  @Override
  protected Map<String, String> osSpecificQTLayout(File sdkPath) {
    LOGGER.info('Will try to proceed with Homebrew layout')
    this.brew = true
    File sdkRootPath = sdkPath.parentFile

    Map<String, String> layout = [:]
    layout.putAll([
      binaries: Paths.get(sdkRootPath.path, 'bin').toString(),
      libraries: Paths.get(sdkRootPath.toPath().toRealPath().toString(), 'lib').toString(),
      includes: Paths.get(sdkRootPath.path, 'include').toString(),
      sdkPath: sdkPath.path
    ])
    layout.putAll(getQTBinaries(sdkPath.path, SDK_LAYOUT_PATTERNS.binaries))

    validateSDKConfiguration(layout)

    return layout
  }
}
