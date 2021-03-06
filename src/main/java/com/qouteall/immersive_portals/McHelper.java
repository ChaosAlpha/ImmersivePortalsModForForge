package com.qouteall.immersive_portals;

import com.google.common.collect.Streams;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.qouteall.immersive_portals.ducks.IEThreadedAnvilChunkStorage;
import com.qouteall.immersive_portals.ducks.IEWorldChunk;
import com.qouteall.immersive_portals.portal.Portal;
import com.qouteall.immersive_portals.portal.global_portals.GlobalPortalStorage;
import com.qouteall.immersive_portals.render.CrossPortalEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.SimpleRegistry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import org.apache.commons.lang3.Validate;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

// mc related helper methods
public class McHelper {
    
    public static WeakReference<MinecraftServer> refMinecraftServer =
        new WeakReference<>(null);
    
    public static IEThreadedAnvilChunkStorage getIEStorage(RegistryKey<World> dimension) {
        return (IEThreadedAnvilChunkStorage) (
            (ServerChunkProvider) getServer().getWorld(dimension).getChunkProvider()
        ).chunkManager;
    }
    
    public static ArrayList<ServerPlayerEntity> getCopiedPlayerList() {
        return new ArrayList<>(getServer().getPlayerList().getPlayers());
    }
    
    public static List<ServerPlayerEntity> getRawPlayerList() {
        return getServer().getPlayerList().getPlayers();
    }
    
    public static Vector3d lastTickPosOf(Entity entity) {
        return new Vector3d(entity.prevPosX, entity.prevPosY, entity.prevPosZ);
    }
    
    public static MinecraftServer getServer() {
        return refMinecraftServer.get();
    }
    
    public static ServerWorld getOverWorldOnServer() {
        return getServer().getWorld(World.field_234918_g_);
    }
    
    public static void serverLog(
        ServerPlayerEntity player,
        String text
    ) {
        Helper.log(text);
        player.sendStatusMessage(new StringTextComponent(text), false);
    }
    
    public static AxisAlignedBB getChunkBoundingBox(ChunkPos chunkPos) {
        return new AxisAlignedBB(
            chunkPos.asBlockPos(),
            chunkPos.asBlockPos().add(16, 256, 16)
        );
    }
    
    public static long getServerGameTime() {
        return getOverWorldOnServer().getGameTime();
    }
    
    @Deprecated
    public static <T> void performFindingTaskOnServer(
        boolean isMultithreaded,
        Stream<T> stream,
        Predicate<T> predicate,
        IntPredicate taskWatcher,//return false to abort the task
        Consumer<T> onFound,
        Runnable onNotFound,
        Runnable finalizer
    ) {
        if (isMultithreaded) {
            performMultiThreadedFindingTaskOnServer(
                stream, predicate, taskWatcher, onFound, onNotFound, finalizer
            );
        }
        else {
            performSplittedFindingTaskOnServer(
                stream, predicate, taskWatcher, onFound, onNotFound, finalizer
            );
        }
    }
    
    public static <T> void performSplittedFindingTaskOnServer(
        Stream<T> stream,
        Predicate<T> predicate,
        IntPredicate taskWatcher,//return false to abort the task
        Consumer<T> onFound,
        Runnable onNotFound,
        Runnable finalizer
    ) {
        final long timeValve = (1000000000L / 50);
        int[] countStorage = new int[1];
        countStorage[0] = 0;
        Iterator<T> iterator = stream.iterator();
        ModMain.serverTaskList.addTask(() -> {
            boolean shouldContinueRunning =
                taskWatcher.test(countStorage[0]);
            if (!shouldContinueRunning) {
                finalizer.run();
                return true;
            }
            long startTime = System.nanoTime();
            for (; ; ) {
                for (int i = 0; i < 300; i++) {
                    if (iterator.hasNext()) {
                        T next = iterator.next();
                        if (predicate.test(next)) {
                            onFound.accept(next);
                            finalizer.run();
                            return true;
                        }
                        countStorage[0] += 1;
                    }
                    else {
                        //finished searching
                        onNotFound.run();
                        finalizer.run();
                        return true;
                    }
                }
                
                long currTime = System.nanoTime();
                
                if (currTime - startTime > timeValve) {
                    //suspend the task and retry it next tick
                    return false;
                }
            }
        });
    }
    
    @Deprecated
    public static <T> void performMultiThreadedFindingTaskOnServer(
        Stream<T> stream,
        Predicate<T> predicate,
        IntPredicate taskWatcher,//return false to abort the task
        Consumer<T> onFound,
        Runnable onNotFound,
        Runnable finalizer
    ) {
        int[] progress = new int[1];
        Helper.SimpleBox<Boolean> isAborted = new Helper.SimpleBox<>(false);
        Helper.SimpleBox<Runnable> finishBehavior = new Helper.SimpleBox<>(() -> {
            Helper.err("Error Occured???");
        });
        CompletableFuture<Void> future = CompletableFuture.runAsync(
            () -> {
                try {
                    T result = stream.peek(
                        obj -> {
                            progress[0] += 1;
                        }
                    ).filter(
                        predicate
                    ).findFirst().orElse(null);
                    if (result != null) {
                        finishBehavior.obj = () -> onFound.accept(result);
                    }
                    else {
                        finishBehavior.obj = onNotFound;
                    }
                }
                catch (Throwable t) {
                    t.printStackTrace();
                    finishBehavior.obj = () -> {
                        t.printStackTrace();
                    };
                }
            },
            Util.getServerExecutor()
        );
        ModMain.serverTaskList.addTask(() -> {
            if (future.isDone()) {
                if (!isAborted.obj) {
                    finishBehavior.obj.run();
                    finalizer.run();
                }
                else {
                    Helper.log("Future done but the task is aborted");
                }
                return true;
            }
            if (future.isCancelled()) {
                Helper.err("The future is cancelled???");
                finalizer.run();
                return true;
            }
            if (future.isCompletedExceptionally()) {
                Helper.err("The future is completed exceptionally???");
                finalizer.run();
                return true;
            }
            boolean shouldContinue = taskWatcher.test(progress[0]);
            if (!shouldContinue) {
                isAborted.obj = true;
                future.cancel(true);
                finalizer.run();
                return true;
            }
            else {
                return false;
            }
        });
    }
    
    // TODO remove this
    public static <ENTITY extends Entity> Stream<ENTITY> getEntitiesNearby(
        World world,
        Vector3d center,
        Class<ENTITY> entityClass,
        double range
    ) {
        AxisAlignedBB box = new AxisAlignedBB(center, center).grow(range);
        return (Stream) world.getEntitiesWithinAABB(entityClass, box, e -> true).stream();
    }
    
    public static <ENTITY extends Entity> Stream<ENTITY> getEntitiesNearby(
        Entity center,
        Class<ENTITY> entityClass,
        double range
    ) {
        return getEntitiesNearby(
            center.world,
            center.getPositionVec(),
            entityClass,
            range
        );
    }
    
    public static void runWithTransformation(
        MatrixStack matrixStack,
        Runnable renderingFunc
    ) {
        transformationPush(matrixStack);
        renderingFunc.run();
        transformationPop();
    }
    
    public static void transformationPop() {
        RenderSystem.matrixMode(GL11.GL_MODELVIEW);
        RenderSystem.popMatrix();
    }
    
    public static void transformationPush(MatrixStack matrixStack) {
        RenderSystem.matrixMode(GL11.GL_MODELVIEW);
        RenderSystem.pushMatrix();
        RenderSystem.loadIdentity();
        RenderSystem.multMatrix(matrixStack.getLast().getMatrix());
    }
    
    @Nonnull
    public static List<Portal> getGlobalPortals(World world) {
        List<Portal> result;
        if (world.isRemote()) {
            result = CHelper.getClientGlobalPortal(world);
        }
        else if (world instanceof ServerWorld) {
            result = GlobalPortalStorage.get(((ServerWorld) world)).data;
        }
        else {
            result = null;
        }
        return result != null ? result : Collections.emptyList();
    }
    
    public static Stream<Portal> getNearbyPortals(Entity center, double range) {
        List<Portal> globalPortals = getGlobalPortals(center.world);
        Stream<Portal> nearbyPortals = McHelper.getServerEntitiesNearbyWithoutLoadingChunk(
            center.world,
            center.getPositionVec(),
            Portal.class,
            range
        );
        return Streams.concat(
            globalPortals.stream().filter(
                p -> p.getDistanceToNearestPointInPortal(center.getPositionVec()) < range * 2
            ),
            nearbyPortals
        );
    }
    
    public static int getRenderDistanceOnServer() {
        return getIEStorage(World.field_234918_g_).getWatchDistance();
    }
    
    public static void setPosAndLastTickPos(
        Entity entity,
        Vector3d pos,
        Vector3d lastTickPos
    ) {
        entity.setRawPosition(pos.x, pos.y, pos.z);
        entity.lastTickPosX = lastTickPos.x;
        entity.lastTickPosY = lastTickPos.y;
        entity.lastTickPosZ = lastTickPos.z;
        entity.prevPosX = lastTickPos.x;
        entity.prevPosY = lastTickPos.y;
        entity.prevPosZ = lastTickPos.z;
    }
    
    public static Vector3d getEyePos(Entity entity) {
        float eyeHeight = entity.getEyeHeight();
        return entity.getPositionVec().add(0, eyeHeight, 0);
    }
    
    public static Vector3d getLastTickEyePos(Entity entity) {
        float eyeHeight = entity.getEyeHeight();
        return lastTickPosOf(entity).add(0, eyeHeight, 0);
    }
    
    public static void setEyePos(Entity entity, Vector3d eyePos, Vector3d lastTickEyePos) {
        float eyeHeight = entity.getEyeHeight();
        setPosAndLastTickPos(
            entity,
            eyePos.add(0, -eyeHeight, 0),
            lastTickEyePos.add(0, -eyeHeight, 0)
        );
    }
    
    public static double getVehicleY(Entity vehicle, Entity passenger) {
        return passenger.getPosY() - vehicle.getMountedYOffset() - passenger.getYOffset();
    }
    
    public static void adjustVehicle(Entity entity) {
        Entity vehicle = entity.getRidingEntity();
        if (vehicle == null) {
            return;
        }
        
        vehicle.setPosition(
            entity.getPosX(),
            getVehicleY(vehicle, entity),
            entity.getPosZ()
        );
    }
    
    public static Chunk getServerChunkIfPresent(
        RegistryKey<World> dimension,
        int x, int z
    ) {
        ChunkHolder chunkHolder_ = getIEStorage(dimension).getChunkHolder_(ChunkPos.asLong(x, z));
        if (chunkHolder_ == null) {
            return null;
        }
        return chunkHolder_.getChunkIfComplete();
    }
    
    public static Chunk getServerChunkIfPresent(
        ServerWorld world, int x, int z
    ) {
        ChunkHolder chunkHolder_ = ((IEThreadedAnvilChunkStorage) (
            (ServerChunkProvider) world.getChunkProvider()
        ).chunkManager).getChunkHolder_(ChunkPos.asLong(x, z));
        if (chunkHolder_ == null) {
            return null;
        }
        return chunkHolder_.getChunkIfComplete();
    }
    
    public static <ENTITY extends Entity> Stream<ENTITY> getServerEntitiesNearbyWithoutLoadingChunk(
        World world,
        Vector3d center,
        Class<ENTITY> entityClass,
        double range
    ) {
        return McHelper.findEntitiesRough(
            entityClass,
            world,
            center,
            (int) (range / 16),
            e -> true
        ).stream();
    }
    
    public static void updateBoundingBox(Entity player) {
        player.setPosition(player.getPosX(), player.getPosY(), player.getPosZ());
    }
    
    public static <T extends Entity> List<T> getEntitiesRegardingLargeEntities(
        World world,
        AxisAlignedBB box,
        double maxEntitySizeHalf,
        Class<T> entityClass,
        Predicate<T> predicate
    ) {
        return findEntitiesByBox(
            entityClass,
            world,
            box,
            maxEntitySizeHalf,
            predicate
        );
    }
    
    
    //avoid dedicated server crash
    public static void onClientEntityTick(Entity entity) {
        CrossPortalEntityRenderer.onEntityTickClient(entity);
    }
    
    public static Portal copyEntity(Portal portal) {
        Portal newPortal = ((Portal) portal.getType().create(portal.world));
        
        Validate.notNull(newPortal);
        
        newPortal.read(portal.writeWithoutTypeId(new CompoundNBT()));
        return newPortal;
    }
    
    public static interface ChunkAccessor {
        Chunk getChunk(int x, int z);
    }
    
    public static ChunkAccessor getChunkAccessor(World world) {
        if (world.isRemote()) {
            return world::getChunk;
        }
        else {
            return (x, z) -> getServerChunkIfPresent(((ServerWorld) world), x, z);
        }
    }
    
    public static <T extends Entity> List<T> findEntities(
        Class<T> entityClass,
        ChunkAccessor chunkAccessor,
        int chunkXStart,
        int chunkXEnd,
        int chunkYStart,
        int chunkYEnd,
        int chunkZStart,
        int chunkZEnd,
        Predicate<T> predicate
    ) {
        ArrayList<T> result = new ArrayList<>();
    
        foreachEntities(
            entityClass, chunkAccessor,
            chunkXStart, chunkXEnd, chunkYStart, chunkYEnd, chunkZStart, chunkZEnd,
            entity -> {
                if (predicate.test(entity)) {
                    result.add(entity);
                }
            }
        );
        return result;
    }
    
    public static <T extends Entity> void foreachEntities(
        Class<T> entityClass, ChunkAccessor chunkAccessor,
        int chunkXStart, int chunkXEnd,
        int chunkYStart, int chunkYEnd,
        int chunkZStart, int chunkZEnd,
        Consumer<T> consumer
    ) {
        Validate.isTrue(chunkXEnd >= chunkXStart);
        Validate.isTrue(chunkYEnd >= chunkYStart);
        Validate.isTrue(chunkZEnd >= chunkZStart);
        Validate.isTrue(chunkYStart >= 0);
        Validate.isTrue(chunkYEnd < 16);
        Validate.isTrue(chunkXEnd - chunkXStart < 1000, "too big");
        Validate.isTrue(chunkZEnd - chunkZStart < 1000, "too big");
        
        for (int x = chunkXStart; x <= chunkXEnd; x++) {
            for (int z = chunkZStart; z <= chunkZEnd; z++) {
                Chunk chunk = chunkAccessor.getChunk(x, z);
                if (chunk != null && !(chunk instanceof EmptyChunk)) {
                    ClassInheritanceMultiMap<Entity>[] entitySections =
                        ((IEWorldChunk) chunk).getEntitySections();
                    for (int i = chunkYStart; i <= chunkYEnd; i++) {
                        ClassInheritanceMultiMap<Entity> entitySection = entitySections[i];
                        for (T entity : entitySection.getByClass(entityClass)) {
                            consumer.accept(entity);
                        }
                    }
                }
            }
        }
    }
    
    //faster
    public static <T extends Entity> List<T> findEntitiesRough(
        Class<T> entityClass,
        World world,
        Vector3d center,
        int radiusChunks,
        Predicate<T> predicate
    ) {
        // the minimun is 1
        if (radiusChunks == 0) {
            radiusChunks = 1;
        }
        
        ChunkPos chunkPos = new ChunkPos(new BlockPos(center));
        return findEntities(
            entityClass,
            getChunkAccessor(world),
            chunkPos.x - radiusChunks,
            chunkPos.x + radiusChunks,
            0, 15,
            chunkPos.z - radiusChunks,
            chunkPos.z + radiusChunks,
            predicate
        );
    }
    
    //does not load chunk on server and works with large entities
    public static <T extends Entity> List<T> findEntitiesByBox(
        Class<T> entityClass,
        World world,
        AxisAlignedBB box,
        double maxEntityRadius,
        Predicate<T> predicate
    ) {
        ArrayList<T> result = new ArrayList<>();
        
        foreachEntitiesByBox(entityClass, world, box, maxEntityRadius, predicate, result::add);
        return result;
    }
    
    public static <T extends Entity> void foreachEntitiesByBox(
        Class<T> entityClass, World world, AxisAlignedBB box,
        double maxEntityRadius, Predicate<T> predicate, Consumer<T> consumer
    ) {
        
        foreachEntitiesByBoxApproximateRegions(entityClass, world, box, maxEntityRadius, entity -> {
            if (entity.getBoundingBox().intersects(box) && predicate.test(entity)) {
                consumer.accept(entity);
            }
        });
    }
    
    public static <T extends Entity> void foreachEntitiesByBoxApproximateRegions(
        Class<T> entityClass, World world, AxisAlignedBB box, double maxEntityRadius, Consumer<T> consumer
    ) {
        int xMin = (int) Math.floor(box.minX - maxEntityRadius);
        int yMin = (int) Math.floor(box.minY - maxEntityRadius);
        int zMin = (int) Math.floor(box.minZ - maxEntityRadius);
        int xMax = (int) Math.ceil(box.maxX + maxEntityRadius);
        int yMax = (int) Math.ceil(box.maxY + maxEntityRadius);
        int zMax = (int) Math.ceil(box.maxZ + maxEntityRadius);
        
        foreachEntities(
            entityClass, getChunkAccessor(world),
            xMin >> 4, xMax >> 4,
            Math.max(0, yMin >> 4), Math.min(15, yMax >> 4),
            zMin >> 4, zMax >> 4,
            consumer
        );
    }
    
    public static ResourceLocation dimensionTypeId(RegistryKey<World> dimType) {
        return dimType.func_240901_a_();
    }
    
    public static <T> String serializeToJson(T object, Codec<T> codec) {
        DataResult<JsonElement> r = codec.encode(object, JsonOps.INSTANCE, new JsonObject());
        Either<JsonElement, DataResult.PartialResult<JsonElement>> either = r.get();
        JsonElement result = either.left().orElse(null);
        if (result != null) {
            return Global.gson.toJson(result);
        }
        
        return either.right().map(DataResult.PartialResult::toString).orElse("");
    }
    
    public static Vector3d getCurrentCameraPos() {
        return Minecraft.getInstance().gameRenderer.getActiveRenderInfo().getProjectedView();
    }
    
    public static class MyDecodeException extends RuntimeException {
        
        public MyDecodeException(String message) {
            super(message);
        }
    }
    
    public static <T, Serialized> T decodeFailHard(
        Codec<T> codec,
        DynamicOps<Serialized> ops,
        Serialized target
    ) {
        return codec.decode(ops, target)
            .getOrThrow(false, s -> {
                throw new MyDecodeException("Cannot decode" + s + target);
            }).getFirst();
    }
    
    public static <Serialized> Serialized getElementFailHard(
        DynamicOps<Serialized> ops,
        Serialized target,
        String key
    ) {
        return ops.get(target, key).getOrThrow(false, s -> {
            throw new MyDecodeException("Cannot find" + key + s + target);
        });
    }
    
    public static <T, Serialized> void encode(
        Codec<T> codec,
        DynamicOps<Serialized> ops,
        Serialized target,
        T object
    ) {
        codec.encode(object, ops, target);
    }
    
    public static <Serialized, T> T decodeElementFailHard(
        DynamicOps<Serialized> ops, Serialized input,
        Codec<T> codec, String key
    ) {
        return decodeFailHard(
            codec, ops,
            getElementFailHard(ops, input, key)
        );
    }
    
    public static void sendMessageToFirstLoggedPlayer(ITextComponent text) {
        Helper.log(text.getUnformattedComponentText());
        ModMain.serverTaskList.addTask(() -> {
            MinecraftServer server = getServer();
            if (server == null) {
                return false;
            }
            
            List<ServerPlayerEntity> playerList = server.getPlayerList().getPlayers();
            if (playerList.isEmpty()) {
                return false;
            }
            
            for (ServerPlayerEntity player : playerList) {
                player.sendStatusMessage(text, false);
            }
            
            return true;
        });
    }
    
    public static Iterable<Entity> getWorldEntityList(World world) {
        if (world.isRemote()) {
            return CHelper.getWorldEntityList(world);
        }
        else {
            if (world instanceof ServerWorld) {
                return ((ServerWorld) world).func_241136_z_();
            }
            else {
                return ((Iterable<Entity>) Collections.emptyList().iterator());
            }
        }
    }
    
    /**
     * It will spawn even if the chunk is not loaded
     * ServerWorld#addEntity(Entity)
     */
    public static void spawnServerEntity(Entity entity) {
        Validate.isTrue(!entity.world.isRemote());
        
        entity.forceSpawn = true;
        
        entity.world.addEntity(entity);
        
        entity.forceSpawn = false;
    }
    
    public static void executeOnServerThread(Runnable runnable) {
        MinecraftServer server = McHelper.getServer();
        
        if (server.isOnExecutionThread()) {
            runnable.run();
        }
        else {
            server.execute(runnable);
        }
    }
    
    public static <T> SimpleRegistry<T> filterAndCopyRegistry(
        SimpleRegistry<T> registry, BiPredicate<RegistryKey<T>, T> predicate
    ) {
        SimpleRegistry<T> newRegistry = new SimpleRegistry<>(
            registry.func_243578_f(),
            registry.func_241875_b()
        );
        
        for (Map.Entry<RegistryKey<T>, T> entry : registry.func_239659_c_()) {
            T object = entry.getValue();
            RegistryKey<T> key = entry.getKey();
            if (predicate.test(key, object)) {
                newRegistry.register(
                    key, object, registry.func_241876_d(object)
                );
            }
        }
        
        return newRegistry;
    }
    
    public static ServerWorld getServerWorld(RegistryKey<World> dim) {
        ServerWorld world = McHelper.getServer().getWorld(dim);
        if (world == null) {
            throw new RuntimeException("Missing dimension " + dim.func_240901_a_());
        }
        return world;
    }
    
}
