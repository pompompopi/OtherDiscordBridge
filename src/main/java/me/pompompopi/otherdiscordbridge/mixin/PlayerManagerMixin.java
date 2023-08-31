package me.pompompopi.otherdiscordbridge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Predicate;
import me.pompompopi.otherdiscordbridge.OtherDiscordBridge;
import net.minecraft.network.message.MessageType.Parameters;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
  @WrapOperation(method = "onPlayerConnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Z)V"))
  private void wrapPlayerConnect(final PlayerManager instance, final Text message, final boolean overlay, Operation<Void> operation) {
    operation.call(instance, message, overlay);
    OtherDiscordBridge.INSTANCE.queueSystem(message);
  }

  @Inject(method = "broadcast(Lnet/minecraft/network/message/SignedMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V", at = @At("HEAD"))
  private void injectBroadcast(final SignedMessage message, final Predicate<ServerPlayerEntity> shouldSendFiltered,
      final @Nullable ServerPlayerEntity sender, final Parameters params, final CallbackInfo ci) {
    if (sender == null) {
      return;
    }

    OtherDiscordBridge.INSTANCE.onChat(sender, message.getContent());
  }
}
