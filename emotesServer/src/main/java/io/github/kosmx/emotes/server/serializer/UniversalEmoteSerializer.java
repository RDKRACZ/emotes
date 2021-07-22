package io.github.kosmx.emotes.server.serializer;


import io.github.kosmx.emotes.common.emote.EmoteData;
import io.github.kosmx.emotes.server.serializer.type.EmoteSerializerException;
import io.github.kosmx.emotes.server.serializer.type.IReader;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

public class UniversalEmoteSerializer {
    public static String binaryExtension = "mcemote";
    public static List<IReader> readers = new ArrayList<>();


    /**
     * Read an emote file
     * @param bufferedReader binary reader. No physical file needed
     * @param filename filename. can be null
     * @param format lowercase format string
     * @return List of reader emotes.
     * @throws EmoteSerializerException If the file is not valid or cannot be readed.
     */
    public static List<EmoteData> readData(BufferedReader bufferedReader,@Nullable String filename, String format) throws EmoteSerializerException {
        for(IReader reader : readers){
            if(reader.getFormatExtension().equals(format)){
                try {
                    return reader.read(bufferedReader, filename);
                }
                catch (EmoteSerializerException e){
                    throw e; //We don't need to catch it.
                }
                catch (Exception e){
                    throw new EmoteSerializerException(e.getClass().getName() + " has occurred while serializing an emote", format, e);
                }
            }
        }
        throw new EmoteSerializerException("No known reader for format", format);
    }

    /**
     * Read a file with a not known extension
     * @param bufferedReader binary file reader
     * @param filename filename. can NOT be null if no format parameter is supplied. {@link UniversalEmoteSerializer#readData(BufferedReader, String, String)}
     * @return list of emotes
     * @throws EmoteSerializerException exception if something goes wrong
     */
    public static List<EmoteData> readData(BufferedReader bufferedReader, String filename) throws EmoteSerializerException{
        if(filename == null || filename.equals(""))throw new IllegalArgumentException("filename can not be null if no format type was given");
        String format = filename.substring(filename.lastIndexOf(".")).toLowerCase();
        return readData(bufferedReader, filename, format);
    }
}
