package com.expeditions.worldgen;

import com.expeditions.registry.ModFeatures;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

/** A recognizable piece of a donor world's architecture anchored to a shelf. */
public final class ShatterwakeLandmark extends Structure {
    public static final MapCodec<ShatterwakeLandmark> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(landmark -> landmark.pool),
            ResourceLocation.CODEC.fieldOf("template").forGetter(landmark -> landmark.template)
    ).apply(instance, ShatterwakeLandmark::new));

    private final Holder<StructureTemplatePool> pool;
    private final ResourceLocation template;

    private ShatterwakeLandmark(StructureSettings settings, Holder<StructureTemplatePool> pool, ResourceLocation template) {
        super(settings);
        this.pool = pool;
        this.template = template;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!(context.chunkGenerator() instanceof ShatterwakeChunkGenerator)
                || context.structureTemplateManager().get(template).isEmpty()) return Optional.empty();

        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();
        var generator = context.chunkGenerator();
        var biome = generator.getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(x), QuartPos.fromBlock(0), QuartPos.fromBlock(z), context.randomState().sampler());
        if (!biomes().contains(biome)) return Optional.empty();

        var rotation = Rotation.getRandom(context.random());
        var element = pool.value().getRandomTemplate(context.random());
        var templates = context.structureTemplateManager();
        var footprint = element.getBoundingBox(templates, BlockPos.ZERO, rotation);
        int originX = x - (footprint.minX() + footprint.maxX()) / 2;
        int originZ = z - (footprint.minZ() + footprint.maxZ()) / 2;
        var heightmap = Heightmap.Types.WORLD_SURFACE_WG;
        int floor = generator.getBaseHeight(x, z, heightmap, context.heightAccessor(), context.randomState());
        if (floor < 48 || floor > 192) return Optional.empty();

        // Allow a cracked edge to hang over the void while keeping most of the fragment on the shelf.
        int supportedCorners = 0;
        for (int dx : new int[]{footprint.minX(), footprint.maxX()}) {
            for (int dz : new int[]{footprint.minZ(), footprint.maxZ()}) {
                int edge = generator.getBaseHeight(originX + dx, originZ + dz, heightmap,
                        context.heightAccessor(), context.randomState());
                if (Math.abs(edge - floor) <= 8) supportedCorners++;
            }
        }
        if (supportedCorners < 3) return Optional.empty();

        var origin = new BlockPos(originX, floor - 1 - footprint.minY(), originZ);
        var piece = new PoolElementStructurePiece(templates, element, origin, element.getGroundLevelDelta(), rotation,
                element.getBoundingBox(templates, origin, rotation), LiquidSettings.IGNORE_WATERLOGGING);
        return Optional.of(new GenerationStub(new BlockPos(x, floor, z), pieces -> pieces.addPiece(piece)));
    }

    @Override
    public void afterPlace(WorldGenLevel level, StructureManager manager, ChunkGenerator generator, RandomSource random,
            BoundingBox writable, ChunkPos chunk, PiecesContainer pieces) {
        var bounds = pieces.calculateBoundingBox();
        for (int x = Math.max(bounds.minX(), writable.minX()); x <= Math.min(bounds.maxX(), writable.maxX()); x++) {
            for (int z = Math.max(bounds.minZ(), writable.minZ()); z <= Math.min(bounds.maxZ(), writable.maxZ()); z++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    // The same band crosses adjacent walls, even where the footprint spans two chunks.
                    int band = Math.floorMod(2 * (x - bounds.minX()) + z - bounds.minZ() + 3 * (y - bounds.minY()), 19);
                    if (band > 8) continue;
                    var surface = new BlockPos(x, y, z);
                    var state = level.getBlockState(surface);
                    if (state.isAir() || state.is(BlockTags.LEAVES) || state.hasBlockEntity()) continue;
                    for (Direction face : Direction.values()) {
                        var film = surface.relative(face);
                        if (writable.isInside(film)) ShatterwakeBloomFeature.coat(level, film, face.getOpposite(), band <= 5);
                    }
                }
            }
        }
    }

    @Override
    public StructureType<?> type() {
        return ModFeatures.SHATTERWAKE_LANDMARK.get();
    }
}
