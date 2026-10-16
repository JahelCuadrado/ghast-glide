package com.einsteinmods.ghastglide.client.config;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Decimal row in the style of YACL's slider controller: shows the value, and while hovered or
 * focused a thin track with a thumb appears on the right. Dragging, clicking the track or the arrow
 * keys change the value in steps of 0.1; values set by code (a preset) are kept exact, so a
 * preset's speeds survive Save untouched.
 */
final class SliderControl extends OptionControl<Double> {
  /** Slider steps per unit: 0.1 per step. */
  private static final double STEPS_PER_UNIT = 10.0;

  private static final int THUMB_WIDTH = 4;
  private static final int TRACK_HEIGHT = 10;
  private static final int VALUE_GAP = 6;

  /** The track takes this fraction of the row width. */
  private static final int TRACK_WIDTH_DIVISOR = 3;

  private final Component description;
  private final double min;
  private final double max;
  private final String valueFormat;
  private boolean dragging;

  SliderControl(
      Font font,
      Component name,
      Component description,
      double min,
      double max,
      String valueFormat,
      double initial,
      double defaultValue,
      Consumer<Double> onPlayerChange) {
    super(font, name, initial, defaultValue, onPlayerChange);
    this.description = description;
    this.min = min;
    this.max = max;
    this.valueFormat = valueFormat;
  }

  @Override
  public Component description() {
    return description;
  }

  @Override
  protected Component valueText() {
    return Component.literal(String.format(valueFormat, get()));
  }

  @Override
  protected int controlWidth() {
    int valueWidth = super.controlWidth();
    return showsTrack() ? trackWidth() + VALUE_GAP + THUMB_WIDTH / 2 + valueWidth : valueWidth;
  }

  @Override
  protected void extractControl(GuiGraphicsExtractor graphics, int textY) {
    if (!showsTrack()) {
      super.extractControl(graphics, textY);
      return;
    }
    int left = trackLeft();
    int right = trackRight();
    int middle = centerY();
    graphics.fill(left, middle - 1, right, middle, TEXT_COLOR);
    graphics.fill(left + 1, middle, right + 1, middle + 1, CONTROL_SHADOW_COLOR);
    int thumbX = left + (int) Math.round((right - left) * fraction());
    int top = middle - TRACK_HEIGHT / 2;
    int bottom = top + TRACK_HEIGHT;
    int halfThumb = THUMB_WIDTH / 2;
    graphics.fill(
        thumbX - halfThumb + 1, top + 1, thumbX + halfThumb + 1, bottom + 1, CONTROL_SHADOW_COLOR);
    graphics.fill(thumbX - halfThumb, top, thumbX + halfThumb, bottom, TEXT_COLOR);
    Component text = valueText();
    graphics.text(
        font, text, left - halfThumb - VALUE_GAP - font.width(text), textY, TEXT_COLOR, true);
  }

  @Override
  public void onClick(MouseButtonEvent event, boolean doubleClick) {
    dragging = isOverTrack(event.x());
    if (dragging) {
      setFromMouse(event.x());
    }
  }

  @Override
  protected void onDrag(MouseButtonEvent event, double dx, double dy) {
    if (dragging) {
      setFromMouse(event.x());
    }
  }

  @Override
  public void onRelease(MouseButtonEvent event) {
    dragging = false;
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    if (event.isLeft() || event.isRight()) {
      double step = (event.isLeft() ? -1.0 : 1.0) / STEPS_PER_UNIT;
      setByPlayer(snap(get() + step));
      return true;
    }
    return false;
  }

  private boolean showsTrack() {
    return active && (isHoveredOrFocused() || dragging);
  }

  private int trackWidth() {
    return getWidth() / TRACK_WIDTH_DIVISOR;
  }

  private int trackRight() {
    return getRight() - X_PADDING - THUMB_WIDTH / 2;
  }

  private int trackLeft() {
    return trackRight() - trackWidth();
  }

  private boolean isOverTrack(double mouseX) {
    return mouseX >= trackLeft() - THUMB_WIDTH && mouseX <= trackRight() + THUMB_WIDTH;
  }

  private double fraction() {
    return (get() - min) / (max - min);
  }

  private void setFromMouse(double mouseX) {
    double ratio = Mth.clamp((mouseX - trackLeft()) / (trackRight() - trackLeft()), 0.0, 1.0);
    setByPlayer(snap(min + ratio * (max - min)));
  }

  private double snap(double raw) {
    return Mth.clamp(Math.round(raw * STEPS_PER_UNIT) / STEPS_PER_UNIT, min, max);
  }
}
