// com/exoticmatter/totm/network/ModNet.java
package com.exoticmatter.totm.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNet {
    public static final String PROTO = "1";
    public static SimpleChannel CHANNEL;

    public static void init() { // call in @Mod ctor
        if (CHANNEL != null) return;
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation("totm", "main"),
                () -> PROTO, PROTO::equals, PROTO::equals
        );
    }


    private ModNet() {}
}
