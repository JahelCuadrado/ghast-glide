package com.einsteinmods.ghastglide.client.config;

import net.minecraft.network.chat.Component;

/** Something the description panel can show: an option or a group header. */
interface DescribedOption {
  Component name();

  Component description();
}
