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
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.platops.gradle.plugins.qt.tasks.QTResourcesTask
import org.slf4j.LoggerFactory

class QTPlugin implements Plugin<Project> {
  private static final String TaskPrefix = 'generateQT'
  private static final String ExtensionName = 'qt'

  private static final Logger LOGGER = LoggerFactory.getLogger(this.simpleName) as Logger


  @Override
  void apply(Project project) {
    QTPluginExtension qtPluginExtension = project.extensions.create(ExtensionName, QTPluginExtension, project)

    configure(project, qtPluginExtension)
  }

  static void configure(Project project, QTPluginExtension extension) {
    LOGGER.info("${this.simpleName} configuration stage")

    LOGGER.info("Register ${QTResourcesTask.simpleName}")
    project.tasks.register("${TaskPrefix}Resources", QTResourcesTask) {
      description = 'Generate QT Resources'
      group = LifecycleBasePlugin.BUILD_GROUP
      compileCmd = 'rcc-qt5'
      qtSources = extension.qtResources
    }
  }
}
