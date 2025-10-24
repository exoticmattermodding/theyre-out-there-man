package com.exoticmatter.totm.mixin;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.DecoratedPotPatterns;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DecoratedPotPatterns.class)
public abstract class MixinDecoratedPotPatterns {

    // Our custom pattern key, same registry as vanilla uses (of strings)
    private static final ResourceKey<String> TOTM_ALIEN =
            ResourceKey.create(Registries.DECORATED_POT_PATTERNS, new ResourceLocation("totm", "alien_pottery_pattern"));

    // 1) Register the pattern string when vanilla registers theirs
    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void totm$registerPattern(Registry<String> reg, CallbackInfoReturnable<String> cir) {
        // value is just the string id written into the registry
        Registry.register(reg, TOTM_ALIEN, "alien_pottery_pattern");
    }

    // 2) On lookup, return our key when the queried item is our sherd
    @Inject(method = "getResourceKey", at = @At("HEAD"), cancellable = true)
    private static void totm$mapOurSherd(Item item, CallbackInfoReturnable<ResourceKey<String>> cir) {
        // Avoid touching your ModItems class early: compare by registry name instead
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id != null && id.equals(new ResourceLocation("totm", "alien_pottery_sherd"))) {
            cir.setReturnValue(TOTM_ALIEN);
        }
    }
}
