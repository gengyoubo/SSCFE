package github.com.gengyoubo.sscfe;

import github.com.gengyoubo.sscfe.client.SscfeClientConfig;
import github.com.gengyoubo.sscfe.init.ModForms;
import github.com.gengyoubo.sscfe.init.ModMoonContent;
import github.com.gengyoubo.sscfe.init.ModWaterContent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Sscfe.MOD_ID)
public final class Sscfe {
    public static final String MOD_ID = "sscfe";

    @SuppressWarnings("removal")
    public Sscfe() {
        SscfeGameRules.initialize();
        ModWaterContent.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModMoonContent.register(FMLJavaModLoadingContext.get().getModEventBus());
        registerForms();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SscfeClientConfig.SPEC, "sscfe-client.toml");
    }

    private static void registerForms() {
        ModForms.init();
    }
}
