package net.mehvahdjukaar.supplementaries.common.block.blocks;

import net.mehvahdjukaar.moonlight.api.block.IRotatable;
import net.mehvahdjukaar.supplementaries.reg.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class CrankBlock extends HorizontalDirectionalBlock implements IRotatable {
    protected static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public CrankBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWER, 0));
    }
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWER);
    }

    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    //make redstone connect, should happen only on the direction it outputs
     public boolean isSignalSource(BlockState state) {
        return true;
    }

    //can't be on air
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportRigidBlock(level, pos.below());
    }

    //update and break if necessary
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!state.canSurvive(level, pos)) {
            BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            dropResources(state, level, pos, blockEntity);
            level.removeBlock(pos, false);
        }
    }

    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        } else {
            boolean ccw = player.isShiftKeyDown();
            this.activate(state, level, pos, ccw);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    protected void activate(BlockState state, Level level, BlockPos pos, boolean ccw) {
        state = state.setValue(POWER, (16 + state.getValue(POWER) + (ccw ? -1 : 1)) % 16);
        level.setBlock(pos, state, 3);
        this.updateNeighborsInFront(level, pos, state);
        //add smoke particle and sound when going back to 0
        if (state.getValue(POWER) == 0) {
            makeParticle (state, pos, level, ParticleTypes.SMOKE);
            level.playSound(null, pos,
                    ModSounds.CRANK.get(), SoundSource.BLOCKS, 0.5F, 0.5f);
        }
        else {
            level.playSound(null, pos,
                    ModSounds.CRANK.get(), SoundSource.BLOCKS, 0.5F, 0.5f + state.getValue(POWER) * 0.05f);
        }
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState)this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getValue(POWER) != 0) {
            this.updateNeighborsInFront(level, pos, state);
        }
    }

    //power
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(FACING) == direction.getOpposite() ? state.getValue(POWER) : 0;
    }

    //strong power
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(FACING) == direction.getOpposite() ? state.getValue(POWER) : 0;
    }

    protected void updateNeighborsInFront(Level level, BlockPos pos, BlockState state) {
        Direction direction = (Direction)state.getValue(FACING).getOpposite();
        BlockPos blockPos = pos.relative(direction.getOpposite());
        level.neighborChanged(blockPos, this, pos);
        level.updateNeighborsAtExceptFromFacing(blockPos, this, direction);
    }


    //set particle position in the center of the block and add offsets according to direction
    private static void makeParticle (BlockState state, BlockPos pos, Level level, ParticleOptions pParticleData){
        Direction direction = state.getValue(FACING);
        double posX = pos.getX() + 0.5;
        double posY = pos.getY() + 0.2;
        double posZ = pos.getZ() + 0.5;
        double xOffset = 0.3 * direction.getStepX();
        double zOffset = 0.3 * direction.getStepZ();
        level.addParticle(pParticleData,
                posX + xOffset, posY, posZ + zOffset,0, 0, 0);
    }

    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWER) == 0) {
            return;
        }
        //rate of particles created grows with power
        if (random.nextFloat() < ((float)state.getValue(POWER)/16.0 *0.8 +0.2)) {
            makeParticle (state, pos, level, DustParticleOptions.REDSTONE);
        }
    }

    @Override
    public Optional<BlockState> getRotatedState(BlockState blockState, LevelAccessor levelAccessor, BlockPos blockPos, Rotation rotation, Direction direction, @Nullable Vec3 vec3) {
        //System.out.println("getRotatedState: " + "BlockState: " + blockState + "LevelAccessor: " + levelAccessor + "BlockPos: " + blockPos + "Rotation: " + rotation + "Direction: " + direction);
        //getRotatedState: BlockState: Block{supplementaries:crank}[facing=north,power=8]LevelAccessor: ServerLevel[test]BlockPos: BlockPos{x=-45, y=-59, z=15}Rotation: CLOCKWISE_90Direction: up
        //getRotatedState: BlockState: Block{supplementaries:crank}[facing=north,power=8]LevelAccessor: ServerLevel[test]BlockPos: BlockPos{x=-45, y=-59, z=15}Rotation: COUNTERCLOCKWISE_90Direction: up
        boolean cww = rotation == Rotation.CLOCKWISE_90;
        activate(blockState, (Level) levelAccessor, blockPos, cww);
        return Optional.empty();
    }

    @Override
    public Optional<Direction> rotateOverAxis(BlockState state, LevelAccessor world, BlockPos pos, Rotation rotation, Direction axis, @Nullable Vec3 hit) {
        /*
        System.out.println("rotateOverAxis: " + "BlockState: " + state + "LevelAccessor: " + world + "BlockPos: " + pos + "Rotation: " + rotation + "Direction: " + axis);
        boolean cww = rotation == Rotation.CLOCKWISE_90;
        activate(state, (Level) world, pos, cww);
         */
        return IRotatable.super.rotateOverAxis(state, world, pos, rotation, axis, hit);
    }


    @Override
    public void onRotated(BlockState newState, BlockState oldState, LevelAccessor world, BlockPos pos, Rotation rotation, Direction axis, @Nullable Vec3 hit) {
        //System.out.println("onRotated: " + "newState: " + newState + "oldState: " + oldState + "LevelAccessor: " + world + "BlockPos: " + pos + "Rotation: " + rotation + "Direction: " + axis);
        IRotatable.super.onRotated(newState, oldState, world, pos, rotation, axis, hit);
    }

}