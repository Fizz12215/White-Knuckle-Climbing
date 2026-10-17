package dev.fizz.wkclimb.client;

import dev.fizz.wkclimb.ClimbConfig;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

/**
 * The Sounds menu's "plus or minus" volume slider: 0% in the middle is White Knuckle's volume, the left
 * end is off, the right end the most the sound can really get (ClimbConfig.sliderToVolume). With max 1
 * the handle stops in the middle. The name sits in the left half and the value in the right, so the
 * handle's resting place in the middle covers neither.
 */
public class VolumeSlider extends AbstractSliderButton {
	private static final int HANDLE_GAP = 6;
	private final Component name;
	private final double max;
	private final DoubleConsumer set;
	private Component valueText = Component.empty();

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
		if (pos == -100) {
			valueText = Component.translatable("options.off");
		} else {
			long change = Math.round((ClimbConfig.sliderToVolume(pos, max) - 1) * 100);
			valueText = Component.literal((change > 0 ? "+" : "") + change + "%");
		}
		setMessage(Component.empty().append(name).append(": ").append(valueText)); // narration, tests
	}

	@Override
	protected void applyValue() {
		set.accept(ClimbConfig.sliderToVolume(pos(), max));
	}

	@Override
	protected void extractScrollingStringOverContents(ActiveTextCollector text, Component message, int margin) {
		int middle = getX() + getWidth() / 2;
		text.acceptScrollingWithDefaultCenter(name, getX() + margin, middle - HANDLE_GAP, getY(), getY() + getHeight());
		text.acceptScrollingWithDefaultCenter(valueText, middle + HANDLE_GAP, getX() + getWidth() - margin, getY(), getY() + getHeight());
	}
}
