package me.jellysquid.mods.lithium.mixin.gen.cached_generator_settings;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseChunkGenerator.class)
public class NoiseChunkGeneratorMixin {

    @Shadow
    @Final
    private ChunkGeneratorSettings settings;
    private int cachedSeaLevel;

    /**
     * Use cached sea level instead of retrieving from the registry every time.
     * This method is called for every block in the chunk so this will save a lot of registry lookups.
     * This has been further optimized to avoid using the RegistryEntry and directly access the ChunkGeneratorSettings.
     * As a result, there's no need to cache the sea level separately in this mixin.
     * The mixin now uses the ChunkGeneratorSettings' sea level directly in getSeaLevel() method.
     */
    @Overwrite
    public int getSeaLevel() {
        return this.settings.seaLevel();
    }

    /**
     * Remove the cachedSeaLevel and access the seaLevel directly from ChunkGeneratorSettings.
     * This avoids potential issues with caching seaLevel and reduces redundant code.
     */
    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/base/Suppliers;memoize(Lcom/google/common/base/Supplier;)Lcom/google/common/base/Supplier;",
                    remap = false,
                    shift = At.Shift.BEFORE
            )
    )
    private void hookConstructor(BiomeSource biomeSource, RegistryEntry<ChunkGeneratorSettings> settings, CallbackInfo ci) {
        // Remove cachedSeaLevel and access seaLevel directly from ChunkGeneratorSettings.
    }
}
