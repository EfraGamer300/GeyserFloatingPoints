package oxy.geyser.fp.session;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.cloudburstmc.math.vector.Vector2d;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.EntityCache;
import org.geysermc.geyser.session.cache.WorldBorder;
import oxy.geyser.fp.GeyserFloatingPoints;
import oxy.geyser.fp.session.cache.ChunkCache;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;

@RequiredArgsConstructor
public class GeyserFPUser {
    public static final String SHOW_COORDINATES_GAME_RULE = "showcoordinates";

    private final GeyserSession session;
    public GeyserSession session() {
        return session;
    }

    @Setter
    private boolean positionShown = GeyserFloatingPoints.config().showPositionByDefault();
    public boolean shouldShowPosition() {
        return this.positionShown;
    }

    private final ChunkCache chunkCache = new ChunkCache(this);
    public ChunkCache chunkCache() {
        return this.chunkCache;
    }

    private Vector3i offset = Vector3i.from(0, 0 ,0);
    public Vector3i offset() {
        return offset;
    }

    public Vector3i prevPosition = Vector3i.ZERO;

    public void offset(Vector3i offset, boolean teleport) {
        final SessionPlayerEntity entity = this.session.getPlayerEntity();

        // We have to set this first regardless of teleport so the chunks so up properly.
        entity.setPosition(entity.position().add(this.offset.toFloat()).sub(offset.toFloat()));

        if (teleport) {
            // A re-offset is not a Java teleport: it only shifts our coordinate frame while
            // the real position stays the same. MoveEntityAbsolutePacket already relocates the
            // Bedrock client; no downstream teleport has to be acknowledged.
            //
            // Registering an UnconfirmedTeleport here makes Geyser discard every player
            // movement packet (BedrockMovePlayer.translate) until Bedrock reports the exact
            // TeleportCache.adjustedPosition. That position is computed from blocks at the
            // spoofed coordinates, so a cache/world-manager mismatch makes it impossible to
            // confirm: the player freezes while vehicles keep moving, since vehicle input is
            // processed before that early return.
            entity.moveAbsolute(
                    entity.position(),
                    entity.getYaw(), entity.getPitch(), entity.isOnGround(), true
            );
        }

        try {
            Field field = WorldBorder.class.getDeclaredField("center");
            field.setAccessible(true);
            final Vector2d center = (Vector2d) field.get(session.getWorldBorder());

            final WorldBorder border = session.getWorldBorder();
            border.setCenter(center.add(this.offset.getX(), this.offset.getZ()).sub(offset.getX(), offset.getZ()));

            border.update();
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        // This hacky mess is due to Geyser relocating stuff....
        try {
            final Field entitiesField = EntityCache.class.getDeclaredField("entities");
            entitiesField.setAccessible(true);
            final Object entities = entitiesField.get(session.getEntityCache());

            final Method valuesMethod = entities.getClass().getDeclaredMethod("values");
            valuesMethod.setAccessible(true);
            final Collection<Entity> values = (Collection<Entity>) valuesMethod.invoke(entities);

            for (Entity other : values) {
                if (other == entity) {
                    continue;
                }
                other.setPosition(other.position().add(this.offset.toFloat()).sub(offset.toFloat()));
                other.despawnEntity();
                other.spawnEntity();
            }
        } catch (Exception ignored) {
        }

        boolean wasHidingCoordinates = !this.offset.equals(Vector3i.ZERO);
        this.offset = offset;
        chunkCache.sendChunksWithOffset();

        boolean shouldHideCoordinates = !this.offset.equals(Vector3i.ZERO);
        if (wasHidingCoordinates != shouldHideCoordinates) {
            session.sendGameRule(SHOW_COORDINATES_GAME_RULE, shouldShowCoordinates());
        }
    }

    private boolean shouldShowCoordinates() {
        return this.offset.equals(Vector3i.ZERO)
                // From https://github.com/GeyserMC/Geyser/blob/fc2681ada4e0b5e344d64927f978ec7ac751fea5/core/src/main/java/org/geysermc/geyser/session/cache/PreferencesCache.java#L78
                && !session.isReducedDebugInfo()
                && session.getGeyser().config().gameplay().showCoordinates()
                && session.getPreferencesCache().isPrefersShowCoordinates();
    }
}
