package me.pompompopi.otherdiscordbridge;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class MixinExtrasBootstrapper implements PreLaunchEntrypoint {
  @Override
  public void onPreLaunch() {
    MixinExtrasBootstrap.init();
  }
}
