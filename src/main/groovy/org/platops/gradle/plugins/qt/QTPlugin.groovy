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
package org.platops.gradle.plugins.qt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.logging.Logger
import org.gradle.language.cpp.CppApplication
import org.gradle.language.cpp.CppBinary
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.tasks.AbstractLinkTask
import org.gradle.nativeplatform.test.cpp.CppTestSuite
import org.platops.gradle.plugins.qt.internal.QTToolchainFactory
import org.platops.gradle.plugins.qt.internal.tasks.QTBundleTask
import org.platops.gradle.plugins.qt.internal.tasks.QTMetaObjectTask
import org.platops.gradle.plugins.qt.internal.tasks.QTResourcesTask
import org.platops.gradle.plugins.qt.internal.tasks.QTUIObjectTask
import org.platops.gradle.plugins.qt.internal.toolchains.QTToolchain
import org.slf4j.LoggerFactory

class QTPlugin implements Plugin<Project> {
  private static final String TASK_PREFIX = 'generateQT'
  private static final String EXTENSION_NAME = 'qt'
  private static final Logger LOGGER = LoggerFactory.getLogger(this.simpleName) as Logger

  @Override
  void apply(Project project) {
    QTPluginExtension qtPluginExtension = project.extensions.create(EXTENSION_NAME, QTPluginExtension, project)

    configure(project, qtPluginExtension)
  }

  private static void configure(Project project, QTPluginExtension qtPluginExtension) {
    LOGGER.info("${this.simpleName} configuration stage")
    QTToolchain qtToolchain = QTToolchainFactory.initializeToolchain(qtPluginExtension)

    LOGGER.info("Register ${QTResourcesTask.simpleName}")
    project.tasks.register("${TASK_PREFIX}Resources", QTResourcesTask) {
      description = 'Generate QT Resources'
      group = EXTENSION_NAME
      compileCmd = qtToolchain.rccTool
      qtSources = qtPluginExtension.resources
    }
    LOGGER.info("Register ${QTMetaObjectTask.simpleName}")
    project.tasks.register("${TASK_PREFIX}Sources", QTMetaObjectTask) {
      description = 'Generate QT Sources'
      group = EXTENSION_NAME
      compileCmd = qtToolchain.mocTool
      qtSources = qtPluginExtension.sources
    }
    LOGGER.info("Register ${QTUIObjectTask.simpleName}")
    project.tasks.register("${TASK_PREFIX}UI", QTUIObjectTask) {
      description = 'Generate QT UI Resources'
      group = EXTENSION_NAME
      compileMoc = qtToolchain.mocTool
      compileCmd = qtToolchain.uicTool
      qtSources = qtPluginExtension.ui
    }

    project.tasks.withType(CppCompile).configureEach { CppCompile cppCompileTask ->
      cppCompileTask.dependsOn(project.tasks.withType(QTResourcesTask))
      LOGGER.info("'${cppCompileTask.name}' is now depends on '${TASK_PREFIX}Resources'")
      cppCompileTask.dependsOn(project.tasks.withType(QTMetaObjectTask))
      LOGGER.info("'${cppCompileTask.name}' is now depends on '${TASK_PREFIX}Sources'")
      cppCompileTask.dependsOn(project.tasks.withType(QTUIObjectTask))
      LOGGER.info("'${cppCompileTask.name}' is now depends on '${TASK_PREFIX}UI'")

      LOGGER.info('Add platform-specific compiler args')
      cppCompileTask.compilerArgs.addAll(qtToolchain.compilerArgs)
      LOGGER.info("Update include paths for compile tasks")
      cppCompileTask.includes.from qtToolchain.includes

      List<String> includeModules = ['QtCore'] + qtPluginExtension.modules
      if (cppCompileTask.name.contains('Test')) {
        includeModules.add('QtTest')
      }
      qtToolchain.processQTModulesIncludes(includeModules).each { File moduleInclude ->
        cppCompileTask.includes.from moduleInclude.path
      }

      if (!cppCompileTask.name.contains('Test')) {
        LOGGER.info("Generated sources attached to '${cppCompileTask.name}'")
        [
          'resources',
          'sources',
          'ui',
        ].each { String extensionType ->
          qtPluginExtension[extensionType].each { String directory, Map<String, Serializable> options ->
            cppCompileTask.source.from project.fileTree(dir: options.targetPath, exclude: '**/*.h')
            switch (extensionType) {
              case 'sources':
                cppCompileTask.includes.from directory
                break
              case 'ui':
                cppCompileTask.includes.from options.targetPath
                break
            }
          }
        }
      }
    }

    project.tasks.withType(AbstractLinkTask).configureEach { AbstractLinkTask linkTask ->
      List<String> includeLibraries = ['QtCore'] + qtPluginExtension.modules
      if (linkTask.name.contains('Test')) {
        includeLibraries.add('QtTest')
      }

      LOGGER.info('Add platform-specific linker args')
      linkTask.linkerArgs.addAll(qtToolchain.linkerArgs)

      qtToolchain.processQTModulesLibraries(includeLibraries,
        linkTask.name.toLowerCase().contains('debug')).each { File includeLibrary ->

        linkTask.libs.from includeLibrary.path
      }
    }

    project.components.configureEach { SoftwareComponent softwareComponent ->
      if (softwareComponent instanceof CppApplication || softwareComponent instanceof CppTestSuite) {
        softwareComponent.binaries.whenElementFinalized { CppBinary cppBinary ->
          NativePlatform binaryTargetPlatform = cppBinary.compileTask.get().targetPlatform.get()
          String bundleTaskName = "${TASK_PREFIX}Bundle${cppBinary.name.capitalize()}"
          if (!binaryTargetPlatform.operatingSystem.isLinux()) {
            LOGGER.info("Register ${QTBundleTask.simpleName}")
            project.tasks.register(bundleTaskName, QTBundleTask) {
              description = 'Generate QT Bundle'
              group = EXTENSION_NAME
              dependsOn = [ cppBinary.linkTask.get(), cppBinary.installTask.get() ]
              deployCmd = qtToolchain.deployTool
              targetPlatform = binaryTargetPlatform.operatingSystem.toFamilyName()
              binaryFile = cppBinary.getExecutableFile().get().getAsFile()
              buildVariant = cppBinary.isOptimized() ? 'release' : 'debug'
              installPath = cppBinary.getInstallDirectory().dir('lib').get()
            }

            cppBinary.installTask.get().finalizedBy(project.tasks.findByName(bundleTaskName))
          }
        }
      }
    }
  }
}
