package com.goodbird.player2npc.network;

import adris.altoclef.player2api.Character;
import adris.altoclef.player2api.utils.CharacterUtils;
import baritone.api.entity.LivingEntityInventory;
import com.goodbird.player2npc.Player2NPC;
import com.goodbird.player2npc.companion.AutomatoneEntity;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public class AutomatonSpawnPacket implements FabricPacket {

    public static final PacketType<AutomatonSpawnPacket> TYPE = PacketType.create(
            Player2NPC.SPAWN_PACKET_ID,
            AutomatonSpawnPacket::new
    );

    private final int id;
    private final UUID uuid;
    private final Vec3d pos;
    private final Vec3d velocity;
    private final float pitch;
    private final float yaw;
    private final Character character;
    private final LivingEntityInventory inventory;

    private AutomatonSpawnPacket(AutomatoneEntity entity) {
        this.id = entity.getId();
        this.uuid = entity.getUuid();
        this.pos = entity.getPos();
        this.velocity = entity.getVelocity();
        this.pitch = entity.getPitch();
        this.yaw = entity.getYaw();

        this.character = entity.getCharacter();
        this.inventory = entity.inventory;
    }

    public AutomatonSpawnPacket(PacketByteBuf buf) {
        this.id = buf.readVarInt();
        this.uuid = buf.readUuid();
        this.pos = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        double velX = buf.readShort() / 8000.0;
        double velY = buf.readShort() / 8000.0;
        double velZ = buf.readShort() / 8000.0;
        this.velocity = new Vec3d(velX, velY, velZ);
        this.pitch = (buf.readByte() * 360) / 256.0F;
        this.yaw = (buf.readByte() * 360) / 256.0F;

        this.character = CharacterUtils.readFromBuf(buf);
        this.inventory = new LivingEntityInventory(null);
        this.inventory.readNbt(buf.readNbt().getList("inv", 10));
    }

    public static Packet<ClientPlayPacketListener> create(AutomatoneEntity entity) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        new AutomatonSpawnPacket(entity).write(buf);
        return ServerPlayNetworking.createS2CPacket(Player2NPC.SPAWN_PACKET_ID, buf);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeVarInt(this.id);
        buf.writeUuid(this.uuid);
        buf.writeDouble(this.pos.x);
        buf.writeDouble(this.pos.y);
        buf.writeDouble(this.pos.z);
        buf.writeShort((int) (MathHelper.clamp(this.velocity.x, -3.9, 3.9) * 8000.0));
        buf.writeShort((int) (MathHelper.clamp(this.velocity.y, -3.9, 3.9) * 8000.0));
        buf.writeShort((int) (MathHelper.clamp(this.velocity.z, -3.9, 3.9) * 8000.0));
        buf.writeByte((byte) ((int) (this.pitch * 256.0F / 360.0F)));
        buf.writeByte((byte) ((int) (this.yaw * 256.0F / 360.0F)));

        CharacterUtils.writeToBuf(buf, character);
        NbtCompound compound = new NbtCompound();
        compound.put("inv", inventory.writeNbt(new NbtList()));
        buf.writeNbt(compound);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    public static void handle(MinecraftClient client, ClientPlayNetworkHandler var2, PacketByteBuf var3, PacketSender var4) {
        AutomatonSpawnPacket packet = new AutomatonSpawnPacket(var3);
        client.execute(() -> {
            ClientWorld world = var2.getWorld();
            AutomatoneEntity entity = new AutomatoneEntity(Player2NPC.AUTOMATONE, world);
            entity.setId(packet.id);
            entity.setUuid(packet.uuid);
            entity.syncPacketPositionCodec(packet.pos.x, packet.pos.y, packet.pos.z);
            entity.refreshPositionAfterTeleport(packet.pos.x, packet.pos.y, packet.pos.z);
            entity.setVelocity(packet.velocity);
            entity.setPitch(packet.pitch);
            entity.setYaw(packet.yaw);

            entity.setCharacter(packet.character);
            packet.inventory.player = entity;
            entity.inventory = packet.inventory;

            world.addEntity(packet.id, entity);
        });
    }
}