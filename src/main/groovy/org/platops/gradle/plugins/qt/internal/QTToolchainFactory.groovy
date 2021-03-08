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
package org.platops.gradle.plugins.qt.internal

import org.gradle.internal.os.OperatingSystem
import org.platops.gradle.plugins.qt.QTPluginExtension
import org.platops.gradle.plugins.qt.internal.toolchains.QTToolchain
import org.platops.gradle.plugins.qt.internal.toolchains.QTToolchainLinux
import org.platops.gradle.plugins.qt.internal.toolchains.QTToolchainOSX
import org.platops.gradle.plugins.qt.internal.toolchains.QTToolchainWindows

class QTToolchainFactory {
  static QTToolchain initializeToolchain(QTPluginExtension qtPluginExtension) {
    switch (OperatingSystem.current()) {
      case OperatingSystem.LINUX:
        return new QTToolchainLinux(qtPluginExtension)
        break
      case OperatingSystem.MAC_OS:
        return new QTToolchainOSX(qtPluginExtension)
        break
      case OperatingSystem.WINDOWS:
        return new QTToolchainWindows(qtPluginExtension)
        break
      default:
        throw new UnsupportedOperationException(
          """
            Unsupported host operating system - '${OperatingSystem.current().name}'.
            Please check README.md #Supported platforms section
          """.stripIndent()
        )
    }
  }
}
