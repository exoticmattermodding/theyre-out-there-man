package com.exoticmatter.totm.client;

import com.exoticmatter.totm.network.ModNetwork;
import com.exoticmatter.totm.network.packet.SaucerJumpPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "totm", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PilotClientState {
    private static int pilotedSaucerId = -1;
    private static boolean wasJumpDown = false;
    private static int chargeTicks = 0;
    private static net.minecraft.client.CameraType prevCam = null;
    private static double camDistance = 14.0;
    private static double camHeight = 4.0;

    public static void startPiloting(int saucerId) {
        pilotedSaucerId = saucerId;
        wasJumpDown = false;
        chargeTicks = 0;
        var mc = Minecraft.getInstance();
        if (prevCam == null) prevCam = mc.options.getCameraType();
        mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        // No in-game chat messages
    }
    public static void stopPiloting() {
        pilotedSaucerId = -1;
        wasJumpDown = false;
        chargeTicks = 0;
        var mc = Minecraft.getInstance();
        if (prevCam != null) {
            mc.options.setCameraType(prevCam);
            prevCam = null;
        }
        if (mc.player != null) {
            // Restore normal player physics/state after piloting
            mc.player.noPhysics = false;
            mc.player.setDeltaMovement(0, 0, 0);
        }
    }
    public static boolean isPiloting() { return pilotedSaucerId != -1; }
    public static int getPilotedSaucerId() { return pilotedSaucerId; }

    @SubscribeEvent
    public static void onScroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent e) {
        if (!isPiloting()) return;
        double d = e.getScrollDelta();
        if (d == 0) return;
        camDistance -= Math.signum(d) * 2.0; // scroll faster
        if (camDistance < 8.0) camDistance = 8.0;
        if (camDistance > 34.0) camDistance = 34.0;
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseClick(net.minecraftforge.client.event.InputEvent.MouseButton.Pre e) {
        if (!isPiloting()) return;
        if (e.getButton() == 1 && e.getAction() == 1) { // right click pressed
            ModNetwork.sendToServer(new com.exoticmatter.totm.network.packet.PilotStopC2SPacket(pilotedSaucerId));
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // Auto-stop if saucer no longer exists (e.g., on new world)
        if (isPiloting() && mc.level.getEntity(pilotedSaucerId) == null) {
            stopPiloting();
        }
        if (!isPiloting()) return;

        if (mc.player == null) return;

        boolean jumpDown = mc.options.keyJump.isDown();
        // Send pilot input each tick
        // Read camera orientation and raw key states so possession works even when camera is the saucer
        if (mc.player != null) {
            var cam = mc.gameRenderer.getMainCamera();
            float yaw = cam.getYRot();
            float pitch = cam.getXRot();
            boolean up = mc.options.keyUp.isDown();
            boolean down = mc.options.keyDown.isDown();
            boolean left = mc.options.keyLeft.isDown();
            boolean right = mc.options.keyRight.isDown();
            float forward = (up ? 1f : 0f) + (down ? -1f : 0f);
            float strafe = (left ? -1f : 0f) + (right ? 1f : 0f);
            boolean sneak = mc.options.keyShift.isDown();
            // clamp to [-1,1]
            if (forward > 1f) forward = 1f; if (forward < -1f) forward = -1f;
            if (strafe > 1f) strafe = 1f; if (strafe < -1f) strafe = -1f;
            ModNetwork.sendToServer(new com.exoticmatter.totm.network.packet.PilotInputPacket(pilotedSaucerId, yaw, pitch, strafe, forward, sneak));
        }

        // Smooth camera follow by tethering the local player to a point behind/above the saucer
        var saucer = mc.level.getEntity(pilotedSaucerId);
        if (saucer != null && mc.player != null) {
            var camNow = mc.gameRenderer.getMainCamera();
            float cyaw = camNow.getYRot();
            float cpitch = camNow.getXRot();
            var look = net.minecraft.world.phys.Vec3.directionFromRotation(cpitch, cyaw);
            var base = new net.minecraft.world.phys.Vec3(saucer.getX(), saucer.getY(), saucer.getZ()).add(0, camHeight, 0);
            var desired = base.add(look.scale(-camDistance));
            var p = mc.player;
            p.noPhysics = true;
            p.setDeltaMovement(0, 0, 0);
            double lerp = 0.7;
            double nx = p.getX() + (desired.x - p.getX()) * lerp;
            double ny = p.getY() + (desired.y - p.getY()) * lerp;
            double nz = p.getZ() + (desired.z - p.getZ()) * lerp;
            p.setPos(nx, ny, nz);
        }
        if (jumpDown) {
            chargeTicks = Math.min(chargeTicks + 1, 20); // ~1s to full
        }
        if (wasJumpDown && !jumpDown) {
            int power = (int)Math.round((chargeTicks / 20.0) * 100.0);
            ModNetwork.sendToServer(new SaucerJumpPacket(pilotedSaucerId, power));
            chargeTicks = 0;
        }
        wasJumpDown = jumpDown;
    }
}
