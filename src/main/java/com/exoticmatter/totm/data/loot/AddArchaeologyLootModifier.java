// AddArchaeologyLootModifier.java
package com.exoticmatter.totm.data.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.loot.LootModifier;

import java.util.List;

public class AddArchaeologyLootModifier extends LootModifier {
    public static final MapCodec<AddArchaeologyLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            // Which archaeology table to target
            ResourceLocation.CODEC.fieldOf("target").forGetter(m -> m.target),
            // Items to add
            ItemAddition.CODEC.listOf().fieldOf("additions").forGetter(m -> m.additions)
    ).and(LOOT_CONDITIONS_CODEC.fieldOf("conditions").forGetter(m -> m.conditions)).apply(inst, AddArchaeologyLootModifier::new));

    public static record ItemAddition(ResourceLocation itemId, float chance, int min, int max) {
        public static final Codec<ItemAddition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(ItemAddition::itemId),
                Codec.FLOAT.fieldOf("chance").forGetter(ItemAddition::chance),
                Codec.INT.fieldOf("min").forGetter(ItemAddition::min),
                Codec.INT.fieldOf("max").forGetter(ItemAddition::max)
        ).apply(inst, ItemAddition::new));
    }

    private final ResourceLocation target;
    private final List<ItemAddition> additions;

    public AddArchaeologyLootModifier(ResourceLocation target, List<ItemAddition> additions, LootItemCondition[] conditions) {
        super(conditions);
        this.target = target;
        this.additions = additions;
    }

    @Override
    protected List<ItemStack> doApply(List<ItemStack> generatedLoot, LootContext ctx) {
        // Only when THIS archaeology table is being generated
        LootTable table = ctx.getQueriedLootTableId() != null ? ctx.getLevel().getServer().getLootData().getLootTable(ctx.getQueriedLootTableId()) : null;
        if (ctx.getQueriedLootTableId() == null || !ctx.getQueriedLootTableId().equals(this.target)) return generatedLoot;

        RandomSource rand = ctx.getRandom();
        for (ItemAddition add : additions) {
            if (rand.nextFloat() <= add.chance()) {
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(add.itemId());
                if (item != null) {
                    int count = add.min() + rand.nextInt(Math.max(1, add.max() - add.min() + 1));
                    generatedLoot.add(new ItemStack(item, count));
                }
            }
        }
        return generatedLoot;
    }

    @Override public MapCodec<? extends LootModifier> codec() { return CODEC; }
}
