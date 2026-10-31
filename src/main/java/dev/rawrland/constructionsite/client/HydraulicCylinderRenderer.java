package dev.rawrland.constructionsite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rawrland.constructionsite.CreateConstructionSite;
import dev.rawrland.constructionsite.content.cylinder.CylinderMountBlockEntity;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a hydraulic cylinder from its barrel end to its rod end (prototype).
 *
 * The two ends may sit on different contraptions, for example the excavator's body
 * and its boom. Each frame the rod end's centre is taken into the world with its
 * contraption's pose, then into this block's own frame with this contraption's pose
 * (Sable Companion; without Create Aeronautics both steps change nothing). The
 * cylinder is then drawn as boxes along that line: barrel from the barrel end,
 * rod from the rod end, overlapping in between.
 *
 * Barrel and rod are each one tube length long (set in the settings window). A cylinder
 * pulled longer than both together, or pushed shorter than one, is shown with a red
 * barrel: the tube would have to be longer or shorter for that movement.
 */
public class HydraulicCylinderRenderer implements BlockEntityRenderer<CylinderMountBlockEntity> {

    // Sizes in blocks (1/16 = one pixel).
    private static final float BARREL_HALF = 3.5F / 16F;
    private static final float COLLAR_HALF = 4.5F / 16F;
    private static final float ROD_HALF = 2.0F / 16F;
    private static final float EYE_HALF = 3.0F / 16F;
    private static final float COLLAR_LENGTH = 2.0F / 16F;
    /** Room left between a fully pushed-in part and the other pin. */
    private static final float CLEARANCE = 0.15F;
    /** How far the end collar sticks out past the barrel's end face (half a pixel). */
    private static final float COLLAR_OVERHANG = 0.5F / 16F;
    /** How far past either end of its reach the cylinder may be before the barrel shows red. */
    public static final double RED_SLACK = 0.2;

    public HydraulicCylinderRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CylinderMountBlockEntity end, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (!end.isLinked() || !end.isBarrelEnd() || end.isRemoved() || end.getLevel() == null) {
            return;
        }
        CylinderMountBlockEntity rodEnd = end.partner();
        if (rodEnd == null) {
            return;
        }

        // Both pins in this block's own frame, measured from its lower corner.
        Vec3 origin = Vec3.atLowerCornerOf(end.getBlockPos());
        Vec3 a = new Vec3(0.5, 0.5, 0.5);
        Vec3 b = toOwnFrame(end, toWorld(rodEnd, partialTick), partialTick).subtract(origin);

        Vec3 line = b.subtract(a);
        double length = line.length();
        if (length < 1.0E-3) {
            return;
        }
        Vec3 dir = line.scale(1.0 / length);

        float barrel = end.barrelLength();
        float rod = end.rodLength();
        // Its reach: one tube length (fully in) to two (fully out). Only red beyond 0.2 blocks
        // past either end (owner, 30 October 2026): a loaded arm gives a little at the end stops.
        boolean overExtended = length > end.longestLength() + RED_SLACK;
        boolean overRetracted = length < end.shortestLength() - RED_SLACK;
        int[] barrelColour = overExtended || overRetracted ? new int[] {255, 110, 110} : new int[] {255, 255, 255};
        int[] plain = {255, 255, 255};

        double barrelDrawn = Math.max(0.2, Math.min(barrel, length - CLEARANCE));
        // Over-stretched: the rod still reaches into the barrel instead of leaving a gap
        // (the red barrel shows it is out of reach).
        double rodDrawn = Math.max(0.2, Math.min(Math.max(rod, length - barrelDrawn + 0.25), length - CLEARANCE));

        Prism prism = new Prism(bufferSource.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)),
            poseStack.last(), dir, packedLight);

        TextureAtlasSprite darkSteel = sprite("dark_steel_fill");
        TextureAtlasSprite brass = sprite("brass_fill");
        TextureAtlasSprite iron = sprite("polished_iron_fill");

        // Barrel with a brass collar at each end.
        prism.draw(a, a.add(dir.scale(barrelDrawn)), BARREL_HALF, darkSteel, barrelColour);
        prism.draw(a.add(dir.scale(0.12)), a.add(dir.scale(0.12 + COLLAR_LENGTH)), COLLAR_HALF, brass, plain);
        // The end collar reaches a hair past the barrel's end, so its end face covers the barrel's
        // instead of lying in the same spot and flickering through it (owner, 30 October 2026).
        prism.draw(a.add(dir.scale(barrelDrawn - COLLAR_LENGTH)), a.add(dir.scale(barrelDrawn + COLLAR_OVERHANG)),
            COLLAR_HALF, brass, plain);
        // Rod, coming out of the barrel, with an eye at the pin.
        prism.draw(b.subtract(dir.scale(rodDrawn)), b, ROD_HALF, iron, plain);
        prism.draw(b.subtract(dir.scale(0.2)), b.add(dir.scale(0.1)), EYE_HALF, darkSteel, plain);
    }

    // ------------------------------------------------------------ frames (Sable)

    /** A block's centre in the world as the player sees it this frame. */
    private static Vec3 toWorld(CylinderMountBlockEntity end, float partialTick) {
        Vec3 centre = Vec3.atCenterOf(end.getBlockPos());
        ClientSubLevelAccess contraption = SableCompanion.INSTANCE.getContainingClient(end);
        return contraption == null ? centre : contraption.renderPose(partialTick).transformPosition(centre);
    }

    /** A world position in the frame this block is stored and drawn in. */
    private static Vec3 toOwnFrame(CylinderMountBlockEntity end, Vec3 world, float partialTick) {
        ClientSubLevelAccess contraption = SableCompanion.INSTANCE.getContainingClient(end);
        return contraption == null ? world : contraption.renderPose(partialTick).transformPositionInverse(world);
    }

    private static TextureAtlasSprite sprite(String name) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(ResourceLocation.fromNamespaceAndPath(CreateConstructionSite.MODID, "block/" + name));
    }

    // ---------------------------------------------------- telling the game what to draw

    /** The cylinder reaches far beyond its block: as far as the longest cylinder. */
    public AABB getRenderBoundingBox(CylinderMountBlockEntity end) {
        return new AABB(end.getBlockPos()).inflate(CylinderMountBlockEntity.MAX_LENGTH + 2.0);
    }

    /** Drawn even when the barrel's own block is out of view, or the cylinder would vanish. */
    @Override
    public boolean shouldRenderOffScreen(CylinderMountBlockEntity end) {
        return end.isBarrelEnd();
    }

    // ------------------------------------------------------------ a square bar

    /**
     * Draws square bars along one direction. The texture repeats every block along
     * the bar, so long bars are not stretched.
     */
    private static final class Prism {
        private final VertexConsumer buffer;
        private final PoseStack.Pose pose;
        private final int light;
        private final Vec3 dir;
        private final Vec3 side;
        private final Vec3 up;

        Prism(VertexConsumer buffer, PoseStack.Pose pose, Vec3 dir, int light) {
            this.buffer = buffer;
            this.pose = pose;
            this.light = light;
            this.dir = dir;
            Vec3 reference = Math.abs(dir.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            this.side = dir.cross(reference).normalize();
            this.up = side.cross(dir).normalize();
        }

        void draw(Vec3 from, Vec3 to, float half, TextureAtlasSprite sprite, int[] colour) {
            double length = to.subtract(from).length();
            if (length < 1.0E-4) {
                return;
            }
            // Side faces, one piece per block of length.
            Vec3[] normals = {side, up, side.scale(-1), up.scale(-1)};
            Vec3[] across = {up, side.scale(-1), up.scale(-1), side};
            float uSpan = sprite.getU1() - sprite.getU0();
            float vSpan = sprite.getV1() - sprite.getV0();
            float u0 = sprite.getU0() + uSpan * (0.5F - half);
            float u1 = sprite.getU0() + uSpan * (0.5F + half);
            for (double start = 0; start < length; start += 1.0) {
                double stop = Math.min(length, start + 1.0);
                Vec3 p0 = from.add(dir.scale(start));
                Vec3 p1 = from.add(dir.scale(stop));
                float v0 = sprite.getV0();
                float v1 = sprite.getV0() + vSpan * (float) (stop - start);
                for (int i = 0; i < 4; i++) {
                    Vec3 out = normals[i].scale(half);
                    Vec3 w = across[i].scale(half);
                    quad(p0.add(out).subtract(w), p0.add(out).add(w), p1.add(out).add(w), p1.add(out).subtract(w),
                        u0, u1, v0, v1, normals[i], colour);
                }
            }
            // End caps.
            float c0 = sprite.getV0() + vSpan * (0.5F - half);
            float c1 = sprite.getV0() + vSpan * (0.5F + half);
            cap(from, half, u0, u1, c0, c1, dir.scale(-1), colour);
            cap(to, half, u0, u1, c0, c1, dir, colour);
        }

        private void cap(Vec3 centre, float half, float u0, float u1, float v0, float v1, Vec3 normal, int[] colour) {
            Vec3 s = side.scale(half);
            Vec3 u = up.scale(half);
            quad(centre.subtract(s).subtract(u), centre.add(s).subtract(u), centre.add(s).add(u), centre.subtract(s).add(u),
                u0, u1, v0, v1, normal, colour);
        }

        private void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float u1, float v0, float v1,
                          Vec3 normal, int[] colour) {
            vertex(a, u0, v0, normal, colour);
            vertex(b, u1, v0, normal, colour);
            vertex(c, u1, v1, normal, colour);
            vertex(d, u0, v1, normal, colour);
        }

        private void vertex(Vec3 p, float u, float v, Vec3 normal, int[] colour) {
            buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(colour[0], colour[1], colour[2], 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }
}
