// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class OSProcessManager {
  public static OSProcessManager getInstance() {
    return ServiceManager.getService(OSProcessManager.class);
  }

  public final boolean killProcessTree(@NotNull Process process) {
    return ProcessUtil.killProcessTree(process);
  }

  @NotNull
  public abstract List<String> getCommandLinesOfRunningProcesses();
}