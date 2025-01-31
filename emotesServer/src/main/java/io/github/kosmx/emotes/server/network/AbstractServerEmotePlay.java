package io.github.kosmx.emotes.server.network;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import io.github.kosmx.emotes.api.events.impl.EventResult;
import io.github.kosmx.emotes.api.events.server.ServerEmoteEvents;
import io.github.kosmx.emotes.api.proxy.AbstractNetworkInstance;
import io.github.kosmx.emotes.common.network.EmotePacket;
import io.github.kosmx.emotes.common.network.GeyserEmotePacket;
import io.github.kosmx.emotes.common.network.PacketTask;
import io.github.kosmx.emotes.common.network.objects.NetData;
import io.github.kosmx.emotes.api.proxy.INetworkInstance;
import io.github.kosmx.emotes.common.tools.BiMap;
import io.github.kosmx.emotes.executor.EmoteInstance;
import io.github.kosmx.emotes.server.config.Serializer;
import io.github.kosmx.emotes.server.geyser.EmoteMappings;
import io.github.kosmx.emotes.server.serializer.BiMapSerializer;
import io.github.kosmx.emotes.server.serializer.EmoteSerializer;
import io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * This will be used for modded servers
 *
 */
public abstract class AbstractServerEmotePlay<P> {
    protected EmoteMappings bedrockEmoteMap = new EmoteMappings(new BiMap<>());


    public AbstractServerEmotePlay(){
        try {
            initMappings(EmoteInstance.instance.getGameDirectory().resolve("config"));
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    public void initMappings(Path configPath) throws IOException{
        Path filePath = configPath.resolve("emotecraft_emote_map.json");
        if(filePath.toFile().isFile()){
            BufferedReader reader = Files.newBufferedReader(filePath);
            try {
                this.bedrockEmoteMap = new EmoteMappings(Serializer.serializer.fromJson(reader, new TypeToken<BiMap<UUID, UUID>>() {}.getType()));
            }catch (JsonParseException e){
                e.printStackTrace();
            }
            reader.close();
        }
        else {
            BiMap<UUID, UUID> example = new BiMap<>();
            example.put(new UUID(0x0011223344556677L, 0x8899aabbccddeeffL), new UUID(0xffeeddccbbaa9988L, 0x7766554433221100L));
            BufferedWriter writer = Files.newBufferedWriter(filePath);
            Serializer.serializer.toJson(example, new TypeToken<BiMap<UUID, UUID>>() {}.getType(), writer);
            writer.close();
        }
    }

    protected boolean doValidate(){
        return EmoteInstance.config.validateEmote.get();
    }

    protected abstract UUID getUUIDFromPlayer(P player);

    protected abstract long getRuntimePlayerID(P player);

    public void receiveMessage(byte[] bytes, P player, INetworkInstance instance) throws IOException{
        receiveMessage(new EmotePacket.Builder().setThreshold(EmoteInstance.config.validThreshold.get()).build().read(ByteBuffer.wrap(bytes)), player, instance);
    }

    public void receiveMessage(NetData data, P player, INetworkInstance instance) throws IOException {
        switch (data.purpose){
            case STOP:
                sendForEveryoneElse(data, null, player);
                break;
            case CONFIG:
                instance.setVersions(data.versions);
                instance.presenceResponse();
                break;
            case STREAM:
                streamEmote(data, player, instance);
                break;
            case UNKNOWN:
            default:
                throw new IOException("Unknown packet task");
        }
    }

    /**
     * Receive emote from GeyserMC
     * @param player player
     * @param emotePacket BE emote uuid
     */
    public void receiveBEEmote(P player, GeyserEmotePacket emotePacket) throws IOException {
        UUID javaEmote = bedrockEmoteMap.getJavaEmote(emotePacket.getEmoteID());
        if(javaEmote != null && UniversalEmoteSerializer.getEmote(javaEmote) != null){
            NetData data = new NetData();
            data.emoteData = UniversalEmoteSerializer.getEmote(javaEmote);
            data.purpose = PacketTask.STREAM;
            streamEmote(data, player, null);
        }
        else sendForEveryoneElse(emotePacket, player);
    }

    protected void streamEmote(NetData data, P player, INetworkInstance instance) throws IOException {
        if (!data.valid && doValidate()) {
            EventResult result = ServerEmoteEvents.EMOTE_VERIFICATION.invoker().verify(data.emoteData, getUUIDFromPlayer(player));
            if (result != EventResult.FAIL) {
                EmotePacket.Builder stopMSG = new EmotePacket.Builder().configureToSendStop(data.emoteData.getUuid()).configureTarget(getUUIDFromPlayer(player)).setSizeLimit(0x100000);
                if(instance != null)instance.sendMessage(stopMSG, null);
                return;
            }
        }
        UUID target = data.player;
        data.player = getUUIDFromPlayer(player);
        if (target == null) {
            UUID bedrockEmoteID = bedrockEmoteMap.getBeEmote(data.emoteData.getUuid());
            GeyserEmotePacket geyserEmotePacket = null;
            if(bedrockEmoteID != null){
                geyserEmotePacket = new GeyserEmotePacket();
                geyserEmotePacket.setEmoteID(bedrockEmoteID);
                geyserEmotePacket.setRuntimeEntityID(getRuntimePlayerID(player));
            }
            sendForEveryoneElse(data, geyserEmotePacket, player);
        } else {
            sendForPlayerInRange(data, player, target);
        }
    }

    public void receiveGeyserMessage(P player, byte[] data){
        try {
            GeyserEmotePacket packet = new GeyserEmotePacket();
            packet.read(data);
            packet.setRuntimeEntityID(getRuntimePlayerID(player));
            receiveBEEmote(player, packet);
        }catch (Throwable t){
            t.printStackTrace();
        }
    }

    protected abstract void sendForEveryoneElse(GeyserEmotePacket packet, P player);

    /**
     * Send the message to everyone, except for the player
     * @param data message
     * @param emotePacket GeyserMC emote packet for Geyser users ;D
     * @param player send around this player
     */
    protected abstract void sendForEveryoneElse(NetData data, @Nullable GeyserEmotePacket emotePacket, P player);

    /**
     * Send message to target. If target see player the message will be sent
     * @param data message
     * @param player around player
     * @param target target player
     */
    protected abstract void sendForPlayerInRange(NetData data, P player, UUID target);

    /**
     * Send a message to target. This will send a message even if target doesn't see player
     * @param data message
     * @param player player for the ServerWorld information
     * @param target target entity
     */
    protected abstract void sendForPlayer(NetData data, P player, UUID target);
}
