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
package org.platops.gradle.plugins.qt.toolchains

import org.platops.gradle.plugins.qt.QTPluginExtension

class QTToolchainLinux extends QTToolchain {
  private static final List<String> DEFAULT_BINARY_PATH = ['/usr/bin', '/bin']

  QTToolchainLinux(QTPluginExtension qtPluginExtension) {
    super(qtPluginExtension)
  }

  @Override
  protected Map<String, String> initializeSDK(File sdkProposedPath) {
    LOGGER.info("Initialize QT Toolchain with SDK provided at '${sdkProposedPath}'")
    this.sdkPath = sdkProposedPath
    Map<String, String> sdkLayout = (sdkProposedPath.path in DEFAULT_BINARY_PATH)
      ? osSpecificQTLayout(sdkProposedPath)
      : produceDefaultQTLayout(sdkProposedPath)

    return sdkLayout
  }
}
