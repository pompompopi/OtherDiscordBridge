package me.pompompopi.otherdiscordbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.pompompopi.otherdiscordbridge.OtherDiscordBridge;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
  @WrapOperation(method = "onDisconnected", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Z)V"))
  private void wrapDisconnected(final PlayerManager instance, final Text message, final boolean overlay, final Operation<Void> operation) {
    operation.call(instance, message, overlay);
    OtherDiscordBridge.INSTANCE.queueSystem(message);
  }
}
