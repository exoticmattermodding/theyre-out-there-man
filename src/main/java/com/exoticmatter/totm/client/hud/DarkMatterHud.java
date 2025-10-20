package com.exoticmatter.totm.client.hud;

import com.exoticmatter.totm.client.PilotClientState;
import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "totm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DarkMatterHud {
    private static final ResourceLocation BG = new ResourceLocation("totm", "textures/gui/darkmatter_bar_background.png");
    private static final ResourceLocation FG = new ResourceLocation("totm", "textures/gui/darkmatter_bar_progress.png");
    private static final int TEX_W = 182;
    private static final int TEX_H = 5;

    @SubscribeEvent
    public static void onGuiRender(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!PilotClientState.isPiloting()) return;
        int id = PilotClientState.getPilotedSaucerId();
        if (id == -1) return;
        var e = mc.level.getEntity(id);
        if (!(e instanceof FlyingSaucerEntity saucer)) return;

        int dm = saucer.getDarkMatter();
        int dmMax = saucer.getMaxDarkMatter();
        if (dmMax <= 0) return;

        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int x = (w - TEX_W) / 2;
        int y = h - 35; // just above XP bar
        int filled = Math.max(0, Math.min(TEX_W, (int) Math.round((dm / (double) dmMax) * TEX_W)));

        // Draw background
        g.blit(BG, x, y, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
        // Draw progress (partial width)
        if (filled > 0) {
            g.blit(FG, x, y, 0, 0, filled, TEX_H, TEX_W, TEX_H);
        }
    }
}
