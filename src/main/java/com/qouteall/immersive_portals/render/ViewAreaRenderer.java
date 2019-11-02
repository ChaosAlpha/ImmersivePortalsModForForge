package com.qouteall.immersive_portals.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.qouteall.immersive_portals.CGlobal;
import com.qouteall.immersive_portals.portal.Mirror;
import com.qouteall.immersive_portals.portal.Portal;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;

import static org.lwjgl.opengl.GL11.GL_CLIP_PLANE0;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;

public class ViewAreaRenderer {
    private static void buildPortalViewAreaTrianglesBuffer(
        Vec3d fogColor, Portal portal, BufferBuilder bufferbuilder,
        Vec3d cameraPos, float partialTicks, float layerWidth
    ) {
        //if layerWidth is small, the teleportation will not be seamless
        
        //counter-clockwise triangles are front-faced in default
        
        bufferbuilder.begin(GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        
        Vec3d posInPlayerCoordinate = portal.getPositionVec().subtract(
            cameraPos
        );
    
        if (!(portal instanceof Mirror)) {
            Vec3d layerOffsest = portal.getNormal().scale(-layerWidth);
        
            Vec3d[] frontFace = Arrays.stream(portal.getFourVerticesRelativeToCenter(0))
                .map(pos -> pos.add(posInPlayerCoordinate))
                .toArray(Vec3d[]::new);
        
            Vec3d[] backFace = Arrays.stream(portal.getFourVerticesRelativeToCenter(0.2))
                .map(pos -> pos.add(posInPlayerCoordinate).add(layerOffsest))
                .toArray(Vec3d[]::new);
        
            //3  2
            //1  0
        
            //we do not render the front side
        
            //back side can only be seen from front
            putIntoQuad(
                bufferbuilder,
                backFace[0],
                backFace[2],
                backFace[3],
                backFace[1],
                fogColor
            );
        
            //right side can only be seen from center
            putIntoQuad(
                bufferbuilder,
                backFace[2],
                backFace[0],
                frontFace[0],
                frontFace[2],
                fogColor
            );
        
            //left side can only be seen from center
            putIntoQuad(
                bufferbuilder,
                backFace[1],
                backFace[3],
                frontFace[3],
                frontFace[1],
                fogColor
            );
        
            //top side can only be seen from center
            putIntoQuad(
                bufferbuilder,
                backFace[3],
                backFace[2],
                frontFace[2],
                frontFace[3],
                fogColor
            );
        
            //bottom side can only be seen from bottom
            putIntoQuad(
                bufferbuilder,
                backFace[0],
                backFace[1],
                frontFace[1],
                frontFace[0],
                fogColor
            );
        }
        else {
            Vec3d layerOffsest = portal.getNormal().scale(-0.01);
            Vec3d[] frontFace = Arrays.stream(portal.getFourVerticesRelativeToCenter(0))
                .map(pos -> pos.add(posInPlayerCoordinate).add(layerOffsest))
                .toArray(Vec3d[]::new);
        
            putIntoQuad(
                bufferbuilder,
                frontFace[0],
                frontFace[1],
                frontFace[3],
                frontFace[2],
                fogColor
            );
        }
    }
    
    static private void putIntoVertex(BufferBuilder bufferBuilder, Vec3d pos, Vec3d fogColor) {
        bufferBuilder
            .pos(pos.x, pos.y, pos.z)
            .color((float) fogColor.x, (float) fogColor.y, (float) fogColor.z, 1.0f)
            .endVertex();
    }
    
    //a d
    //b c
    private static void putIntoQuad(
        BufferBuilder bufferBuilder,
        Vec3d a,
        Vec3d b,
        Vec3d c,
        Vec3d d,
        Vec3d fogColor
    ) {
        //counter-clockwise triangles are front-faced in default
    
        putIntoVertex(bufferBuilder, b, fogColor);
        putIntoVertex(bufferBuilder, c, fogColor);
        putIntoVertex(bufferBuilder, d, fogColor);
    
        putIntoVertex(bufferBuilder, d, fogColor);
        putIntoVertex(bufferBuilder, a, fogColor);
        putIntoVertex(bufferBuilder, b, fogColor);
    
    }
    
    public static void drawPortalViewTriangle(Portal portal) {
        DimensionRenderHelper helper =
            CGlobal.clientWorldLoader.getDimensionRenderHelper(portal.dimensionTo);
        
        Vec3d fogColor = helper.getFogColor();
        
        GlStateManager.disableCull();
        GlStateManager.disableAlphaTest();
        GlStateManager.disableTexture();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.disableBlend();
        GlStateManager.disableLighting();
        
        GL11.glDisable(GL_CLIP_PLANE0);

//        if (OFHelper.getIsUsingShader()) {
//            fogColor = Vec3d.ZERO;
//        }
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        buildPortalViewAreaTrianglesBuffer(
            fogColor,
            portal,
            bufferbuilder,
            PortalRenderer.mc.gameRenderer.getActiveRenderInfo().getProjectedView(),
            MyRenderHelper.partialTicks,
            portal instanceof Mirror ? -0.1F : 0.4F
        );
        
        tessellator.draw();
        
        GlStateManager.enableCull();
        GlStateManager.enableAlphaTest();
        GlStateManager.enableTexture();
        GlStateManager.enableLighting();
    }
}