package org.seedatlas.xaero.gui;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvents;

/** The vanilla button click, which 1.21.1 only plays through a widget instance. */
final class GuiSounds {
	private GuiSounds() {
	}

	static void click(final SoundManager soundManager) {
		soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}
}
