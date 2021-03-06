package com.qouteall.immersive_portals.portal;

import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.my_util.Plane;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;

public interface PortalLike {
    boolean isConventionalPortal();
    
    // bounding box
    AxisAlignedBB getExactAreaBox();
    
    Vector3d transformPoint(Vector3d pos);
    
    Vector3d transformLocalVec(Vector3d localVec);
    
    // TODO remove this and use the area box
    double getDistanceToNearestPointInPortal(
        Vector3d point
    );
    
    // TODO remove this and use the area box
    double getDestAreaRadiusEstimation();
    
    Vector3d getOriginPos();
    
    Vector3d getDestPos();
    
    World getOriginWorld();
    
    World getDestWorld();
    
    RegistryKey<World> getDestDim();
    
    boolean isRoughlyVisibleTo(Vector3d cameraPos);
    
    @Nullable
    Plane getInnerClipping();
    
    @Nullable
    Quaternion getRotation();
    
    double getScale();
    
    boolean getIsGlobal();
    
    // used for advanced frustum culling
    @Nullable
    Vector3d[] getInnerFrustumCullingVertices();
    
    // used for super advanced frustum culling
    @Nullable
    Vector3d[] getOuterFrustumCullingVertices();
    
    @OnlyIn(Dist.CLIENT)
    void renderViewAreaMesh(Vector3d posInPlayerCoordinate, Consumer<Vector3d> vertexOutput);
    
    // Scaling does not interfere camera transformation
    @Nullable
    Matrix4f getAdditionalCameraTransformation();
    
    @Nullable
    UUID getDiscriminator();
    
    boolean isParallelWith(Portal portal);
    
    default boolean hasScaling() {
        return getScale() != 1.0;
    }
    
    default RegistryKey<World> getOriginDim() {
        return getOriginWorld().func_234923_W_();
    }
    
    default boolean isInside(Vector3d entityPos, double valve) {
        Plane innerClipping = getInnerClipping();
        
        if (innerClipping == null) {
            return true;
        }
        
        double v = entityPos.subtract(innerClipping.pos).dotProduct(innerClipping.normal);
        return v > valve;
    }
    
    default double getSizeEstimation() {
        final Vector3d boxSize = Helper.getBoxSize(getExactAreaBox());
        final double maxDimension = Math.max(Math.max(boxSize.x, boxSize.y), boxSize.z);
        return maxDimension;
    }
    
}
