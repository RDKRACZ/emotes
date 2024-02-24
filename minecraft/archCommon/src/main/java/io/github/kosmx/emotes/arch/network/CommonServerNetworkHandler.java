package io.github.kosmx.emotes.arch.network;

import io.github.kosmx.emotes.arch.mixin.ServerChunkCacheAccessor;
import io.github.kosmx.emotes.common.network.EmotePacket;
import io.github.kosmx.emotes.common.network.GeyserEmotePacket;
import io.github.kosmx.emotes.common.network.objects.NetData;
import io.github.kosmx.emotes.executor.EmoteInstance;
import io.github.kosmx.emotes.server.network.AbstractServerEmotePlay;
import io.github.kosmx.emotes.server.network.IServerNetworkInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class CommonServerNetworkHandler extends AbstractServerEmotePlay<ServerPlayer> {
    public static CommonServerNetworkHandler instance = new CommonServerNetworkHandler();

    private static MinecraftServer server;

    public static void setServer(@NotNull MinecraftServer server) {
        CommonServerNetworkHandler.server = server;
    }

    private CommonServerNetworkHandler() {} // make ctor private for singleton class

    @NotNull
    public static MinecraftServer getServer() {
        return server;
    }

    public void init() {
    }

    private byte[] unwrapBuffer(FriendlyByteBuf buf) {
        if(buf.isDirect()){
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            return bytes;
        }
        else {
            return buf.array();
        }
    }

    public static IServerNetworkInstance getHandler(ServerGamePacketListenerImpl handler) {
        return ((EmotesMixinNetwork)handler).emotecraft$getServerNetworkInstance();
    }

    public void receiveMessage(ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf) {
        try
        {
            receiveMessage(unwrapBuffer(buf), player, getHandler(handler));
        } catch (IOException e) {
            EmoteInstance.instance.getLogger().log(Level.WARNING, e.getMessage(), e);
        }
    }
    public void receiveStreamMessage(ServerPlayer player, ServerGamePacketListenerImpl handler, FriendlyByteBuf buf) {
        receiveStreamMessage(player, getHandler(handler), ByteBuffer.wrap(unwrapBuffer(buf)));
    }

    public void receiveStreamMessage(ServerPlayer player, IServerNetworkInstance handler, ByteBuffer buf) {
        try
        {
            if (((EmotesMixinNetwork)handler).emotecraft$getServerNetworkInstance().allowEmoteStreamC2S()) {
                var packet = ((AbstractServerNetwork)handler).receiveStreamChunk(buf);
                if (packet != null) {
                    receiveMessage(packet.array(), player, handler);
                }
            } else {
                handler.disconnect("Emote stream is disabled on this server");
            }
        } catch (IOException e) {
            EmoteInstance.instance.getLogger().log(Level.WARNING, e.getMessage(), e);
        }
    }

    public void receiveGeyserMessage(ServerPlayer player, FriendlyByteBuf buf) {
        receiveGeyserMessage(player, unwrapBuffer(buf));
    }

    @Override
    protected UUID getUUIDFromPlayer(ServerPlayer player) {
        return player.getUUID();
    }

    @Override
    protected ServerPlayer getPlayerFromUUID(UUID player) {
        return server.getPlayerList().getPlayer(player);
    }

    @Override
    protected long getRuntimePlayerID(ServerPlayer player) {
        return player.getId();
    }

    @Override
    protected IServerNetworkInstance getPlayerNetworkInstance(ServerPlayer player) {
        return ((EmotesMixinNetwork)player.connection).emotecraft$getServerNetworkInstance();
    }

    @Override
    protected void sendForEveryoneElse(GeyserEmotePacket packet, ServerPlayer player) {
        sendForEveryoneElse(null, packet, player); // don't make things complicated
    }

    @Override
    protected void sendForEveryoneElse(@Nullable NetData data, @Nullable GeyserEmotePacket geyserPacket, ServerPlayer player) {
        getTrackedPlayers(player).forEach(target -> {
            if (target != player) {
                try {
                    if (data != null && NetworkPlatformTools.canSendPlay(target, NetworkPlatformTools.EMOTE_CHANNEL_ID)) {
                        IServerNetworkInstance playerNetwork = getPlayerNetworkInstance(target);
                        playerNetwork.sendMessage(new EmotePacket.Builder(data), null);
                    } else if (geyserPacket != null && NetworkPlatformTools.canSendPlay(target, NetworkPlatformTools.GEYSER_CHANNEL_ID)) {
                        IServerNetworkInstance playerNetwork = getPlayerNetworkInstance(target);
                        playerNetwork.sendGeyserPacket(ByteBuffer.wrap(geyserPacket.write()));
                    }
                } catch (IOException e) {
                    EmoteInstance.instance.getLogger().log(Level.WARNING, e.getMessage(), e);
                }
            }
        });
    }

    @Override
    protected void sendForPlayerInRange(NetData data, ServerPlayer sourcePlayer, UUID target) {
        try {
            var targetPlayer = sourcePlayer.server.getPlayerList().getPlayer(target);
            if (targetPlayer != null && targetPlayer.getChunkTrackingView().contains(sourcePlayer.chunkPosition())) {
                getPlayerNetworkInstance(targetPlayer).sendMessage(new EmotePacket.Builder(data), null);
            }

        } catch (IOException e) {
            EmoteInstance.instance.getLogger().log(Level.WARNING, e.getMessage(), e);
        }
    }

    @Override
    protected void sendForPlayer(NetData data, ServerPlayer ignore, UUID target) {
        try {
            ServerPlayer player = getPlayerFromUUID(target);
            IServerNetworkInstance playerNetwork = getPlayerNetworkInstance(player);

            EmotePacket.Builder packetBuilder = new EmotePacket.Builder(data);
            playerNetwork.sendMessage(packetBuilder, null);
        } catch (IOException e) {
            EmoteInstance.instance.getLogger().log(Level.WARNING, e.getMessage(), e);
        }
    }

    private Collection<ServerPlayer> getTrackedPlayers(Entity entity) {
        var level = entity.level().getChunkSource();
        if (level instanceof ServerChunkCache chunkCache) {
            ServerChunkCacheAccessor storage = (ServerChunkCacheAccessor) chunkCache.chunkMap;

            var tracker = storage.getTrackedEntity().get(entity.getId());
            if (tracker != null) {
                return tracker.getPlayersTracking()
                        .stream().map(ServerPlayerConnection::getPlayer).collect(Collectors.toUnmodifiableSet());
            }
            return Collections.emptyList();
        }
        throw new IllegalArgumentException("server function called on logical client");
    }
}
