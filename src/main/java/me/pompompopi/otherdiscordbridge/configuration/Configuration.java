package me.pompompopi.otherdiscordbridge.configuration;

import com.mojang.authlib.GameProfile;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.TextChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.spongepowered.configurate.AttributedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.xml.XmlConfigurationLoader;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@ConfigSerializable
public class Configuration {
  private transient XmlConfigurationLoader loader;
  private transient AttributedConfigurationNode node;
  private transient Function<GameProfile, String> gameProfileMapper;

  @Comment("The ID of the channel the bot should listen for messages in, and send messages to.")
  private @MonotonicNonNull String channel = "0000000000000000000";

  @Comment("The token of the Discord bot.")
  private @MonotonicNonNull String token = "ENTER_TOKEN_HERE";

  @Comment("The name of the webhook to create/use in the associated channel.")
  private @MonotonicNonNull String webhookName = "Other Bridge";

  @Comment(
      "The URL to use for player avatars. Valid placeholders: {uuid-without-dashes}, {uuid}, {name}")
  private @MonotonicNonNull String avatarUrl = "https://minotar.net/avatar/{uuid-without-dashes}";

  public static Configuration load(final Path path) throws IOException {
    final Path parent = path.getParent();
    if (parent != path) {
      // Ensure that the configuration file's parent exists
      if (Files.notExists(parent)) {
        Files.createDirectories(parent);
      }
    }

    final XmlConfigurationLoader loader =
        XmlConfigurationLoader.builder()
            .defaultOptions(opts -> opts.shouldCopyDefaults(true))
            .path(path)
            .build();
    final AttributedConfigurationNode node = loader.load();
    final Configuration configuration = node.get(Configuration.class);
    if (configuration == null) {
      throw new IllegalArgumentException("Configuration node could not be loaded!");
    }

    // Initialize wrappers
    configuration.setLoader(loader);
    configuration.setNode(node);
    configuration.initializeGameProfileMapper();

    loader.save(node);
    return configuration;
  }

  public void initializeGameProfileMapper() {
    if (this.avatarUrl.contains("{uuid-without-dashes}")) {
      this.gameProfileMapper =
          gp ->
              this.avatarUrl.replace(
                  "{uuid-without-dashes}", gp.getId().toString().replace("-", ""));
    } else if (this.avatarUrl.contains("{uuid}")) {
      this.gameProfileMapper = gp -> this.avatarUrl.replace("{uuid}", gp.getId().toString());
    } else if (this.avatarUrl.contains("{name}")) {
      this.gameProfileMapper = gp -> this.avatarUrl.replace("{name}", gp.getName());
    } else {
      this.gameProfileMapper = gp -> this.avatarUrl;
    }
  }

  public Function<GameProfile, String> getGameProfileMapper() {
    if (this.gameProfileMapper == null) {
      throw new IllegalStateException("Game profile mapper null!");
    }

    return this.gameProfileMapper;
  }

  public void setNode(final AttributedConfigurationNode node) {
    if (this.node != null) {
      throw new IllegalStateException("Node already set");
    }
    this.node = node;
  }

  public void save() throws ConfigurateException {
    this.loader.save(this.node);
  }

  public void setLoader(final XmlConfigurationLoader loader) throws IllegalStateException {
    if (this.loader != null) {
      throw new IllegalStateException("Loader already set");
    }
    this.loader = loader;
  }

  public String getToken() {
    return this.token;
  }

  public String getWebhookName() {
    return this.webhookName;
  }

  public Mono<TextChannel> getChannel(final GatewayDiscordClient client) {
    return Mono.zip(Mono.just(this.channel), Mono.just(client))
        .map(t -> Tuples.of(Snowflake.of(t.getT1()), t.getT2()))
        .flatMap(t -> client.getChannelById(t.getT1()))
        .filter(c -> c.getType() == Channel.Type.GUILD_TEXT)
        .map(TextChannel.class::cast);
  }
}
