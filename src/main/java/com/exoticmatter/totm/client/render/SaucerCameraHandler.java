package com.exoticmatter.totm.client.render;

import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

/**
 * Forge 1.20.1 (47.4.0) camera zoom for the saucer without ComputeCameraPosition:
 * - Mouse wheel changes real third-person distance
 * - Smooth interpolation each client tick
 * - Locks view to THIRD_PERSON_BACK while riding
 */
@Mod.EventBusSubscriber(modid = "totm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SaucerCameraHandler {

    // --- Tunables ---
    private static final double BASE_DISTANCE = 6.0;   // vanilla-ish
    private static final double MAX_EXTRA     = 26.0;  // total ~32 blocks (6 + 26)
    private static final double MIN_EXTRA     = 0.0;
    private static final double SCROLL_STEP   = 1.0;   // wheel notch step
    private static final double LERP_SPEED    = 0.25;  // 0..1 smoothing
    private static final boolean SCALE_FOV_WHEN_RIDING = false;
    private static final double  FOV_SCALE             = 1.0;

    // --- State ---
    private static boolean active = false;
    private static double  extraDistance = 8.0; // start a bit farther than vanilla
    private static double  curExtra      = extraDistance;

    // Reflection cache (GameRenderer.thirdPersonDistance / thirdPersonDistancePrev)
    private static Field thirdPersonDistanceField;
    private static Field thirdPersonDistancePrevField;
    private static boolean triedBindFields = false;

    private static boolean isRidingSaucer(Player p) {
        Entity v = p.getVehicle();
        return v instanceof FlyingSaucerEntity;
    }

    // Mount/dismount toggles the behavior (correct accessors on 47.4.0)
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (!(event.getEntityMounting() instanceof Player)) return;
        Entity mount = event.getEntityBeingMounted();

        if (event.isMounting() && mount instanceof FlyingSaucerEntity) {
            active = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        } else if (event.isDismounting() && mount instanceof FlyingSaucerEntity) {
            active = false;
        }
    }

    // Mouse wheel sets the target distance (consume so hotbar doesn’t scroll)
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!active || player == null || !isRidingSaucer(player)) return;

        double delta = event.getScrollDelta();
        if (delta == 0) return;

        extraDistance = Mth.clamp(extraDistance - (delta * SCROLL_STEP), MIN_EXTRA, MAX_EXTRA);
        event.setCanceled(true);
    }

    // Smoothly push the renderer’s distance every client tick (no missing hooks used)
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!active || player == null || !isRidingSaucer(player)) return;

        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }

        curExtra = Mth.lerp(LERP_SPEED, curExtra, extraDistance);
        double total = BASE_DISTANCE + curExtra;
        setThirdPersonDistance(mc, total);
    }

    // Optional: widen FOV when riding (this hook exists on 47.4.0)
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!SCALE_FOV_WHEN_RIDING) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!active || player == null || !isRidingSaucer(player)) return;

        event.setFOV(event.getFOV() * FOV_SCALE);
    }

    // --- Reflection helpers ---

    private static void bindDistanceFields(Minecraft mc) {
        if (triedBindFields) return;
        triedBindFields = true;
        try {
            // Mojmap names in 1.20.1:
            // private float thirdPersonDistance;
            // private float thirdPersonDistancePrev;
            Class<?> gr = mc.gameRenderer.getClass();
            thirdPersonDistanceField = gr.getDeclaredField("thirdPersonDistance");
            thirdPersonDistancePrevField = gr.getDeclaredField("thirdPersonDistancePrev");
            thirdPersonDistanceField.setAccessible(true);
            thirdPersonDistancePrevField.setAccessible(true);
        } catch (Throwable t) {
            // Fallback: scan for likely names if mappings differ
            try {
                Class<?> gr = mc.gameRenderer.getClass();
                thirdPersonDistanceField = findAnyField(gr, "third", "person", "distance");
                thirdPersonDistancePrevField = findAnyField(gr, "third", "person", "distance", "prev");
                if (thirdPersonDistanceField != null) thirdPersonDistanceField.setAccessible(true);
                if (thirdPersonDistancePrevField != null) thirdPersonDistancePrevField.setAccessible(true);
            } catch (Throwable ignored) {}
        }
    }

    private static Field findAnyField(Class<?> cls, String... parts) {
        outer:
        for (Field f : cls.getDeclaredFields()) {
            String name = f.getName().toLowerCase();
            for (String p : parts) {
                if (!name.contains(p)) continue outer;
            }
            return f;
        }
        return null;
    }

    private static void setThirdPersonDistance(Minecraft mc, double distance) {
        bindDistanceFields(mc);
        if (thirdPersonDistanceField == null || thirdPersonDistancePrevField == null) return;
        try {
            float f = (float) distance;
            thirdPersonDistanceField.setFloat(mc.gameRenderer, f);
            thirdPersonDistancePrevField.setFloat(mc.gameRenderer, f);
        } catch (Throwable ignored) {
            // If reflection ever fails, we just skip this tick gracefully.
        }
    }
}
