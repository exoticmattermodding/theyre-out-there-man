package com.exoticmatter.totm.mixin;

import com.exoticmatter.totm.world.block.entity.ModPotDecorations;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.DecoratedPotPatterns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DecoratedPotPatterns.class)
public class MixinDecoratedPotPatterns {

    @Inject(method = "getResourceKey", at = @At("HEAD"), cancellable = true)
    private static void onGetResourceKey(Item item, CallbackInfoReturnable<ResourceKey<String>> cir) {
        System.out.println("[DEBUG Mixin] getResourceKey called for item: " + item);
        ResourceKey<String> pattern = ModPotDecorations.getPatternFromItem(item);
        if (pattern != null) {
            System.out.println("[DEBUG Mixin] Returning custom pattern: " + pattern.location());
            cir.setReturnValue(pattern);
        }
    }
}


