package dev.fizz.wkclimb.client;

import dev.fizz.wkclimb.ClimbConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

/**
 * The Sounds menu's "plus or minus" volume slider: 0% in the middle is White Knuckle's volume, the left
 * end is off, the right end the most the sound can really get (ClimbConfig.sliderToVolume). With max 1
 * the handle stops in the middle.
 */
public class VolumeSlider extends AbstractSliderButton {
	private final Component name;
	private final double max;
	private final DoubleConsumer set;

	/** Label and tooltip from wkclimb.settings.<key>(.tooltip). */
	public VolumeSlider(String key, double volume, double max, DoubleConsumer set) {
		super(0, 0, 150, 20, Component.empty(), (ClimbConfig.volumeToSlider(volume, max) + 100) / 200.0);
		this.name = Component.translatable("wkclimb.settings." + key);
		this.max = max;
		this.set = set;
		setTooltip(Tooltip.create(Component.translatable("wkclimb.settings." + key + ".tooltip")));
		updateMessage();
	}

	private int pos() {
		return (int) Math.round(value * 200) - 100;
	}

	@Override
	protected void setValue(double v) {
		super.setValue(max > 1 ? v : Math.min(v, 0.5)); // nothing louder to offer: stop at 0%
	}

	@Override
	protected void updateMessage() {
		int pos = pos();
		Component valueText = pos == -100 ? Component.translatable("options.off")
				: Component.literal(String.format("%+d%%", Math.round((ClimbConfig.sliderToVolume(pos, max) - 1) * 100)).replace("+0%", "0%"));
		setMessage(Component.empty().append(name).append(": ").append(valueText));
	}

	@Override
	protected void applyValue() {
		set.accept(ClimbConfig.sliderToVolume(pos(), max));
	}
}
