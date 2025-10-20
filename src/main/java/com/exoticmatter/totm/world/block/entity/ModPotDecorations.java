package com.exoticmatter.totm.world.block.entity;

import com.exoticmatter.totm.TotmMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

public class ModPotDecorations {

    // 🏷️ Register to the decorated pot pattern registry
    public static final DeferredRegister<String> POT_DECORATION_PATTERNS =
            DeferredRegister.create(Registries.DECORATED_POT_PATTERNS, TotmMod.MODID);

    // 🖼️ Custom pattern key
    public static final ResourceKey<String> ALIEN_POTTERY_PATTERN =
            ResourceKey.create(Registries.DECORATED_POT_PATTERNS, new ResourceLocation(TotmMod.MODID, "alien_pottery_pattern"));

    // 🔑 Register the actual pattern string (pattern name = texture file name)
    public static final RegistryObject<String> ALIEN_PATTERN =
            POT_DECORATION_PATTERNS.register("alien_pottery_pattern", () -> "alien_pottery_pattern");

    // 🔁 Item → Pattern map (for use in rendering)
    private static final Map<Item, ResourceKey<String>> ITEM_TO_POT_TEXTURE = new HashMap<>();

    public static void registerPatternForItem(Item item, ResourceKey<String> pattern) {
        System.out.println("[DEBUG] Registering pattern for item " + item + " as " + pattern);
        ITEM_TO_POT_TEXTURE.put(item, pattern);
    }

    public static ResourceKey<String> getPatternFromItem(Item item) {
        ResourceKey<String> pattern = ITEM_TO_POT_TEXTURE.get(item);
        System.out.println("[DEBUG] getPatternFromItem called for " + item + ", returns " + pattern);
        return pattern;
    }
}
