package com.goodbird.player2npc.client.gui;

import adris.altoclef.player2api.Character;
import adris.altoclef.player2api.utils.CharacterUtils;
import com.goodbird.player2npc.Player2NPC;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;

public class CharacterSelectionScreen extends Screen {

    private Character[] characters = null;
    private boolean isLoading = true;
    private Text statusMessage = null;

    public CharacterSelectionScreen() {
        super(Text.of("Select a Character"));
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();
        isLoading = true;
        statusMessage = null;

        MinecraftClient minecraftClient = MinecraftClient.getInstance();

        CompletableFuture.supplyAsync(
                        () -> CharacterUtils.requestCharacters(minecraftClient.player, "player2-ai-npc-minecraft"))
                .whenCompleteAsync((result, throwable) -> {
                    this.isLoading = false;

                    if (throwable != null) {
                        Player2NPC.LOGGER.error("Failed to load Player2 characters", throwable);
                        this.characters = new Character[0];
                        this.statusMessage = Text.of("Failed to load characters");
                        return;
                    }

                    this.characters = (result != null) ? result : new Character[0];
                    this.statusMessage = (this.characters.length == 0)
                            ? Text.of("No characters available")
                            : null;

                    this.clearChildren();
                    this.createCharacterCards();
                }, minecraftClient);
    }

    private void createCharacterCards() {
        if (characters == null || characters.length == 0) return;

        int cardWidth = 100;
        int cardHeight = 130;
        int padding = 30;
        int cardsPerRow = Math.max(1, (this.width - padding) / (cardWidth + padding));

        int totalWidth = cardsPerRow * (cardWidth + padding) - padding;
        int startX = this.width / 2 - totalWidth / 2;
        int startY = 70;

        int currentX = startX;
        int currentY = startY;

        for (Character character : characters) {
            this.addDrawableChild(new CharacterCardWidget(currentX, currentY, cardWidth, cardHeight, character, this::onCharacterClicked));

            currentX += cardWidth + padding;
            if (currentX + cardWidth > startX + totalWidth) {
                currentX = startX;
                currentY += cardHeight + padding;
            }
        }
    }

    private void onCharacterClicked(Character character) {
        if (this.client != null) {
            this.client.setScreen(new CharacterDetailScreen(this, character));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics);

        graphics.drawCenteredShadowedText(this.textRenderer, "Select a Character", this.width / 2, 20, 0xFFFFFF);

        if (isLoading) {
            graphics.drawCenteredShadowedText(this.textRenderer, "Loading...", this.width / 2, this.height / 2, 0xAAAAAA);
        } else if (statusMessage != null) {
            graphics.drawCenteredShadowedText(this.textRenderer, statusMessage.getString(), this.width / 2,
                    this.height / 2, 0xAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}