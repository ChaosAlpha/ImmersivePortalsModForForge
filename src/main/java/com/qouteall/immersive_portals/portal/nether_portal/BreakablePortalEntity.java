package com.qouteall.immersive_portals.portal.nether_portal;

import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.McHelper;
import com.qouteall.immersive_portals.ModMain;
import com.qouteall.immersive_portals.my_util.LimitedLogger;
import com.qouteall.immersive_portals.portal.Portal;
import com.qouteall.immersive_portals.portal.PortalPlaceholderBlock;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.commons.lang3.Validate;

import java.util.List;
import java.util.UUID;

public abstract class BreakablePortalEntity extends Portal {
    public BlockPortalShape blockPortalShape;
    public UUID reversePortalId;
    public boolean unbreakable = false;
    private boolean isNotified = true;
    private boolean shouldBreakPortal = false;
    
    public BreakablePortalEntity(
        EntityType<?> entityType_1,
        World world_1
    ) {
        super(entityType_1, world_1);
    }
    
    @Override
    public boolean isPortalValid() {
        if (world.isRemote) {
            return super.isPortalValid();
        }
        return super.isPortalValid() && blockPortalShape != null && reversePortalId != null;
    }
    
    @Override
    protected void readAdditional(CompoundNBT compoundTag) {
        super.readAdditional(compoundTag);
        if (compoundTag.contains("netherPortalShape")) {
            blockPortalShape = new BlockPortalShape(compoundTag.getCompound("netherPortalShape"));
        }
        reversePortalId = Helper.getUuid(compoundTag, "reversePortalId");
        unbreakable = compoundTag.getBoolean("unbreakable");
    }
    
    @Override
    protected void writeAdditional(CompoundNBT compoundTag) {
        super.writeAdditional(compoundTag);
        if (blockPortalShape != null) {
            compoundTag.put("netherPortalShape", blockPortalShape.toTag());
        }
        Helper.putUuid(compoundTag, "reversePortalId", reversePortalId);
        compoundTag.putBoolean("unbreakable", unbreakable);
    }
    
    private void breakPortalOnThisSide() {
        blockPortalShape.area.forEach(
            blockPos -> {
                if (world.getBlockState(blockPos).getBlock() == PortalPlaceholderBlock.instance) {
                    world.setBlockState(
                        blockPos, Blocks.AIR.getDefaultState()
                    );
                }
            }
        );
        this.remove();
        
        Helper.log("Broke " + this);
    }
    
    public void notifyPlaceholderUpdate() {
        isNotified = true;
    }
    
    private BreakablePortalEntity getReversePortal() {
        
        ServerWorld world = getServer().getWorld(dimensionTo);
        Entity entity = world.getEntityByUuid(reversePortalId);
        if (entity instanceof BreakablePortalEntity) {
            return (BreakablePortalEntity) entity;
        }
        else {
            return null;
        }
    }
    
    @Override
    public void tick() {
        super.tick();
        
        if (world.isRemote) {
            addSoundAndParticle();
        }
        else {
            if (!unbreakable) {
                if (isNotified || world.getGameTime() % 233 == getEntityId() % 233) {
                    isNotified = false;
                    checkPortalIntegrity();
                }
                if (shouldBreakPortal) {
                    breakPortalOnThisSide();
                }
            }
        }
        
    }
    
    private void checkPortalIntegrity() {
        Validate.isTrue(!world.isRemote);
        
        if (!isPortalValid()) {
            remove();
            return;
        }
        
        if (!isPortalIntactOnThisSide()) {
            markShouldBreak();
        }
        else if (!isPortalPaired()) {
            Helper.err("Break portal because of abnormal pairing");
            markShouldBreak();
        }
    }
    
    
    protected abstract boolean isPortalIntactOnThisSide();
    
    @OnlyIn(Dist.CLIENT)
    protected abstract void addSoundAndParticle();
    
    private static final LimitedLogger limitedLogger = new LimitedLogger(20);
    
    public boolean isPortalPaired() {
        Validate.isTrue(!world.isRemote());
        
        if (!isOtherSideChunkLoaded()) {
            return true;
        }
        
        List<BreakablePortalEntity> revs = McHelper.findEntitiesByBox(
            BreakablePortalEntity.class,
            getDestinationWorld(),
            new AxisAlignedBB(new BlockPos(destination)),
            10,
            e -> (e.getPositionVec().squareDistanceTo(destination) < 0.1) &&
                e.getContentDirection().dotProduct(getNormal()) > 0.6
        );
        if (revs.size() == 1) {
            BreakablePortalEntity reversePortal = revs.get(0);
            if (reversePortal.destination.squareDistanceTo(getPositionVec()) > 1) {
                return false;
            }
            else {
                return true;
            }
        }
        else if (revs.size() > 1) {
            return false;
        }
        else {
//            limitedLogger.err("Missing Reverse Portal " + this);
            return true;
        }
    }
    
    public void markShouldBreak() {
        shouldBreakPortal = true;
        BreakablePortalEntity reversePortal = getReversePortal();
        if (reversePortal != null) {
            reversePortal.shouldBreakPortal = true;
        }
        else {
            int[] counter = {30};
            ModMain.serverTaskList.addTask(() -> {
                BreakablePortalEntity reversePortal1 = getReversePortal();
                if (reversePortal1 != null) {
                    reversePortal1.shouldBreakPortal = true;
                    return true;
                }
                counter[0]--;
                return counter[0] >= 0;
            });
        }
    }
    
    private boolean isOtherSideChunkLoaded() {
        ChunkPos destChunkPos = new ChunkPos(new BlockPos(destination));
        return McHelper.getServerChunkIfPresent(
            dimensionTo, destChunkPos.x, destChunkPos.z
        ) != null;
    }
}
