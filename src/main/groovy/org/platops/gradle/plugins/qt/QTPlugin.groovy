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
import org.gradle.api.logging.Logger
import org.gradle.language.cpp.tasks.CppCompile
import org.platops.gradle.plugins.qt.tasks.QTResourcesTask
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
    LOGGER.lifecycle("${this.simpleName} configuration stage")

    LOGGER.info("Register ${QTResourcesTask.simpleName}")
    project.tasks.register("${TASK_PREFIX}Resources", QTResourcesTask) {
      description = 'Generate QT Resources'
      group = EXTENSION_NAME
      compileCmd = 'rcc-qt5'
      qtSources = qtPluginExtension.qtResources
    }

    project.tasks.withType(CppCompile).configureEach { CppCompile cppCompileTask ->
      cppCompileTask.dependsOn(project.tasks.withType(QTResourcesTask))
      LOGGER.info("'${cppCompileTask.name}' is now depends on '${TASK_PREFIX}Resources'")

      if (!cppCompileTask.name.contains('Test')) {
        LOGGER.info("Generated sources attached to '${cppCompileTask.name}'")

        qtPluginExtension['qtResources'].each { String directory, LinkedHashMap<String, Serializable> options ->
          cppCompileTask.source.from project.fileTree(dir: options.targetPath, exclude: '**/*.h')
        }
      }
    }
  }
}
