package cc.irori.refixes.early;

import com.hypixel.hytale.server.core.Options;
import joptsimple.OptionSpec;

public class RefixesOptions {

    public static final OptionSpec<String> OAUTH_ACCESS_TOKEN = Options.PARSER
            .accepts("oauth-access-token", "OAuth access token")
            .withRequiredArg()
            .ofType(String.class);

    public static final OptionSpec<String> OAUTH_REFRESH_TOKEN = Options.PARSER
            .accepts("oauth-refresh-token", "OAuth refresh token for automatic renewal")
            .withRequiredArg()
            .ofType(String.class);

    public static final OptionSpec<Long> OAUTH_ACCESS_EXPIRES = Options.PARSER
            .accepts("oauth-access-expires", "OAuth access token expiry (epoch seconds)")
            .withRequiredArg()
            .ofType(Long.class);

    public static final OptionSpec<String> PROFILE_UUID = Options.PARSER
            .accepts("profile-uuid", "Game profile UUID")
            .withRequiredArg()
            .ofType(String.class);

    public static final OptionSpec<String> PROFILE_NAME = Options.PARSER
            .accepts("profile-name", "Game profile display name")
            .withRequiredArg()
            .ofType(String.class);

    public static void init() {
        // triggers class loading / static init
    }
}
