package com.exoticmatter.totm.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.DecoratedPotPatterns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;

@Mixin(Sheets.class)
public abstract class MixinSheets {
    @Inject(method = "getDecoratedPotMaterial", at = @At("HEAD"), cancellable = true)
    private static void totm$lazyPotMaterial(@Nullable ResourceKey<String> key, CallbackInfoReturnable<Material> cir) {
        if (key == null) return;
        // Construct material on-demand for data-driven keys (avoids early static map miss)
        Material material = new Material(Sheets.DECORATED_POT_SHEET, DecoratedPotPatterns.location(key));
        LogUtils.getLogger().debug("[totm] Lazy pot material for key={}", key.location());
        cir.setReturnValue(material);
    }
}

