package github.com.gengyoubo.sscfe.init;

import github.com.gengyoubo.sscfe.Sscfe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Stable events; music audio is supplied by the player's resource pack. */
public final class ModWaterSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Sscfe.MOD_ID);
    public static final RegistryObject<SoundEvent> SPECIAL_MUSIC = register("music.water_purple_special");
    public static final RegistryObject<SoundEvent> CHARGE = register("skill.water_purple_charge");
    public static final RegistryObject<SoundEvent> RELEASE = register("skill.water_purple_release");
    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Sscfe.MOD_ID, name)));
    }
    public static void register(IEventBus bus) { SOUNDS.register(bus); }
    private ModWaterSounds() {}
}
