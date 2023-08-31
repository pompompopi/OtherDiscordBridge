package me.pompompopi.otherdiscordbridge.mixin;

import me.pompompopi.otherdiscordbridge.OtherDiscordBridge;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftDedicatedServer.class)
public class MinecraftDedicatedServerMixin {
  @Inject(method = "setupServer", at = @At("TAIL"))
  private void injectSetupServer(final CallbackInfoReturnable<Boolean> cir) {
    OtherDiscordBridge.INSTANCE.setDedicatedServer((MinecraftDedicatedServer) (Object) this);
  }

  @Inject(method = "shutdown", at = @At("HEAD"))
  private void injectShutdown(final CallbackInfo ci) {
    OtherDiscordBridge.INSTANCE.shutdown();
  }
}
