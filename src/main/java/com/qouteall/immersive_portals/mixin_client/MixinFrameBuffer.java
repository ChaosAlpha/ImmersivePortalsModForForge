package com.qouteall.immersive_portals.mixin_client;

import com.mojang.blaze3d.platform.GLX;
import com.qouteall.immersive_portals.CHelper;
import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.ducks.IEFrameBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Framebuffer.class)
public abstract class MixinFrameBuffer implements IEFrameBuffer {
    
    private boolean isStencilBufferEnabled;
    
    @Shadow
    public int framebufferTextureWidth;
    @Shadow
    public int framebufferTextureHeight;
    
    @Shadow
    public abstract void createBuffers(
        int p_216492_1_,
        int p_216492_2_,
        boolean p_216492_3_
    );
    
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(
        int int_1,
        int int_2,
        boolean boolean_1,
        boolean boolean_2,
        CallbackInfo ci
    ) {
        isStencilBufferEnabled = false;
    }
    
    @Inject(
        method = "createBuffers",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;bindRenderbuffer(II)V"
        ),
        cancellable = true
    )
    private void onInitFrameBuffer(int int_1, int int_2, boolean isMac, CallbackInfo ci) {
        if (isStencilBufferEnabled) {
            Framebuffer this_ = (Framebuffer) (Object) this;
    
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, this_.depthBuffer);
            GL30.glRenderbufferStorage(
                GL30.GL_RENDERBUFFER,
                org.lwjgl.opengl.EXTPackedDepthStencil.GL_DEPTH24_STENCIL8_EXT,
                this_.framebufferTextureWidth,
                this_.framebufferTextureHeight
            );
            GL30.glFramebufferRenderbuffer(
                GL30.GL_FRAMEBUFFER,
                org.lwjgl.opengl.EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT,
                GL30.GL_RENDERBUFFER,
                this_.depthBuffer
            );
            GL30.glFramebufferRenderbuffer(
                GL30.GL_FRAMEBUFFER,
                org.lwjgl.opengl.EXTFramebufferObject.GL_STENCIL_ATTACHMENT_EXT,
                GL30.GL_RENDERBUFFER,
                this_.depthBuffer
            );
    
            this_.checkFramebufferComplete();
            this_.framebufferClear(isMac);
            this_.unbindFramebuffer();
    
            CHelper.checkGlError();
    
            Helper.log("Frame Buffer Reloaded with Stencil Buffer");
    
            ci.cancel();
        }
    }
    
    @Override
    public boolean getIsStencilBufferEnabled() {
        return isStencilBufferEnabled;
    }
    
    @Override
    public void setIsStencilBufferEnabledAndReload(boolean cond) {
        if (isStencilBufferEnabled != cond) {
            isStencilBufferEnabled = cond;
            createBuffers(
                framebufferTextureWidth,
                framebufferTextureHeight,
                Minecraft.IS_RUNNING_ON_MAC
            );
        }
    }
}
