package dev.coa.wiis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface WIIS {
    String ID = "wiis";
    String NAME = "Why Is It Spawn";
    String VERSION = "3";
    Logger LOGGER = LoggerFactory.getLogger(ID.toUpperCase());

    void registerCommands();
}
