package cc.irori.refixes.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

@Name("refixes.Action")
@Label("Refixes Action")
@Category({"Mods", "Refixes"})
@Description("Something Refixes did, such as a watchdog alert, a world restart or a view radius change")
@StackTrace(false)
public final class ActionEvent extends Event {

    @Label("Category")
    @Description("The Refixes part that acted, for example Watchdog or PerPlayerHotRadius")
    public String category;

    @Label("Message")
    @Description("What happened, without player names, UUIDs, IP addresses or file paths")
    public String message;
}
