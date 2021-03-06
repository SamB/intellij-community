// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class KeymapManager {
  public static final String DEFAULT_IDEA_KEYMAP = "$default";
  public static final String MAC_OS_X_KEYMAP = "Mac OS X";
  public static final String MAC_OS_X_10_5_PLUS_KEYMAP = "Mac OS X 10.5+";
  public static final String X_WINDOW_KEYMAP = "Default for XWin";
  public static final String KDE_KEYMAP = "Default for KDE";
  public static final String GNOME_KEYMAP = "Default for GNOME";

  @NotNull
  public abstract Keymap getActiveKeymap();

  @Nullable
  public abstract Keymap getKeymap(@NotNull String name);

  private static volatile KeymapManager INSTANCE;

  public static KeymapManager getInstance() {
    Application application = ApplicationManager.getApplication();
    if (application == null || !LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred()) {
      return null;
    }

    KeymapManager instance = INSTANCE;
    if (instance == null) {
      instance = application.getComponent(KeymapManager.class);
      INSTANCE = instance;
    }
    return instance;
  }

  /**
   * @deprecated use {@link KeymapManagerListener#TOPIC} instead
   */
  @Deprecated
  public abstract void addKeymapManagerListener(@NotNull KeymapManagerListener listener);

  /**
   * @deprecated use {@link KeymapManagerListener#TOPIC} instead
   */
  @Deprecated
  public abstract void addKeymapManagerListener(@NotNull KeymapManagerListener listener, @NotNull Disposable parentDisposable);

  /**
   * @deprecated use {@link KeymapManagerListener#TOPIC} instead
   */
  @Deprecated
  public abstract void removeKeymapManagerListener(@NotNull KeymapManagerListener listener);
}
