package com.exoticmatter.totm.network;

import com.exoticmatter.totm.TotmMod;
import com.exoticmatter.totm.network.packet.SaucerJumpPacket;
import com.exoticmatter.totm.network.packet.AbductionStateC2SPacket;
import com.exoticmatter.totm.network.packet.PilotStartS2CPacket;
import com.exoticmatter.totm.network.packet.PilotStopS2CPacket;
import com.exoticmatter.totm.network.packet.PilotInputPacket;
import com.exoticmatter.totm.network.packet.PilotStopC2SPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TotmMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void init() {
        int id = 0;
        CHANNEL.messageBuilder(SaucerJumpPacket.class, id++)
                .encoder(SaucerJumpPacket::encode)
                .decoder(SaucerJumpPacket::decode)
                .consumerMainThread(SaucerJumpPacket::handle)
                .add();

        CHANNEL.messageBuilder(PilotStartS2CPacket.class, id++)
                .encoder(PilotStartS2CPacket::encode)
                .decoder(PilotStartS2CPacket::decode)
                .consumerMainThread(PilotStartS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(PilotStopS2CPacket.class, id++)
                .encoder(PilotStopS2CPacket::encode)
                .decoder(PilotStopS2CPacket::decode)
                .consumerMainThread(PilotStopS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(PilotInputPacket.class, id++)
                .encoder(PilotInputPacket::encode)
                .decoder(PilotInputPacket::decode)
                .consumerMainThread(PilotInputPacket::handle)
                .add();

        CHANNEL.messageBuilder(PilotStopC2SPacket.class, id++)
                .encoder(PilotStopC2SPacket::encode)
                .decoder(PilotStopC2SPacket::decode)
                .consumerMainThread(PilotStopC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(AbductionStateC2SPacket.class, id++)
                .encoder(AbductionStateC2SPacket::encode)
                .decoder(AbductionStateC2SPacket::decode)
                .consumerMainThread(AbductionStateC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(com.exoticmatter.totm.network.packet.ToggleGlowC2SPacket.class, id++)
                .encoder(com.exoticmatter.totm.network.packet.ToggleGlowC2SPacket::encode)
                .decoder(com.exoticmatter.totm.network.packet.ToggleGlowC2SPacket::decode)
                .consumerMainThread(com.exoticmatter.totm.network.packet.ToggleGlowC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(com.exoticmatter.totm.network.packet.PairSaucerC2SPacket.class, id++)
                .encoder(com.exoticmatter.totm.network.packet.PairSaucerC2SPacket::encode)
                .decoder(com.exoticmatter.totm.network.packet.PairSaucerC2SPacket::decode)
                .consumerMainThread(com.exoticmatter.totm.network.packet.PairSaucerC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(com.exoticmatter.totm.network.packet.AlienCowSkinS2CPacket.class, id++)
                .encoder(com.exoticmatter.totm.network.packet.AlienCowSkinS2CPacket::encode)
                .decoder(com.exoticmatter.totm.network.packet.AlienCowSkinS2CPacket::decode)
                .consumerMainThread(com.exoticmatter.totm.network.packet.AlienCowSkinS2CPacket::handle)
                .add();
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
