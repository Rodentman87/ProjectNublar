package net.dumbcode.projectnublar.server.recipes;

import com.google.common.collect.Maps;
import net.dumbcode.projectnublar.server.ProjectNublar;
import net.dumbcode.projectnublar.server.block.entity.MachineModuleBlockEntity;
import net.dumbcode.projectnublar.server.block.entity.MachineModuleItemStackHandler;
import net.dumbcode.projectnublar.server.block.entity.SequencingSynthesizerBlockEntity;
import net.dumbcode.projectnublar.server.item.ItemHandler;
import net.dumbcode.projectnublar.server.item.MachineModuleType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;

import java.util.Map;

public enum SequencingSynthesizerRecipe implements MachineRecipe<SequencingSynthesizerBlockEntity>{
    INSTANCE;

    private final ResourceLocation registryName = new ResourceLocation(ProjectNublar.MODID, "dna_creation");

    @Override
    public boolean accepts(SequencingSynthesizerBlockEntity blockEntity, MachineModuleBlockEntity.MachineProcess process) {
        MachineModuleItemStackHandler handler = blockEntity.getHandler();
        ItemStack testTube = handler.getStackInSlot(process.getInputSlot(0));

        double storageSize = SequencingSynthesizerBlockEntity.DEFAULT_STORAGE;

        if(blockEntity.getTank().getFluidAmount() >= Fluid.BUCKET_VOLUME / 2 &&
                blockEntity.getPlantAmount() >= storageSize / 2 &&
                blockEntity.getBoneAmount() >= storageSize / 2 &&
                blockEntity.getSugarAmount() >= storageSize / 2 &&
                testTube.getItem() == ItemHandler.EMPTY_TEST_TUBE) {
            NBTTagCompound nbt = handler.getStackInSlot(0).getOrCreateSubCompound(ProjectNublar.MODID).getCompoundTag("drive_information");

            Map<String, Double> amountMap = Maps.newHashMap();

            amountMap.put(blockEntity.getSelectKey(1), blockEntity.getSelectAmount(1));
            amountMap.put(blockEntity.getSelectKey(2), blockEntity.getSelectAmount(2));
            amountMap.put(blockEntity.getSelectKey(3), blockEntity.getSelectAmount(3));

            for (Map.Entry<String, Double> entry : amountMap.entrySet()) {
                double amountIn = nbt.getCompoundTag(entry.getKey()).getInteger("amount") / 100D;
                if(amountIn < entry.getValue()) {
                    return false;
                }
            }
            return handler.insertOutputItem(process.getOutputSlot(0), this.createStack(blockEntity, true), true).isEmpty();

        }

        return false;
    }

    @Override
    public int getRecipeTime(SequencingSynthesizerBlockEntity blockEntity, MachineModuleBlockEntity.MachineProcess process) {
        return 12000 - 3600*blockEntity.getTier(MachineModuleType.COMPUTER_CHIP);
    }

    @Override
    public void onRecipeFinished(SequencingSynthesizerBlockEntity blockEntity, MachineModuleBlockEntity.MachineProcess process) {
        MachineModuleItemStackHandler handler = blockEntity.getHandler();
        handler.getStackInSlot(process.getInputSlot(0)).shrink(1);
        handler.insertOutputItem(process.getOutputSlot(0), this.createStack(blockEntity, false), false);


        double storageSize = SequencingSynthesizerBlockEntity.DEFAULT_STORAGE;

        blockEntity.getTank().drainInternal(Fluid.BUCKET_VOLUME / 2, true);
        blockEntity.setBoneAmount(blockEntity.getBoneAmount() - storageSize / 2);
        blockEntity.setPlantAmount(blockEntity.getPlantAmount() - storageSize / 2);
        blockEntity.setSugarAmount(blockEntity.getSugarAmount() - storageSize / 2);

    }

    @Override
    public boolean acceptsInputSlot(SequencingSynthesizerBlockEntity blockEntity, int slotIndex, ItemStack testStack, MachineModuleBlockEntity.MachineProcess process) {
        switch (slotIndex) {
            case 0: return testStack.getItem() == ItemHandler.EMPTY_TEST_TUBE;
        }
        return false;
    }

    private ItemStack createStack(SequencingSynthesizerBlockEntity blockEntity, boolean testOnly) {
        if(blockEntity.getWorld().isRemote) {
            return ItemStack.EMPTY;
        }
        NBTTagCompound driveNbt = blockEntity.getHandler().getStackInSlot(0).getOrCreateSubCompound(ProjectNublar.MODID).getCompoundTag("drive_information");

        String key = blockEntity.getSelectKey(1);
        if(!key.isEmpty() && ProjectNublar.DINOSAUR_REGISTRY.containsKey(new ResourceLocation(key))) {
            ItemStack out = new ItemStack(ItemHandler.TEST_TUBES_DNA.get(ProjectNublar.DINOSAUR_REGISTRY.getValue(new ResourceLocation(key))));
            NBTTagCompound nbt = out.getOrCreateSubCompound(ProjectNublar.MODID).getCompoundTag("dna_info");
            for (int i = 1; i < 4; i++) {
                String dKey = blockEntity.getSelectKey(i);
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("translation_key", driveNbt.getCompoundTag(dKey).getString("translation_key"));
                tag.setInteger("amount", (int) (blockEntity.getSelectAmount(i) * 100D));
                nbt.setTag(dKey, tag);
                if(!testOnly) {
                    NBTTagCompound driveTag = driveNbt.getCompoundTag(dKey);
                    int amount = driveTag.getInteger("amount") - (int)(blockEntity.getSelectAmount(i) * 100D);
                    if(amount <= 0) {
                        driveNbt.removeTag(dKey);
                    } else {
                        driveTag.setInteger("amount", amount);
                    }

                }
            }
            return out;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ResourceLocation getRegistryName() {
        return this.registryName;
    }

    // TODO: test values, change for balance
    @Override
    public int getCurrentConsumptionPerTick(SequencingSynthesizerBlockEntity blockEntity, MachineModuleBlockEntity.MachineProcess process) {
        return 20;
    }

    @Override
    public int getCurrentProductionPerTick(SequencingSynthesizerBlockEntity blockEntity, MachineModuleBlockEntity.MachineProcess process) {
        return 0;
    }
}
