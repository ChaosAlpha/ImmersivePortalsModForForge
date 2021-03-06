package com.qouteall.immersive_portals.portal.global_portals;

import com.qouteall.immersive_portals.McHelper;
import com.qouteall.immersive_portals.portal.Portal;
import com.qouteall.immersive_portals.portal.PortalExtension;
import net.minecraft.entity.EntityType;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import java.util.function.Predicate;

public class VerticalConnectingPortal extends GlobalTrackedPortal {
    public static EntityType<VerticalConnectingPortal> entityType;
    
    public static enum ConnectorType {
        ceil, floor
    }
    
    private static Predicate<Portal> getPredicate(ConnectorType connectorType) {
        switch (connectorType) {
            case floor:
                return portal -> portal instanceof VerticalConnectingPortal && portal.getNormal().y > 0;
            default:
            case ceil:
                return portal -> portal instanceof VerticalConnectingPortal && portal.getNormal().y < 0;
        }
    }
    
    public VerticalConnectingPortal(
        EntityType<?> entityType_1,
        World world_1
    ) {
        super(entityType_1, world_1);
    }
    
    public static void connect(
        RegistryKey<World> from,
        ConnectorType connectorType,
        RegistryKey<World> to
    ) {
        int upY = connectorType == ConnectorType.ceil ? getHeight(from) : getHeight(to);
        connect(from, connectorType, to, 0, upY, false);
    }
    
    public static void connect(
        RegistryKey<World> from,
        ConnectorType connectorType,
        RegistryKey<World> to,
        int downY,
        int upY,
        boolean respectSpaceRatio
    ) {
        removeConnectingPortal(connectorType, from);
        
        ServerWorld fromWorld = McHelper.getServer().getWorld(from);
        
        VerticalConnectingPortal connectingPortal = createConnectingPortal(
            fromWorld,
            connectorType,
            McHelper.getServer().getWorld(to),
            downY,
            upY,
            respectSpaceRatio
        );
        
        GlobalPortalStorage storage = GlobalPortalStorage.get(fromWorld);
        
        storage.addPortal(connectingPortal);
    }
    
    public static void connectMutually(
        RegistryKey<World> up,
        RegistryKey<World> down,
        boolean respectSpaceRatio
    ) {
        connectMutually(
            up, down,
            0, getHeight(down),
            respectSpaceRatio
        );
    }
    
    public static void connectMutually(
        RegistryKey<World> up,
        RegistryKey<World> down,
        int downY,
        int upY,
        boolean respectSpaceRatio
    ) {
        connect(up, ConnectorType.floor, down, downY, upY, respectSpaceRatio);
        connect(down, ConnectorType.ceil, up, downY, upY, respectSpaceRatio);
    }
    
    private static VerticalConnectingPortal createConnectingPortal(
        ServerWorld fromWorld,
        ConnectorType connectorType,
        ServerWorld toWorld,
        int downY,
        int upY,
        boolean respectSpaceRatio
    ) {
        VerticalConnectingPortal verticalConnectingPortal = new VerticalConnectingPortal(
            entityType, fromWorld
        );
        
        switch (connectorType) {
            case floor:
                
                verticalConnectingPortal.setPosition(0, downY, 0);
                verticalConnectingPortal.setDestination(new Vector3d(0, upY, 0));
                verticalConnectingPortal.axisW = new Vector3d(0, 0, 1);
                verticalConnectingPortal.axisH = new Vector3d(1, 0, 0);
                break;
            case ceil:
                verticalConnectingPortal.setPosition(0, upY, 0);
                verticalConnectingPortal.setDestination(new Vector3d(0, downY, 0));
                verticalConnectingPortal.axisW = new Vector3d(1, 0, 0);
                verticalConnectingPortal.axisH = new Vector3d(0, 0, 1);
                break;
        }
        
        verticalConnectingPortal.dimensionTo = toWorld.func_234923_W_();
        verticalConnectingPortal.width = 23333333333.0d;
        verticalConnectingPortal.height = 23333333333.0d;
        
        if (respectSpaceRatio) {
            verticalConnectingPortal.scaling =
                fromWorld.func_230315_m_().func_242724_f() / toWorld.func_230315_m_().func_242724_f();
            verticalConnectingPortal.teleportChangesScale = false;
            PortalExtension.get(verticalConnectingPortal).adjustPositionAfterTeleport = false;
        }
        
        return verticalConnectingPortal;
    }
    
    public static void removeConnectingPortal(
        ConnectorType connectorType,
        RegistryKey<World> dimension
    ) {
        removeConnectingPortal(getPredicate(connectorType), dimension);
    }
    
    private static void removeConnectingPortal(
        Predicate<Portal> predicate, RegistryKey<World> dimension
    ) {
        ServerWorld endWorld = McHelper.getServer().getWorld(dimension);
        GlobalPortalStorage storage = GlobalPortalStorage.get(endWorld);
        
        storage.removePortals(
            portal -> portal instanceof VerticalConnectingPortal && predicate.test(portal)
        );
    }
    
    public static VerticalConnectingPortal getConnectingPortal(
        World world, ConnectorType type
    ) {
        return (VerticalConnectingPortal) McHelper.getGlobalPortals(world).stream()
            .filter(getPredicate(type))
            .findFirst().orElse(null);
    }
    
    public static int getHeight(RegistryKey<World> dim) {
        return McHelper.getServer().getWorld(dim).func_234938_ad_();
    }
}
