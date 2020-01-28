package com.qouteall.immersive_portals.alternate_dimension;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.IWorld;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.EndChunkGenerator;
import net.minecraft.world.gen.EndGenerationSettings;

public class MyFloatingIslandChunkGenerator extends EndChunkGenerator {
    private final BlockState AIR;
    
    private double wxx;
    private double wzx;
    private double wxz;
    private double wzz;
    
    public MyFloatingIslandChunkGenerator(
        IWorld iWorld,
        BiomeProvider biomeSource,
        EndGenerationSettings floatingIslandsChunkGeneratorConfig
    ) {
        super(iWorld, biomeSource, floatingIslandsChunkGeneratorConfig);
        AIR = Blocks.AIR.getDefaultState();
    }
    
    protected int transformX(int x, int z) {
        return x / 2;
    }
    
    protected int transformZ(int x, int z) {
        return z * 10;
    }
    
    @Override
    protected void func_222546_a(
        double[] buffer,
        int x,
        int z,
        double d,
        double e,
        double f,
        double g,
        int i,
        int j
    ) {
        super.func_222546_a(buffer,
            transformX(x, z), transformZ(x, z),
            d, e, f, g, i, j
        );
        
    }
    
}