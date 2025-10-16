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
    private static boolean wasRiding = false;
    private static double  extraDistance = 8.0; // start a bit farther than vanilla
    private static double  curExtra      = extraDistance;

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
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        }
    }

    // Mouse wheel sets the target distance (consume so hotbar doesn’t scroll)
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !isRidingSaucer(player)) return;

        double delta = event.getScrollDelta();
        if (delta == 0) return;

        extraDistance = Mth.clamp(extraDistance - (delta * SCROLL_STEP), MIN_EXTRA, MAX_EXTRA);
        event.setCanceled(true);
    }

    // Smoothly push the renderer’s distance every client tick (no missing hooks used)
    // Maintain state and enforce third person every tick
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            wasRiding = false;
            return;
        }

        if (!isRidingSaucer(player)) {
            wasRiding = false;
            return;
        }

        if (!wasRiding) {
            wasRiding = true;
            curExtra = extraDistance;
        }

        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }

        curExtra = Mth.lerp(LERP_SPEED, curExtra, extraDistance);
        double total = BASE_DISTANCE + curExtra;
        updateCameraDistance(mc, total);
    }

    // Optional: widen FOV when riding (this hook exists on 47.4.0)
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!SCALE_FOV_WHEN_RIDING) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !isRidingSaucer(player)) return;

        event.setFOV(event.getFOV() * FOV_SCALE);
    }

    // --- Reflection helpers ---

        private static Field thirdPersonDistanceField;
        private static Field thirdPersonDistancePrevField;
        private static boolean triedBindFields = false;

        private static void ensureDistanceFields(Minecraft mc) {
            if (triedBindFields) return;
            triedBindFields = true;
            try {
                // Mojmap names in 1.20.1:
                // private float thirdPersonDistance;
                // private float thirdPersonDistancePrev;
                Class<?> gr = mc.gameRenderer.getClass();
                thirdPersonDistanceField = gr.getDeclaredField("thirdPersonDistance");
                thirdPersonDistancePrevField = gr.getDeclaredField("thirdPersonDistancePrev");
                Class<?> rendererClass = mc.gameRenderer.getClass();
                thirdPersonDistanceField = rendererClass.getDeclaredField("thirdPersonDistance");
                thirdPersonDistancePrevField = rendererClass.getDeclaredField("thirdPersonDistancePrev");
                thirdPersonDistanceField.setAccessible(true);
                thirdPersonDistancePrevField.setAccessible(true);
            } catch (ReflectiveOperationException ignored) {
                // Best-effort fallback for remapped or obfuscated names
                try {
                    Class<?> rendererClass = mc.gameRenderer.getClass();
                    thirdPersonDistanceField = findField(rendererClass, "third", "person", "distance");
                    thirdPersonDistancePrevField = findField(rendererClass, "third", "person", "distance", "prev");
                    if (thirdPersonDistanceField != null) thirdPersonDistanceField.setAccessible(true);
                    if (thirdPersonDistancePrevField != null) thirdPersonDistancePrevField.setAccessible(true);
                } catch (Exception ignoredAgain) {
                // If we cannot bind the fields we will simply skip updates.
            }

        }
    }

                private static Field findField(Class<?> type, String... parts) {
                    search:
                    for (Field field : type.getDeclaredFields()) {
                        String name = field.getName().toLowerCase();
                        for (String part : parts) {
                            if (!name.contains(part)) {
                                continue search;
                            }
                        }
                        return field;
                    }
                    return null;
                }

                    private static void updateCameraDistance(Minecraft mc, double distance) {
                        ensureDistanceFields(mc);
                        if (thirdPersonDistanceField == null || thirdPersonDistancePrevField == null) return;
                        try {
                            float dist = (float) distance;
                            thirdPersonDistanceField.setFloat(mc.gameRenderer, dist);
                            thirdPersonDistancePrevField.setFloat(mc.gameRenderer, dist);
                        } catch (IllegalAccessException ignored) {
                            // Leave vanilla distance untouched if reflection fails.
                        }
                    }
                }