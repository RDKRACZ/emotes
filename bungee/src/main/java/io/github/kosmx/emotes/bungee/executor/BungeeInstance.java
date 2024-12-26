package io.github.kosmx.emotes.bungee.executor;

import io.github.kosmx.emotes.bungee.BungeeWrapper;
import io.github.kosmx.emotes.executor.EmoteInstance;
import io.github.kosmx.emotes.executor.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;

public class BungeeInstance extends EmoteInstance implements Logger {
    protected final BungeeWrapper plugin;

    public BungeeInstance(BungeeWrapper plugin) {
        this.plugin = plugin;
    }

    @Override
    public Logger getLogger() {
        return this;
    }

    @Override
    public void writeLog(Level level, String msg, Throwable throwable) {
        this.plugin.getLogger().log(level, msg, throwable);
    }

    @Override
    public void writeLog(Level level, String msg) {
        this.plugin.getLogger().log(level, msg);
    }

    @Override
    public boolean isClient() {
        return false;
    }

    @Override
    public Path getGameDirectory() {
        return Paths.get("");
    }
}