package com.exoticmatter.totm.client.render;

import com.exoticmatter.totm.world.entity.FlyingSaucerEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
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
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Saucer third-person scroll zoom for Forge 47.4.0.
 * - Preferred: distance-based zoom by adjusting Camera third-person distance via reflection
 * - Fallback available (commented earlier) was FOV-based; kept disabled due to distortion
 * - Smooth interpolation and safety clamps; forces third-person-back while mounted
 */
@Mod.EventBusSubscriber(modid = "totm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SaucerCameraHandler {

    // --- Tunables ---
    private static final double BASE_DISTANCE          = 6.0;   // vanilla-ish base
    private static final double MAX_EXTRA              = 26.0;  // additional distance beyond base
    private static final double MIN_TOTAL              = 3.0;   // hard lower bound to avoid inversion
    private static final double SCROLL_STEP            = 0.6;   // distance change per notch
    private static final double LERP_SPEED             = 0.18;  // smoother interpolation
    private static final int    SCROLL_DIR             = -1;    // scroll up zooms OUT
    private static final boolean DEBUG_LOGS            = true; // enable briefly only
    private static final boolean PRINT_CAMERA_SNAPSHOT = true; // set true once to log Camera fields

    // --- State ---
    private static boolean wasRiding = false;
    private static double  extraDistance   = 8.0; // start farther than vanilla
    private static double  curExtra        = extraDistance;
    private static double  lastAppliedTotal = -1.0;

    // Reflection bindings to Camera fields (development-friendly, deobf-aware)
    private static Field camDistField;
    private static Field camDistPrevField;
    private static boolean triedBindCamera = false;

    private static boolean isRidingSaucer(Player p) {
        Entity v = p.getVehicle();
        return v instanceof FlyingSaucerEntity;
    }

    // Mount/dismount toggles the behavior
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (!(event.getEntityMounting() instanceof Player)) return;
        Entity mount = event.getEntityBeingMounted();

        if (event.isMounting() && mount instanceof FlyingSaucerEntity) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            // Reset cache when mounting
            lastAppliedTotal = -1.0;
            if (PRINT_CAMERA_SNAPSHOT) dumpCameraNumericFields(mc);
        }
    }

    // Mouse wheel sets the target distance (consume to prevent hotbar scroll)
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !isRidingSaucer(player)) return;

        double delta = event.getScrollDelta();
        if (delta == 0) return;

        double before = extraDistance;
        double target = extraDistance + (delta * SCROLL_DIR * SCROLL_STEP);
        extraDistance = Mth.clamp(target, 0.0, MAX_EXTRA);
        event.setCanceled(true);
        if (DEBUG_LOGS) {
            System.out.println("[TOTM] Scroll delta=" + delta + " extraDistance: " + before + " -> " + extraDistance);
        }
    }

    // Smoothly push the renderer's distance every client tick
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

        if (!wasRiding) wasRiding = true;

        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }

        curExtra = Mth.lerp(LERP_SPEED, curExtra, extraDistance);
        double total = Math.max(MIN_TOTAL, BASE_DISTANCE + curExtra);
        if (Math.abs(total - lastAppliedTotal) > 0.05) {
            applyCameraDistance(mc, total);
            lastAppliedTotal = total;
        }
    }

    // Optional: widen FOV when riding (hook exists on 47.4.0)
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !isRidingSaucer(player)) return;

        // Ensure distance is applied near render as well
        double total = Math.max(MIN_TOTAL, BASE_DISTANCE + curExtra);
        if (Math.abs(total - lastAppliedTotal) > 0.05) {
            applyCameraDistance(mc, total);
            lastAppliedTotal = total;
        }
    }

    // Also apply around angle computation, which runs during camera setup each frame
    @SubscribeEvent
    public static void onComputeAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !isRidingSaucer(player)) return;
        double total = Math.max(MIN_TOTAL, BASE_DISTANCE + curExtra);
        if (Math.abs(total - lastAppliedTotal) > 0.05) {
            applyCameraDistance(mc, total);
            lastAppliedTotal = total;
        }
    }

    // --- Binding & application for Camera distance ---

    private static void bindCameraFields(Minecraft mc) {
        if (triedBindCamera || (camDistField != null && camDistPrevField != null)) return;
        triedBindCamera = true;
        try {
            Class<?> camClass = mc.gameRenderer.getMainCamera().getClass();
            // Try common deobf names first
            camDistField = tryField(camClass, "thirdPersonDistance", "thirdPersonBackDistance", "cameraDistance");
            camDistPrevField = tryField(camClass, "thirdPersonDistancePrev", "thirdPersonBackDistancePrev", "cameraDistancePrev");

            if (camDistField == null || camDistPrevField == null) {
                // Fallback: pick two non-static float/double fields with plausible values (3..10)
                Object cam = mc.gameRenderer.getMainCamera();
                List<Field> nums = new ArrayList<>();
                List<Field> pref = new ArrayList<>();
                for (Field f : camClass.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    Class<?> t = f.getType();
                    if (t != float.class && t != double.class) continue;
                    try {
                        f.setAccessible(true);
                        double v = (t == float.class) ? f.getFloat(cam) : f.getDouble(cam);
                        nums.add(f);
                        if (v >= 3.0 && v <= 10.0) pref.add(f);
                    } catch (Throwable ignored) {}
                }
                List<Field> pick = !pref.isEmpty() ? pref : nums;
                if (!pick.isEmpty()) camDistField = pick.get(0);
                if (pick.size() >= 2) camDistPrevField = pick.get(1);
            }
        } catch (Throwable ignored) {}
    }

    private static Field tryField(Class<?> cls, String... names) {
        for (String n : names) {
            try { Field f = cls.getDeclaredField(n); f.setAccessible(true); return f; } catch (NoSuchFieldException ignored) {}
            try { Field f = ObfuscationReflectionHelper.findField(cls, n); f.setAccessible(true); return f; } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void applyCameraDistance(Minecraft mc, double distance) {
        bindCameraFields(mc);
        if (camDistField == null || camDistPrevField == null) return;
        Object cam = mc.gameRenderer.getMainCamera();
        try {
            setNumeric(cam, camDistField, distance);
            setNumeric(cam, camDistPrevField, distance);
        } catch (Throwable ignored) {}
    }

    private static void setNumeric(Object target, Field field, double value) throws IllegalAccessException {
        if (field.getType() == float.class) field.setFloat(target, (float) value);
        else if (field.getType() == double.class) field.setDouble(target, value);
    }

    private static void dumpCameraNumericFields(Minecraft mc) {
        try {
            Object cam = mc.gameRenderer.getMainCamera();
            Class<?> cc = cam.getClass();
            System.out.println("[TOTM] Camera numeric field snapshot:");
            for (Field f : cc.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                if (t != float.class && t != double.class) continue;
                try {
                    f.setAccessible(true);
                    double v = (t == float.class) ? f.getFloat(cam) : f.getDouble(cam);
                    System.out.println("  - " + f.getName() + " = " + v + " (" + t.getSimpleName() + ")");
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    // Accessed by mixin to know how far to push back beyond vanilla distance
    public static double getExtraPush() {
        return Math.max(0.0, curExtra);
    }
}
