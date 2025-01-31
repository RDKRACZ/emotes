package io.github.kosmx.emotes.main;

import io.github.kosmx.emotes.common.CommonData;
import io.github.kosmx.emotes.common.emote.EmoteData;
import io.github.kosmx.emotes.common.emote.EmoteFormat;
import io.github.kosmx.emotes.common.tools.MathHelper;
import io.github.kosmx.emotes.common.tools.UUIDMap;
import io.github.kosmx.emotes.executor.EmoteInstance;
import io.github.kosmx.emotes.main.config.ClientConfig;
import io.github.kosmx.emotes.main.network.ClientPacketManager;
import io.github.kosmx.emotes.server.serializer.EmoteSerializer;
import io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer;
import io.github.kosmx.emotes.server.serializer.type.EmoteSerializerException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

/**
 * Initializing client and other load stuff...
 *
 */
public class MainClientInit {

    public static void init(){
        loadEmotes();//:D

        ClientPacketManager.init(); //initialize proxy service
    }


    public static void loadEmotes() {
        UniversalEmoteSerializer.loadEmotes();

        EmoteHolder.clearEmotes();

        EmoteHolder.addEmoteToList(UniversalEmoteSerializer.hiddenServerEmotes);

    }

    /**
     * play the test emote
     */
    public static void playDebugEmote(){
        EmoteInstance.instance.getLogger().log(Level.INFO, "Playing debug emote");
        Path location = null;
        for(EmoteFormat source:EmoteFormat.values()){
            location = EmoteInstance.instance.getGameDirectory().resolve("emote." + source.getExtension());
            if(location.toFile().isFile()){
                break;
            }
        }
        if(location == null)return;
        try{
            InputStream reader = Files.newInputStream(location);
            EmoteHolder emoteHolder = new EmoteHolder(UniversalEmoteSerializer.readData(reader, location.getFileName().toString()).get(0));
            reader.close();
            if(EmoteInstance.instance.getClientMethods().getMainPlayer() != null){
                emoteHolder.playEmote(EmoteInstance.instance.getClientMethods().getMainPlayer());
            }
        }catch(Exception e){
            EmoteInstance.instance.getLogger().log(Level.INFO, "Error while importing debug emote.", true);
            EmoteInstance.instance.getLogger().log(Level.INFO, e.getMessage());
            e.printStackTrace();
        }
    }
}
