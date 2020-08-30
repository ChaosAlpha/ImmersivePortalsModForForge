package com.qouteall.imm_ptl_peripheral.mixin.client.altius_world;

import com.qouteall.imm_ptl_peripheral.altius_world.AltiusInfo;
import com.qouteall.imm_ptl_peripheral.altius_world.AltiusScreen;
import com.qouteall.imm_ptl_peripheral.ducks.IELevelProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.CreateWorldScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.WorldOptionsScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.datafix.codec.DatapackCodec;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.settings.DimensionGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class MixinCreateWorldScreen extends Screen {
    @Shadow
    public abstract void func_231164_f_();
    
    private Button altiusButton;
    private AltiusScreen altiusScreen;
    
    protected MixinCreateWorldScreen(ITextComponent title) {
        super(title);
        throw new RuntimeException();
    }
    
    @Inject(
        method = "<init>(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/util/datafix/codec/DatapackCodec;Lnet/minecraft/client/gui/screen/WorldOptionsScreen;)V",
        at = @At("RETURN")
    )
    private void onConstructEnded(
        Screen screen, DatapackCodec dataPackSettings, WorldOptionsScreen moreOptionsDialog,
        CallbackInfo ci
    ) {
        altiusScreen = new AltiusScreen((CreateWorldScreen) (Object) this);
    }
    
    @Inject(
        method = "Lnet/minecraft/client/gui/screen/CreateWorldScreen;func_231160_c_()V",
        at = @At("HEAD")
    )
    private void onInitEnded(CallbackInfo ci) {
        
        altiusButton = (Button) this.func_230480_a_(new Button(
            field_230708_k_ / 2 + 5, 151, 150, 20,
            new TranslationTextComponent("imm_ptl.altius_screen_button"),
            (buttonWidget) -> {
                openAltiusScreen();
            }
        ));
        altiusButton.field_230694_p_ = false;
        
    }
    
    @Inject(
        method = "Lnet/minecraft/client/gui/screen/CreateWorldScreen;showMoreWorldOptions(Z)V",
        at = @At("RETURN")
    )
    private void onMoreOptionsOpen(boolean moreOptionsOpen, CallbackInfo ci) {
        if (moreOptionsOpen) {
            altiusButton.field_230694_p_ = true;
        }
        else {
            altiusButton.field_230694_p_ = false;
        }
    }
    
    @Redirect(
        method = "Lnet/minecraft/client/gui/screen/CreateWorldScreen;createWorld()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;func_238192_a_(Ljava/lang/String;Lnet/minecraft/world/WorldSettings;Lnet/minecraft/util/registry/DynamicRegistries$Impl;Lnet/minecraft/world/gen/settings/DimensionGeneratorSettings;)V"
        )
    )
    private void redirectOnCreateLevel(
        Minecraft client, String worldName, WorldSettings levelInfo,
        DynamicRegistries.Impl registryTracker, DimensionGeneratorSettings generatorOptions
    ) {
        AltiusInfo info = altiusScreen.getAltiusInfo();
        ((IELevelProperties) (Object) levelInfo).setAltiusInfo(info);
    
        client.func_238192_a_(worldName, levelInfo, registryTracker, generatorOptions);
    }
    
    private void openAltiusScreen() {
        Minecraft.getInstance().displayGuiScreen(altiusScreen);
    }
}