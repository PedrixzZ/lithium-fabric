import it.unimi.dsi.fastutil.objects.ObjectArrayList;

// ...

@Mixin(EntityTrackingSection.class)
public abstract class EntityTrackingSectionMixin<T extends EntityLike> implements EntityMovementTrackerSection, PositionedEntityTrackingSection {
    // ...

    private final ReferenceOpenHashSet<SectionedEntityMovementTracker<?, ?>> sectionVisibilityListeners = new ReferenceOpenHashSet<>(0);
    @SuppressWarnings("unchecked")
    private final ObjectArrayList<SectionedEntityMovementTracker<?, ?>>[] entityMovementListenersByType = new ObjectArrayList[MovementTrackerHelper.NUM_MOVEMENT_NOTIFYING_CLASSES];
    private final long[] lastEntityMovementByType = new long[MovementTrackerHelper.NUM_MOVEMENT_NOTIFYING_CLASSES];

    // ...

    @ModifyVariable(method = "swapStatus(Lnet/minecraft/world/entity/EntityTrackingStatus;)Lnet/minecraft/world/entity/EntityTrackingStatus;", at = @At(value = "HEAD"), argsOnly = true)
    public EntityTrackingStatus swapStatus(final EntityTrackingStatus newStatus) {
        boolean shouldTrack = this.status.shouldTrack();
        boolean newShouldTrack = newStatus.shouldTrack();

        if (shouldTrack != newShouldTrack) {
            if (!newShouldTrack) {
                for (SectionedEntityMovementTracker<?, ?> listener : this.sectionVisibilityListeners) {
                    listener.onSectionLeftRange(this);
                }
            } else {
                for (SectionedEntityMovementTracker<?, ?> listener : this.sectionVisibilityListeners) {
                    listener.onSectionEnteredRange(this);
                }
            }
        }

        return newStatus;
    }

    // ...

    @Override
    public <S, E extends EntityLike> void listenToMovementOnce(SectionedEntityMovementTracker<E, S> listener, int trackedClass) {
        if (this.entityMovementListenersByType[trackedClass] == null) {
            this.entityMovementListenersByType[trackedClass] = new ObjectArrayList<>();
        }
        this.entityMovementListenersByType[trackedClass].add(listener);
    }

    @Override
    public <S, E extends EntityLike> void removeListenToMovementOnce(SectionedEntityMovementTracker<E, S> listener, int trackedClass) {
        if (this.entityMovementListenersByType[trackedClass] != null) {
            this.entityMovementListenersByType[trackedClass].remove(listener);
        }
    }
}
