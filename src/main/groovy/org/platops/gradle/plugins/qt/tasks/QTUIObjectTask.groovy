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

import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input

import javax.inject.Inject
import java.nio.file.Paths

@CacheableTask
class QTUIObjectTask extends QTResourcesTask {
  private HashMap<File, String> fileRegistry

  @Inject
  QTUIObjectTask() {
    this.fileRegistry = populateRegistry(extension, 'ui')
  }

  @Input
  public String compileMoc

    String getCompileMoc() {
    return compileMoc
  }

  void setCompileMoc(String compileMoc) {
    this.compileMoc = compileMoc
  }

  protected String getHeaderTargetName(File file) {
    return "${getTargetFileName(file)}.h"
  }

  protected String getMocTargetName(File file) {
    return "moc_${getTargetFileName(file)}.cpp"
  }

  protected String getMocTargetPath(File file, String targetFile) {
    return Paths.get(Paths.get(targetFile).parent.toString(), getMocTargetName(file))
  }

  protected String getHeaderTargetPath(File file, String targetFile) {
    return Paths.get(Paths.get(targetFile).parent.toString(), getHeaderTargetName(file))
  }

  @Override
  protected String getTargetFileName(File file) {
    return "ui_${getFileName(file)}"
  }

  @Override
  void addOutputs(HashMap<File, String> fileRegistry) {
    fileRegistry.each { File sourceFile, String targetFile ->
      inputs.file(sourceFile)
      outputs.file(project.file(getHeaderTargetPath(sourceFile, targetFile)))
      outputs.file(project.file(getMocTargetPath(sourceFile, targetFile)))
    }
  }

  @Override
  void processSources() {
    fileRegistry.each { File sourceFile, String targetFile ->
      String mocTargetPath = getMocTargetPath(sourceFile, targetFile)
      String headerTargetPath = getHeaderTargetPath(sourceFile, targetFile)

      project.exec {
        commandLine compileCmd
        args '-o', headerTargetPath, sourceFile.path
      }

      project.exec {
        commandLine compileMoc
        args '-o', mocTargetPath, headerTargetPath
      }
    }
  }
}
