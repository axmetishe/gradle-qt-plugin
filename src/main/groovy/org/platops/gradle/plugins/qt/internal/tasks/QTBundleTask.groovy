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
package org.platops.gradle.plugins.qt.internal.tasks

import groovy.text.SimpleTemplateEngine
import org.gradle.api.DefaultTask
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.util.VersionNumber
import org.platops.gradle.plugins.qt.QTPluginExtension
import org.slf4j.LoggerFactory

import javax.inject.Inject
import java.nio.file.Paths

class QTBundleTask extends DefaultTask {
  private static final Logger LOGGER = LoggerFactory.getLogger(this.simpleName) as Logger

  @Inject
  QTBundleTask() {}

  @Internal
  QTPluginExtension getExtension() {
    project.extensions.findByType(QTPluginExtension)
  }

  @Input
  public String deployCmd

  @Input
  public String buildVariant

  @Input
  public String installPath

  @Input
  public String targetPlatform

  @InputFile
  public File binaryFile

  @OutputDirectory
  public File outputDir

  String getDeployCmd() {
    return deployCmd
  }

  void setDeployCmd(String deployCmd) {
    this.deployCmd = deployCmd
  }

  String getBuildVariant() {
    return buildVariant
  }

  void setBuildVariant(String buildVariant) {
    this.buildVariant = buildVariant
  }

  String getInstallPath() {
    return installPath
  }

  void setInstallPath(String installPath) {
    this.installPath = installPath
  }

  File getBinaryFile() {
    return binaryFile
  }

  void setBinaryFile(File binaryFile) {
    this.binaryFile = binaryFile
  }

  String getTargetPlatform() {
    return targetPlatform
  }

  void setTargetPlatform(String targetPlatform) {
    this.targetPlatform = targetPlatform
  }

  File getOutputDir() {
    return new File(targetPlatform == 'macos'
      ? Paths.get(project.buildDir.path, 'bundle', buildVariant, "${binaryFile.name}.app").toAbsolutePath().toString()
      : installPath
    )
  }

  private void macosBundleLayout(String plistFilePath, String executableFile) {
    LOGGER.info('Prepare MacOS bundle layout')

    String contentsPath = "${getOutputDir()}/Contents"

    project.copy {
      from(executableFile) {
        into "MacOS"
      }

      into contentsPath
    }

    if (plistFilePath) {
      LOGGER.info("Using Info.plist from '${plistFilePath}'")

      project.copy {
        from(plistFilePath) {
          into "."
        }
        into contentsPath
      }
    } else {
      LOGGER.info('Info.plist is not provided for the bundle, we will generate the default one.')

      generateInfoPlist(contentsPath)
    }
  }

  private void generateInfoPlist(String targetPath) {
    String plistTemplate = this.getClass().getResource('Info.plist').text
    SimpleTemplateEngine simpleTemplateEngine = new SimpleTemplateEngine()
    VersionNumber versionNumber = VersionNumber.parse(project.version.toString())

    Map<String, String> templateBinding = [
      bundleName        : "${project.name}.app".toString(),
      bundleDisplayName : project.name,
      bundleBinary      : project.name,
      bundleVersion     : versionNumber.baseVersion.toString(),
      bundleLongVersion : versionNumber.baseVersion.toString(),
      bundleShortVersion: "${versionNumber.major}.${versionNumber.minor}".toString(),
      bundleCopyright   : "${Calendar.YEAR}".toString(),
      bundleGUIId       : "${project.group}.${project.name}".toString(),
      bundleIconFile    : 'application.icns',
    ]

    Writable template = simpleTemplateEngine.createTemplate(plistTemplate).make(templateBinding)
    new File(targetPath, 'Info.plist').withWriter('utf-8') { BufferedWriter writer ->
      writer.write(template)
    }
  }

  private void deployLibraries() {
    List<String> deployArgs = []
    switch (targetPlatform) {
      case 'windows':
        deployArgs.add(Paths.get(this.getOutputDir().path, binaryFile.name).toString())
        deployArgs.add("--${buildVariant}")
        deployArgs.addAll(['--dir', installPath])
        deployArgs.addAll(['--libdir', installPath])
        break
      case 'macos':
        macosBundleLayout(extension.plistFile, binaryFile.path)
        deployArgs.add(this.getOutputDir().path)
        deployArgs.add("-executable=${binaryFile.path}")
        deployArgs.add("-libpath=${installPath}")
        deployArgs.add('-always-overwrite')
        deployArgs.add('-verbose=1')
        buildVariant == 'debug' ? deployArgs.add('-use-debug-libs') : null
        break
    }
    deployArgs.addAll(extension.deployParameters[targetPlatform])

    project.exec {
      commandLine deployCmd
      args deployArgs
    }
  }

  @TaskAction
  void run() {
    deployLibraries()
  }
}
