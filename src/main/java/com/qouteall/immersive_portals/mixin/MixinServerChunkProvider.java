package com.qouteall.immersive_portals.mixin;

import com.qouteall.immersive_portals.exposer.IEServerChunkManager;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.TicketManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerChunkProvider.class)
public class MixinServerChunkProvider implements IEServerChunkManager {
    @Shadow
    @Final
    private TicketManager ticketManager;
    
    @Override
    public TicketManager getTicketManager() {
        return ticketManager;
    }
}