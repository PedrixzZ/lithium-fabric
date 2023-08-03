package me.jellysquid.mods.lithium.mixin.entity.collisions.movement;

import me.jellysquid.mods.lithium.common.entity.LithiumEntityCollisions;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.List;

@Mixin(Entity.class)
public class EntityMixin {
    @Overwrite
    public static Vec3d adjustMovementForCollisions(@Nullable Entity entity, Vec3d movement, Box entityBoundingBox, World world, List<VoxelShape> collisions) {
        double velX = movement.x;
        double velY = movement.y;
        double velZ = movement.z;
        boolean isVerticalOnly = velX == 0 && velZ == 0;
        Box movementSpace;
        if (isVerticalOnly) {
            if (velY < 0) {
                //Check block directly below center of entity first
                VoxelShape voxelShape = LithiumEntityCollisions.getCollisionShapeBelowEntity(world, entity, entityBoundingBox);
                if (voxelShape != null && voxelShape.calculateMaxDistance(Direction.Axis.Y, entityBoundingBox, velY) == 0) {
                    return Vec3d.ZERO;
                }
                //Reduced collision volume optimization for entities that are just standing around
                movementSpace = new Box(entityBoundingBox.minX, entityBoundingBox.minY + velY, entityBoundingBox.minZ, entityBoundingBox.maxX, entityBoundingBox.minY, entityBoundingBox.maxZ);
            } else {
                movementSpace = new Box(entityBoundingBox.minX, entityBoundingBox.maxY, entityBoundingBox.minZ, entityBoundingBox.maxX, entityBoundingBox.maxY + velY, entityBoundingBox.maxZ);
            }
        } else {
            movementSpace = entityBoundingBox.stretch(movement);
        }

        List<VoxelShape> blockCollisions = LithiumEntityCollisions.getBlockCollisions(world, entity, movementSpace);
        velY = VoxelShapes.calculateMaxOffset(Direction.Axis.Y, entityBoundingBox, blockCollisions, velY);

        if (!isVerticalOnly) {
            boolean velXSmallerVelZ = Math.abs(velX) < Math.abs(velZ);
            if (velXSmallerVelZ) {
                velZ = VoxelShapes.calculateMaxOffset(Direction.Axis.Z, entityBoundingBox, blockCollisions, velZ);
            } else {
                velX = VoxelShapes.calculateMaxOffset(Direction.Axis.X, entityBoundingBox, blockCollisions, velX);
            }
            if (!velXSmallerVelZ && velZ != 0.0) {
                velZ = VoxelShapes.calculateMaxOffset(Direction.Axis.Z, entityBoundingBox, blockCollisions, velZ);
            }
        }

        return new Vec3d(velX, velY, velZ);
    }
                                        }
        
