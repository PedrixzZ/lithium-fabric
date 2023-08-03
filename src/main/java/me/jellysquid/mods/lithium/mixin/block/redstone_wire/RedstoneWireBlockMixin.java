package me.jellysquid.mods.lithium.mixin.block.redstone_wire;

import me.jellysquid.mods.lithium.common.util.DirectionConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

@Mixin(RedstoneWireBlock.class)
public class RedstoneWireBlockMixin extends Block {
    
    private static final int MIN = 0;
    private static final int MAX = 15;
    private static final int MAX_WIRE = MAX - 1;
    
    public RedstoneWireBlockMixin(Settings settings) {
        super(settings);
    }
    
    @Inject(
            method = "getReceivedRedstonePower",
            cancellable = true,
            at = @At("HEAD")
    )
    private void getReceivedPowerFaster(World world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(this.getReceivedPower(world, pos));
    }
    
    private int getReceivedPower(World world, BlockPos pos) {
        WorldChunk chunk = world.getWorldChunk(pos);
        int power = MIN;
        
        for (Direction dir : DirectionConstants.VERTICAL) {
            BlockPos side = pos.offset(dir);
            BlockState neighbor = chunk.getBlockState(side);
            
            if (!neighbor.isAir() && !neighbor.isOf(this)) {
                power = Math.max(power, this.getPowerFromSide(world, side, neighbor, dir));
                
                if (power >= MAX) {
                    return MAX;
                }
            }
        }
        
        BlockPos up = pos.up();
        boolean checkWiresAbove = !chunk.getBlockState(up).isSolidBlock(world, up);
        
        for (Direction dir : DirectionConstants.HORIZONTAL) {
            power = Math.max(power, this.getPowerFromSide(world, pos.offset(dir), chunk.getBlockState(pos.offset(dir)), dir, checkWiresAbove));
            
            if (power >= MAX) {
                return MAX;
            }
        }
        
        return power;
    }
    
    private int getPowerFromSide(World world, BlockPos pos, BlockState state, Direction toDir) {
        if (state.isOf(this)) {
            return state.get(Properties.POWER) - 1;
        }
        
        int power = state.getWeakRedstonePower(world, pos, toDir);
        
        if (power >= MAX) {
            return MAX;
        }
        
        if (state.isSolidBlock(world, pos)) {
            power = Math.max(power, this.getStrongPowerTo(world, pos, toDir.getOpposite()));
            
            if (power >= MAX) {
                return MAX;
            }
        } else if (power < MAX_WIRE) {
            BlockPos down = pos.down();
            BlockState belowState = world.getBlockState(down);
            
            if (belowState.isOf(this)) {
                power = Math.max(power, belowState.get(Properties.POWER) - 1);
            }
        }
        
        return power;
    }
    
    private int getStrongPowerTo(World world, BlockPos pos, Direction ignore) {
        int power = MIN;
        
        for (Direction dir : DirectionConstants.ALL) {
            if (dir != ignore) {
                BlockPos side = pos.offset(dir);
                BlockState neighbor = world.getBlockState(side);
                
                if (!neighbor.isAir() && !neighbor.isOf(this)) {
                    power = Math.max(power, neighbor.getStrongRedstonePower(world, side, dir));
                    
                    if (power >= MAX) {
                        return MAX;
                    }
                }
            }
        }
        
        return power;
    }
}
