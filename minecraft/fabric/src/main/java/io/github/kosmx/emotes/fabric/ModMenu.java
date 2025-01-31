package io.github.kosmx.emotes.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.kosmx.emotes.arch.screen.EmoteMenu;
import net.minecraft.client.gui.screens.Screen;

public class ModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return (ConfigScreenFactory<Screen>) EmoteMenu::new;
    }
}
