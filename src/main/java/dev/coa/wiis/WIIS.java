package dev.coa.wiis;

public abstract class WIIS {
    private static WIIS instance;

    public static final String ID = "wiis";
    public static final String NAME = "Why Is It Spawn";
    public static final String VERSION = "3";
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ID.toUpperCase());

    public abstract void registerCommands();

    public abstract Config<?> getConfig();

    protected static void setInstance(WIIS instance) {
        if (WIIS.instance != null) throw new RuntimeException("Unfortunately, it is not possible to replace an already occupied state.");
        WIIS.instance = instance;
    }

    public static WIIS getInstance() {
        return instance;
    }

    public static <C extends Config<?>> C getConfig(Class<C> type) {
        return type.cast(getInstance().getConfig());
    }

    public static String getConfigLocation() {
        return "wiis/config.json";
    }
}