package net.dumbcode.projectnublar.server.block.entity;

import lombok.Getter;
import lombok.Setter;
import net.dumbcode.dumblibrary.server.entity.GrowthStage;
import net.dumbcode.projectnublar.server.ProjectNublar;
import net.dumbcode.projectnublar.server.block.entity.skeletalbuilder.SkeletalHistory;
import net.dumbcode.projectnublar.server.block.entity.skeletalbuilder.SkeletalProperties;
import net.dumbcode.projectnublar.server.dinosaur.Dinosaur;
import net.dumbcode.projectnublar.server.entity.DinosaurEntity;
import net.dumbcode.projectnublar.server.network.S7FullPoseChange;
import net.ilexiconn.llibrary.client.model.tabula.TabulaModel;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.items.ItemStackHandler;

import javax.vecmath.Vector3f;
import java.util.HashMap;
import java.util.Map;

import static net.dumbcode.projectnublar.server.ProjectNublar.DINOSAUR_REGISTRY;

@Getter
@Setter
public class SkeletalBuilderBlockEntity extends SimpleBlockEntity {
    private final ItemStackHandler boneHandler = new ItemStackHandler();
    private final SkeletalProperties skeletalProperties = new SkeletalProperties();
    private Dinosaur dinosaur = Dinosaur.MISSING;
    private TabulaModel model;
    private Map<String, Vector3f> poseData = new HashMap<>();

    @Getter(lazy = true)
    private final DinosaurEntity dinosaurEntity = createEntity();

    private final SkeletalHistory history = new SkeletalHistory(this);

    // Not saved to NBT, player-specific only to help with posing

    private float cameraPitch;
    private float cameraYaw = 90f;
    private double cameraZoom = 1.0;

    public SkeletalBuilderBlockEntity() {
        this.reassureSize();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setString("Dinosaur", this.dinosaur.getRegName().toString());
        nbt.setTag("Inventory", this.boneHandler.serializeNBT());
        nbt.setTag("History", history.writeToNBT(new NBTTagCompound()));

        // save pose data
        nbt.setTag("Pose", writePoseToNBT(poseData));
        nbt.setInteger("ModelIndex", getDinosaurEntity().modelIndex);
        nbt.setTag("SkeletalProperties", this.skeletalProperties.serialize(new NBTTagCompound()));
        return super.writeToNBT(nbt);
    }

    public NBTTagCompound writePoseToNBT(Map<String, Vector3f> poseData) {
        NBTTagCompound pose = new NBTTagCompound();
        for(String partName : poseData.keySet()) {
            Vector3f eulerAngles = poseData.get(partName);
            NBTTagCompound partNBT = new NBTTagCompound();
            partNBT.setFloat("RotationX", eulerAngles.x);
            partNBT.setFloat("RotationY", eulerAngles.y);
            partNBT.setFloat("RotationZ", eulerAngles.z);
            pose.setTag(partName, partNBT);
        }
        return pose;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        setDinosaur(DINOSAUR_REGISTRY.getValue(new ResourceLocation(nbt.getString("Dinosaur"))));
        this.boneHandler.deserializeNBT(nbt.getCompoundTag("Inventory"));
        // load pose data
        NBTTagCompound pose = nbt.getCompoundTag("Pose");
        poseData.clear();
        poseData.putAll(readPoseFromNBT(pose));
        this.reassureSize();
        this.history.readFromNBT(nbt.getCompoundTag("History"));
        getDinosaurEntity().modelIndex = nbt.getInteger("ModelIndex");
        this.skeletalProperties.deserialize(nbt.getCompoundTag("SkeletalProperties"));
        super.readFromNBT(nbt);
    }

    public Map<String, Vector3f> readPoseFromNBT(NBTTagCompound pose) {
        Map<String, Vector3f> result = new HashMap<>();
        for(String partName : pose.getKeySet()) {
            NBTTagCompound part = pose.getCompoundTag(partName);
            Vector3f eulerAngles = new Vector3f(part.getFloat("RotationX"), part.getFloat("RotationY"), part.getFloat("RotationZ"));
            result.put(partName, eulerAngles);
        }
        return result;
    }

    private void reassureSize() {
        int size = this.dinosaur.getSkeletalInformation().getBoneListed().size();
        if(size != this.boneHandler.getSlots()) {
            this.boneHandler.setSize(size); //TODO: Maybe make a diffrent method that keeps the items?
        }
    }

    public Dinosaur getDinosaur() {
        return dinosaur;
    }

    public void setDinosaur(Dinosaur dinosaur) {
        this.dinosaur = dinosaur;
        this.model = dinosaur.getModelContainer().getModelMap().get(GrowthStage.ADULT);
        resetPoseDataToDefaultPose();
        this.reassureSize();
    }

    public TabulaModel getModel() {
        return model;
    }

    public ItemStackHandler getBoneHandler() {
        return boneHandler;
    }

    private DinosaurEntity createEntity() {
        DinosaurEntity entity = this.dinosaur.createEntity(this.world);
        //Initilize stuff
        return entity;
    }

    public Map<String, Vector3f> getPoseData() {
        return poseData;
    }

    public void updateAngles(String affectedPart, Vector3f anglesToApply) {
        if(!poseData.containsKey(affectedPart)) {
            poseData.put(affectedPart, new Vector3f());
        }
        Vector3f angles = poseData.get(affectedPart);
        angles.set(anglesToApply);
        markDirty();
    }

    public void resetPose() {
        history.recordPoseReset();
        model.resetToDefaultPose();
        resetPoseDataToDefaultPose();
        ProjectNublar.NETWORK.sendToAll(new S7FullPoseChange(this, getPoseData()));
    }

    public void resetPoseDataToDefaultPose() {
        poseData.clear();
        model.resetToDefaultPose();
        for(ModelRenderer box : model.boxList) {
            Vector3f rotations = new Vector3f(box.rotateAngleX, box.rotateAngleY, box.rotateAngleZ);
            poseData.put(box.boxName, rotations);
        }
        markDirty();
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB; //TODO: get size of dinosaur, and fit this to it
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return Double.MAX_VALUE;
    }

    public void setCameraAngles(float cameraYaw, float cameraPitch) {
        this.cameraYaw = cameraYaw;
        this.cameraPitch = cameraPitch;
    }
}