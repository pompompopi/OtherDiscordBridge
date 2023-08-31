package me.pompompopi.otherdiscordbridge;

import com.google.common.collect.Queues;
import com.mojang.authlib.GameProfile;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Webhook;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.WebhookCreateSpec;
import discord4j.core.spec.WebhookExecuteSpec;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.util.AllowedMentions;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Queue;
import me.pompompopi.otherdiscordbridge.configuration.Configuration;
import me.pompompopi.otherdiscordbridge.executor.MainThreadScheduler;
import me.pompompopi.otherdiscordbridge.util.sanitization.SanitizationUtil;
import me.pompompopi.otherdiscordbridge.util.throwing.ThrowingUtil;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

public class OtherDiscordBridge extends ReactiveEventAdapter implements DedicatedServerModInitializer {
  public static final Logger LOGGER = LoggerFactory.getLogger("OtherDiscordBridge");
  public static OtherDiscordBridge INSTANCE;
  private final Scheduler scheduler = Schedulers.newParallel("OtherDiscordBridge Executor");
  private final Queue<Mono<?>> mainMonoQueue = Queues.newConcurrentLinkedQueue();
  private Configuration configuration;
  private TextChannel textChannel;
  private Webhook webhook;
  private GatewayDiscordClient client;
  private @Nullable MinecraftDedicatedServer server;
  private @Nullable Scheduler mainScheduler;

  public void setDedicatedServer(final MinecraftDedicatedServer server) {
    this.server = server;
    this.setMainThreadScheduler(new MainThreadScheduler(server));
    
    OtherDiscordBridge.INSTANCE.queueSystem("Server has started");
  }

  private void setMainThreadScheduler(final MainThreadScheduler mainScheduler) {
    if (this.mainScheduler != null && !this.mainScheduler.isDisposed()) {
      throw new IllegalStateException("Main scheduler already set");
    }

    this.mainScheduler = mainScheduler;

    synchronized (this.mainMonoQueue) {
      if (this.mainMonoQueue.isEmpty()) {
        return;
      }

      LOGGER.info("Executing {} scheduled Monos on the main thread", this.mainMonoQueue.size());
      Mono<?> mono;
      while ((mono = this.mainMonoQueue.poll()) != null) {
        mono.subscribeOn(mainScheduler).subscribe();
      }
    }
  }

  @Override
  public void onInitializeServer() {
    INSTANCE = this;

    final Path defaultConfigPath =
        FabricLoader.getInstance()
            .getConfigDir()
            .resolve("otherdiscordbridge")
            .resolve("config.xml");

    final Path configPath = ThrowingUtil.supply(() -> Path.of(System.getProperty("OTHERDISCORDBRIDGE_CONFIG_PATH", defaultConfigPath.toString()))).orElse(defaultConfigPath);

    try {
      this.configuration = Configuration.load(configPath);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load configuration file", e);
    }

    final String webhookName = this.configuration.getWebhookName();
    final Webhook existingWebhook =
        DiscordClient.create(configuration.getToken())
            .gateway()
            .setEnabledIntents(
                IntentSet.nonPrivileged()
                    .and(
                        IntentSet.of(
                            Intent.GUILD_WEBHOOKS, Intent.GUILD_MESSAGES, Intent.MESSAGE_CONTENT)))
            .login()
            .doOnNext(c -> this.client = c)
            .doOnNext(c -> c.on(this).subscribe())
            .flatMap(this.configuration::getChannel)
            .doOnNext(c -> this.textChannel = c)
            .flux()
            .flatMap(TextChannel::getWebhooks)
            .zipWith(Mono.just(webhookName))
            .filter(wt -> wt.getT1().getName().orElse("").equals(wt.getT2()))
            .single()
            .map(Tuple2::getT1)
            .block();

    if (existingWebhook != null) {
      this.webhook = existingWebhook;
    } else {
      this.webhook =
          this.textChannel
              .createWebhook(WebhookCreateSpec.of(webhookName))
              .blockOptional()
              .orElseThrow(() -> new IllegalStateException("Failed to create webhook"));
    }

    ServerLivingEntityEvents.AFTER_DEATH.register(this::afterDeath);
  }

  public void shutdown() {
    this.queueSystem("Server has stopped");
    if (this.mainScheduler != null) {
      this.mainScheduler.dispose();
    }
    this.scheduler.dispose();
    // Give the client a maximum of 10 seconds to log out
    ThrowingUtil.run(() -> this.client.logout().block(Duration.ofSeconds(10)));
  }

  public void onChat(final ServerPlayerEntity sender, final Text message) {
    this.queuePlayerMessage(sender.getGameProfile(), message.getString());
  }

  public void afterDeath(final LivingEntity dead, final DamageSource damageSource) {
    final Entity attacker = damageSource.getAttacker();

    // Only broadcast deaths caused by/of players
    if (!dead.isPlayer() && (attacker == null) || (attacker != null && !attacker.isPlayer())) {
      return;
    }
    
    this.queueSystem(damageSource.getDeathMessage(dead).getString());
  }

  private void queuePlayerMessage(final GameProfile profile, final String content) {
    this.queue(
        WebhookExecuteSpec.builder()
            .username(profile.getName())
            .avatarUrl(this.configuration.getGameProfileMapper().apply(profile))
            .content(content)
            .allowedMentions(AllowedMentions.suppressAll())
            .build());
  }

  public void queueSystem(final Text content) {
    this.queueSystem(content.getString());
  }

  public void queueSystem(final String content) {
    this.queue(
        WebhookExecuteSpec.builder()
            .username("System")
            .content("**" + SanitizationUtil.sanitizeDiscord(content) + "**")
            .allowedMentions(AllowedMentions.suppressAll())
            .build());
  }

  private void queue(final WebhookExecuteSpec spec) {
    this.webhook.execute(spec).subscribeOn(this.scheduler).subscribe();
  }

  @Override
  public @NotNull Publisher<?> onReady(final @NotNull ReadyEvent event) {
    return Mono.fromRunnable(() -> LOGGER.info("Logged into Discord."));
  }

  @SuppressWarnings("ConstantConditions") // server will always be non-null by the time broadcast is executed
  private Mono<?> processMessageCreate(final MessageCreateEvent event) {
    return Mono.just(event)
        .map(MessageCreateEvent::getMessage)
        .filter(m -> m.getChannelId().equals(this.textChannel.getId()))
        .filter(m -> m.getAuthor().isPresent() && !m.getAuthor().get().isBot())
        .zipWhen(m -> Mono.fromSupplier(m::getUserData))
        .map(t -> Text.literal("<").append(SanitizationUtil.sanitizeMinecraft(t.getT2().globalName().orElseGet(t.getT2()::username))).append("> ").append(SanitizationUtil.sanitizeMinecraft(t.getT1().getContent())))
        .doOnNext(m -> this.server.getPlayerManager().broadcast(m, false));
  }

  // We need to broadcast messages off-thread
  @Override
  public @NotNull Publisher<?> onMessageCreate(final @NotNull MessageCreateEvent event) {
    final Mono<?> messageProcessMono = this.processMessageCreate(event);

    if (this.mainScheduler != null) {
      messageProcessMono.subscribeOn(this.mainScheduler).subscribe();
    } else {
      synchronized (this.mainMonoQueue) {
        this.mainMonoQueue.add(messageProcessMono);
      }
    }

    return Mono.empty();
  }
}