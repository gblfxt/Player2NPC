package com.gblfxt.player2npc.entity;

import com.gblfxt.player2npc.ChunkLoadingManager;
import com.gblfxt.player2npc.Config;
import com.gblfxt.player2npc.Player2NPC;
import com.gblfxt.player2npc.ai.CompanionAI;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CompanionEntity extends PathfinderMob implements Container {
    // Synced data
    private static final EntityDataAccessor<String> DATA_NAME =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_SKIN_URL =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);

    // Inventory (36 slots like player + 4 armor + 1 offhand)
    private final NonNullList<ItemStack> inventory = NonNullList.withSize(36, ItemStack.EMPTY);
    private final NonNullList<ItemStack> armorSlots = NonNullList.withSize(4, ItemStack.EMPTY);
    private ItemStack offhandItem = ItemStack.EMPTY;
    private int selectedSlot = 0;

    // AI Controller
    private CompanionAI aiController;

    public CompanionEntity(EntityType<? extends CompanionEntity> type, Level level) {
        super(type, level);
        // Step height is set via entity type attributes in 1.21.1
        if (!level.isClientSide) {
            this.aiController = new CompanionAI(this);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_NAME, "Companion");
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_SKIN_URL, "");
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            // Tick AI controller
            if (aiController != null) {
                aiController.tick();
            }

            // Item pickup
            pickupItems();

            // Start/update chunk loading
            if (this.tickCount == 1) {
                ChunkLoadingManager.startLoadingChunks(this);
            } else if (this.tickCount % 100 == 0) {
                ChunkLoadingManager.updateChunkLoading(this);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide) {
            ChunkLoadingManager.stopLoadingChunks(this);
        }
        super.remove(reason);
    }

    private void pickupItems() {
        if (!this.isAlive() || !this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }

        int radius = Config.ITEM_PICKUP_RADIUS.get();
        AABB pickupBox = this.getBoundingBox().inflate(radius);

        List<ItemEntity> items = this.level().getEntitiesOfClass(ItemEntity.class, pickupBox,
                item -> !item.isRemoved() && !item.getItem().isEmpty() && item.isAlive());

        for (ItemEntity itemEntity : items) {
            ItemStack stack = itemEntity.getItem();
            int originalCount = stack.getCount();

            ItemStack remaining = addToInventory(stack);
            if (remaining.isEmpty()) {
                this.take(itemEntity, originalCount);
                itemEntity.discard();
            } else if (remaining.getCount() < originalCount) {
                this.take(itemEntity, originalCount - remaining.getCount());
                itemEntity.setItem(remaining);
            }
        }
    }

    public ItemStack addToInventory(ItemStack stack) {
        // Try to stack with existing items first
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack slot = inventory.get(i);
            if (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() < slot.getMaxStackSize()) {
                int toAdd = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(toAdd);
                stack.shrink(toAdd);
                if (stack.isEmpty()) return ItemStack.EMPTY;
            }
        }

        // Find empty slot
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.get(i).isEmpty()) {
                inventory.set(i, stack.copy());
                return ItemStack.EMPTY;
            }
        }

        return stack;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // If damage is disabled, companions are invulnerable
        if (!Config.COMPANIONS_TAKE_DAMAGE.get()) {
            return false;
        }
        boolean wasHurt = super.hurt(source, amount);
        if (wasHurt && aiController != null) {
            aiController.onCompanionHurt();
        }
        return wasHurt;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            // Open companion GUI (inventory/settings)
            // TODO: Implement GUI
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // Owner management
    public void setOwner(@Nullable Player player) {
        this.entityData.set(DATA_OWNER, player != null ? Optional.of(player.getUUID()) : Optional.empty());
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    @Nullable
    public Player getOwner() {
        UUID uuid = getOwnerUUID();
        if (uuid == null) return null;
        return this.level().getPlayerByUUID(uuid);
    }

    public boolean isOwner(Player player) {
        UUID ownerUUID = getOwnerUUID();
        return ownerUUID != null && ownerUUID.equals(player.getUUID());
    }

    // Name management
    public void setCompanionName(String name) {
        this.entityData.set(DATA_NAME, name);
    }

    public String getCompanionName() {
        return this.entityData.get(DATA_NAME);
    }

    @Override
    public Component getDisplayName() {
        String name = getCompanionName();
        return name.isEmpty() ? super.getDisplayName() : Component.literal(name);
    }

    // Skin URL
    public void setSkinUrl(String url) {
        this.entityData.set(DATA_SKIN_URL, url);
    }

    public String getSkinUrl() {
        return this.entityData.get(DATA_SKIN_URL);
    }

    // Inventory slot selection
    public int getSelectedSlot() {
        return selectedSlot;
    }

    public void setSelectedSlot(int slot) {
        this.selectedSlot = Math.max(0, Math.min(slot, 8));
    }

    // Get held items
    @Override
    public ItemStack getMainHandItem() {
        return selectedSlot >= 0 && selectedSlot < inventory.size() ? inventory.get(selectedSlot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack getOffhandItem() {
        return offhandItem;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return armorSlots;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> getMainHandItem();
            case OFFHAND -> offhandItem;
            case HEAD -> armorSlots.get(3);
            case CHEST -> armorSlots.get(2);
            case LEGS -> armorSlots.get(1);
            case FEET -> armorSlots.get(0);
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        switch (slot) {
            case MAINHAND -> inventory.set(selectedSlot, stack);
            case OFFHAND -> offhandItem = stack;
            case HEAD -> armorSlots.set(3, stack);
            case CHEST -> armorSlots.set(2, stack);
            case LEGS -> armorSlots.set(1, stack);
            case FEET -> armorSlots.set(0, stack);
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    // AI Controller access
    public CompanionAI getAIController() {
        return aiController;
    }

    // Process chat message from owner
    public void onChatMessage(Player sender, String message) {
        if (aiController != null && isOwner(sender)) {
            aiController.processMessage(message);
        }
    }

    // NBT Serialization
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        tag.putString("CompanionName", getCompanionName());
        tag.putString("SkinUrl", getSkinUrl());
        tag.putInt("SelectedSlot", selectedSlot);

        UUID owner = getOwnerUUID();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }

        // Save inventory
        ListTag inventoryTag = new ListTag();
        for (int i = 0; i < inventory.size(); i++) {
            if (!inventory.get(i).isEmpty()) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putByte("Slot", (byte) i);
                inventoryTag.add(inventory.get(i).save(this.registryAccess(), slotTag));
            }
        }
        tag.put("Inventory", inventoryTag);

        // Save armor
        ListTag armorTag = new ListTag();
        for (int i = 0; i < armorSlots.size(); i++) {
            if (!armorSlots.get(i).isEmpty()) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putByte("Slot", (byte) i);
                armorTag.add(armorSlots.get(i).save(this.registryAccess(), slotTag));
            }
        }
        tag.put("Armor", armorTag);

        if (!offhandItem.isEmpty()) {
            tag.put("Offhand", offhandItem.save(this.registryAccess()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        setCompanionName(tag.getString("CompanionName"));
        setSkinUrl(tag.getString("SkinUrl"));
        selectedSlot = tag.getInt("SelectedSlot");

        if (tag.hasUUID("Owner")) {
            this.entityData.set(DATA_OWNER, Optional.of(tag.getUUID("Owner")));
        }

        // Load inventory
        ListTag inventoryTag = tag.getList("Inventory", 10);
        for (int i = 0; i < inventoryTag.size(); i++) {
            CompoundTag slotTag = inventoryTag.getCompound(i);
            int slot = slotTag.getByte("Slot") & 255;
            if (slot < inventory.size()) {
                inventory.set(slot, ItemStack.parse(this.registryAccess(), slotTag).orElse(ItemStack.EMPTY));
            }
        }

        // Load armor
        ListTag armorTag = tag.getList("Armor", 10);
        for (int i = 0; i < armorTag.size(); i++) {
            CompoundTag slotTag = armorTag.getCompound(i);
            int slot = slotTag.getByte("Slot") & 255;
            if (slot < armorSlots.size()) {
                armorSlots.set(slot, ItemStack.parse(this.registryAccess(), slotTag).orElse(ItemStack.EMPTY));
            }
        }

        if (tag.contains("Offhand")) {
            offhandItem = ItemStack.parse(this.registryAccess(), tag.getCompound("Offhand")).orElse(ItemStack.EMPTY);
        }

        // Recreate AI controller on load
        if (!this.level().isClientSide && aiController == null) {
            aiController = new CompanionAI(this);
        }
    }

    // Container implementation for inventory access
    @Override
    public int getContainerSize() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        return inventory.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < inventory.size() ? inventory.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(inventory, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(inventory, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < inventory.size()) {
            inventory.set(slot, stack);
        }
    }

    @Override
    public void setChanged() {
        // Inventory changed callback
    }

    @Override
    public boolean stillValid(Player player) {
        return this.isAlive() && player.distanceToSqr(this) < 64.0D;
    }

    @Override
    public void clearContent() {
        inventory.clear();
    }
}
