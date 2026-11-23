package oxy.geyser.fp.util.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Config(@JsonProperty("show-position-by-default")
                     boolean showPositionByDefault, @JsonProperty("capped-value") int maxPosition) {
    /** Values from config.yml, used when the config file cannot be written or read. */
    public static final Config DEFAULT = new Config(true, 3000);
}
