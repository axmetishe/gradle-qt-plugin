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

import org.gradle.api.Project

class QTPluginExtension {

  protected Project project

  @SuppressWarnings("GroovyAssignabilityCheck")
  LinkedHashMap<String, LinkedHashMap<String, Serializable>> resources = [
    'src/main/resources': [
      includes: '*.qrc',
      targetPath: "${project.buildDir}/generated-sources",
      flat: true
    ]
  ]
  @SuppressWarnings("GroovyAssignabilityCheck")
  LinkedHashMap<String, LinkedHashMap<String, Serializable>> sources = [
    'src/main/meta-headers': [
      includes: '**/*.h',
      targetPath: "${project.buildDir}/generated-sources/sources",
      flat: true
    ]
  ]
  @SuppressWarnings("GroovyAssignabilityCheck")
  LinkedHashMap<String, LinkedHashMap<String, Serializable>> ui = [
    'src/resources/ui': [
      includes: '**/*.ui',
      targetPath: "${project.buildDir}/generated-sources/ui",
      flat: true
    ]
  ]

  List<String> modules = [
    'QtCore'
  ]

  QTPluginExtension(Project project) {
    this.project = project
  }
}
