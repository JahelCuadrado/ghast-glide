package com.einsteinmods.ghastglide.client.config;

import com.einsteinmods.ghastglide.client.ClientGlideState;
import com.einsteinmods.ghastglide.client.GlideKeyMappings;
import com.einsteinmods.ghastglide.core.ControlScheme;
import com.einsteinmods.ghastglide.core.FlightPreset;
import com.einsteinmods.ghastglide.core.GlideSettings;
import com.einsteinmods.ghastglide.core.LeaveMode;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Mod settings built from vanilla widgets, laid out like the YACL screen the mod used before: a tab
 * bar with the category, the options list on the left two thirds, and on the right a description of
 * the hovered option above the Cancel/Reset, Undo and Save/Done buttons.
 *
 * <p>As in YACL, changes stay pending until "Save Changes", which keeps the screen open; with
 * nothing pending that button reads "Done" and closes. "Cancel" (or Escape) discards pending
 * changes and closes; with nothing pending it reads "Reset" and sets every option to its default
 * (still pending, so Undo brings the previous values back).
 *
 * <p>Keys are edited as in {@code KeyBindsScreen}: click a key row, then press a key or mouse
 * button; Escape unbinds. The vanilla {@link KeyMapping} stays the only store, so Options →
 * Controls shows the same bindings; new keys apply on save.
 */
public final class GlideSettingsScreen extends Screen {
  private static final String KEY_PREFIX = "config.ghastglide.";
  private static final String DESCRIPTION_SUFFIX = ".description";
  private static final String TOOLTIP_SUFFIX = ".tooltip";
  private static final String PRESET_KEY_PREFIX = KEY_PREFIX + "preset.";
  private static final String LEAVE_MODE_KEY = KEY_PREFIX + "leave_mode";
  private static final String CONTROL_SCHEME_KEY = KEY_PREFIX + "controls";
  private static final String VISIBILITY_KEY_PREFIX = KEY_PREFIX + "first_person_visibility.";
  private static final String MULTIPLIER_FORMAT = "x%.1f";
  private static final String SECONDS_FORMAT = "%.1f s";

  private static final Component SAVE = Component.translatable(KEY_PREFIX + "save");
  private static final Tooltip SAVE_TOOLTIP = tooltip("save");
  private static final Tooltip DONE_TOOLTIP = tooltip("done");
  private static final Tooltip CANCEL_TOOLTIP = tooltip("cancel");
  private static final Component RESET_ALL = Component.translatable("controls.reset");
  private static final Tooltip RESET_ALL_TOOLTIP = tooltip("reset");
  private static final Component UNDO = Component.translatable(KEY_PREFIX + "undo");
  private static final Tooltip UNDO_TOOLTIP = tooltip("undo");

  /** Darker texture behind the description pane, as YACL. */
  private static final Identifier MENU_LIST_BACKGROUND =
      Identifier.withDefaultNamespace("textures/gui/menu_list_background.png");

  /** Background and separator textures tile in squares of this size. */
  private static final int TEXTURE_SIZE = 32;

  private static final int TAB_HEADER_TEXTURE_SIZE = 16;

  private static final int SEPARATOR_THICKNESS = 2;
  private static final float VERTICAL_RADIANS = (float) Math.toRadians(90.0);

  /** The options list takes two of three columns; the right pane the third. */
  private static final int COLUMNS = 3;

  private static final int LIST_COLUMNS = 2;

  /** Pane padding is this fraction of the column width, as YACL. */
  private static final int PADDING_DIVISOR = 20;

  private static final int MAX_COLUMN_WIDTH = 400;
  private static final int BUTTON_HEIGHT = 20;
  private static final int BUTTON_ROW_GAP = 2;
  private static final int BUTTON_GAP = 4;

  private final @Nullable Screen parent;
  private final ClientGlideState state;
  private final CyclingControl<ControlScheme> controlScheme;
  private final SliderControl horizontal;
  private final SliderControl vertical;
  private final SliderControl sprint;
  private final SliderControl acceleration;
  private final CyclingControl<FlightPreset> preset;
  private final TickBoxControl noclip;
  private final CyclingControl<LeaveMode> leaveMode;
  private final TickBoxControl hangBoat;
  private final TickBoxControl buildThroughGhast;
  private final TickBoxControl mineAtNormalSpeed;
  private final TickBoxControl passThroughEntities;
  private final CyclingControl<FirstPersonVisibility> ghastFirstPerson;
  private final CyclingControl<FirstPersonVisibility> cargoFirstPerson;

  /** Key rows in display order, each with the mapping it edits. */
  private final Map<KeyMapping, KeyBindControl> keyControls = new LinkedHashMap<>();

  /** Every control, for Save, Undo and Reset. */
  private final List<OptionControl<?>> controls = new ArrayList<>();

  private final GlideSettingsList list;
  private final OptionDescriptionPanel description;
  private final Button cancelOrReset;
  private final Button undo;
  private final Button saveOrDone;
  private final TabManager tabManager =
      new TabManager(this::addRenderableWidget, this::removeWidget);
  private final FlightTab flightTab = new FlightTab();
  private @Nullable MenuTabBar tabBar;
  private @Nullable ScreenRectangle descriptionArea;
  private @Nullable ScreenRectangle rightPane;
  private @Nullable KeyBindControl capturingKey;

  public GlideSettingsScreen(
      @Nullable Screen parent, ClientGlideState state, GlideKeyMappings keyMappings) {
    super(Component.translatable(KEY_PREFIX + "title"));
    this.parent = parent;
    this.state = state;
    GlideSettings settings = state.requested();
    GlideSettings defaults = GlideSettings.DEFAULTS;
    Consumer<Double> onSpeedChange = value -> selectMatchingPreset();
    controlScheme =
        register(
            new CyclingControl<>(
                font,
                Component.translatable(CONTROL_SCHEME_KEY),
                Arrays.asList(ControlScheme.values()),
                value -> Component.translatable(controlSchemeKey(value)),
                value -> Component.translatable(controlSchemeKey(value) + DESCRIPTION_SUFFIX),
                settings.controlScheme(),
                defaults.controlScheme(),
                value -> updateButtons()));
    Map<FlightPreset, Component> presetNames = new EnumMap<>(FlightPreset.class);
    Map<FlightPreset, Component> presetDescriptions = new EnumMap<>(FlightPreset.class);
    for (FlightPreset value : FlightPreset.values()) {
      presetNames.put(value, Component.translatable(presetKey(value)));
      presetDescriptions.put(value, Component.translatable(presetKey(value) + DESCRIPTION_SUFFIX));
    }
    preset =
        register(
            new CyclingControl<>(
                font,
                Component.translatable(KEY_PREFIX + "preset"),
                Arrays.asList(FlightPreset.values()),
                presetNames::get,
                presetDescriptions::get,
                FlightPreset.matching(settings),
                FlightPreset.matching(defaults),
                this::applyPreset));
    horizontal =
        slider(
            "horizontal_speed",
            GlideSettings.MIN_SPEED_MULTIPLIER,
            GlideSettings.MAX_SPEED_MULTIPLIER,
            MULTIPLIER_FORMAT,
            settings.horizontalMultiplier(),
            defaults.horizontalMultiplier(),
            onSpeedChange);
    vertical =
        slider(
            "vertical_speed",
            GlideSettings.MIN_SPEED_MULTIPLIER,
            GlideSettings.MAX_SPEED_MULTIPLIER,
            MULTIPLIER_FORMAT,
            settings.verticalMultiplier(),
            defaults.verticalMultiplier(),
            onSpeedChange);
    sprint =
        slider(
            "sprint_multiplier",
            GlideSettings.MIN_SPRINT_MULTIPLIER,
            GlideSettings.MAX_SPRINT_MULTIPLIER,
            MULTIPLIER_FORMAT,
            settings.sprintMultiplier(),
            defaults.sprintMultiplier(),
            onSpeedChange);
    acceleration =
        slider(
            "acceleration",
            GlideSettings.MIN_ACCELERATION_SECONDS,
            GlideSettings.MAX_ACCELERATION_SECONDS,
            SECONDS_FORMAT,
            settings.accelerationSeconds(),
            defaults.accelerationSeconds(),
            onSpeedChange);
    noclip = toggle("noclip", settings.noclipEnabled(), defaults.noclipEnabled());
    leaveMode =
        register(
            new CyclingControl<>(
                font,
                Component.translatable(LEAVE_MODE_KEY),
                Arrays.asList(LeaveMode.values()),
                value -> Component.translatable(leaveModeKey(value)),
                value -> Component.translatable(leaveModeKey(value) + DESCRIPTION_SUFFIX),
                settings.leaveMode(),
                defaults.leaveMode(),
                value -> updateButtons()));
    hangBoat = toggle("hang_boat", settings.hangBoatWithClick(), defaults.hangBoatWithClick());
    buildThroughGhast =
        toggle("build_through_ghast", settings.buildThroughGhast(), defaults.buildThroughGhast());
    mineAtNormalSpeed =
        toggle("mine_at_normal_speed", settings.mineAtNormalSpeed(), defaults.mineAtNormalSpeed());
    passThroughEntities =
        toggle(
            "pass_through_entities",
            settings.passThroughEntities(),
            defaults.passThroughEntities());
    ClientPreferences preferences = state.preferences();
    ghastFirstPerson =
        visibility(
            "ghast_first_person",
            preferences.ghastFirstPerson(),
            ClientPreferences.DEFAULTS.ghastFirstPerson());
    cargoFirstPerson =
        visibility(
            "cargo_first_person",
            preferences.cargoFirstPerson(),
            ClientPreferences.DEFAULTS.cargoFirstPerson());
    Component keyDescription = Component.translatable(KEY_PREFIX + "key" + DESCRIPTION_SUFFIX);
    Component waiting = Component.translatable(KEY_PREFIX + "key.waiting");
    for (KeyMapping mapping : keyMappings.configurable()) {
      keyControls.put(
          mapping,
          register(
              new KeyBindControl(
                  font,
                  Component.translatable(mapping.getName()),
                  keyDescription,
                  waiting,
                  KeyMappingHelper.getBoundKeyOf(mapping),
                  mapping.getDefaultKey(),
                  this::startCapture,
                  key -> updateButtons())));
    }

    description = new OptionDescriptionPanel(font, preset);
    list = new GlideSettingsList(minecraft, description::show);
    for (OptionControl<?> option :
        List.<OptionControl<?>>of(
            controlScheme,
            preset,
            horizontal,
            vertical,
            sprint,
            acceleration,
            noclip,
            leaveMode,
            hangBoat,
            buildThroughGhast,
            mineAtNormalSpeed,
            passThroughEntities,
            ghastFirstPerson,
            cargoFirstPerson)) {
      list.addOption(option);
    }
    list.addGroup(
        Component.translatable(KEY_PREFIX + "group.keys"),
        Component.translatable(KEY_PREFIX + "group.keys" + DESCRIPTION_SUFFIX));
    keyControls.values().forEach(list::addOption);

    cancelOrReset = Button.builder(CommonComponents.GUI_CANCEL, button -> cancelOrReset()).build();
    undo = Button.builder(UNDO, button -> undoAll()).tooltip(UNDO_TOOLTIP).build();
    saveOrDone = Button.builder(CommonComponents.GUI_DONE, button -> saveOrDone()).build();
    updateButtons();
  }

  @Override
  protected void init() {
    MenuTabBar bar = MenuTabBar.builder(tabManager, width).addTabs(flightTab).build();
    tabBar = bar;
    addRenderableWidget(bar);
    bar.selectTab(0, false);
    repositionElements();
  }

  @Override
  protected void repositionElements() {
    if (tabBar == null) {
      return;
    }
    tabBar.arrangeElements(width);
    int top = tabBar.getRectangle().bottom();
    tabManager.setTabArea(new ScreenRectangle(0, top, width, height - top));
  }

  @Override
  public void onClose() {
    minecraft.gui.setScreen(parent);
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    if (capturingKey == null) {
      return super.keyPressed(event);
    }
    assignCapturedKey(event.isEscape() ? InputConstants.UNKNOWN : InputConstants.getKey(event));
    return true;
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    if (capturingKey == null) {
      return super.mouseClicked(event, doubleClick);
    }
    assignCapturedKey(InputConstants.Type.MOUSE.getOrCreate(event.button()));
    return true;
  }

  /** Tab header texture above, menu background below, as vanilla tabbed screens. */
  @Override
  protected void extractMenuBackground(GuiGraphicsExtractor graphics) {
    int headerHeight = tabBar == null ? 0 : tabBar.getRectangle().bottom();
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        CreateWorldScreen.TAB_HEADER_BACKGROUND,
        0,
        0,
        0.0F,
        0.0F,
        width,
        headerHeight,
        TAB_HEADER_TEXTURE_SIZE,
        TAB_HEADER_TEXTURE_SIZE);
    extractMenuBackground(graphics, 0, headerHeight, width, height - headerHeight);
  }

  /** Darker description pane with a separator along its left edge, as YACL. */
  @Override
  public void extractBackground(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    super.extractBackground(graphics, mouseX, mouseY, partialTick);
    if (rightPane == null) {
      return;
    }
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        minecraft.level == null
            ? MENU_LIST_BACKGROUND
            : AbstractSelectionList.INWORLD_MENU_LIST_BACKGROUND,
        rightPane.left(),
        rightPane.top(),
        rightPane.right(),
        rightPane.bottom(),
        rightPane.width(),
        rightPane.height(),
        TEXTURE_SIZE,
        TEXTURE_SIZE);
    graphics.pose().pushMatrix();
    graphics.pose().translate(rightPane.left(), rightPane.top());
    graphics.pose().rotate(VERTICAL_RADIANS);
    graphics.blit(
        RenderPipelines.GUI_TEXTURED,
        minecraft.level == null ? FOOTER_SEPARATOR : INWORLD_FOOTER_SEPARATOR,
        0,
        0,
        0.0F,
        0.0F,
        rightPane.height(),
        SEPARATOR_THICKNESS,
        TEXTURE_SIZE,
        SEPARATOR_THICKNESS);
    graphics.pose().popMatrix();
  }

  @Override
  public void extractRenderState(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    if (descriptionArea != null) {
      description.extract(graphics, descriptionArea);
    }
  }

  private <C extends OptionControl<?>> C register(C control) {
    controls.add(control);
    return control;
  }

  private SliderControl slider(
      String key,
      double min,
      double max,
      String valueFormat,
      double initial,
      double defaultValue,
      Consumer<Double> onPlayerChange) {
    return register(
        new SliderControl(
            font,
            Component.translatable(KEY_PREFIX + key),
            Component.translatable(KEY_PREFIX + key + DESCRIPTION_SUFFIX),
            min,
            max,
            valueFormat,
            initial,
            defaultValue,
            onPlayerChange));
  }

  private TickBoxControl toggle(String key, boolean initial, boolean defaultValue) {
    return register(
        new TickBoxControl(
            font,
            Component.translatable(KEY_PREFIX + key),
            Component.translatable(KEY_PREFIX + key + DESCRIPTION_SUFFIX),
            initial,
            defaultValue,
            value -> updateButtons()));
  }

  /** A visibility row: the value names are shared, the description belongs to the row. */
  private CyclingControl<FirstPersonVisibility> visibility(
      String key, FirstPersonVisibility initial, FirstPersonVisibility defaultValue) {
    Component description = Component.translatable(KEY_PREFIX + key + DESCRIPTION_SUFFIX);
    return register(
        new CyclingControl<>(
            font,
            Component.translatable(KEY_PREFIX + key),
            Arrays.asList(FirstPersonVisibility.values()),
            value -> Component.translatable(visibilityKey(value)),
            value -> description,
            initial,
            defaultValue,
            value -> updateButtons()));
  }

  private static Tooltip tooltip(String button) {
    return Tooltip.create(Component.translatable(KEY_PREFIX + button + TOOLTIP_SUFFIX));
  }

  private void startCapture(KeyBindControl control) {
    if (capturingKey != null) {
      capturingKey.setCapturing(false);
    }
    capturingKey = control;
    control.setCapturing(true);
  }

  private void assignCapturedKey(InputConstants.Key key) {
    KeyBindControl control = capturingKey;
    capturingKey = null;
    if (control != null) {
      control.assign(key);
    }
  }

  /** Choosing a preset moves the sliders; {@link FlightPreset#CUSTOM} keeps them as they are. */
  private void applyPreset(FlightPreset chosen) {
    GlideSettings target = chosen.applyTo(pendingSettings());
    horizontal.setQuietly(target.horizontalMultiplier());
    vertical.setQuietly(target.verticalMultiplier());
    sprint.setQuietly(target.sprintMultiplier());
    acceleration.setQuietly(target.accelerationSeconds());
    updateButtons();
  }

  /** Moving a slider selects the preset the values match, which is CUSTOM unless one equals it. */
  private void selectMatchingPreset() {
    preset.setQuietly(FlightPreset.matching(pendingSettings()));
    updateButtons();
  }

  private GlideSettings pendingSettings() {
    return new GlideSettings(
        horizontal.get(),
        vertical.get(),
        sprint.get(),
        acceleration.get(),
        noclip.get(),
        leaveMode.get(),
        controlScheme.get(),
        hangBoat.get(),
        buildThroughGhast.get(),
        mineAtNormalSpeed.get(),
        passThroughEntities.get());
  }

  private boolean hasPendingChanges() {
    return controls.stream().anyMatch(OptionControl::isChanged);
  }

  /**
   * Button labels follow YACL: Save Changes / Done, Cancel / Reset; Undo only with changes. Every
   * value change ends here, so it also greys out the speed rows, which vanilla controls ignore
   * (they keep their values).
   */
  private void updateButtons() {
    boolean speedsApply = controlScheme.get() == ControlScheme.MOD;
    for (OptionControl<?> speedRow : List.of(preset, horizontal, vertical, sprint, acceleration)) {
      speedRow.active = speedsApply;
    }
    boolean pending = hasPendingChanges();
    saveOrDone.setMessage(pending ? SAVE : CommonComponents.GUI_DONE);
    saveOrDone.setTooltip(pending ? SAVE_TOOLTIP : DONE_TOOLTIP);
    cancelOrReset.setMessage(pending ? CommonComponents.GUI_CANCEL : RESET_ALL);
    cancelOrReset.setTooltip(pending ? CANCEL_TOOLTIP : RESET_ALL_TOOLTIP);
    undo.active = pending;
  }

  private void saveOrDone() {
    if (!hasPendingChanges()) {
      onClose();
      return;
    }
    state.update(
        pendingSettings(), new ClientPreferences(ghastFirstPerson.get(), cargoFirstPerson.get()));
    applyPendingKeys();
    controls.forEach(OptionControl::markSaved);
    updateButtons();
  }

  private void cancelOrReset() {
    if (hasPendingChanges()) {
      onClose();
      return;
    }
    controls.forEach(OptionControl::resetQuietly);
    updateButtons();
  }

  private void undoAll() {
    controls.forEach(OptionControl::undo);
    updateButtons();
  }

  /** Same steps as vanilla {@code KeyBindsScreen}: set, rebuild the key lookup, save options. */
  private void applyPendingKeys() {
    boolean changed = false;
    for (Map.Entry<KeyMapping, KeyBindControl> entry : keyControls.entrySet()) {
      KeyMapping mapping = entry.getKey();
      InputConstants.Key key = entry.getValue().get();
      if (!key.equals(KeyMappingHelper.getBoundKeyOf(mapping))) {
        mapping.setKey(key);
        changed = true;
      }
    }
    if (changed) {
      KeyMapping.resetMapping();
      minecraft.options.save();
    }
  }

  private static String leaveModeKey(LeaveMode value) {
    return LEAVE_MODE_KEY + "." + value.name().toLowerCase(Locale.ROOT);
  }

  private static String controlSchemeKey(ControlScheme value) {
    return CONTROL_SCHEME_KEY + "." + value.name().toLowerCase(Locale.ROOT);
  }

  private static String visibilityKey(FirstPersonVisibility value) {
    return VISIBILITY_KEY_PREFIX + value.name().toLowerCase(Locale.ROOT);
  }

  private static String presetKey(FlightPreset value) {
    return PRESET_KEY_PREFIX + value.name().toLowerCase(Locale.ROOT);
  }

  /**
   * The single category. Lays out YACL's columns: list on the left two thirds; on the right, the
   * description above Cancel/Reset + Undo, with Save/Done along the bottom.
   */
  private final class FlightTab implements Tab {
    private final FrameLayout bounds = new FrameLayout();

    @Override
    public Component getTabTitle() {
      return Component.translatable(KEY_PREFIX + "category.flight");
    }

    @Override
    public Component getTabExtraNarration() {
      return Component.empty();
    }

    @Override
    public void visitChildren(Consumer<AbstractWidget> childrenConsumer) {
      childrenConsumer.accept(list);
      childrenConsumer.accept(cancelOrReset);
      childrenConsumer.accept(undo);
      childrenConsumer.accept(saveOrDone);
    }

    @Override
    public void doLayout(ScreenRectangle area) {
      bounds.setPosition(area.left(), area.top());
      bounds.setMinDimensions(area.width(), area.height());
      bounds.arrangeElements();
      int column = area.width() / COLUMNS;
      int padding = column / PADDING_DIVISOR;
      int paneWidth = Math.min(column, MAX_COLUMN_WIDTH) - padding * 2;
      int listWidth = column * LIST_COLUMNS;
      int paneCenter = listWidth + column / 2;
      int paneLeft = paneCenter - paneWidth / 2;

      list.updateSizeAndPosition(listWidth + 1, area.height(), area.left(), area.top());
      rightPane =
          new ScreenRectangle(
              area.left() + listWidth + 1, area.top(), area.width() - listWidth - 1, area.height());

      int saveY = area.bottom() - padding - BUTTON_HEIGHT;
      saveOrDone.setRectangle(paneWidth, BUTTON_HEIGHT, paneLeft, saveY);
      int halfWidth = paneWidth / 2 - BUTTON_GAP / 2;
      int topRowY = saveY - BUTTON_HEIGHT - BUTTON_ROW_GAP;
      cancelOrReset.setRectangle(halfWidth, BUTTON_HEIGHT, paneLeft, topRowY);
      undo.setRectangle(halfWidth, BUTTON_HEIGHT, paneLeft + halfWidth + BUTTON_GAP, topRowY);

      int descriptionTop = area.top() + padding;
      descriptionArea =
          new ScreenRectangle(
              paneLeft, descriptionTop, paneWidth, Math.max(0, topRowY - padding - descriptionTop));
    }

    @Override
    public Layout getLayout() {
      return bounds;
    }
  }
}
