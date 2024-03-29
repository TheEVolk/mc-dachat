package com.theevolk.dachat.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "dachat")
public class ModConfig implements ConfigData {
  public String token = "";
}
