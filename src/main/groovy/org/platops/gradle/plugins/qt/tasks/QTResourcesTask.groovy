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
package org.platops.gradle.plugins.qt.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.platops.gradle.plugins.qt.QTPluginExtension
import org.slf4j.LoggerFactory

import javax.inject.Inject

@CacheableTask
class QTResourcesTask extends DefaultTask {
  private HashMap<File, String> fileRegistry
  private static final Logger LOGGER = LoggerFactory.getLogger(this.simpleName) as Logger

  @Inject
  QTResourcesTask() {
    this.fileRegistry = populateRegistry(extension, 'qtResources')
  }

  @Internal
  QTPluginExtension getExtension() {
    project.extensions.findByType(QTPluginExtension)
  }

  @Input
  public String compileCmd

  @Input
  public LinkedHashMap<String, LinkedHashMap<String, Serializable>> qtSources

  String getCompileCmd() {
    return compileCmd
  }

  void setCompileCmd(String compileCmd) {
    this.compileCmd = compileCmd
  }

  LinkedHashMap<String, LinkedHashMap<String, Serializable>> getQtSources() {
    return qtSources
  }

  void setQtSources(LinkedHashMap<String, LinkedHashMap<String, Serializable>> qtSources) {
    this.qtSources = qtSources
  }

  static String getFileName(File file) {
    return file.name.take(file.name.lastIndexOf('.'))
  }

  static String getTargetFileName(File file) {
    return "qrc_${getFileName(file)}.cpp"
  }

  private HashMap<File, String> populateRegistry(QTPluginExtension qtPluginExtension, String moduleType) {
    HashMap<File, String> fileRegistry = [:]
    LOGGER.info("Populate file registry")

    qtPluginExtension[moduleType].each { String directory, LinkedHashMap<String, Serializable> dirParameters ->
      LOGGER.info("Evaluate sources at '${directory}' with '${dirParameters.includes}' includes.")
      project.fileTree(dir: directory, include: dirParameters.includes).files.each { File projectFile ->
        String targetPath = dirParameters.flat ? dirParameters.targetPath :
          "${dirParameters.targetPath}${File.separator}" +
            "${project.projectDir.toPath().relativize(projectFile.parentFile.toPath())}"
        String generatedFile = "${targetPath}${File.separator}${getTargetFileName(projectFile)}"
        fileRegistry.putAll([(projectFile): generatedFile])

        inputs.file(projectFile)
        outputs.file(project.file(generatedFile))
      }
    }

    return fileRegistry
  }

  void processSources() {
    fileRegistry.each { File sourceFile, String targetPath ->
      String fileName = getFileName(sourceFile)

      project.exec {
        commandLine compileCmd
        args "-name", fileName, '-o', targetPath, sourceFile.path
      }
    }
  }

  @TaskAction
  void run() {
    processSources()
  }
}
