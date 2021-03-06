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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.logging.Logger
import org.slf4j.LoggerFactory
import org.platops.gradle.plugins.qt.QTPluginExtension

import javax.inject.Inject
import java.nio.file.Paths

@CacheableTask
class QTResourcesTask extends DefaultTask {
  private Map<File, String> fileRegistry
  private static final Logger LOGGER = LoggerFactory.getLogger(this.simpleName) as Logger

  @Inject
  QTResourcesTask() {
    this.fileRegistry = populateRegistry(extension, 'resources')
  }

  @Internal
  QTPluginExtension getExtension() {
    project.extensions.findByType(QTPluginExtension)
  }

  @Input
  public String compileCmd

  @Input
  public Map<String, Map<String, Serializable>> qtSources

  String getCompileCmd() {
    return compileCmd
  }

  void setCompileCmd(String compileCmd) {
    this.compileCmd = compileCmd
  }

  Map<String, Map<String, Serializable>> getQtSources() {
    return qtSources
  }

  void setQtSources(Map<String, Map<String, Serializable>> qtSources) {
    this.qtSources = qtSources
  }

  protected static String getFileName(File file) {
    return file.name.take(file.name.lastIndexOf('.'))
  }

  protected String getTargetFileName(File file) {
    return "qrc_${getFileName(file)}.cpp"
  }

  protected String getTargetFilePath(File projectFile, Map<String, Serializable> dirParameters) {
    String relativeDir = project.projectDir.toPath().relativize(projectFile.parentFile.toPath()).toString()
    return dirParameters.flat ? dirParameters.targetPath : Paths.get(dirParameters.targetPath.toString(), relativeDir)
  }

  protected void addOutputs(Map<File, String> fileRegistry) {
    fileRegistry.each { File sourceFile, String targetPath ->
      inputs.file(sourceFile)
      outputs.file(project.file(targetPath))
    }
  }

  protected Map<File, String> populateRegistry(QTPluginExtension qtPluginExtension, String moduleType) {
    Map<File, String> fileRegistry = [:]
    LOGGER.info("Populate file registry")

    qtPluginExtension[moduleType].each { String directory, Map<String, Serializable> dirParameters ->
      LOGGER.info("Evaluate sources at '${directory}' with '${dirParameters.includes}' includes.")
      project.fileTree(dir: directory, include: dirParameters.includes).files.each { File projectFile ->
        String targetPath = getTargetFilePath(projectFile, dirParameters)
        String generatedFile = Paths.get(targetPath, getTargetFileName(projectFile))
        fileRegistry.putAll([(projectFile): generatedFile])
      }
    }

    addOutputs(fileRegistry)

    return fileRegistry
  }

  void processSources() {
    fileRegistry.each { File sourceFile, String targetPath ->
      project.exec {
        commandLine compileCmd
        args '-name', getFileName(sourceFile), '-o', targetPath, sourceFile.path
      }
    }
  }

  @TaskAction
  void run() {
    processSources()
  }
}
