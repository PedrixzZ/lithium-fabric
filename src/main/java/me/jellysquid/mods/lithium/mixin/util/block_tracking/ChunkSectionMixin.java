package me.jellysquid.mods.lithium.mixin.util.block_tracking;

import me.jellysquid.mods.lithium.common.block.*;
import me.jellysquid.mods.lithium.common.entity.block_tracking.ChunkSectionChangeCallback;
import me.jellysquid.mods.lithium.common.entity.block_tracking.SectionedBlockChangeTracker;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Keep track of how many blocks that meet certain criteria are in this chunk section.
 * E.g. if no over-sized blocks are there, collision code can skip a few blocks.
 *
 * @author 2No2Name
 */
@Mixin(ChunkSection.class)
public abstract class ChunkSectionMixin implements BlockCountingSection, BlockListeningSection {

    @Shadow
    @Final
    private PalettedContainer<BlockState> blockStateContainer;
    @Unique
    private short[] countsByFlag = null;
    private ChunkSectionChangeCallback changeListener;
    private short listeningMask;

    @Override
    public boolean mayContainAny(TrackedBlockStatePredicate trackedBlockStatePredicate) {
        if (this.countsByFlag == null) {
            fastInitClientCounts();
        }
        return this.countsByFlag[trackedBlockStatePredicate.getIndex()] != (short) 0;
    }

    private void fastInitClientCounts() {
        this.countsByFlag = new short[BlockStateFlags.NUM_FLAGS];
        for (TrackedBlockStatePredicate trackedBlockStatePredicate : BlockStateFlags.FLAGS) {
            if (this.blockStateContainer.hasAny(trackedBlockStatePredicate)) {
                //We haven't counted, so we just set the count so high that it never incorrectly reaches 0.
                //For most situations, this overestimation does not hurt client performance compared to correct counting,
                this.countsByFlag[trackedBlockStatePredicate.getIndex()] = 16 * 16 * 16;
            }
        }
    }

    @Redirect(
            method = "calculateCounts()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/PalettedContainer;count(Lnet/minecraft/world/chunk/PalettedContainer$Counter;)V"
            )
    )
    private void initFlagCounters(PalettedContainer<BlockState> palettedContainer, PalettedContainer.Counter<BlockState> consumer) {
        palettedContainer.count((state, count) -> {
            consumer.accept(state, count);
            addToFlagCount(this.countsByFlag, state, count);
        });
    }

    private static void addToFlagCount(short[] countsByFlag, BlockState state, int change) {
        int flags = ((BlockStateFlagHolder) state).getAllFlags();
        int i;
        while ((i = Integer.numberOfTrailingZeros(flags)) < 32) {
            //either count up by one (prevFlag not set) or down by one (prevFlag set)
            countsByFlag[i] += change;
            flags &= ~(1 << i);
        }
    }

    @Inject(method = "calculateCounts()V", at = @At("HEAD"))
    private void createFlagCounters(CallbackInfo ci) {
        this.countsByFlag = new short[BlockStateFlags.NUM_FLAGS];
    }

    @Inject(
            method = "readDataPacket",
            at = @At(value = "HEAD")
    )
    private void resetData(PacketByteBuf buf, CallbackInfo ci) {
        this.countsByFlag = null;
    }

    @Inject(
            method = "setBlockState(IIILnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/BlockState;getFluidState()Lnet/minecraft/fluid/FluidState;",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void updateFlagCounters(int x, int y, int z, BlockState newState, boolean lock, CallbackInfoReturnable<BlockState> cir, BlockState oldState) {
        short[] countsByFlag = this.countsByFlag;
        if (countsByFlag == null) {
            return;
        }
        int prevFlags = ((BlockStateFlagHolder) oldState).getAllFlags();
        int flags = ((BlockStateFlagHolder) newState).getAllFlags();

        //no need to update indices that did not change, XOR returns the differences
        int flagsXOR = prevFlags ^ flags; //We could do this to avoid iterating Listening Flags: & -(1 << BlockStateFlags.NUM_LISTENING_FLAGS);
        //we need to iterate over indices that changed or are in the listeningMask
        int iterateFlags = flagsXOR | (this.listeningMask & (prevFlags | flags));
        int flagIndex;

        while ((flagIndex = Integer.numberOfTrailingZeros(iterateFlags)) < 32) {
            int flagBit = 1 << flagIndex;
            //either count up by one (prevFlag not set) or down by one (prevFlag set)
            if ((flagsXOR & flagBit) != 0) {
                countsByFlag[flagIndex] += 1 - (((prevFlags >>> flagIndex) & 1) << 1);
            }
            if ((this.listeningMask & flagBit) != 0) {
                this.listeningMask = this.changeListener.onBlockChange(flagIndex, this);
            }
            iterateFlags &= ~flagBit;
        }
    }

    @Override
    public void addToCallback(ListeningBlockStatePredicate blockGroup, SectionedBlockChangeTracker tracker) {
        if (this.changeListener == null) {
            this.changeListener = new ChunkSectionChangeCallback();
        }

        this.listeningMask = this.changeListener.addTracker(tracker, blockGroup);
    }

    @Override
    public void removeFromCallback(ListeningBlockStatePredicate blockGroup, SectionedBlockChangeTracker tracker) {
        if (this.changeListener != null) {
            this.listeningMask = this.changeListener.removeTracker(tracker, blockGroup);
        }
    }

    private boolean isListening(ListeningBlockStatePredicate blockGroup) {
        return (this.listeningMask & (1 << blockGroup.getIndex())) != 0;
    }

    public void invalidateSection() {
        //TODO on section unload, unregister all kinds of stuff
    }
}
