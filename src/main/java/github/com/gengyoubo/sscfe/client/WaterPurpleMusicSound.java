package github.com.gengyoubo.sscfe.client;

import github.com.gengyoubo.sscfe.sound.WaterPurpleMusicEnvelope;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.function.LongSupplier;

/** One streaming instance stays alive across charge/release and updates its gain every sound tick. */
final class WaterPurpleMusicSound extends AbstractTickableSoundInstance {
    private final WaterPurpleMusicEnvelope envelope;
    private final LongSupplier clock;
    private final float targetVolume;

    WaterPurpleMusicSound(ResourceLocation id, float targetVolume, int fadeInTicks, int holdTicks, int fadeOutTicks,
                          LongSupplier clock) {
        super(SoundEvent.createVariableRangeEvent(id), SoundSource.MUSIC, RandomSource.create());
        this.clock = clock;
        this.targetVolume = targetVolume;
        envelope = new WaterPurpleMusicEnvelope(clock.getAsLong(), fadeInTicks, holdTicks, fadeOutTicks);
        relative = true;
        attenuation = SoundInstance.Attenuation.NONE;
        looping = false;
        volume = targetVolume * envelope.gain(clock.getAsLong());
    }

    // Minecraft normally drops zero-volume sounds; a fade-in must start silently and remain tickable.
    @Override public boolean canStartSilent() { return true; }

    @Override public void tick() {
        volume = targetVolume * envelope.gain(clock.getAsLong());
        if (fadeFinished()) stop();
    }

    void release() { envelope.release(clock.getAsLong()); }
    boolean fadeFinished() { return envelope.finished(clock.getAsLong()); }
    void stopImmediately() { stop(); }
}
